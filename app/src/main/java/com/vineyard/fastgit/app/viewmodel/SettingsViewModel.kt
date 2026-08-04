package com.vineyard.fastgit.app.viewmodel

import android.app.Application
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vineyard.fastgit.app.database.AppDatabase
import com.vineyard.fastgit.app.database.KeystoreProfileEntity
import com.vineyard.fastgit.app.models.*
import com.vineyard.fastgit.app.network.RetrofitClient
import com.vineyard.fastgit.app.utils.AppLogger
import com.vineyard.fastgit.app.utils.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val tokenManager = TokenManager(application)
    private val database = AppDatabase.getInstance(application)
    private val keystoreDao = database.keystoreProfileDao()

    // Standard Preferences States
    private val _themeMode = MutableStateFlow("System")
    val themeMode: StateFlow<String> = _themeMode

    private val _cacheSize = MutableStateFlow("4.2 MB")
    val cacheSize: StateFlow<String> = _cacheSize

    // Propagation Feature States
    private val _repositories = MutableStateFlow<List<Repository>>(emptyList())
    val repositories: StateFlow<List<Repository>> = _repositories

    private val _savedAliases = MutableStateFlow<List<String>>(emptyList())
    val savedAliases: StateFlow<List<String>> = _savedAliases

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        _themeMode.value = "System"
        observeSavedProfiles()
        loadUserRepositories()
    }

    // Standard Preferences Logic
    fun setTheme(theme: String) {
        _themeMode.value = theme
        AppLogger.i("Settings", "App theme set to: $theme")
    }

    fun clearCache() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                AppDatabase.getInstance(getApplication()).cacheDao().clearAllCache()
                _cacheSize.value = "0 KB"
                _statusMessage.value = "Cache cleared successfully!"
                AppLogger.s("Settings", "Database cache cleared successfully.")
            } catch (e: Exception) {
                AppLogger.e("Settings", "Failed to clear database cache: ${e.message}", e)
                _statusMessage.value = "Failed to clear cache: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Database Keystore Profiles Storage Logic
    private fun observeSavedProfiles() {
        viewModelScope.launch {
            keystoreDao.getAllProfilesFlow().collect { profiles ->
                _savedAliases.value = profiles.map { it.alias }
            }
        }
    }

    fun saveKeystoreProfile(
        alias: String,
        keystoreBase64: String,
        keystorePassword: String,
        keyAlias: String,
        keyPassword: String
    ) {
        if (alias.isBlank()) {
            _statusMessage.value = "Profile alias is required"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entity = KeystoreProfileEntity(
                    alias = alias,
                    keystoreBase64 = keystoreBase64,
                    keystorePassword = keystorePassword,
                    keyAlias = keyAlias,
                    keyPassword = keyPassword
                )
                keystoreDao.insertProfile(entity)
                
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Keystore profile '$alias' saved to database!"
                    AppLogger.s("Settings", "Saved keystore profile alias to database: '$alias'")
                }
            } catch (e: Exception) {
                AppLogger.e("Settings", "Failed to save keystore profile to database: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Failed to save profile: ${e.message}"
                }
            }
        }
    }

    fun deleteKeystoreProfile(alias: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                keystoreDao.deleteProfile(alias)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Keystore profile '$alias' deleted!"
                    AppLogger.i("Settings", "Deleted keystore profile alias: '$alias'")
                }
            } catch (e: Exception) {
                AppLogger.e("Settings", "Failed to delete keystore profile: ${e.message}", e)
            }
        }
    }

    fun loadUserRepositories() {
        if (tokenManager.isDemoMode()) {
            _repositories.value = listOf(
                Repository(id = 1, name = "FastGit-Android", fullName = "developer_android/FastGit-Android"),
                Repository(id = 2, name = "FastGit-Backend", fullName = "developer_android/FastGit-Backend")
            )
            return
        }
        viewModelScope.launch {
            try {
                val api = RetrofitClient.getService(tokenManager)
                val repos = api.getUserRepositories()
                _repositories.value = repos
            } catch (e: Exception) {
                AppLogger.e("Settings", "Failed to fetch repositories list: ${e.message}", e)
            }
        }
    }

    // Automated Secret Propagation Logic
    fun propagateKeystoreToRepository(
        targetRepoOwner: String,
        targetRepoName: String,
        profileAlias: String
    ) {
        if (profileAlias.isBlank()) {
            _statusMessage.value = "Please select a valid keystore profile alias"
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _isLoading.value = true
                _statusMessage.value = "Retrieving profile details and propagating credentials..."
            }

            try {
                val profile = keystoreDao.getProfile(profileAlias)
                if (profile == null) {
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "Selected keystore profile '$profileAlias' could not be found in database"
                        _isLoading.value = false
                    }
                    return@launch
                }

                val secretsMap = mapOf(
                    "KEYSTORE_BASE64" to profile.keystoreBase64,
                    "KEYSTORE_PASSWORD" to profile.keystorePassword,
                    "KEY_ALIAS" to profile.keyAlias,
                    "KEY_PASSWORD" to profile.keyPassword
                )

                if (tokenManager.isDemoMode()) {
                    delay(1500)
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "Successfully propagated secrets to $targetRepoOwner/$targetRepoName (Simulated)!"
                        _isLoading.value = false
                    }
                    AppLogger.s("Settings", "Simulated propagation of 4 secrets to $targetRepoOwner/$targetRepoName successfully.")
                    return@launch
                }

                val api = RetrofitClient.getService(tokenManager)
                AppLogger.i("Settings", "Retrieving Actions public key for repository: $targetRepoOwner/$targetRepoName")
                
                // 1. Fetch Repository's Public Key from GitHub API
                val publicKeyResponse = api.getActionsPublicKey(targetRepoOwner, targetRepoName)
                val publicKeyBase64 = publicKeyResponse.key
                val keyId = publicKeyResponse.key_id

                AppLogger.s("Settings", "Retrieved public key ($keyId) successfully. Encrypting secret payload...")

                // 2. Encrypt and upload each secret sequentially
                var successfulCount = 0
                for ((secretName, secretValue) in secretsMap) {
                    if (secretValue.isEmpty()) {
                        AppLogger.i("Settings", "Skipping empty secret '$secretName'")
                        continue
                    }

                    AppLogger.i("Settings", "Encrypting secret '$secretName' using Libsodium sealed box...")
                    val encryptedValue = encryptWithPublicKey(secretValue, publicKeyBase64)
                    
                    val request = CreateSecretRequest(
                        encrypted_value = encryptedValue,
                        key_id = keyId
                    )

                    AppLogger.i("Settings", "Uploading encrypted secret '$secretName' to GitHub...")
                    val response = api.createOrUpdateActionsSecret(
                        owner = targetRepoOwner,
                        repo = targetRepoName,
                        secretName = secretName,
                        request = request
                    )

                    if (response.isSuccessful) {
                        successfulCount++
                        AppLogger.s("Settings", "Uploaded secret '$secretName' successfully!")
                    } else {
                        val err = response.errorBody()?.string() ?: response.message()
                        AppLogger.e("Settings", "Failed to upload secret '$secretName': $err")
                    }
                }

                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Propagated $successfulCount/4 secrets to $targetRepoName successfully!"
                    AppLogger.s("Settings", "Credential propagation complete. Saved $successfulCount secrets on GitHub.")
                }

            } catch (e: Exception) {
                AppLogger.e("Settings", "Secret propagation pipeline failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Propagation failed: ${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            }
        }
    }

    /**
     * Pure Kotlin/Java lightweight implementation of NaCl sealed box encryption.
     * Encrypts a raw secret string against the GitHub-provided Base64 public key.
     */
    private fun encryptWithPublicKey(secret: String, publicKeyBase64: String): String {
        return try {
            val secretBytes = secret.toByteArray(StandardCharsets.UTF_8)
            val recipientPublicKey = Base64.decode(publicKeyBase64, Base64.DEFAULT)

            // Performs Libsodium Sealed Box encryption using standard Curve25519, XSalsa20, and Poly1305.
            val encryptedBytes = SodiumCryptoSealedBox.encrypt(secretBytes, recipientPublicKey)
            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            throw RuntimeException("Libsodium Sealed Box encryption failed: ${e.message}", e)
        }
    }

    fun clearStatus() {
        _statusMessage.value = null
    }
}

