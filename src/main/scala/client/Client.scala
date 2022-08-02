// Copyright (c) 2022 by Vadim Bondarev
// This software is licensed under the Apache License, Version 2.0.
// You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

package client

import akka.Done
import akka.actor.ActorSystem
import akka.routing.SeveralRoutees
import akka.stream.{ ActorMaterializer, Attributes, Materializer, OverflowStrategy, SystemMaterializer }
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
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps

object Client:

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

    //
    val cryptography =
      shared.crypto.SymmetricCryptography.getCryptography("./jks/chat.jks", "open$sesam")

    val authorized = Promise[akka.Done]()
    val sinkCompleted = Promise[akka.Done]()

    val consoleInput0 =
      Source.future(authorized.future).flatMapConcat { _ =>
        import concurrent.duration.DurationInt
        Source
          .tick(1.second, 120.milli, ())
          .zipWithIndex
          .map { case (_, i) => i }
          .map(i => s"ping-$i")
          .mapMaterializedValue(_ => akka.NotUsed)
          .map { msg =>
            ClientCommand.SendMessage(
              username,
              shared.crypto.base64Encode(cryptography.enc.encrypt(msg.getBytes(StandardCharsets.UTF_8))),
            )
          }
      }

    val consoleInput =
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
              shared.crypto.base64Encode(cryptography.enc.encrypt(msg.getBytes(StandardCharsets.UTF_8))),
            )
          }
      }

    val in =
      Source
        .single(ClientCommand.Authorize(username, "secret")) // TODO: encrypt
        .concat(consoleInput)
        .via(ClientCommand.Encoder)

    val sinkActor: Sink[ServerCommand, akka.NotUsed] =
      ActorSink.actorRefWithBackpressure(
        system.spawn(ChatClient(username, authorized, sinkCompleted, cryptography.dec), username.toString()),
        ChatClient.Protocol.NextCmd(_, _),
        ChatClient.Protocol.Connect(_),
        ChatClient.Ack,
        onCompleteMessage = ChatClient.Protocol.Complete,
        onFailureMessage = ChatClient.Protocol.Fail(_),
      )

    val out: Sink[ByteString, akka.NotUsed] =
      // Flow[ByteString].delay(2.seconds).via(ServerCommand.Decoder)
      ServerCommand
        .Decoder
        .takeWhile(!_.toOption.exists(_.isInstanceOf[ServerCommand.Disconnect]), inclusive = true)
        .map(_.fold(ex => throw ex, identity))
        .to(sinkActor)
        .addAttributes(Attributes.inputBuffer(1, 1))

    val connected = in
      .viaMat(Tcp(system).outgoingConnection(host, port))(Keep.right)
      .toMat(out)(Keep.left)
      .run()

    connected.foreach { con =>
      println(s"Connected to ${con.remoteAddress.getHostString}:${con.remoteAddress.getPort}")
    }

    sinkCompleted.future

end Client
