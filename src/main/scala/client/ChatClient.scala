package client

import akka.actor.typed.{ ActorRef, Behavior }
import akka.actor.typed.scaladsl.{ Behaviors, StashBuffer }
import org.slf4j.Logger
import shared.Protocol.{ ServerCommand, UserName }
import shared.crypto.SymmetricCryptography

import java.nio.charset.StandardCharsets
import scala.Console.*
import scala.concurrent.Promise

object ChatClient:

  trait Ack

  object Ack extends Ack

  enum Protocol:
    case NextCmd(ackTo: ActorRef[Ack], cmd: ServerCommand) extends Protocol
    case Connect(ackTo: ActorRef[Ack]) extends Protocol
    case Complete extends Protocol
    case Fail(ex: Throwable) extends Protocol

  def apply(
      username: UserName,
      authorized: Promise[akka.Done],
      done: Promise[akka.Done],
      dec: SymmetricCryptography.Decrypter,
    ): Behavior[Protocol] =
    Behaviors.setup { ctx =>
      given logger: Logger = ctx.log

      Behaviors.withStash(1 << 2) { buf =>
        Behaviors.receiveMessage {
          case Protocol.Connect(ackTo) =>
            ackTo.tell(Ack)
            awaitAuth(username, authorized, done, dec, buf)
          case other =>
            buf.stash(other)
            Behaviors.same
        }
      }
    }

  def awaitAuth(
      username: UserName,
      auth: Promise[akka.Done],
      done: Promise[akka.Done],
      dec: SymmetricCryptography.Decrypter,
      buf: StashBuffer[Protocol],
    )(using log: Logger
    ): Behavior[Protocol] =
    Behaviors.receiveMessage {
      case c @ Protocol.NextCmd(ackTo, cmd) =>
        cmd match
          case ServerCommand.Authorized(user, _) =>
            println(s"$RED_B$BOLD$WHITE Logger in as $user $RESET")
            auth.trySuccess(akka.Done)
            ackTo.tell(Ack)
            buf.unstashAll(active(username, done, dec))
          case _ =>
            buf.stash(c)
            Behaviors.same
      case Protocol.Complete =>
        println(s"$GREEN_B$BOLD$WHITE Completed $RESET")
        done.trySuccess(akka.Done)
        Behaviors.stopped
      case Protocol.Fail(ex) =>
        println(s"$GREEN_B$BOLD$WHITE Failure $RESET")
        done.trySuccess(akka.Done)
        log.error("Error: ", ex)
        Behaviors.stopped
      case Protocol.Connect(_) =>
        Behaviors.unhandled
    }

  def active(
      username: UserName,
      done: Promise[akka.Done],
      dec: SymmetricCryptography.Decrypter,
    )(using log: Logger
    ): Behavior[Protocol] =
    Behaviors.receiveMessage {
      case Protocol.NextCmd(ackTo, cmd) =>
        // log.info("Got {}", cmd)
        handle(username, cmd, dec)
        ackTo.tell(Ack)
        Behaviors.same
      case Protocol.Complete =>
        println(s"$GREEN_B$BOLD$WHITE Completed $RESET")
        done.trySuccess(akka.Done)
        Behaviors.stopped
      case Protocol.Fail(ex) =>
        println(s"$GREEN_B$BOLD$WHITE Failure $RESET")
        done.trySuccess(akka.Done)
        log.error("Error: ", ex)
        Behaviors.stopped
      case Protocol.Connect(_) =>
        Behaviors.unhandled
    }

  def handle(
      username: UserName,
      cmd: ServerCommand,
      dec: SymmetricCryptography.Decrypter,
    ): Unit =
    cmd match
      case ServerCommand.Message(user, msg) =>
        shared.crypto.base64Decode(msg) match
          case Some(bts) =>
            val msg = new String(dec.decrypt(bts), StandardCharsets.UTF_8)
            if (username.!==(user)) println(s"$GREEN_B$RED_B$WHITE $user: $msg $RESET")
            else println(s"$GREEN_B$BOLD$WHITE $user: $msg $RESET")

          case None =>
            throw new Exception("Decrypt error !!!")
      case ServerCommand.Disconnect(cause) =>
        val msg = s"Server disconnected because: $cause"
        println(s"$GREEN_B$BOLD$WHITE $msg $RESET")
      case ServerCommand.Authorized(usr, greeting) =>
        val msg = s"Logger in as $usr. $greeting"
        println(s"$GREEN_B$BOLD$WHITE $msg $RESET")

end ChatClient
