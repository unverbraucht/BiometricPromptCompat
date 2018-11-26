package com.kevinread.fingerprintcompat

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.fingerprint.FingerprintManager
import android.os.Bundle
import android.os.CancellationSignal
import android.support.annotation.RequiresPermission
import android.support.v4.app.FragmentActivity
import android.support.v4.app.FragmentManager
import java.util.concurrent.Executor

/**
 * Created by Kevin Read <me@kevin-read.com> on 26.11.18 for biometricprint-compat.
 * Copyright (c) 2018 ${ORGANIZATION_NAME}. All rights reserved.
 */
class BiometricPromptCompatMarshmallow internal constructor(ctx: FragmentActivity,
                                                            bundle: Bundle,
                                                            positiveButtonInfo: BiometricPromptCompat.ButtonInfo?,
                                                            negativeButtonInfo: BiometricPromptCompat.ButtonInfo) : BiometricPromptCompat(ctx, bundle, positiveButtonInfo, negativeButtonInfo) {


    private var packageManager: PackageManager

    private var fingerPrintManager: FingerprintManager

    private var fragmentManager: FragmentManager

    @RequiresPermission(Manifest.permission.USE_FINGERPRINT)
    override fun authenticate(cryptoObject: CryptoObject, cancel: CancellationSignal, executor: Executor, callback: AuthenticationCallback) {
        doAuthenticate(cryptoObject, cancel, executor, callback)
    }

    override fun authenticate(cancel: CancellationSignal, executor: Executor, callback: AuthenticationCallback) {
        doAuthenticate(null, cancel, executor, callback)
    }

    private fun doAuthenticate(cryptoObject: CryptoObject?, cancel: CancellationSignal, executor: Executor, callback: AuthenticationCallback) {

        if (handlePreAuthenticationErrors(callback, executor)) {
            return
        }
        val fragment = FingerprintAuthenticationDialogFragment()
        fragment.setData(convertCryptoObject(cryptoObject), bundle, cancel, executor, callback, negativeButtonInfo)

        val transaction = fragmentManager.beginTransaction()
        transaction.add(fragment, DIALOG_FRAGMENT_TAG)
        transaction.commitAllowingStateLoss()
    }

    init {
        packageManager = ctx.packageManager
        fingerPrintManager = ctx.getSystemService(FingerprintManager::class.java)
        fragmentManager = ctx.supportFragmentManager
    }

    private fun handlePreAuthenticationErrors(callback: AuthenticationCallback,
                                              executor: Executor): Boolean {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) {
            sendError(BIOMETRIC_ERROR_HW_NOT_PRESENT, callback,
                    executor)
            return true
        } else if (!fingerPrintManager.isHardwareDetected()) {
            sendError(BIOMETRIC_ERROR_HW_UNAVAILABLE, callback,
                    executor)
            return true
        } else {
            try {
                if (!fingerPrintManager.hasEnrolledFingerprints()) {
                    sendError(BIOMETRIC_ERROR_NO_BIOMETRICS, callback,
                            executor)
                    return true
                }
            } catch (e: SecurityException) {
                // Some older MM phones throw this exception
                sendError(BIOMETRIC_ERROR_HW_UNAVAILABLE, callback,
                        executor)
            }
        }
        return false
    }

    private fun convertCryptoObject(cryptoObject: CryptoObject?): FingerprintManager.CryptoObject? {
        if (cryptoObject == null) {
            return null
        }

        if (cryptoObject.signature != null) {
            return FingerprintManager.CryptoObject(cryptoObject.signature)
        } else if (cryptoObject.cipher != null) {
            return FingerprintManager.CryptoObject(cryptoObject.cipher)
        } else if (cryptoObject.mac != null) {
            return FingerprintManager.CryptoObject(cryptoObject.mac)
        } else {
            throw IllegalArgumentException("illegal cryptoObject")
        }
    }

    private fun sendError(error: Int, callback: AuthenticationCallback, executor: Executor) {
        executor.execute {
            callback.onAuthenticationError(error, retrieveErrorString(error))
        }
    }


    companion object {

        private const val DIALOG_FRAGMENT_TAG = "com.kevinread.fingerprintcompat.BiometricCompat"
    }
}