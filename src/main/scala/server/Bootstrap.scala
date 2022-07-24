// Copyright (c) 2022 by Vadim Bondarev
// This software is licensed under the Apache License, Version 2.0.
// You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

package server

import scala2.AppConfig
import akka.{ Done, NotUsed }
import akka.actor.CoordinatedShutdown
import akka.actor.CoordinatedShutdown.{
  PhaseActorSystemTerminate,
  PhaseBeforeServiceUnbind,
  PhaseServiceUnbind,
  Reason,
}
import akka.actor.typed.{ ActorRef, ActorSystem, Props, Scheduler, SpawnProtocol }
import akka.actor.typed.scaladsl.AskPattern.*
import akka.stream.{ ActorAttributes, Attributes, KillSwitches, Supervision, UniqueKillSwitch }
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.*
import akka.stream.scaladsl.*
import akka.stream.typed.scaladsl.ActorFlow
import akka.util.ByteString
import org.slf4j.Logger
import shared.Protocol
import shared.Protocol.{ ClientCommand, ServerCommand }

import java.net.InetSocketAddress
import scala.util.{ Failure, Success }

object Bootstrap:
  case object BindFailure extends Reason

final class Bootstrap(
    host: String,
    port: Int,
    appCfg: scala2.AppConfig,
  )(using system: ActorSystem[SpawnProtocol.Command]):

  given ec: ExecutionContext = system.executionContext
  given sch: Scheduler = system.scheduler
  given to: akka.util.Timeout = akka.util.Timeout(2.seconds)
  given logger: Logger = system.log

  val bs = 1 << 2
  val shutdown = CoordinatedShutdown(system)

  private def serverFlow(guardian: ActorRef[Guardian.GCmd.WrappedCmd], logger: Logger) =
    Protocol
      .ClientCommand
      .Decoder
      .mapAsync(1) {
        case Success(clientCmd) =>
          guardian
            .ask[Guardian.ChatMsgReply](Guardian.GCmd.WrappedCmd(clientCmd, _))
            .map(_.serverCmd)(ExecutionContext.parasitic)
        case Failure(parseError) =>
          Future.successful(
            Guardian
              .ChatMsgReply
              .DirectResponse(ServerCommand.Alert(s"Invalid command: ${parseError.getMessage}"))
              .serverCmd
          )
      }
      // customize supervision stage per stage
      .addAttributes(ActorAttributes.supervisionStrategy(resume(_, logger)))
      .via(Protocol.ServerCommand.Encoder)

  private def createRoom(guardian: ActorRef[Guardian.GCmd[?]], logger: Logger) =
    MergeHub
      .sourceWithDraining[ByteString](bs)
      /*.mapMaterializedValue { sink =>
        logger.info("MergeHub-BroadcastHub")
        sink
      }*/
      .via(serverFlow(guardian, logger))
      .viaMat(KillSwitches.single)(Keep.both)
      .toMat(
        BroadcastHub
          .sink[ByteString](bs)
          /*.mapMaterializedValue { src =>
            // logger.info("BroadcastHub.sink")
            src
          }*/
      )(Keep.both)
      .run()

  def stopConFlow(cause: Throwable, logger: Logger) =
    logger.error(s"Flow failed :", cause)
    Supervision.Stop

  def resume(cause: Throwable, logger: Logger) =
    logger.error(s"CharRoom failed and resumes", cause)
    Supervision.Resume

  def runTcpServer(): Unit =
    system
      .ask((ref: ActorRef[ActorRef[Guardian.GCmd[?]]]) =>
        SpawnProtocol.Spawn(Guardian(appCfg), "guardian", Props.empty, ref)
      )
      .foreach { guardian =>
        val (((sinkHub, dc), ks), sourceHub) = createRoom(guardian, logger)

        Tcp(system)
          .bind(host, port)
          .to(Sink.foreach { (connection: Tcp.IncomingConnection) =>
            val remote = connection.remoteAddress
            system.log.info("Accepted client from {}:{}", remote.getHostString, remote.getPort)

            val showAdvtEvery = 100.millis // seconds
            Source
              .tick(
                showAdvtEvery,
                showAdvtEvery,
                Protocol.ClientCommand.ShowAdvt(msg = "Toyota - Let's Go Places!"),
              )
              .via(Protocol.ClientCommand.Encoder)
              .to(sinkHub)
              .run()

            /*Source
              .single(Protocol.ClientCommand.Connected(host = remote.getHostString, port = remote.getPort))
              .via(Protocol.ClientCommand.Encoder)
              .to(sinkHub)
              .run()*/

            /*
              Ensure that the Broadcast output is dropped if there are no listening parties.
              If this dropping Sink is not attached, then the broadcast hub will not drop any
              elements itself when there are no subscribers, backpressuring the producer instead.
             */
            sourceHub.runWith(Sink.ignore)

            guardian
              .ask[Guardian.ConnectionAcceptedReply](Guardian.GCmd.Accept(remote, _))
              .map {
                case Guardian.ConnectionAcceptedReply.Ok =>
                  // Thread.sleep(2_500)
                  val connectionFlow: Flow[ByteString, ByteString, NotUsed /*UniqueKillSwitch*/ ] =
                    Flow
                      .fromSinkAndSourceCoupled(sinkHub, sourceHub)
                      .addAttributes(ActorAttributes.supervisionStrategy(stopConFlow(_, logger)))
                      // .joinMat(KillSwitches.singleBidi[ByteString, ByteString])(Keep.right)
                      .withAttributes(Attributes.inputBuffer(1, 1))
                      /*.mapMaterializedValue { r =>
                        logger.info(s"Connection from ${remote.getHostString}:${remote.getPort}")
                        r
                      }*/
                      .watchTermination() { (_, done) =>
                        done.map { _ =>
                          logger.info(s"Connection from ${remote.getHostString}:${remote.getPort} has been terminated")
                          guardian.tell(Guardian.GCmd.Disconnected(remote))
                        }
                        NotUsed
                      }
                  connection.handleWith(connectionFlow)

                case Guardian.ConnectionAcceptedReply.Err(msg) =>
                  val rejectingFlow: Flow[ByteString, ByteString, NotUsed] =
                    Flow
                      .fromSinkAndSourceCoupled(Sink.cancelled[ByteString], Source.empty[ByteString])
                      .watchTermination() { (_, done) =>
                        done.map(_ =>
                          logger
                            .info(s"${remote.getHostString}:${remote.getPort} has been rejected due to " + msg)
                        )
                        NotUsed
                      }
                  connection.handleWith(rejectingFlow)
              }
          })
          .run()
          .onComplete {
            case Failure(ex) =>
              logger.error("Tcp binding error: ", ex)
              shutdown.run(Bootstrap.BindFailure)

            case Success(binding: Tcp.ServerBinding) =>
              logger.info(s"Listening for Tcp connections on ${binding.localAddress}")

              shutdown.addTask(PhaseBeforeServiceUnbind, "before-unbind") { () =>
                Future {
                  dc.drainAndComplete()
                  system.log.info("★ ★ ★ before-unbind [draining existing connections]  ★ ★ ★")
                }.flatMap { _ =>
                  akka.pattern.after(3.seconds) {
                    system.log.info("★ ★ ★ before-unbind [drain-sharding.shutdown] ★ ★ ★")
                    ks.shutdown()
                    akka.pattern.after(1.seconds)(Future.successful(akka.Done))
                  }
                }
              // 3 + 1 < 5
              }

              shutdown.addTask(PhaseServiceUnbind, "unbind") { () =>
                // No new connections are accepted. Existing connections are still allowed to perform request/response cycles
                binding.unbind().map { _ =>
                  logger.info("★ ★ ★ CoordinatedShutdown [unbind] ★ ★ ★")
                  Done
                }
              }

              shutdown.addTask(PhaseActorSystemTerminate, "system.term") { () =>
                Future.successful {
                  logger.info("★ ★ ★ CoordinatedShutdown [system.term] ★ ★ ★")
                  Done
                }
              }
          }
      }
