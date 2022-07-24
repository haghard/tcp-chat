package server

import server.Guardian.{ ChatMsgReply, ConnectionAcceptedReply }
import shared.Protocol
import shared.Protocol.{ ServerCommand, UserName }

import java.net.InetSocketAddress

final case class State(
    appCfg: scala2.AppConfig,
    pendingAddr: Option[InetSocketAddress] = None,
    usersOnline: Map[InetSocketAddress, UserName] = Map.empty):
  self =>

  def authorize(usr: UserName): (State, ChatMsgReply) =
    if (usr.inBanned(appCfg.bannedUsers))
      (copy(pendingAddr = None), ChatMsgReply.Error(ServerCommand.Disconnect(s"Auth $usr - error")))
    else
      val s =
        pendingAddr match
          case Some(remoteAddress) =>
            copy(pendingAddr = None, usersOnline + (remoteAddress -> usr))
          case None =>
            println("This should never happen !!!")
            self

      (s, ChatMsgReply.DirectResponse(ServerCommand.Welcome(usr, s"Authorized $usr")))

  def disconnect(remoteAddress: InetSocketAddress): State =
    self.copy(pendingAddr = None, usersOnline - remoteAddress)

  def acceptCon(remoteAddress: InetSocketAddress): (State, ConnectionAcceptedReply) =
    (self.copy(pendingAddr = Some(remoteAddress)), ConnectionAcceptedReply.Ok)

  /*if (ThreadLocalRandom.current().nextBoolean())
    (self, ConnectionAcceptedReply.Err(s"$remoteAddress is blacklisted!"))
  else
    (self.copy(pendingAddr = Some(remoteAddress)), ConnectionAcceptedReply.Ok)*/

  def msgReply(usr: UserName, msg: String): ChatMsgReply =
    ChatMsgReply.Broadcast(Protocol.ServerCommand.Message(usr, msg))

  override def toString: String =
    s"(online=[${usersOnline.mkString(",")}])"
