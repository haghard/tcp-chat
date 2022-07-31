package shared.crypto

import java.security.{ KeyStore, SecureRandom }
import java.nio.file.{ Files, Paths }
import java.io.{ FileInputStream, FileOutputStream }
import java.math.BigInteger
import java.security.cert.CertificateException
import java.util.Date
import javax.crypto.{ Cipher, KeyGenerator }
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider

import java.security.KeyPair
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import scala.util.Using
import javax.crypto.spec.IvParameterSpec
import javax.crypto.{ Cipher, CipherInputStream }

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }
import javax.crypto.{ Cipher, CipherOutputStream }

import scala.annotation.tailrec
import scala.util.control.NonFatal

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
object AESProgram:

  val ALGORITHM = "AES"
  val CIPHER = "AES/CBC/PKCS5Padding" // AES
  val jksFileName = "haghard-secret.jks"
  val jksFilePath = "./jks/" + jksFileName
  val keyEntryName = "haghard-secret"
  val jksPassword = "qwerty"

  def generateSymmetricKeyAndPutItInJKS(): Unit =
    println("deleteIfExists: " + Files.deleteIfExists(Paths.get(jksFilePath)))

    // val secretKey: javax.crypto.SecretKey = javax.crypto.KeyGenerator.getInstance(ALGORITHM).generateKey
    // OR

    val secureRandom = new SecureRandom()
    val key = Array.ofDim[Byte](32)
    secureRandom.nextBytes(key)
    val secretKey: javax.crypto.SecretKey = new javax.crypto.spec.SecretKeySpec(key, ALGORITHM)

    // create key-store with a Symmetric Key (AES)
    val pwdArray = jksPassword.toCharArray()
    val ks: KeyStore = KeyStore.getInstance("pkcs12")

    // We tell KeyStore to create a new one by passing null as the first parameter
    ks.load(null, pwdArray)

    // Save a Symmetric Key
    val secret = new KeyStore.SecretKeyEntry(secretKey)
    println(shared.crypto.base64Encode(secret.getSecretKey.getEncoded))

    val password = new KeyStore.PasswordProtection(pwdArray)

    ks.setEntry(keyEntryName, secret, password)

    Using.resource(new FileOutputStream(jksFilePath))(fos => ks.store(fos, pwdArray))

  def main(args: Array[String]): Unit =
    println("MaxAllowedKey: " + Cipher.getMaxAllowedKeyLength(ALGORITHM))

    generateSymmetricKeyAndPutItInJKS()

    val jksFile = Paths.get(jksFilePath)

    println("Load jks file from:  " + jksFile)
    println("jks exists:" + Files.exists(jksFile))

    // symmetric
    val password = jksPassword.toCharArray
    val ks: KeyStore = KeyStore.getInstance("pkcs12")
    ks.load(new FileInputStream(jksFilePath), password)

    // ks.aliases()

    val secretKey: javax.crypto.SecretKey =
      ks.getKey(keyEntryName, password).asInstanceOf[javax.crypto.SecretKey]

    val encrypter = Encrypter(secretKey, CIPHER)
    val decrypter = Decrypter(secretKey, CIPHER)

    val content =
      "bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-blabla-bla-bla-bla-bla-blabla-bla-bla-bla-bla-bla"
    val dataBts = content.getBytes

    val encData = encrypter.encrypt(dataBts)
    println("OrgSize:" + dataBts.size + " EncSize:" + encData.size)

    println("Encrypted base64: " + java.util.Base64.getEncoder.encodeToString(encData))
    println("Encrypted hex : " + shared.crypto.bytes2Hex(encData))

    val originalData = decrypter.decrypt(encData)

    println("Decrypted: " + new String(originalData))

    println("eq: " + (content == new String(originalData)))

end AESProgram

class Encrypter(secretKey: javax.crypto.SecretKey, alg: String):

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

  def encrypt(content: Array[Byte], bufferSize: Int = 1 << 7): Array[Byte] =
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
        out.flush
        out.close
      if (cipherOut != null)
        cipherOut.flush
        cipherOut.close
    out.toByteArray

class Decrypter(secretKey: javax.crypto.SecretKey, algorithm: String):

  // Int = 1 << 7
  def decrypt(content: Array[Byte], bufferSize: Int = 1 << 7): Array[Byte] =
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
      in.close
      cipherIn.close
      out.flush
      out.close
    out.toByteArray
