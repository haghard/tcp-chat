package server

import server.Guardian.{ ChatMsgReply, ConnectionAcceptedReply }
import shared.Protocol
import shared.Protocol.{ ServerCommand, UserName }

import java.net.InetSocketAddress

final case class State(
    appCfg: scala2.AppConfig,
    pendingAddress: Option[InetSocketAddress] = None,
    usersOnline: Map[InetSocketAddress, UserName] = Map.empty):
  self =>

  def authorize(usr: UserName): (State, ChatMsgReply) =
    if (usr.inBanned(appCfg.bannedUsers))
      (copy(pendingAddress = None), ChatMsgReply.Error(ServerCommand.Disconnect(s"Auth $usr - error")))
    else
      val s =
        pendingAddress match
          case Some(remoteAddress) =>
            copy(pendingAddress = None, usersOnline + (remoteAddress -> usr))
          case None =>
            println("This should never happen !!!")
            self
      (s, ChatMsgReply.Broadcast(ServerCommand.Authorized(usr, s"Authorized $usr")))

  def disconnect(remoteAddress: InetSocketAddress): State =
    self.copy(pendingAddress = None, usersOnline - remoteAddress)

  def acceptCon(remoteAddress: InetSocketAddress): (State, ConnectionAcceptedReply) =
    (self.copy(pendingAddress = Some(remoteAddress)), ConnectionAcceptedReply.Ok)

  /*if (ThreadLocalRandom.current().nextBoolean())
    (self, ConnectionAcceptedReply.Err(s"$remoteAddress is blacklisted!"))
  else
    (self.copy(pendingAddr = Some(remoteAddress)), ConnectionAcceptedReply.Ok)*/

  def msgReply(usr: UserName, msg: String): ChatMsgReply =
    ChatMsgReply.Broadcast(Protocol.ServerCommand.Message(usr, msg))

  override def toString: String =
    s"(online=[${usersOnline.mkString(",")}])"