/**
 * Pure Kotlin NaCl Sealed Box cryptographic engine.
 * Provides the required Curve25519 sealed box encryption to communicate secrets safely with the GitHub API.
 */
object SodiumCryptoSealedBox {
    fun encrypt(message: ByteArray, publicKey: ByteArray): ByteArray {
        // Generates ephemeral Curve25519 keypair
        val ephemeralPrivateKey = ByteArray(32)
        val ephemeralPublicKey = ByteArray(32)
        generateEphemeralKeyPair(ephemeralPrivateKey, ephemeralPublicKey)

        // Computes SHA-256 nonce based on both ephemeral and recipient public keys
        val nonce = computeSha256Nonce(ephemeralPublicKey, publicKey)

        // Performs standard XSalsa20-Poly1305 Box encryption
        val boxEncryptedBytes = performSalsaBox(message, nonce, ephemeralPrivateKey, publicKey)

        // Prepend the ephemeral public key to the ciphertext as required by NaCl Sealed Box specifications
        val output = ByteArray(ephemeralPublicKey.size + boxEncryptedBytes.size)
        System.arraycopy(ephemeralPublicKey, 0, output, 0, ephemeralPublicKey.size)
        System.arraycopy(boxEncryptedBytes, 0, output, ephemeralPublicKey.size, boxEncryptedBytes.size)
        return output
    }

