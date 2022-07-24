// Copyright (c) 2022 by Vadim Bondarev
// This software is licensed under the Apache License, Version 2.0.
// You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

package scala2

final case class AppConfig(
  port: Int,
  bannedUsers: List[String],
  bannedHosts: List[String],
  bufferSize: Int
  //timeout: FiniteDuration,
  //parallelism: Int,
  //bufferSize: Int,
  //passivationAfter: FiniteDuration
)

object bridge {

  def readAppConfig(systemName: String): AppConfig = {
    import pureconfig.generic.auto.exportReader
    pureconfig.ConfigSource.default.at(systemName).loadOrThrow[AppConfig]
  }

}
