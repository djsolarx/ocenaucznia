package com.example.opiniaucznia

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Szyfrowanie lokalnego transferu danych przez kody QR.
 * Format binarny: salt (16 B) + IV (12 B) + ciphertext z tagiem GCM.
 */
object QrTransferCrypto {
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val KEY_SIZE = 256
    private const val ITERATIONS = 180_000
    private const val HEADER = "OPINIAQR1"
    private const val CHUNK_SIZE = 500
    private const val MAX_CHUNKS = 2000

    data class QrChunk(
        val transferId: String,
        val index: Int,
        val total: Int,
        val data: String
    )

    fun encrypt(plainText: String, password: String): String {
        require(password.length >= 8) {
            "Hasło musi mieć co najmniej 8 znaków."
        }

        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
        val iv = ByteArray(IV_SIZE).also(random::nextBytes)
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val packed = salt + iv + encrypted
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    fun decrypt(payload: String, password: String): String {
        require(password.length >= 8) {
            "Podane hasło jest nieprawidłowe."
        }

        val packed = Base64.decode(payload, Base64.DEFAULT)
        require(packed.size > SALT_SIZE + IV_SIZE + 16) {
            "Pakiet jest niekompletny lub uszkodzony."
        }

        val salt = packed.copyOfRange(0, SALT_SIZE)
        val iv = packed.copyOfRange(SALT_SIZE, SALT_SIZE + IV_SIZE)
        val encrypted = packed.copyOfRange(SALT_SIZE + IV_SIZE, packed.size)
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_SIZE)
        return try {
            val bytes = SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun splitIntoQrChunks(
        encryptedPayload: String,
        transferId: String = java.util.UUID.randomUUID().toString()
    ): List<String> {
        require(transferId.matches(Regex("[A-Za-z0-9-]{1,64}"))) {
            "Nieprawidłowy identyfikator transferu."
        }

        val parts = encryptedPayload.chunked(CHUNK_SIZE)
        require(parts.isNotEmpty() && parts.size <= MAX_CHUNKS) {
            "Pakiet ma nieobsługiwaną wielkość."
        }

        return parts.mapIndexed { index, part ->
            "$HEADER|$transferId|${index + 1}|${parts.size}|$part"
        }
    }

    fun parseQrChunk(value: String): QrChunk {
        val fields = value.split('|', limit = 5)
        require(fields.size == 5 && fields[0] == HEADER) {
            "To nie jest kod transferu opinii."
        }

        val transferId = fields[1]
        val index = fields[2].toIntOrNull()
            ?: throw IllegalArgumentException("Nieprawidłowy numer części.")
        val total = fields[3].toIntOrNull()
            ?: throw IllegalArgumentException("Nieprawidłowa liczba części.")
        val data = fields[4]

        require(transferId.matches(Regex("[A-Za-z0-9-]{1,64}"))) {
            "Nieprawidłowy identyfikator transferu."
        }
        require(total in 1..MAX_CHUNKS && index in 1..total && data.isNotEmpty()) {
            "Nieprawidłowe dane części kodu QR."
        }

        return QrChunk(transferId, index, total, data)
    }

    fun combineQrChunks(chunks: List<QrChunk>): String {
        require(chunks.isNotEmpty()) { "Nie zeskanowano żadnych kodów QR." }

        val first = chunks.first()
        require(chunks.all {
            it.transferId == first.transferId && it.total == first.total
        }) {
            "Zeskanowane kody pochodzą z różnych transferów."
        }

        val unique = chunks.associateBy { it.index }
        require(unique.size == first.total &&
                (1..first.total).all { it in unique }) {
            "Brakuje części transferu. Zeskanuj wszystkie kody QR."
        }

        return (1..first.total).joinToString("") { unique.getValue(it).data }
    }
}