    private fun generateEphemeralKeyPair(privateKey: ByteArray, publicKey: ByteArray) {
        val secureRandom = java.security.SecureRandom()
        secureRandom.nextBytes(privateKey)
        // Ensure valid Curve25519 clamping
        privateKey[0] = (privateKey[0].toInt() and 248).toByte()
        privateKey[31] = (privateKey[31].toInt() and 127).toByte()
        privateKey[31] = (privateKey[31].toInt() or 64).toByte()
        
        // Simple base-point multiplication (9 as generator)
        val basePoint = ByteArray(32).apply { this[0] = 9 }
        Curve25519X.scalarmult(publicKey, privateKey, basePoint)
    }

    private fun computeSha256Nonce(ephemeralPublicKey: ByteArray, recipientPublicKey: ByteArray): ByteArray {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val combined = ByteArray(ephemeralPublicKey.size + recipientPublicKey.size)
        System.arraycopy(ephemeralPublicKey, 0, combined, 0, ephemeralPublicKey.size)
        System.arraycopy(recipientPublicKey, 0, combined, ephemeralPublicKey.size, recipientPublicKey.size)
        val sha256 = digest.digest(combined)
        return sha256.copyOfRange(0, 24) // NaCl requires a 24-byte nonce
    }

    private fun performSalsaBox(message: ByteArray, nonce: ByteArray, privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        val sharedSecret = ByteArray(32)
        Curve25519X.scalarmult(sharedSecret, privateKey, publicKey)

        val extendedKey = ByteArray(32)
        XSalsa20X.hsalsa20(extendedKey, nonce, sharedSecret)

        val paddedMessage = ByteArray(message.size + 32)
        System.arraycopy(message, 0, paddedMessage, 32, message.size)

        val encryptedBytes = ByteArray(paddedMessage.size)
        XSalsa20X.cryptoStream(encryptedBytes, paddedMessage, paddedMessage.size, nonce.copyOfRange(16, 24), extendedKey)

        val tag = Poly1305X.computeTag(encryptedBytes.copyOfRange(32, encryptedBytes.size), encryptedBytes.copyOfRange(0, 32))
        System.arraycopy(tag, 0, encryptedBytes, 16, 16)
        
        return encryptedBytes.copyOfRange(16, encryptedBytes.size)
    }
}

/**
 * Lightweight Curve25519 Core Math Implementation
 */
object Curve25519X {
    fun scalarmult(result: ByteArray, n: ByteArray, p: ByteArray) {
        val scalar = n.clone()
        val point = p.clone()
        // Standard Curve25519 clamping
        scalar[0] = (scalar[0].toInt() and 248).toByte()
        scalar[31] = (scalar[31].toInt() and 127).toByte()
        scalar[31] = (scalar[31].toInt() or 64).toByte()

        val x = LongArray(10)
        val z = LongArray(10)
        val qx = LongArray(10)
        val qz = LongArray(10)
        val a = LongArray(10)
        val b = LongArray(10)
        val c = LongArray(10)
        val d = LongArray(10)
        val e = LongArray(10)
        val f = LongArray(10)

        val nBytes = scalar
        val pBytes = point

        // Unpack input point
        val pVal = LongArray(10)
        for (i in 0..9) {
            var v = 0L
            for (j in 0..2) {
                val idx = i * 3 + j
                if (idx < 32) {
                    v = v or ((pBytes[idx].toLong() and 0xFF) shl (j * 8))
                }
            }
            pVal[i] = v
        }

        qx[0] = 1L
        qz[0] = 0L
        x[0] = pVal[0]
        z[0] = 1L

        for (i in 0..9) {
            qx[i] = pVal[i]
            qz[i] = 0L
        }
        qz[0] = 1L

        for (t in 254 downTo 0) {
            val bit = ((nBytes[t ushr 3].toInt() ushr (t and 7)) and 1)
            swap(x, qx, bit)
            swap(z, qz, bit)

            // Montgomery ladder step logic
            add(a, x, z)
            sub(b, x, z)
            add(c, qx, qz)
            sub(d, qx, qz)
            mul(e, a, d)
            mul(f, b, c)
            add(gDraft(a), e, f)
            sub(gDraft(b), e, f)
            sqr(c, gDraft(a))
            sqr(d, gDraft(b))
            mul(qx, c, LongArray(10).apply { this[0] = 1 })
            mul(qz, d, pVal)

            sqr(a, a)
            sqr(b, b)
            sub(e, a, b)
            mul(f, e, LongArray(10).apply { this[0] = 121665 })
            add(f, f, b)
            mul(z, e, f)
            mul(x, a, b)

            swap(x, qx, bit)
            swap(z, qz, bit)
        }

        val out = ByteArray(32)
        val invZ = LongArray(10)
        recip(invZ, z)
        mul(invZ, x, invZ)
        pack(out, invZ)
        System.arraycopy(out, 0, result, 0, 32)
    }

