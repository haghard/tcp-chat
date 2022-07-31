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
import shared.Protocol
import shared.Protocol.{ ClientCommand, ServerCommand, UserName }
import shared.crypto.SymmetricCryptography

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

  def serverCommand(decrypter: SymmetricCryptography.Decrypter) = Flow[ServerCommand].map {
    case ServerCommand.Authorized(user, _) =>
      s"Logged in as $user"
    case ServerCommand.Message(user, msg) =>
      shared.crypto.base64Decode(msg) match
        case Some(bts) =>
          val out = new String(decrypter.decrypt(bts), StandardCharsets.UTF_8)
          s"$user: $out"
        case None =>
          throw new Exception("Decrypt error !!!")
    case ServerCommand.Disconnect(cause) =>
      s"Server disconnected because: $cause"
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

    val (encrypter, decrypter) =
      shared.crypto.SymmetricCryptography.getCryptography("./jks/chat.jks", "open$sesam")

    val authorized = scala.concurrent.Promise[Unit]()

    val commandsIn =
      Source.future(authorized.future).flatMapConcat { _ =>
        Source
          .unfoldResource[String, Iterator[String]](
            () => Iterator.continually(StdIn.readLine("> ")),
            iterator => Some(iterator.next()),
            _ => (),
          )
          .map { msg =>
            ClientCommand.SendMessage(
              username,
              shared.crypto.base64Encode(encrypter.encrypt(msg.getBytes(StandardCharsets.UTF_8))),
            )
          }
      }

    val in =
      Source
        .single(ClientCommand.Authorize(username, "secret"))
        .concat(commandsIn)
        .via(ClientCommand.Encoder)

    val out: Sink[ByteString, Future[akka.Done]] =
      // Flow[ByteString].delay(2.seconds).via(ServerCommand.Decoder)
      ServerCommand
        .Decoder
        .takeWhile(!_.toOption.exists(_.isInstanceOf[ServerCommand.Disconnect]), inclusive = true)
        .collect { case Success(v) => v }
        .scan((false, null.asInstanceOf[ServerCommand])) { (acc, c) =>
          c.asMatchable match
            case a: ServerCommand.Authorized =>
              authorized.trySuccess(())
              (true, a)
            case _: ServerCommand =>
              (acc._1, c)
        }
        .collect { case (true, c) => c }
        .via(serverCommand(decrypter))
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
