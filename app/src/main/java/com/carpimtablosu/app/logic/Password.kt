package com.carpimtablosu.app.logic

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Ebeveyn şifresi: düz metin saklanmaz, PBKDF2 özeti tutulur. */
object Password {
    const val MIN_LENGTH = 6
    const val MAX_LENGTH = 32
    private const val ITERATIONS = 60_000
    private const val FREE_ATTEMPTS = 5
    private const val BASE_LOCKOUT_MS = 30_000L
    private const val MAX_LOCKOUT_MS = 15 * 60_000L
    private const val RECOVERY_ALPHABET = "ABCDEFGHJKLMNPRSTUVYZ23456789"

    private val random = SecureRandom()

    /** Geçersizse hata mesajı, geçerliyse null. Harf ve rakam kullanılabilir. */
    fun validate(pw: String): String? = when {
        pw.length < MIN_LENGTH -> "Şifre en az $MIN_LENGTH karakter olmalı"
        pw.length > MAX_LENGTH -> "Şifre en fazla $MAX_LENGTH karakter olabilir"
        !pw.all { it.isLetterOrDigit() } -> "Sadece harf ve rakam kullanılabilir"
        else -> null
    }

    fun newSalt(): ByteArray = ByteArray(16).also { random.nextBytes(it) }

    fun hash(secret: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, ITERATIONS, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun verify(secret: String, salt: ByteArray, expected: ByteArray): Boolean =
        MessageDigest.isEqual(hash(secret, salt), expected)

    /** Şifre unutulursa kullanılacak tek seferlik kod (ör. ABCD2-EFGH3). */
    fun newRecoveryCode(): String {
        val chars = CharArray(10) { RECOVERY_ALPHABET[random.nextInt(RECOVERY_ALPHABET.length)] }
        return String(chars, 0, 5) + "-" + String(chars, 5, 5)
    }

    fun normalizeRecovery(code: String): String =
        code.uppercase(Locale.ROOT).filter { it in RECOVERY_ALPHABET }

    /** Arka arkaya hatalı denemeden sonra bekleme süresi (artarak). */
    fun lockoutMs(failures: Int): Long {
        if (failures < FREE_ATTEMPTS) return 0L
        val steps = (failures - FREE_ATTEMPTS).coerceAtMost(10)
        return (BASE_LOCKOUT_MS shl steps).coerceAtMost(MAX_LOCKOUT_MS)
    }

    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    fun decode(s: String): ByteArray = Base64.getDecoder().decode(s)
}
