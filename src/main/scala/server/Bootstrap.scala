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
import akka.stream.{
  ActorAttributes,
  Attributes,
  BoundedSourceQueue,
  KillSwitches,
  QueueCompletionResult,
  QueueOfferResult,
  Supervision,
  UniqueKillSwitch,
}
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.*
import akka.stream.scaladsl.*
import akka.stream.typed.scaladsl.ActorFlow
import akka.util.ByteString
import org.slf4j.Logger
import shared.Protocol
import shared.Protocol.{ ClientCommand, ServerCommand }
import shared.crypto.SymmetricCryptography

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import scala.util.{ Failure, Success }

object Bootstrap:
  case object BindFailure extends Reason

final class Bootstrap(
    host: String,
    port: Int,
    appCfg: scala2.AppConfig,
    ecrypter: SymmetricCryptography.Encrypter,
    decrypter: SymmetricCryptography.Decrypter,
  )(using system: ActorSystem[SpawnProtocol.Command]):

  given ec: ExecutionContext = system.executionContext
  given sch: Scheduler = system.scheduler
  given to: akka.util.Timeout = akka.util.Timeout(3.seconds)
  given logger: Logger = system.log

  val bs = appCfg.bufferSize
  val shutdown = CoordinatedShutdown(system)

  def resume(cause: Throwable, logger: Logger) =
    logger.error(s"CharRoom failed and resumes", cause)
    Supervision.Resume

  def mk(
      guardian: ActorRef[Guardian.GCmd.WrappedCmd],
      logger: Logger,
    ) =
    Source
      .queue[ClientCommand](appCfg.bufferSize)
      .mapAsync(1) { clientCmd =>
        // TODO: FIXME handle ask timeout
        guardian
          .ask[Guardian.ChatMsgReply](Guardian.GCmd.WrappedCmd(clientCmd, _))
          .map(_.serverCmd)(ExecutionContext.parasitic)
      }
      .viaMat(KillSwitches.single)(Keep.both)
      .toMat(BroadcastHub.sink[ServerCommand](appCfg.bufferSize))(Keep.both)
      // customize supervision stage per stage
      .addAttributes(ActorAttributes.supervisionStrategy(resume(_, logger)))
      .run()

  def runTcpServer(): Unit =
    system
      .ask((ref: ActorRef[ActorRef[Guardian.GCmd[?]]]) =>
        SpawnProtocol.Spawn(Guardian(appCfg, ecrypter, decrypter), "guardian", Props.empty, ref)
      )
      .foreach { guardian =>

        val ((q, ks), broadcastSource) = mk(guardian, logger)

        /*val showAdvtEvery = 45.seconds
        Source
          .tick(showAdvtEvery, showAdvtEvery, Protocol.ClientCommand.ShowAdvt(msg = "Toyota - Let's Go Places!"))
          .to(Sink.foreach(cmd => q.offer(cmd)))
          .run()*/

        Tcp(system)
          .bind(host, port)
          .to(Sink.foreach { (connection: Tcp.IncomingConnection) =>
            val remote = connection.remoteAddress
            system.log.info("Accepted client from {}:{}", remote.getHostString, remote.getPort)

            guardian
              .ask[Guardian.ConnectionAcceptedReply](Guardian.GCmd.Accept(remote, _))
              .map {
                case Guardian.ConnectionAcceptedReply.Ok =>
                  val connectionFlow: Flow[ByteString, ByteString, NotUsed] =
                    Protocol
                      .ClientCommand
                      .Decoder
                      .takeWhile(
                        _.filter {
                          case ClientCommand.SendMessage(_, msg) =>
                            shared.crypto.base64Decode(msg) match
                              case Some(bts) =>
                                !(new String(decrypter.decrypt(bts), StandardCharsets.UTF_8) == ClientCommand.Quit)
                              case None =>
                                system.log.error(s"Decrypter($msg) error")
                                false
                          case _ => true
                        }.isSuccess
                      )
                      .mapConcat {
                        case Success(cmd) =>
                          if (q.offer(cmd).isEnqueued) Nil else ClientCommand.Backpressured :: Nil
                        case Failure(ex) =>
                          Protocol.ServerCommand.Disconnect(ex.getMessage) :: Nil
                      }
                      /*
                      .statefulMapConcat { () => tryClientCommand =>
                        // 1.auth
                        // 2.keys

                        tryClientCommand match
                          case Success(cmd) =>
                            if (q.offer(cmd).isEnqueued) Nil else Bootstrap.Backpressured :: Nil

                          case Failure(ex) =>
                            Protocol.ServerCommand.Disconnect(ex.getMessage) :: Nil
                      }*/
                      .merge(broadcastSource, eagerComplete = true)
                      .watchTermination() { (_, done) =>
                        done.map { _ =>
                          logger.info(s"Connection from ${remote.getHostString}:${remote.getPort} has been terminated")
                          guardian.tell(Guardian.GCmd.Disconnected(remote))
                        }
                        NotUsed
                      }
                      .via(Protocol.ServerCommand.Encoder)
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
                  // dc.drainAndComplete()
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
