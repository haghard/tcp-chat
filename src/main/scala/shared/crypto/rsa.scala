/*

package shared.crypto

import java.util
import java.security.{ MessageDigest, PrivateKey, PublicKey, SecureRandom }
import org.spongycastle.crypto.engines.RSAEngine
import org.spongycastle.crypto.signers.PSSSigner
import org.spongycastle.crypto.digests.SHA256Digest

import java.security.interfaces.{ RSAPrivateCrtKey, RSAPublicKey }
import org.spongycastle.crypto.params.{ ParametersWithRandom, RSAKeyParameters, RSAPrivateCrtKeyParameters }
import shared.Protocol.{ ClientCommand, UserName }

import java.io.{ BufferedInputStream, ByteArrayInputStream, ByteArrayOutputStream, InputStream }
import java.nio.charset.StandardCharsets
import java.util.{ Base64, UUID }
import scala.util.{ Try, Using }
import compiletime.asMatchable
import java.security.interfaces.RSAKey
import javax.crypto.Cipher
import scala.Console.println

class Signature(val bytes: Array[Byte]) extends Base64EncodedBytes

object Signature:
  def fromEncoded(s: String): Option[Signature] =
    base64Decode(s).map(new Signature(_))

case class SignedDocument[T](data: T, sign: Signature)

def base64Encode(bs: Array[Byte]): String =
  new String(Base64.getUrlEncoder.withoutPadding.encode(bs))

def base64Decode(s: String): Option[Array[Byte]] =
  Try(Base64.getUrlDecoder.decode(s)).toOption

def sha256(bts: Array[Byte]): Array[Byte] =
  MessageDigest.getInstance("SHA-256").digest(bts)

abstract class Base64EncodedBytes:

  def bytes: Array[Byte]

  def size: Int = bytes.size

  final override def toString: String =
    base64Encode(bytes)

  override def equals(that: Any): Boolean = that.asMatchable match
    case bs: Base64EncodedBytes => bs.bytes.sameElements(bytes)
    case _                      => false

  override def hashCode(): Int = util.Arrays.hashCode(bytes)

trait Signable:
  def signingBts: Array[Byte]

object Signable:
  implicit class SignableSyntax[T <: Signable](t: T):
    def sign(priv: RSAPrivateCrtKey): SignedDocument[T] =
      val sigBts = t.signingBts
      val params = new RSAPrivateCrtKeyParameters(
        priv.getModulus,
        priv.getPublicExponent,
        priv.getPrivateExponent,
        priv.getPrimeP,
        priv.getPrimeQ,
        priv.getPrimeExponentP,
        priv.getPrimeExponentQ,
        priv.getCrtCoefficient,
      )

      val signer = new PSSSigner(new RSAEngine, new SHA256Digest, 20)
      signer.init(false, new ParametersWithRandom(params, new SecureRandom()))
      signer.update(sigBts, 0, sigBts.length)
      SignedDocument(t, new Signature(signer.generateSignature))

  implicit class VerifiableSyntax[T <: Signable](sd: SignedDocument[T]):
    def verify(pub: RSAPublicKey): Boolean =
      val sigBts = sd.signingBts
      val signer = new PSSSigner(new RSAEngine, new SHA256Digest, 20)
      signer.init(true, new RSAKeyParameters(false, pub.getModulus, pub.getPublicExponent))
      signer.update(sigBts, 0, sigBts.length)
      signer.verifySignature(sd.sign.bytes)

  implicit def castSignable[T](st: SignedDocument[T]): T = st.data

object UnsignedBigInt:
  def ofBigEndianBytes(bs: Array[Byte]): Option[BigInt] =
    if (bs.isEmpty) None else Some(BigInt(0.toByte +: bs))

  def toBigEndianBytes(bi: BigInt): Array[Byte] =
    val bs = bi.toByteArray
    if (bs.length > 1 && bs.head == 0.toByte) bs.tail else bs

case class SignableData(s: String) extends Signable:
  override def signingBts: Array[Byte] = s.getBytes

class Handle protected (val bytes: Array[Byte]) extends Base64EncodedBytes

object Handle:

  def apply(bs: Array[Byte]): Try[Handle] = Try(new Handle(bs))

  def fromEncoded(s: String): Option[Handle] =
    base64Decode(s).map(new Handle(_))

  def ofModulus(n: BigInt): Handle =
    new Handle(sha256(UnsignedBigInt.toBigEndianBytes(n)))

  def ofKey(k: RSAKey): Handle = ofModulus(k.getModulus)

end Handle

def md5sum(input: InputStream): String =
  val bis = new BufferedInputStream(input)
  val buf = new Array[Byte](1024)
  val md5 = java.security.MessageDigest.getInstance("MD5")
  LazyList.continually(bis.read(buf)).takeWhile(_ != -1).foreach(md5.update(buf, 0, _))
  md5.digest().map(0xff & _).map("%02x".format(_)).foldLeft("")(_ + _)
end md5sum
 */
