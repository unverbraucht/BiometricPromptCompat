package com.kevinread.fingerprintcompat

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.support.v4.app.FragmentActivity
import com.samsung.android.sdk.SsdkUnsupportedException
import com.samsung.android.sdk.pass.Spass
import com.samsung.android.sdk.pass.SpassFingerprint
import com.samsung.android.sdk.pass.SpassFingerprint.*
import java.util.concurrent.Executor

/**
 * Created by Kevin Read <me@kevin-read.com> on 26.11.18 for biometricprint-compat.
 * Copyright (c) 2018 Kevin Read. All rights reserved.
 */


private class ImplBuilder : BiometricPromptCompatImplBuilder() {
    override fun build(ctx: FragmentActivity, bundle: Bundle, positiveButtonInfo: BiometricPromptCompat.ButtonInfo?, negativeButtonInfo: BiometricPromptCompat.ButtonInfo): BiometricPromptCompat? {
        try {
            val spass = Spass()
            spass.initialize(ctx)

            return BiometricPromptCompatSpass(spass, ctx, bundle, positiveButtonInfo, negativeButtonInfo)
        } catch (e: SsdkUnsupportedException) {
            return null
        }
    }

}

class BiometricPromptCompatSpass internal constructor(val spass: Spass, val ctx: FragmentActivity,
                                                      bundle: Bundle,
                                                      positiveButtonInfo: BiometricPromptCompat.ButtonInfo?,
                                                      negativeButtonInfo: BiometricPromptCompat.ButtonInfo) : BiometricPromptCompat(ctx, bundle, positiveButtonInfo, negativeButtonInfo) {

    private var spassFingerPrint: SpassFingerprint

    init {
        spassFingerPrint = SpassFingerprint(ctx)
    }

    override fun authenticate(cancel: CancellationSignal, executor: Executor, callback: AuthenticationCallback) {
        spassFingerPrint.startIdentifyWithDialog(ctx, object : SpassFingerprint.IdentifyListener {
            override fun onFinished(eventStatus: Int) {
                // It is called when fingerprint identification is finished.
                when (eventStatus) {
                    SpassFingerprint.STATUS_AUTHENTIFICATION_SUCCESS, SpassFingerprint.STATUS_AUTHENTIFICATION_PASSWORD_SUCCESS -> callback.onAuthenticationSucceeded(AuthenticationResult(null))
                    SpassFingerprint.STATUS_AUTHENTIFICATION_FAILED, STATUS_QUALITY_FAILED -> callback.onAuthenticationFailed()
                    else -> {
                        val errorCode = mapSsdkError(eventStatus)
                        callback.onAuthenticationError(errorCode, retrieveErrorString(errorCode))
                    }

                }
            }

            override fun onReady() {
            }

            override fun onStarted() {
            }

            override fun onCompleted() {
            }

        }, false)

        cancel.setOnCancelListener { spassFingerPrint.cancelIdentify() }
    }

    override fun authenticate(cryptoObject: CryptoObject, cancel: CancellationSignal, executor: Executor, callback: AuthenticationCallback) {
        throw RuntimeException("Samsung Pass cannot do encryption")
    }

    private fun mapSsdkError(eventStatus: Int): Int {
        return when (eventStatus) {
            STATUS_BUTTON_PRESSED, STATUS_USER_CANCELLED_BY_TOUCH_OUTSIDE, STATUS_USER_CANCELLED -> BIOMETRIC_ERROR_USER_CANCELED
            STATUS_TIMEOUT_FAILED -> BIOMETRIC_ERROR_TIMEOUT
            else -> BIOMETRIC_ERROR_VENDOR
        }
    }


    companion object {
        init {
            _builders.add(ImplBuilder())
        }

        fun isSupported(ctx: Context): Boolean {
            try {
                val spass = Spass()
                spass.initialize(ctx)
                return true
            } catch (e: SsdkUnsupportedException) {
                return false
            }
        }
    }
}