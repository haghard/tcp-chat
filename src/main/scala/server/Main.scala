// Copyright (c) 2022 by Vadim Bondarev
// This software is licensed under the Apache License, Version 2.0.
// You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

package server

//import ProjectA.MainProjectA
import scala2.bridge
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, ActorSystem, Props, Scheduler, SpawnProtocol }
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.*
import scala.util.Try

object Main extends Ops:

  val AkkaSystemName = "tcp-chat-room"

  def main(args: Array[String]): Unit =
    argsToOpts(args)

    val config = ConfigFactory.load("application.conf").withFallback(ConfigFactory.load())
    val appCfg: scala2.AppConfig = bridge.readAppConfig(AkkaSystemName)

    given system: ActorSystem[SpawnProtocol.Command] =
      ActorSystem(Behaviors.setup[SpawnProtocol.Command](_ => SpawnProtocol()), AkkaSystemName, config)

    val host = Try(args(0)).getOrElse("127.0.0.1")
    val port = Try(args(1).toInt).getOrElse(appCfg.port)

    system.log.warn(system.printTree)

    new Bootstrap(host, port, appCfg).runTcpServer()

  end main