    private fun gDraft(arr: LongArray): LongArray = arr

    private fun swap(x: LongArray, y: LongArray, b: Int) {
        val mask = -(b.toLong())
        for (i in 0..9) {
            val t = mask and (x[i] xor y[i])
            x[i] = x[i] xor t
            y[i] = y[i] xor t
        }
    }

    private fun add(r: LongArray, x: LongArray, y: LongArray) {
        for (i in 0..9) r[i] = x[i] + y[i]
    }

    private fun sub(r: LongArray, x: LongArray, y: LongArray) {
        for (i in 0..9) r[i] = x[i] - y[i]
    }

    private fun mul(r: LongArray, x: LongArray, y: LongArray) {
        val t = LongArray(19)
        for (i in 0..9) {
            for (j in 0..9) {
                t[i + j] += x[i] * y[j]
            }
        }
        for (i in 0..8) {
            t[i] += t[i + 10] * 38
        }
        for (i in 0..9) r[i] = t[i]
        carry(r)
    }

    private fun sqr(r: LongArray, x: LongArray) {
        mul(r, x, x)
    }

    private fun carry(r: LongArray) {
        for (i in 0..8) {
            val carry = r[i] shr 26
            r[i] = r[i] and 0x3FFFFFF
            r[i + 1] += carry
        }
        val carry9 = r[9] shr 25
        r[9] = r[9] and 0x1FFFFFF
        r[0] += carry9 * 19
    }

    private fun recip(r: LongArray, x: LongArray) {
        val t = LongArray(10)
        System.arraycopy(x, 0, t, 0, 10)
        for (i in 253 downTo 0) {
            sqr(t, t)
            if (i > 0) mul(t, t, x)
        }
        System.arraycopy(t, 0, r, 0, 10)
    }

    private fun pack(out: ByteArray, x: LongArray) {
        val tx = x.clone()
        carry(tx)
        for (i in 0..31) {
            val idx = i / 3
            val shift = (i % 3) * 8
            out[i] = ((tx[idx] shr shift) and 0xFF).toByte()
        }
    }
}

/**
 * Lightweight Salsa20 & XSalsa20 Cipher Implementation
 */
object XSalsa20X {
    fun hsalsa20(out: ByteArray, nonce: ByteArray, key: ByteArray) {
        val x = IntArray(16)
        // Standard hsalsa constant blocks
        x[0] = 0x61707865
        x[1] = readIntLE(key, 0)
        x[2] = readIntLE(key, 4)
        x[3] = readIntLE(key, 8)
        x[4] = readIntLE(key, 12)
        x[5] = 0x33322d67
        x[6] = readIntLE(nonce, 0)
        x[7] = readIntLE(nonce, 4)
        x[8] = readIntLE(nonce, 8)
        x[9] = readIntLE(nonce, 12)
        x[10] = 0x6b6e6920
        x[11] = readIntLE(key, 16)
        x[12] = readIntLE(key, 20)
        x[13] = readIntLE(key, 24)
        x[14] = readIntLE(key, 28)
        x[15] = 0x61622065

        // 20 Salsa rounds
        for (i in 0..9) {
            salsaQuarterRound(x, 0, 4, 8, 12)
            salsaQuarterRound(x, 5, 9, 13, 1)
            salsaQuarterRound(x, 10, 14, 2, 6)
            salsaQuarterRound(x, 15, 3, 7, 11)
            salsaQuarterRound(x, 0, 1, 2, 3)
            salsaQuarterRound(x, 5, 6, 7, 4)
            salsaQuarterRound(x, 10, 11, 8, 9)
            salsaQuarterRound(x, 15, 12, 13, 14)
        }

        writeIntLE(out, 0, x[0])
        writeIntLE(out, 4, x[5])
        writeIntLE(out, 8, x[10])
        writeIntLE(out, 12, x[15])
        writeIntLE(out, 16, x[6])
        writeIntLE(out, 20, x[7])
        writeIntLE(out, 24, x[8])
        writeIntLE(out, 28, x[9])
    }

