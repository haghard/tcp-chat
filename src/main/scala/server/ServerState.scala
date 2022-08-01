package server

import java.security.interfaces.RSAPublicKey
import server.Guardian.{ ChatMsgReply, ConnectionAcceptedReply }
import shared.Protocol
import shared.Protocol.{ ServerCommand, UserName }
import shared.crypto.SymmetricCryptography
import shared.crypto.SymmetricCryptography.Cryptography

import java.net.InetSocketAddress

final case class ServerState(
    appCfg: scala2.AppConfig,
    cryptography: Cryptography,
    pendingAddress: Option[InetSocketAddress] = None,
    usersOnline: Map[InetSocketAddress, UserName] = Map.empty):
  self =>

  def authorize(usr: UserName, pws: String): (ServerState, ChatMsgReply) =
    if (usr.inBanned(appCfg.bannedUsers))
      (copy(pendingAddress = None), ChatMsgReply.Error(ServerCommand.Disconnect(s"Auth($usr) error: Banned")))
    else if (pws != "secret")
      (copy(pendingAddress = None), ChatMsgReply.Error(ServerCommand.Disconnect(s"Auth($usr) error: Wrong psw")))
    else
      pendingAddress match
        case Some(remoteAddress) =>
          val s = copy(pendingAddress = None, usersOnline + (remoteAddress -> usr))
          (s, ChatMsgReply.Broadcast(ServerCommand.Authorized(usr, s"Authorized $usr")))
        case None =>
          // println("This should never happen !!!")
          (self, ChatMsgReply.Error(ServerCommand.Disconnect("This should never happen. Invalid state!")))

  def disconnect(remoteAddress: InetSocketAddress): ServerState =
    self.copy(pendingAddress = None, usersOnline - remoteAddress)

  def acceptCon(remoteAddress: InetSocketAddress): (ServerState, ConnectionAcceptedReply) =
    (self.copy(pendingAddress = Some(remoteAddress)), ConnectionAcceptedReply.Ok)

  /*if (ThreadLocalRandom.current().nextBoolean())
    (self, ConnectionAcceptedReply.Err(s"$remoteAddress is blacklisted!"))
  else
    (self.copy(pendingAddr = Some(remoteAddress)), ConnectionAcceptedReply.Ok)*/

  def msgReply(usr: UserName, msg: String): ChatMsgReply =
    ChatMsgReply.Broadcast(Protocol.ServerCommand.Message(usr, msg))

  override def toString: String =
    s"(online=[${usersOnline.mkString(",")}])"
