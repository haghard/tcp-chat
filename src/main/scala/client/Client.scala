// Copyright (c) 2022 by Vadim Bondarev
// This software is licensed under the Apache License, Version 2.0.
// You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

package client

import akka.Done
import akka.actor.ActorSystem
import akka.routing.SeveralRoutees
import akka.stream.{ ActorMaterializer, Materializer, SystemMaterializer }
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Tcp
import akka.util.ByteString
import shared.Protocol.{ ClientCommand, ServerCommand, UserName }
import shared.Protocol.ClientCommand.SendMessage

import scala.concurrent.Await
import scala.concurrent.Future
import scala.io.StdIn
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.Console.*

object Client:

  private val serverCommandToString = Flow[Try[ServerCommand]].map {
    case Success(serverCmd) =>
      serverCmd match
        case ServerCommand.Welcome(user, msg) => s"Logged in as $user \n$msg"
        case ServerCommand.Alert(msg)         => msg
        case ServerCommand.Message(user, msg) => s"$user: $msg"
        case ServerCommand.Disconnect(cause)  => s"Server disconnected because: $cause"
    case Failure(ex) => s"Error parsing server command: ${ex.getMessage}"
  }

  def main(args: Array[String]): Unit =
    implicit val system = ActorSystem("client")
    import system.dispatcher

    try
      val host = args(0)
      val port = args(1).toInt
      val username = args(2)
      run(host, port, UserName(username)).foreach { _ =>
        System.exit(0)
      }
    catch
      case th: Throwable =>
        println(th.getMessage)
        th.printStackTrace()
        println("Usage: Server [host] [port] [username]")
        System.exit(1)
  end main

  def run(
      host: String,
      port: Int,
      username: UserName,
    )(implicit system: ActorSystem
    ): Future[Done] =
    import system.dispatcher

    val commandsIn =
      Source
        .unfoldResource[String, Iterator[String]](
          () => Iterator.continually(StdIn.readLine("> ")),
          iterator => Some(iterator.next()),
          _ => (),
        )
        .map(ClientCommand.SendMessage(username, _))

    //
    val in =
      Source
        .single(ClientCommand.Authorize(username))
        .concat(commandsIn)
        .via(ClientCommand.Encoder)

    import scala.concurrent.duration.*

    val out =
      //Flow[ByteString].delay(2.seconds).via(ServerCommand.Decoder)
      ServerCommand.Decoder
        .takeWhile(!_.toOption.exists(_.isInstanceOf[ServerCommand.Disconnect]), inclusive = true)
        .via(serverCommandToString)
        .concat(Source.single("Disconnected from server"))
        .toMat(Sink.foreach(msg => println(s"$GREEN_B$BOLD$WHITE $msg $RESET")))(Keep.right)

    val (connected, done) = in
      .viaMat(Tcp(system).outgoingConnection(host, port))(Keep.right)
      .toMat(out)(Keep.both)
      .run()

    connected.foreach { con =>
      println(s"Connected to ${con.remoteAddress.getHostString}:${con.remoteAddress.getPort}")
    }

    done

end Client
