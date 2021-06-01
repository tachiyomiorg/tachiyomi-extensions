package eu.kanade.tachiyomi.extension.zh.dmzj.utils

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher

object RSA {
    private const val MAX_DECRYPT_BLOCK = 128

    fun decrypt(encryptedData: ByteArray, privateKey: String): ByteArray {
        val keyBytes = Base64.decode(privateKey, Base64.DEFAULT)
        val pkcs8KeySpec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        val privateK = keyFactory.generatePrivate(pkcs8KeySpec)
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateK)
        return doFinal(encryptedData, cipher)
    }

    private fun doFinal(encryptedData: ByteArray, cipher: Cipher): ByteArray {
        val inputLen = encryptedData.size
        ByteArrayOutputStream().use { out ->
            var offSet = 0
            var cache: ByteArray
            var i = 0

            val block = MAX_DECRYPT_BLOCK
            while (inputLen - offSet > 0) {
                cache = if (inputLen - offSet > block) {
                    cipher.doFinal(encryptedData, offSet, block)
                } else {
                    cipher.doFinal(encryptedData, offSet, inputLen - offSet)
                }
                out.write(cache, 0, cache.size)
                i++
                offSet = i * block
            }
            return out.toByteArray()
        }
    }
}
