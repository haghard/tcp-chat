// Copyright (c) 2022 by Vadim Bondarev
// This software is licensed under the Apache License, Version 2.0.
// You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

package scala2


/*

tcp-chat-room {
  port = 2842
  port = ${?PORT}

  banned-users = [aa, bb, cc]

  banned-hosts = [127.0.0.2, 127.0.0.3]

  buffer-size = 128

  jks-path = ./jks/chat.jks
  jks-psw  = "open$sesam"
}

*/

final case class AppConfig(
  port: Int,
  bannedUsers: List[String],
  bannedHosts: List[String],
  bufferSize: Int,
  jksPath: String,
  jksPsw: String
)

object bridge {

  def readAppConfig(systemName: String): AppConfig = {
    import pureconfig.generic.auto.exportReader
    pureconfig.ConfigSource.default.at(systemName).loadOrThrow[AppConfig]
  }

}
