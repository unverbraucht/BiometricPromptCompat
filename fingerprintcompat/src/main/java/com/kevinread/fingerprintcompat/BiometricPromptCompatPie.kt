package com.kevinread.fingerprintcompat

import android.annotation.SuppressLint
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.support.annotation.RequiresApi
import android.support.v4.app.FragmentActivity
import java.util.concurrent.Executor

/**
 * Created by Kevin Read <me@kevin-read.com> on 26.11.18 for biometricprint-compat.
 * Copyright (c) 2018 Kevin Read. All rights reserved.
 */
@RequiresApi(Build.VERSION_CODES.P)
class BiometricPromptCompatPie internal constructor(ctx: FragmentActivity,
                                                    bundle: Bundle,
                                                    positiveButtonInfo: ButtonInfo?,
                                                    negativeButtonInfo: ButtonInfo) : BiometricPromptCompat(ctx, bundle, positiveButtonInfo, negativeButtonInfo) {

    private var underlying: BiometricPrompt

    init {
        val builder = BiometricPrompt.Builder(ctx)
        val description = bundle.getCharSequence(KEY_DESCRIPTION)

        if (description != null) {
            builder.setDescription(description)
        }

        val subTitle = bundle.getCharSequence(KEY_SUBTITLE)
        if (subTitle != null) {
            builder.setSubtitle(subTitle)
        }

        val title = bundle.getCharSequence(KEY_TITLE)
        if (title != null) {
            builder.setTitle(title)
        }

        val negativeButtonText = bundle.getCharSequence(KEY_NEGATIVE_TEXT)
        if (negativeButtonText != null) {
            builder.setNegativeButton(negativeButtonText, negativeButtonInfo.executor, negativeButtonInfo.listener)
        }
        underlying = builder.build()
    }

    override fun authenticate(cancel: CancellationSignal, executor: Executor, callback: AuthenticationCallback) {
        val underlyingCallback = @SuppressLint("NewApi")
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                callback.onAuthenticationError(errorCode, errString)
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                callback.onAuthenticationSucceeded(AuthenticationResult(nativeCryptoToCompat(result.cryptoObject)))
            }

            override fun onAuthenticationHelp(helpCode: Int, helpString: CharSequence?) {
                callback.onAuthenticationHelp(helpCode, helpString)
            }

            override fun onAuthenticationFailed() {
                callback.onAuthenticationFailed()
            }
        }

        underlying.authenticate(cancel, executor, underlyingCallback)
    }

    override fun authenticate(cryptoObject: CryptoObject, cancel: CancellationSignal, executor: Executor, callback: AuthenticationCallback) {
        val underlyingCallback = @SuppressLint("NewApi")
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                callback.onAuthenticationError(errorCode, errString)
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                callback.onAuthenticationSucceeded(AuthenticationResult(nativeCryptoToCompat(result.cryptoObject)))
            }

            override fun onAuthenticationHelp(helpCode: Int, helpString: CharSequence?) {
                callback.onAuthenticationHelp(helpCode, helpString)
            }

            override fun onAuthenticationFailed() {
                callback.onAuthenticationFailed()
            }
        }

        underlying.authenticate(compatCryptoToNative(cryptoObject), cancel, executor, underlyingCallback)
    }

    private fun compatCryptoToNative(cryptoObject: CryptoObject): BiometricPrompt.CryptoObject {
        if (cryptoObject.signature != null) {
            return BiometricPrompt.CryptoObject(cryptoObject.signature)
        } else if (cryptoObject.cipher != null) {
            return BiometricPrompt.CryptoObject(cryptoObject.cipher)
        } else if (cryptoObject.mac != null) {
            return BiometricPrompt.CryptoObject(cryptoObject.mac)
        } else {
            throw IllegalArgumentException("illegal cryptoObject")
        }
    }

    private fun nativeCryptoToCompat(cryptoObject: BiometricPrompt.CryptoObject?): CryptoObject? {
        if (cryptoObject == null) {
            return null
        }
        return CryptoObject(signature = cryptoObject.signature, mac = cryptoObject.mac, cipher = cryptoObject.cipher)
    }

}