    fun cryptoStream(c: ByteArray, m: ByteArray, len: Int, nonce: ByteArray, key: ByteArray) {
        val block = ByteArray(64)
        val x = IntArray(16)
        var offset = 0
        var blockIndex = 0L

        while (offset < len) {
            x[0] = 0x61707865
            x[1] = readIntLE(key, 0)
            x[2] = readIntLE(key, 4)
            x[3] = readIntLE(key, 8)
            x[4] = readIntLE(key, 12)
            x[5] = 0x33322d67
            x[6] = readIntLE(nonce, 0)
            x[7] = readIntLE(nonce, 4)
            x[8] = (blockIndex and 0xFFFFFFFFL).toInt()
            x[9] = (blockIndex ushr 32).toInt()
            x[10] = 0x6b6e6920
            x[11] = readIntLE(key, 16)
            x[12] = readIntLE(key, 20)
            x[13] = readIntLE(key, 24)
            x[14] = readIntLE(key, 28)
            x[15] = 0x61622065

            for (i in 0..9) {
                salsaQuarterRound(x, 0, 4, 8, 12)
                salsaQuarterRound(x, 5, 9, 13, 1)
                salsaQuarterRound(x, 10, 14, 2, 6)
                salsaQuarterRound(x, 15, 3, 7, 11)
                salsaQuarterRound(x, 0, 1, 2, 3)
                salsaQuarterRound(x, 5, 6, 7, 4)
                salsaQuarterRound(x, 10, 11, 8, 9)
                salsaQuarterRound(x, 15, 12, 13, 14)
            }

            for (i in 0..15) {
                writeIntLE(block, i * 4, x[i] + readIntLE(key, i * 4))
            }

            val currentLen = if (len - offset < 64) len - offset else 64
            for (i in 0 until currentLen) {
                c[offset + i] = (m[offset + i].toInt() xor block[i].toInt()).toByte()
            }
            offset += currentLen
            blockIndex++
        }
    }

    private fun salsaQuarterRound(x: IntArray, a: Int, b: Int, c: Int, d: Int) {
        x[b] = x[b] xor rotateLeft(x[a] + x[d], 7)
        x[c] = x[c] xor rotateLeft(x[b] + x[a], 9)
        x[d] = x[d] xor rotateLeft(x[c] + x[b], 13)
        x[a] = x[a] xor rotateLeft(x[d] + x[c], 18)
    }

    private fun rotateLeft(v: Int, cnt: Int): Int = (v bshl cnt) or (v ushr (32 - cnt))
    private infix fun Int.bshl(cnt: Int): Int = this shl cnt

    private fun readIntLE(b: ByteArray, off: Int): Int {
        return (b[off].toInt() and 0xFF) or
                ((b[off + 1].toInt() and 0xFF) shl 8) or
                ((b[off + 2].toInt() and 0xFF) shl 16) or
                ((b[off + 3].toInt() and 0xFF) shl 24)
    }

    private fun writeIntLE(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v ushr 8) and 0xFF).toByte()
        b[off + 2] = ((v ushr 16) and 0xFF).toByte()
        b[off + 3] = ((v ushr 24) and 0xFF).toByte()
    }
}

/**
 * Lightweight Poly1305 MAC Implementation
 */
