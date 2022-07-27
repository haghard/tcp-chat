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
import shared.{ ChatUser, Protocol }
import shared.Protocol.{ ClientCommand, ServerCommand, UserName }
import shared.Protocol.ClientCommand.SendMessage

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }
import java.nio.charset.StandardCharsets
import java.security.{ PrivateKey, PublicKey, Signature }
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.{ Lock, LockSupport }
import javax.crypto.Cipher
import scala.concurrent.Await
import scala.concurrent.Future
import scala.io.StdIn
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.Console.*

object Client:

  /*private val serverCommandToString = Flow[Try[ServerCommand]].map {
    case Success(serverCmd) =>
      serverCmd match
        case ServerCommand.Authorized(user, serverPub) =>
          // Alice owns server's public key, so they can use it to encrypt thi messages
          // val servPub = ChatUser.recoverFromPubKey(serverPub).get
          s"Logged in as $user"
        case ServerCommand.Message(user, msg) => s"$user: $msg"
        case ServerCommand.Disconnect(cause)  => s"Server disconnected because: $cause"
    case Failure(ex) =>
      s"Error parsing server command: ${ex.getMessage}"
  }*/

  /*private val serverCommandToString = Flow[ServerCommand].map {
    case ServerCommand.Authorized(user, serverPub) => s"Logged in as $user"
    case ServerCommand.Message(user, msg) => s"$user: $msg"
    case ServerCommand.Disconnect(cause)  => s"Server disconnected because: $cause"
  }*/

  // TODO:
  // runMain crypto.aes.AESProgram

  def main(args: Array[String]): Unit =
    implicit val system = ActorSystem("client")
    import system.dispatcher

    try
      val host = args(0)
      val port = args(1).toInt
      val username = args(2)
      run(host, port, UserName(username)).foreach(_ => System.exit(0))
    catch
      case th: Throwable =>
        println(th.getMessage)
        th.printStackTrace()
        println("Usage: Server [host] [port] [username]")
        System.exit(-1)
  end main

  val serverPub = new AtomicReference[RSAPublicKey](null)

  def run(
      host: String,
      port: Int,
      username: UserName,
    )(implicit system: ActorSystem
    ): Future[Done] =
    import system.dispatcher

    val user = ChatUser.generate()

    val commandsIn =
      Source
        .unfoldResource[String, Iterator[String]](
          () => Iterator.continually(StdIn.readLine("> ")),
          iterator => Some(iterator.next()),
          _ => (),
        )
        .map { msg =>
          while (serverPub.get() == null) LockSupport.parkNanos(300_000_000)
          shared.crypto.Crypto.encryptAndSend(username, msg, user.priv, serverPub.get())
        }
    //
    val in =
      Source
        .single(ClientCommand.Authorize(username, user.asX509))
        .concat(commandsIn)
        .via(ClientCommand.Encoder)

    import scala.concurrent.duration.*

    import compiletime.asMatchable
    val login /*: Sink[Option[RSAPublicKey], akka.NotUsed]*/ =
      ServerCommand
        .Decoder
        .takeWhile(!_.toOption.exists(_.isInstanceOf[ServerCommand.Disconnect]), inclusive = true)
        .map {
          case Success(c) =>
            c match
              case ServerCommand.Authorized(_, serverPub) =>
                ChatUser.recoverPubKey(serverPub)
              case _: ServerCommand =>
                None
          case Failure(ex) =>
            None
        }

    val out: Sink[ByteString, Future[akka.Done]] =
      // Flow[ByteString].delay(2.seconds).via(ServerCommand.Decoder)
      ServerCommand
        .Decoder
        .takeWhile(!_.toOption.exists(_.isInstanceOf[ServerCommand.Disconnect]), inclusive = true)
        .collect { case Success(v) => v }
        .scan((Option.empty[RSAPublicKey], null.asInstanceOf[ServerCommand])) { (acc, c) =>
          c.asMatchable match
            case ServerCommand.Authorized(_, serverPub) => (ChatUser.recoverPubKey(serverPub), c)
            case _: ServerCommand                       => (acc._1, c)
        }
        .collect {
          case (Some(pk), c) =>
            serverPub.compareAndSet(null, pk)
            c
        }
        // .via(serverCommandToString)
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
