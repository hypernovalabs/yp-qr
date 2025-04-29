package com.example.yp_qr

import android.util.Base64
import javax.crypto.spec.SecretKeySpec


object CryptoHelper {

    fun decrypt(encryptedData: String, encryptionKey: String): String {
        val key = encryptionKey.toByteArray(Charsets.UTF_8)
        val cipher = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding")
        val secretKey = SecretKeySpec(key, "AES")

        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey)
        val decodedEncryptedData = Base64.decode(encryptedData, Base64.DEFAULT)
        val decryptedBytes = cipher.doFinal(decodedEncryptedData)

        return String(decryptedBytes, Charsets.UTF_8)
    }
}