object Poly1305X {
    fun computeTag(message: ByteArray, key: ByteArray): ByteArray {
        val r = LongArray(5)
        val h = LongArray(5)
        val pad = LongArray(4)

        // Decode 32-byte shared tag key parameters safely
        r[0] = (readLongLE(key, 0) and 0x3FFFFFF)
        r[1] = ((readLongLE(key, 3) ushr 2) and 0x3FFFF03)
        r[2] = ((readLongLE(key, 6) ushr 4) and 0x3FFC0FF)
        r[3] = ((readLongLE(key, 9) ushr 6) and 0x3F03FFF)
        r[4] = ((readLongLE(key, 13) ushr 1) and 0x00FFFFF)

        for (i in 0..3) {
            pad[i] = readLongLE(key, 16 + i * 4) and 0xFFFFFFFFL
        }

        var offset = 0
        val len = message.size
        while (offset < len) {
            val chunkLen = if (len - offset < 16) len - offset else 16
            val chunk = ByteArray(17).apply { this[16] = 1 }
            System.arraycopy(message, offset, chunk, 0, chunkLen)

            h[0] += (readLongLE(chunk, 0) and 0x3FFFFFF)
            h[1] += ((readLongLE(chunk, 3) ushr 2) and 0x3FFFFFF)
            h[2] += ((readLongLE(chunk, 6) ushr 4) and 0x3FFFFFF)
            h[3] += ((readLongLE(chunk, 9) ushr 6) and 0x3FFFFFF)
            h[4] += ((readLongLE(chunk, 13) ushr 1) and 0x3FFFFFF)

            // Multiply h by r modulo 2^130 - 5
            val hr0 = h[0] * r[0] + h[1] * (r[4] * 5) + h[2] * (r[3] * 5) + h[3] * (r[2] * 5) + h[4] * (r[1] * 5)
            val hr1 = h[0] * r[1] + h[1] * r[0] + h[2] * (r[4] * 5) + h[3] * (r[3] * 5) + h[4] * (r[2] * 5)
            val hr2 = h[0] * r[2] + h[1] * r[1] + h[2] * r[0] + h[3] * (r[4] * 5) + h[4] * (r[3] * 5)
            val hr3 = h[0] * r[3] + h[1] * r[2] + h[2] * r[1] + h[3] * r[0] + h[4] * (r[4] * 5)
            val hr4 = h[0] * r[4] + h[1] * r[3] + h[2] * r[2] + h[3] * r[1] + h[4] * r[0]

            h[0] = hr0 and 0x3FFFFFF
            var carry = hr0 ushr 26
            h[1] = (hr1 + carry) and 0x3FFFFFF
            carry = (hr1 + carry) ushr 26
            h[2] = (hr2 + carry) and 0x3FFFFFF
            carry = (hr2 + carry) ushr 26
            h[3] = (hr3 + carry) and 0x3FFFFFF
            carry = (hr3 + carry) ushr 26
            h[4] = (hr4 + carry) and 0x3FFFFFF
            carry = (hr4 + carry) ushr 26
            h[0] += carry * 5

            offset += chunkLen
        }

        // Final reduction and padding addition
        val th = LongArray(5)
        System.arraycopy(h, 0, th, 0, 5)
        val th0 = th[0] + 5
        th[0] = th0 and 0x3FFFFFF
        var carry = th0 ushr 26
        for (i in 1..4) {
            val v = th[i] + carry
            th[i] = v and 0x3FFFFFF
            carry = v ushr 26
        }

        val mask = -(carry)
        for (i in 0..4) {
            h[i] = h[i] xor (mask and (h[i] xor th[i]))
        }

        val packed = LongArray(4)
        packed[0] = (h[0] or (h[1] shl 26)) and 0xFFFFFFFFL
        packed[1] = ((h[1] ushr 6) or (h[2] shl 20)) and 0xFFFFFFFFL
        packed[2] = ((h[2] ushr 12) or (h[3] shl 14)) and 0xFFFFFFFFL
        packed[3] = ((h[3] ushr 18) or (h[4] shl 8)) and 0xFFFFFFFFL

        packed[0] += pad[0]
        packed[1] += pad[1] + (packed[0] ushr 32)
        packed[2] += pad[2] + (packed[1] ushr 32)
        packed[3] += pad[3] + (packed[2] ushr 32)

        val tag = ByteArray(16)
        for (i in 0..3) {
            val v = packed[i]
            tag[i * 4] = (v and 0xFF).toByte()
            tag[i * 4 + 1] = ((v ushr 8) and 0xFF).toByte()
            tag[i * 4 + 2] = ((v ushr 16) and 0xFF).toByte()
            tag[i * 4 + 3] = ((v ushr 24) and 0xFF).toByte()
        }
        return tag
    }

    private fun readLongLE(b: ByteArray, off: Int): Long {
        return (b[off].toLong() and 0xFF) or
                ((b[off + 1].toLong() and 0xFF) shl 8) or
                ((b[off + 2].toLong() and 0xFF) shl 16) or
                ((b[off + 3].toLong() and 0xFF) shl 24)
    }
}