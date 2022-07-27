package server

import java.security.interfaces.RSAPublicKey
import server.Guardian.{ ChatMsgReply, ConnectionAcceptedReply }
import shared.{ ChatUser, Protocol }
import shared.Protocol.{ ServerCommand, UserName }

import java.net.InetSocketAddress

final case class State(
    appCfg: scala2.AppConfig,
    chatUser: ChatUser,
    pendingAddress: Option[InetSocketAddress] = None,
    usersOnline: Map[InetSocketAddress, (UserName, RSAPublicKey)] = Map.empty):
  self =>

  def authorize(usr: UserName, pub: String): (State, ChatMsgReply) =
    if (usr.inBanned(appCfg.bannedUsers))
      (copy(pendingAddress = None), ChatMsgReply.Error(ServerCommand.Disconnect(s"Auth $usr error. Banned")))
    else
      pendingAddress match
        case Some(remoteAddress) =>
          ChatUser.recoverPubKey(pub) match
            case Some(pub) =>
              val s = copy(pendingAddress = None, usersOnline + (remoteAddress -> (usr, pub)))
              (s, ChatMsgReply.Broadcast(ServerCommand.Authorized(usr, chatUser.asX509 /*s"Authorized $usr"*/ )))
            case None =>
              (copy(pendingAddress = None), ChatMsgReply.Error(ServerCommand.Disconnect(s"Auth $usr error. Key error")))

        case None =>
          println("This should never happen !!!")
          (self, ChatMsgReply.Error(ServerCommand.Disconnect("Invalid state")))

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
