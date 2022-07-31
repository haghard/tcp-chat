// Copyright (c) 2022 by Vadim Bondarev
// This software is licensed under the Apache License, Version 2.0.
// You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

package client

import akka.Done
import akka.actor.ActorSystem
import akka.routing.SeveralRoutees
import akka.stream.{ ActorMaterializer, Materializer, OverflowStrategy, SystemMaterializer }
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Tcp
import akka.stream.typed.scaladsl.ActorSink
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
import scala.concurrent.{ Await, Future, Promise }
import scala.io.StdIn
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.Console.*
import compiletime.asMatchable

object Client:

  private val serverCommandToString = Flow[Try[ServerCommand]].map {
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
  }

  val serverCommand = Flow[ServerCommand].map {
    case ServerCommand.Authorized(user, _) => s"Logged in as $user"
    case ServerCommand.Message(user, msg)  => s"$user: $msg"
    case ServerCommand.Disconnect(cause)   => s"Server disconnected because: $cause"
  }

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

  def run(
      host: String,
      port: Int,
      username: UserName,
    )(implicit system: ActorSystem
    ): Future[Done] =
    import system.dispatcher

    val user = ChatUser.generate()

    val promisedKey = Promise[RSAPublicKey]()

    val commandsIn =
      Source.future(promisedKey.future).flatMapConcat { serverPubKey =>
        Source
          .unfoldResource[String, Iterator[String]](
            () => Iterator.continually(StdIn.readLine("> ")),
            iterator => Some(iterator.next()),
            _ => (),
          )
          .map(msg => shared.crypto.cryptography.encryptAndSend(username, msg, user.priv, serverPubKey))
      }

    /*val commandsIn =
      Source
        .unfoldResource[String, Iterator[String]](
          () => Iterator.continually(StdIn.readLine("> ")),
          iterator => Some(iterator.next()),
          _ => (),
        )
        .map { msg =>
          while (serverPub.get() == null) LockSupport.parkNanos(300_000_000)
          shared.crypto.cryptography.encryptAndSend(username, msg, user.priv, serverPub.get())
        }*/

    val in =
      Source
        .single(ClientCommand.Authorize(username, user.asX509))
        .concat(commandsIn)
        .via(ClientCommand.Encoder)

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
            promisedKey.trySuccess(pk)
            // serverPub.compareAndSet(null, pk)
            c
        }
        // .via(serverCommandToString)
        .concat(Source.single("Disconnected from server"))
        .toMat(Sink.foreach(msg => println(s"$GREEN_B$BOLD$WHITE $msg $RESET")))(Keep.right)

    val (connected, done) = in
      .viaMat(Tcp(system).outgoingConnection(host, port))(Keep.right)
      .toMat(out)(Keep.both)
      .run()
    // ActorSink.actorRefWithBackpressure()

    connected.foreach { con =>
      println(s"Connected to ${con.remoteAddress.getHostString}:${con.remoteAddress.getPort}")
    }

    done

end Client
