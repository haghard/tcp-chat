package shared

import java.security.interfaces.{ RSAPrivateCrtKey, RSAPublicKey }
import java.math.BigInteger
import java.security.{ KeyFactory, KeyPairGenerator, PublicKey, SecureRandom }
import java.security.interfaces.{ RSAPrivateCrtKey, RSAPublicKey }
import java.security.spec.{ RSAKeyGenParameterSpec, X509EncodedKeySpec }

object ChatUser:
  private val PublicExponent = new BigInteger((Math.pow(2, 16) + 1).toInt.toString)
  private val ALG = "RSA"

  def recoverPubKey(bs: String): Option[RSAPublicKey] =
    crypto
      .base64Decode(bs)
      .map(bts => KeyFactory.getInstance(ALG).generatePublic(new X509EncodedKeySpec(bts)).asInstanceOf[RSAPublicKey])

  /*def recoverFromPrivKey(bs: String): Option[RSAPublicKey] =
    crypto
      .base64Decode(bs)
      .map(bts => KeyFactory.getInstance(ALG).generatePrivate(new X509EncodedKeySpec(bts)).asInstanceOf[RSAPublicKey])*/

  def generate(keySize: Int = 2048): ChatUser =
    val kpg = KeyPairGenerator.getInstance(ALG)
    kpg.initialize(new RSAKeyGenParameterSpec(keySize, PublicExponent), new SecureRandom())
    val kp = kpg.generateKeyPair
    ChatUser(kp.getPublic.asInstanceOf[RSAPublicKey], kp.getPrivate.asInstanceOf[RSAPrivateCrtKey])

end ChatUser

final case class ChatUser(pub: RSAPublicKey, priv: RSAPrivateCrtKey):
  val handle = crypto.Handle.ofKey(pub)

  // public key
  val asX509: String = crypto.base64Encode(pub.getEncoded)

  // private key
  val asPKCS8: Array[Byte] = priv.getEncoded

  override def toString: String = handle.toString
