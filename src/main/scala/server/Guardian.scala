// Copyright (c) 2022 by Vadim Bondarev
// This software is licensed under the Apache License, Version 2.0.
// You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

package server

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors, StashBuffer }
import akka.actor.typed.{ ActorRef, Behavior, DispatcherSelector, PostStop }
import shared.Protocol
import shared.Protocol.{ ClientCommand, ServerCommand, UserName }
import shared.crypto.SymmetricCryptography

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ThreadLocalRandom

object Guardian:

  enum ConnectionAcceptedReply:
    case Ok extends ConnectionAcceptedReply
    case Err(msg: String) extends ConnectionAcceptedReply

  enum ChatMsgReply(val serverCmd: ServerCommand):
    case Broadcast(cmd: ServerCommand) extends ChatMsgReply(cmd)
    case Error(cmd: ServerCommand) extends ChatMsgReply(cmd)

  enum CmdSource:
    case Chat, Auth

  enum GCmd[T <: CmdSource & Singleton](val cmdType: T):
    case WrappedCmd(clientCmd: ClientCommand, replyTo: ActorRef[ChatMsgReply]) extends GCmd(CmdSource.Chat)

    case Accept(remoteAddress: InetSocketAddress, replyTo: ActorRef[ConnectionAcceptedReply])
        extends GCmd(CmdSource.Auth)
    case Disconnected(remoteAddress: InetSocketAddress) extends GCmd(CmdSource.Auth)
  end GCmd

  def apply(
      appCfg: scala2.AppConfig,
      ecrypter: SymmetricCryptography.Encrypter,
      decrypter: SymmetricCryptography.Decrypter,
    ): Behavior[GCmd[?]] =
    // Behaviors.supervise()
    Behaviors
      .setup[GCmd[?]] { implicit ctx =>
        // ctx.system.receptionist
        ctx
          .log
          .info(
            s"★ ★ ★ Guardian started [time:{} tz:{}] ★ ★ ★",
            java.time.LocalDateTime.now(),
            java.util.TimeZone.getDefault.getID,
          )

        ctx.log.info("banned-hosts: [{}]", appCfg.bannedHosts.mkString(","))
        ctx.log.info("banned-users: [{}]", appCfg.bannedUsers.mkString(","))
        ctx.log.info("★ ★ ★ ★ ★ ★ ★ ★ ★")

        Behaviors.withStash(1 << 5)(implicit buf => active(State(appCfg, decrypter, ecrypter)))
      }

  def active(state: State)(using ctx: ActorContext[GCmd[?]], buf: StashBuffer[GCmd[?]]): Behavior[GCmd[?]] =
    Behaviors
      .receiveMessage[GCmd[?]] {
        case GCmd.Accept(address, replyTo) =>
          ctx.log.info("1.Accept: {} - {}", address, state)
          val (updatedState, reply) = state.acceptCon(address)
          replyTo.tell(reply)
          reply match
            case ConnectionAcceptedReply.Ok     => authorizeUser(address, updatedState)
            case _: ConnectionAcceptedReply.Err => active(updatedState)

        case GCmd.Disconnected(address) =>
          val updatedState = state.disconnect(address)
          ctx.log.info("3.Disconnected: {} - {}", address, updatedState)
          active(updatedState)

        case cmd: GCmd.WrappedCmd =>
          cmd.clientCmd match
            case ClientCommand.SendMessage(usr, text) =>
              // println(s"$usr: $text")
              shared.crypto.base64Decode(text) match
                case Some(bts) =>
                  val out = new String(state.decrypter.decrypt(bts), StandardCharsets.UTF_8)
                  if (out == Protocol.ClientCommand.List)
                    cmd
                      .replyTo
                      .tell(
                        ChatMsgReply.Broadcast(
                          Protocol
                            .ServerCommand
                            .Message(
                              usr,
                              shared
                                .crypto
                                .base64Encode(
                                  state
                                    .ecrypter
                                    .encrypt(
                                      state
                                        .usersOnline
                                        .values
                                        .map(_.trim())
                                        .mkString(",")
                                        .getBytes(StandardCharsets.UTF_8)
                                    )
                                ),
                            )
                        )
                      )
                  else
                    cmd.replyTo.tell(state.msgReply(usr, text))

                case None =>
                  throw new Exception("Decrypt error !!!")

              Behaviors.same
            case c: ClientCommand.ShowAdvt =>
              cmd.replyTo.tell(state.msgReply(c.usr, c.msg))
              Behaviors.same
            case c: ClientCommand.Authorize =>
              ctx.log.error("Unexpected {} in active !!!", c)
              Behaviors.same
      }
      .receiveSignal {
        case (ctx, PostStop) =>
          ctx.log.warn("PostStop")
          Behaviors.stopped
      }

  def authorizeUser(
      address: InetSocketAddress,
      state: State,
    )(using ctx: ActorContext[GCmd[?]],
      buf: StashBuffer[GCmd[?]],
    ): Behavior[GCmd[?]] =
    Behaviors.receiveMessage[GCmd[?]] {
      case c @ GCmd.WrappedCmd(clientCmd, replyTo) =>
        clientCmd match
          case ClientCommand.Authorize(usr, pub) =>
            ctx.log.info("2.Authorize [{} - {}]", usr, address)
            val (updatedState, reply) = state.authorize(usr, pub)
            replyTo.tell(reply)
            reply match
              case _: ChatMsgReply.Error => ctx.log.info("2.Authorization [{} - {}] Error", usr, address)
              case _                     => ctx.log.info("2.Authorization [{} - {}] Success", usr, address)
            buf.unstashAll(active(updatedState))
          case _ =>
            buf.stash(c)
            Behaviors.same

      case cmd =>
        buf.stash(cmd)
        Behaviors.same
    }
