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


private class ImplBuilder(val spass: Spass) : BiometricPromptCompatImplBuilder() {
    override fun build(ctx: FragmentActivity, bundle: Bundle, positiveButtonInfo: BiometricPromptCompat.ButtonInfo?, negativeButtonInfo: BiometricPromptCompat.ButtonInfo): BiometricPromptCompat? {
        try {
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
        if (!spassFingerPrint.hasRegisteredFinger()) {
            callback.onAuthenticationError(BIOMETRIC_ERROR_NO_BIOMETRICS, retrieveErrorString(BIOMETRIC_ERROR_NO_BIOMETRICS))
            return
        }

        if (spass.isFeatureEnabled(Spass.DEVICE_FINGERPRINT_CUSTOMIZED_DIALOG)) {
            spassFingerPrint.setDialogTitle(bundle.getString(KEY_TITLE), 0xff0000);
        }

        if (!spass.isFeatureEnabled(Spass.DEVICE_FINGERPRINT_AVAILABLE_PASSWORD)) {
            val description = bundle.getString(KEY_DESCRIPTION, "")
            val subtitle = bundle.getString(KEY_SUBTITLE, "")
            var actualTitle: String? = null
            if (description.isNotBlank() && subtitle.isNotBlank()) {
                actualTitle = "$description $subtitle"
            } else if (description.isNotBlank()) {
                actualTitle = description
            } else if (subtitle.isNotBlank()) {
                actualTitle = subtitle
            }
            if (actualTitle != null) {
                spassFingerPrint.changeStandbyString(actualTitle)
            }
        }

        if (positiveButtonInfo != null && bundle.containsKey(KEY_POSITIVE_TEXT)) {
            if (spass.isFeatureEnabled(Spass.DEVICE_FINGERPRINT_AVAILABLE_PASSWORD)) {
                spassFingerPrint.setDialogButton(bundle.getString(KEY_POSITIVE_TEXT))
            }
        }


        spassFingerPrint.startIdentifyWithDialog(ctx, object : SpassFingerprint.IdentifyListener {
            override fun onFinished(eventStatus: Int) {
                // It is called when fingerprint identification is finished.
                when (eventStatus) {
                    SpassFingerprint.STATUS_QUALITY_FAILED -> callback.onAuthenticationHelp(BIOMETRIC_ACQUIRED_INSUFFICIENT, spassFingerPrint.guideForPoorQuality)
                    SpassFingerprint.STATUS_AUTHENTIFICATION_SUCCESS, SpassFingerprint.STATUS_AUTHENTIFICATION_PASSWORD_SUCCESS -> callback.onAuthenticationSucceeded(AuthenticationResult(null))
                    SpassFingerprint.STATUS_AUTHENTIFICATION_FAILED -> callback.onAuthenticationFailed()
                    SpassFingerprint.STATUS_BUTTON_PRESSED -> positiveButtonInfo?.executor?.execute { positiveButtonInfo.listener.onClick(null, 1) }
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
        executor.execute { callback.onAuthenticationError(BIOMETRIC_ERROR_HW_NOT_PRESENT, retrieveErrorString(BIOMETRIC_ERROR_HW_NOT_PRESENT)) }
    }

    private fun mapSsdkError(eventStatus: Int): Int {
        return when (eventStatus) {
            STATUS_SENSOR_FAILED -> BIOMETRIC_ERROR_LOCKOUT
            STATUS_BUTTON_PRESSED, STATUS_USER_CANCELLED_BY_TOUCH_OUTSIDE, STATUS_USER_CANCELLED -> BIOMETRIC_ERROR_USER_CANCELED
            STATUS_TIMEOUT_FAILED -> BIOMETRIC_ERROR_TIMEOUT
            else -> BIOMETRIC_ERROR_VENDOR
        }
    }


    companion object {
        fun isSupported(ctx: Context): Boolean {
            try {
                val spass = Spass()
                spass.initialize(ctx)
                _builders.add(ImplBuilder(spass))
                return true
            } catch (e: SsdkUnsupportedException) {
                return false
            }
        }

        fun retrieveErrorCode(spassFingerprint: SpassFingerprint): Pair<Int, String?>? {
            if (!spassFingerprint.hasRegisteredFinger()) {
                return Pair(BiometricPromptCompat.BIOMETRIC_ERROR_NO_BIOMETRICS, BiometricPromptCompat.retrieveErrorString(BiometricPromptCompat.BIOMETRIC_ERROR_NO_BIOMETRICS))
            }

            return null
        }
    }
}