/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.kevinread.fingerprintcompat

import android.content.DialogInterface
import android.hardware.fingerprint.FingerprintManager
import android.os.CancellationSignal
import android.widget.ImageView
import android.widget.TextView

import java.util.concurrent.Executor

@Suppress("DEPRECATION")
/**
 * Small helper class to manage text/icon around fingerprint authentication UI.
 */
internal class FingerprintUiHelper
/**
 * Constructor for [FingerprintUiHelper].
 */
internal constructor(private val fingerprintManager: FingerprintManager,
                     private val icon: ImageView, private val errorTextView: TextView,
                     private val dismissListener: DialogInterface.OnDismissListener,
                     private var cancellationSignal: CancellationSignal?,
                     private val executor: Executor?,
                     private val callback: BiometricPromptCompat.AuthenticationCallback?) : FingerprintManager.AuthenticationCallback() {

    private var selfCancelled: Boolean = false

    val isHardwareAvailable: Boolean
        get() = fingerprintManager.isHardwareDetected

    // The line below prevents the false positive inspection from Android Studio
    val isFingerprintAuthAvailable: Boolean
        get() = fingerprintManager.isHardwareDetected && fingerprintManager.hasEnrolledFingerprints()

    private val resetErrorTextRunnable = Runnable {
        errorTextView.setTextColor(
                errorTextView.resources.getColor(R.color.hint_color, null))
        errorTextView.text = errorTextView.resources.getString(R.string.fingerprint_hint)
        icon.setImageResource(R.drawable.ic_fingerprint_black_40dp)
    }

    fun startListening(cryptoObject: FingerprintManager.CryptoObject?) {
        if (!isFingerprintAuthAvailable) {
            return
        }

        selfCancelled = false

        fingerprintManager
                .authenticate(cryptoObject, cancellationSignal, 0 /* flags */, this, null)
        icon.setImageResource(R.drawable.ic_fingerprint_black_40dp)
    }

    fun stopListening() {
        if (cancellationSignal != null) {
            selfCancelled = true
            cancellationSignal!!.cancel()
            cancellationSignal = null
        }
    }

    override fun onAuthenticationError(errMsgId: Int, errString: CharSequence) {
        if (!selfCancelled) {
            showError(errString)
            icon.postDelayed({
                executor!!.execute {
                    callback!!.onAuthenticationError(BiometricPromptCompat.errorCodeFromFingerprintManager(errMsgId), errString)
                    dismissListener.onDismiss(null)
                }
                stopListening()
            }, ERROR_TIMEOUT_MILLIS)
        }
    }

    override fun onAuthenticationHelp(helpMsgId: Int, helpString: CharSequence) {
        showError(helpString)
        executor!!.execute { callback!!.onAuthenticationHelp(helpMsgId, helpString) }
    }

    override fun onAuthenticationFailed() {
        showError(icon.resources.getString(
                R.string.fingerprint_not_recognized))
        executor!!.execute { callback!!.onAuthenticationFailed() }
    }

    override fun onAuthenticationSucceeded(result: FingerprintManager.AuthenticationResult) {
        errorTextView.removeCallbacks(resetErrorTextRunnable)
        icon.setImageResource(R.drawable.ic_fingerprint_success)
        errorTextView.setTextColor(
                errorTextView.resources.getColor(R.color.success_color, null))
        errorTextView.text = errorTextView.resources.getString(R.string.fingerprint_success)

        if (callback != null && executor != null) {
        }
        icon.postDelayed({
            val cryptObject = if (result.cryptoObject == null) null else BiometricPromptCompat.CryptoObject(result.cryptoObject?.signature, result.cryptoObject?.cipher, result.cryptoObject?.mac)
            executor!!.execute {
                callback!!.onAuthenticationSucceeded(BiometricPromptCompat.AuthenticationResult(cryptObject))
                dismissListener.onDismiss(null)
            }
            stopListening()

        }, SUCCESS_DELAY_MILLIS)
    }

    private fun showError(error: CharSequence) {
        icon.setImageResource(R.drawable.ic_fingerprint_error)
        errorTextView.text = error
        errorTextView.setTextColor(
                errorTextView.resources.getColor(R.color.warning_color, null))
        errorTextView.removeCallbacks(resetErrorTextRunnable)
        errorTextView.postDelayed(resetErrorTextRunnable, ERROR_TIMEOUT_MILLIS)
    }

    companion object {

        private val ERROR_TIMEOUT_MILLIS: Long = 1600
        private val SUCCESS_DELAY_MILLIS: Long = 1300
    }

}
