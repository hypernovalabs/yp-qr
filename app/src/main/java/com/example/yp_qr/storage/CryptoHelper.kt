package com.example.tefbanesco.storage

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object CryptoHelper {

    fun decrypt(encryptedData: String, key: String): String {
        val decodedKey = Base64.getUrlDecoder().decode(key) // ✅ Usa URL decoder
        val secretKeySpec = SecretKeySpec(decodedKey, "AES")

        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec)

        val decodedData = Base64.getUrlDecoder().decode(encryptedData) // ✅ También URL decoder aquí
        val decryptedBytes = cipher.doFinal(decodedData)

        return String(decryptedBytes, Charsets.UTF_8)
    }
}
