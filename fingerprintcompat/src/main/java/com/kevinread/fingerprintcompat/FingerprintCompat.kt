package com.kevinread.fingerprintcompat

/**
 * Created by Kevin Read <me@kevin-read.com> on 22.08.18 for myrmecophaga-2.0.
 * Copyright (c) 2018 BörseGo AG. All rights reserved.
 *
 * Based on https://github.com/googlesamples/android-FingerprintDialog/
 */


import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.support.annotation.RequiresApi
import java.io.IOException
import java.security.*
import java.security.cert.CertificateException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

@RequiresApi(Build.VERSION_CODES.M)
/**
 * Main entry point for the sample, showing a backpack and "Purchase" button.
 */
class FingerprintCompat(applicationContext: Context) {

    private var keyStore: KeyStore
    private var keyGenerator: KeyGenerator

    private var fingerprintManager: FingerprintManager

    private var keyguardManager: KeyguardManager

    private var packageManager: PackageManager

    init {
        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore")
        } catch (e: KeyStoreException) {
            throw RuntimeException("Failed to get an instance of KeyStore", e)
        }

        try {
            keyGenerator = KeyGenerator
                    .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException("Failed to get an instance of KeyGenerator", e)
        } catch (e: NoSuchProviderException) {
            throw RuntimeException("Failed to get an instance of KeyGenerator", e)
        }

        keyguardManager = applicationContext.getSystemService(KeyguardManager::class.java) ?: throw RuntimeException("No keyguard")
        fingerprintManager = applicationContext.getSystemService(FingerprintManager::class.java) ?: throw RuntimeException("No FingerprintManager")
        packageManager = applicationContext.packageManager


        // Now the protection level of USE_FINGERPRINT permission is normal instead of dangerous.
        // See http://developer.android.com/reference/android/Manifest.permission.html#USE_FINGERPRINT
        // The line below prevents the false positive inspection from Android Studio

        // createKey(DEFAULT_KEY_NAME, true)
        // createKey(KEY_NAME_NOT_INVALIDATED, false)
    }

    fun hasKey(keyName: String): Boolean {
        keyStore.load(null)
        try {
            val keyExists = keyStore.getKey(keyName, null) != null
            return keyExists
        } catch (e: UnrecoverableKeyException) {
            return false
        }
    }

    fun retrieveErrorCode(): Pair<Int, String?>? {
        if (Build.VERSION.SDK_INT < 23) {
            return Pair(BiometricPromptCompat.BIOMETRIC_ERROR_HW_NOT_PRESENT, BiometricPromptCompat.retrieveErrorString(BiometricPromptCompat.BIOMETRIC_ERROR_HW_NOT_PRESENT)!!)
        }
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) {
            return Pair(BiometricPromptCompat.BIOMETRIC_ERROR_HW_NOT_PRESENT, BiometricPromptCompat.retrieveErrorString(BiometricPromptCompat.BIOMETRIC_ERROR_HW_NOT_PRESENT)!!)
        } else if (!fingerprintManager.isHardwareDetected()) {
            return Pair(BiometricPromptCompat.BIOMETRIC_ERROR_HW_UNAVAILABLE, BiometricPromptCompat.retrieveErrorString(BiometricPromptCompat.BIOMETRIC_ERROR_HW_UNAVAILABLE)!!)
        } else if (!fingerprintManager.hasEnrolledFingerprints()) {
            return Pair(BiometricPromptCompat.BIOMETRIC_ERROR_NO_BIOMETRICS, BiometricPromptCompat.retrieveErrorString(BiometricPromptCompat.BIOMETRIC_ERROR_NO_BIOMETRICS)!!)
        } else if (!keyguardManager.isDeviceSecure) {
            return Pair(BiometricPromptCompat.BIOMETRIC_ERROR_NO_KEYGUARD, null)
        }
        return null
    }

    fun areFingerprintsEnabled(): Boolean {
        if (!keyguardManager.isKeyguardSecure) {
            return false
        }

        @Suppress("DEPRECATION")
        if (!fingerprintManager.hasEnrolledFingerprints()) {
            return false
        }

        return true
    }

    /**
     * Initialize the [Cipher] instance with the created key in the
     * [.createKey] method.
     *
     * @param cipher Cipher to initialize
     * @param keyName the key name to init the cipher
     * @return `true` if initialization is successful, `false` if the lock screen has
     * been disabled or reset after the key was generated, or if a fingerprint got enrolled after
     * the key was generated.
     */
    fun initCipher(cipher: Cipher, keyName: String): Boolean {
        try {
            keyStore.load(null)
            val key = keyStore.getKey(keyName, null) as SecretKey?
            cipher.init(Cipher.ENCRYPT_MODE, key)
            return true
        } catch (e: KeyPermanentlyInvalidatedException) {
            return false
        }
    }

    fun initDecryptingCipher(cipher: Cipher, keyName: String, decodedIVs: ByteArray?): Boolean {
        try {
            keyStore.load(null)
            val key = keyStore.getKey(keyName, null) as SecretKey?
            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(decodedIVs))
            return true
        } catch (e: KeyPermanentlyInvalidatedException) {
            return false
        }
    }

    /**
     * Remove a key from the Android Key Store
     * @return true if the removal was successful
     */
    fun removeKey(keyName: String): Boolean {
        try {
            keyStore.load(null)

            keyStore.deleteEntry(keyName)
            return true
        } catch (e: KeyStoreException) {
            return false
        }
    }

    /**
     * Creates a symmetric key in the Android Key Store which can only be used after the user has
     * authenticated with fingerprint.
     *
     * @param keyName the name of the key to be created
     * @param invalidatedByBiometricEnrollment if `false` is passed, the created key will not
     * be invalidated even if a new fingerprint is enrolled.
     * The default value is `true`, so passing
     * `true` doesn't change the behavior
     * (the key will be invalidated if a new fingerprint is
     * enrolled.). Note that this parameter is only valid if
     * the app works on Android N developer preview.
     */
    fun createKey(keyName: String, invalidatedByBiometricEnrollment: Boolean) {
        // The enrolling flow for fingerprint. This is where you ask the user to set up fingerprint
        // for your flow. Use of keys is necessary if you need to know if the set of
        // enrolled fingerprints has changed.
        try {
            keyStore.load(null)
            // Set the alias of the entry in Android KeyStore where the key will appear
            // and the constrains (purposes) in the constructor of the Builder

            val builder = KeyGenParameterSpec.Builder(keyName,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    // Require the user to authenticate with a fingerprint to authorize every use
                    // of the key
                    .setUserAuthenticationRequired(true)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)

            // This is a workaround to avoid crashes on devices whose API level is < 24
            // because KeyGenParameterSpec.Builder#setInvalidatedByBiometricEnrollment is only
            // visible on API level +24.
            // Ideally there should be a compat library for KeyGenParameterSpec.Builder but
            // which isn't available yet.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setInvalidatedByBiometricEnrollment(invalidatedByBiometricEnrollment)
            }
            keyGenerator.init(builder.build())
            keyGenerator.generateKey()
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e)
        } catch (e: InvalidAlgorithmParameterException) {
            throw RuntimeException(e)
        } catch (e: CertificateException) {
            throw RuntimeException(e)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

    }
}
