package shared.crypto

import java.io.FileOutputStream
import java.nio.file.{ Files, Paths }
import java.security.{ KeyStore, SecureRandom }
import scala.util.Using

object aes:

  val ALGORITHM = "AES"
  val keyEntryName = "secret"
  val CIPHER = "AES/CBC/PKCS5Padding"

  def generateSymmetricKeyAndPutItInJKS(jksFilePath: String, psw: String): Unit =
    println("deleteIfExists: " + Files.deleteIfExists(Paths.get(jksFilePath)))

    // val secretKey: javax.crypto.SecretKey = javax.crypto.KeyGenerator.getInstance(ALGORITHM).generateKey
    val secureRandom = new SecureRandom()
    val key = Array.ofDim[Byte](16)
    secureRandom.nextBytes(key)
    val secretKey: javax.crypto.SecretKey = new javax.crypto.spec.SecretKeySpec(key, ALGORITHM)

    val pwdArray = psw.toCharArray()
    val ks: KeyStore = KeyStore.getInstance("pkcs12")

    ks.load(null, pwdArray)

    // Save a Symmetric Key
    val secret = new KeyStore.SecretKeyEntry(secretKey)
    val password = new KeyStore.PasswordProtection(pwdArray)

    ks.setEntry(keyEntryName, secret, password)
    Using.resource(new FileOutputStream(jksFilePath))(fos => ks.store(fos, pwdArray))

end aes
