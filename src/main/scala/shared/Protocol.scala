// Copyright (c) 2022 by Vadim Bondarev
// This software is licensed under the Apache License, Version 2.0.
// You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

package shared

import scodec.Codec
import scodec.codecs.*
import server.ScodecGlue

object Protocol:

  opaque type UserName /*<: String*/ = String
  object UserName:
    def apply(n: String): UserName = n
    // expose some String methods
    extension (u: UserName)
      def lenght(): Int = u.length
      def trim(): UserName = u.trim
      def inBanned(bannedUsers: List[String]): Boolean = bannedUsers.contains(u)

  private val username: Codec[UserName] =
    utf8_32.as[UserName]

  enum ClientCommand(userName: UserName):
    case Authorize(usr: UserName) extends ClientCommand(usr)
    case SendMessage(usr: UserName, msg: String) extends ClientCommand(usr)
    case ShowAdvt(usr: UserName = "advt", msg: String) extends ClientCommand(usr)

  end ClientCommand

  object ClientCommand:
    private val codec: Codec[ClientCommand] = discriminated[ClientCommand]
      .by(uint8)
      .typecase(1, username.as[ClientCommand.Authorize])
      .typecase(2, (username :: utf8_32).as[ClientCommand.SendMessage])
      .typecase(3, (username :: utf8_32).as[ClientCommand.ShowAdvt])
    val Decoder = ScodecGlue.decoder(codec)
    val Encoder = ScodecGlue.encoder(codec)

  enum ServerCommand:
    case Welcome(usr: UserName, greetingMsg: String) extends ServerCommand
    case Message(usr: UserName, text: String) extends ServerCommand
    case Alert(text: String) extends ServerCommand
    case Disconnect(cause: String) extends ServerCommand
  end ServerCommand

  object ServerCommand:
    private val codec: Codec[ServerCommand] = discriminated[ServerCommand]
      .by(uint8)
      .typecase(100, (username :: utf8_32).as[ServerCommand.Welcome])
      .typecase(101, utf8_32.as[ServerCommand.Alert])
      .typecase(102, (username :: utf8_32).as[ServerCommand.Message])
      .typecase(103, utf8_32.as[ServerCommand.Disconnect])

    val Encoder = ScodecGlue.encoder(codec)
    val Decoder = ScodecGlue.decoder(codec)
