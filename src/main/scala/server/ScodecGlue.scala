// Copyright (c) 2022 by Vadim Bondarev
// This software is licensed under the Apache License, Version 2.0.
// You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

package server

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Framing
import akka.util.ByteString
import scodec.Attempt
import scodec.Codec
import scodec.bits.BitVector

import scala.util.Failure
import scala.util.Success
import scala.util.Try
import shared.Protocol.*
import shared.crypto.SymmetricCryptography

object ScodecGlue:

  private val maxFrameLength = 1024 * 2

  // private val concurrencyLevel = 1 << 4
  // private val extraSpace = 1024 * 2

  // val allocator = new FixedSizeAllocator(maxFrameLength + extraSpace, (maxFrameLength + extraSpace) * concurrencyLevel)

  // def toBinary(o: AnyRef, buf: ByteBuffer): Unit
  // def fromBinary(buf: ByteBuffer, manifest: String): AnyRef

  def decoder[T](codec: Codec[T]): Flow[ByteString, Try[T], NotUsed] =
    // Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true)
    Flow[ByteString]
      .via(Framing.simpleFramingProtocolDecoder(maxFrameLength))
      .map { frame =>
        codec.decode(BitVector.view(frame.toByteBuffer)) match
          case Attempt.Successful(t) => Success(t.value)
          case Attempt.Failure(cause) =>
            Failure(new RuntimeException(s"Unparsable command: $cause"))
      }

  def encoder[T](codec: Codec[T]): Flow[T, ByteString, NotUsed] =
    Flow[T]
      .map { t =>
        // val address = allocator.malloc(maxFrameLength)
        // val buf = DirectMemory.wrap(address, maxFrameLength)
        codec.encode(t) match
          case Attempt.Successful(bytes) =>
            // buf.put(bytes.toByteBuffer)
            ByteString(bytes.toByteBuffer)
          case Attempt.Failure(error) =>
            throw new RuntimeException(s"Failed to encode $t: $error")
      }
      .via(Framing.simpleFramingProtocolEncoder(maxFrameLength))
