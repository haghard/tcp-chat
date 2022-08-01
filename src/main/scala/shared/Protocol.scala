// Copyright (c) 2022 by Vadim Bondarev
// This software is licensed under the Apache License, Version 2.0.
// You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

package shared

import scodec.Codec
import scodec.codecs.*
import server.ScodecGlue
import shared.crypto.SymmetricCryptography

object Protocol:

  opaque type UserName /*<: String*/ = String
  object UserName:
    def apply(n: String): UserName = n
    // expose some String methods
    extension (u: UserName)
      def length(): Int = u.length()
      def trim(): UserName = u.trim()
      def toString(): String = u.toString()
      def !==(other: UserName): Boolean = u != other
      def inBanned(users: List[String]): Boolean = users.contains(u)

  private val username: Codec[UserName] =
    utf8_32.as[UserName]

  enum ClientCommand(userName: UserName):
    case Authorize(usr: UserName, pub: String) extends ClientCommand(usr)
    case SendMessage(usr: UserName, text: String) extends ClientCommand(usr)
    case ShowAdvt(usr: UserName = "advt", msg: String) extends ClientCommand(usr)

  end ClientCommand

  object ClientCommand:

    val Quit = "/quit"
    val List = "/users"
    val Backpressured = Protocol.ServerCommand.Disconnect("Backpressured")

    private val codec: Codec[ClientCommand] = discriminated[ClientCommand]
      .by(uint8)
      .typecase(1, (username :: utf8_32).as[ClientCommand.Authorize])
      .typecase(2, (username :: utf8_32).as[ClientCommand.SendMessage])
      .typecase(3, (username :: utf8_32).as[ClientCommand.ShowAdvt])
    val Decoder = ScodecGlue.decoder(codec)
    val Encoder = ScodecGlue.encoder(codec)

  enum ServerCommand:
    case Authorized(usr: UserName, serverPub: String) extends ServerCommand
    case Message(usr: UserName, text: String) extends ServerCommand
    case Disconnect(cause: String) extends ServerCommand
  end ServerCommand

  object ServerCommand:
    private val codec: Codec[ServerCommand] = discriminated[ServerCommand]
      .by(uint8)
      .typecase(100, (username :: utf8_32).as[ServerCommand.Authorized])
      .typecase(101, (username :: utf8_32).as[ServerCommand.Message])
      .typecase(102, utf8_32.as[ServerCommand.Disconnect])

    val Encoder = ScodecGlue.encoder(codec)
    val Decoder = ScodecGlue.decoder(codec)
