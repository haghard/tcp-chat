package shared.crypto

import java.security.{ KeyStore, SecureRandom }
import java.nio.file.{ Files, Paths }
import java.io.{ FileInputStream, FileOutputStream }
import java.math.BigInteger
import java.security.cert.CertificateException
import java.util.Date
import javax.crypto.{ Cipher, KeyGenerator }

import java.security.KeyPair
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import scala.util.{ Try, Using }
import javax.crypto.spec.IvParameterSpec
import javax.crypto.{ Cipher, CipherInputStream }
import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }
import javax.crypto.{ Cipher, CipherOutputStream }
import scala.annotation.tailrec
import scala.util.control.NonFatal
import java.util.Base64

/*
  Symmetric cryptography

  The Advanced Encryption Standard, or AES, is a symmetric block cipher chosen by the U.S.
  government to protect classified information and is implemented in software and hardware
  throughout the world to encrypt sensitive data.

  https://www.baeldung.com/java-cipher-input-output-stream
  https://www.baeldung.com/java-keystore

  https://www.tutorialspoint.com/java_cryptography/java_cryptography_keygenerator.htm
  https://github.com/eugenp/tutorials/blob/master/core-java-modules/core-java-security/src/test/java/com/baeldung/keystore/JavaKeyStoreUnitTest.java
  http://stackoverflow.com/a/21952301#1#L0

 */
object SymmetricCryptography:

  private val ALGORITHM = "AES"
  private val CIPHER = "AES/CBC/PKCS5Padding"
  private val keyEntryName = "chat"

  final class Encrypter(secretKey: javax.crypto.SecretKey, alg: String):
    @tailrec final def readByChunk(
        in: ByteArrayInputStream,
        out: CipherOutputStream,
        buffer: Array[Byte],
      ): Unit =
      in.read(buffer) match
        case -1 => ()
        case n =>
          out.write(buffer, 0, n)
          readByChunk(in, out, buffer)

    def encrypt(content: Array[Byte], bufferSize: Int = 1024): Array[Byte] =
      val cipher = Cipher.getInstance(alg)
      cipher.init(Cipher.ENCRYPT_MODE, secretKey)
      val initBytes = cipher.getIV
      val in = new ByteArrayInputStream(content)
      val out = new ByteArrayOutputStream()
      val cipherOut = new CipherOutputStream(out, cipher)

      try
        out.write(initBytes)
        readByChunk(in, cipherOut, new Array[Byte](bufferSize))
      catch
        case NonFatal(ex) =>
          throw new Exception("Encryption error", ex)
      finally
        if (out != null)
          out.flush()
          out.close()
        if (cipherOut != null)
          cipherOut.flush()
          cipherOut.close()
      out.toByteArray

  final class Decrypter(secretKey: javax.crypto.SecretKey, algorithm: String):
    def decrypt(content: Array[Byte], bufferSize: Int = 1024): Array[Byte] =
      val cipher = Cipher.getInstance(algorithm)
      val ivBytes = Array.ofDim[Byte](16)
      val buffer = new Array[Byte](bufferSize)
      val in = new ByteArrayInputStream(content)

      in.read(ivBytes)
      cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(ivBytes))

      val cipherIn = new CipherInputStream(in, cipher)
      val out = new ByteArrayOutputStream()

      @tailrec def readChunk(): Unit = cipherIn.read(buffer) match
        case -1 => ()
        case n =>
          out.write(buffer, 0, n)
          readChunk()

      try readChunk()
      catch
        case NonFatal(ex) =>
          throw new Exception("Decryption error", ex)
      finally
        in.close()
        cipherIn.close()
        out.flush()
        out.close()
      out.toByteArray

  def getCryptography(
      jksFilePath: String,
      jksPassword: String,
    ): (Encrypter, Decrypter) =
    val jks = Paths.get(jksFilePath)

    if (Files.exists(jks))
      // println(s"Load jks $jks")
      val password = jksPassword.toCharArray
      val ks: KeyStore = KeyStore.getInstance("pkcs12")
      ks.load(new FileInputStream(jksFilePath), password)

      val secretKey: javax.crypto.SecretKey =
        ks.getKey(keyEntryName, password).asInstanceOf[javax.crypto.SecretKey]

      // println("Loaded: " + shared.crypto.base64Encode(secretKey.getEncoded))

      val encrypter = Encrypter(secretKey, CIPHER)
      val decrypter = Decrypter(secretKey, CIPHER)
      (encrypter, decrypter)
    else throw new Exception(s"$jks doesn't exist!")

  end getCryptography

  def createJKS(jksFilePath: String, jksPassword: String): Unit =
    val secureRandom = new SecureRandom()
    val key = Array.ofDim[Byte](32)
    secureRandom.nextBytes(key)

    val secretKey: javax.crypto.SecretKey = new javax.crypto.spec.SecretKeySpec(key, ALGORITHM)
    val ks: KeyStore = KeyStore.getInstance("pkcs12")

    val pwdArray = jksPassword.toCharArray()
    // We tell KeyStore to create a new one by passing null as the first parameter
    ks.load(null, pwdArray)

    val secret = new KeyStore.SecretKeyEntry(secretKey)
    // println("Symmetric Key: " + shared.crypto.base64Encode(secret.getSecretKey.getEncoded))

    val password = new KeyStore.PasswordProtection(pwdArray)
    ks.setEntry(keyEntryName, secret, password)
    Using.resource(new FileOutputStream(jksFilePath))(fos => ks.store(fos, pwdArray))
  end createJKS

end SymmetricCryptography

def base64Encode(bs: Array[Byte]): String =
  new String(Base64.getUrlEncoder.withoutPadding.encode(bs))

def base64Decode(s: String): Option[Array[Byte]] =
  Try(Base64.getUrlDecoder.decode(s)).toOption
