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
internal constructor(private val mFingerprintManager: FingerprintManager,
                     private val mIcon: ImageView, private val mErrorTextView: TextView,
                     private val mDismissListener: DialogInterface.OnDismissListener,
                     private var mCancellationSignal: CancellationSignal?,
                     private val mExecutor: Executor?,
                     private val mCallback: BiometricPromptCompat.AuthenticationCallback?) : FingerprintManager.AuthenticationCallback() {

    private var mSelfCancelled: Boolean = false

    val isHardwareAvailable: Boolean
        get() = mFingerprintManager.isHardwareDetected

    // The line below prevents the false positive inspection from Android Studio
    val isFingerprintAuthAvailable: Boolean
        get() = mFingerprintManager.isHardwareDetected && mFingerprintManager.hasEnrolledFingerprints()

    private val mResetErrorTextRunnable = Runnable {
        mErrorTextView.setTextColor(
                mErrorTextView.resources.getColor(R.color.hint_color, null))
        mErrorTextView.text = mErrorTextView.resources.getString(R.string.fingerprint_hint)
        mIcon.setImageResource(R.drawable.ic_fingerprint_black_40dp)
    }

    fun startListening(cryptoObject: FingerprintManager.CryptoObject) {
        if (!isFingerprintAuthAvailable) {
            return
        }

        mSelfCancelled = false
        // The line below prevents the false positive inspection from Android Studio

        mFingerprintManager
                .authenticate(cryptoObject, mCancellationSignal, 0 /* flags */, this, null)
        mIcon.setImageResource(R.drawable.ic_fingerprint_black_40dp)
    }

    fun stopListening() {
        if (mCancellationSignal != null) {
            mSelfCancelled = true
            mCancellationSignal!!.cancel()
            mCancellationSignal = null
        }
    }

    override fun onAuthenticationError(errMsgId: Int, errString: CharSequence) {
        if (!mSelfCancelled) {
            showError(errString)
            mIcon.postDelayed({
                mExecutor!!.execute {
                    mDismissListener.onDismiss(null)
                    mCallback!!.onAuthenticationError(BiometricPromptCompat.errorCodeFromFingerprintManager(errMsgId), errString)
                }
                stopListening()
            }, ERROR_TIMEOUT_MILLIS)
        }
    }

    override fun onAuthenticationHelp(helpMsgId: Int, helpString: CharSequence) {
        showError(helpString)
        mExecutor!!.execute { mCallback!!.onAuthenticationHelp(helpMsgId, helpString) }
    }

    override fun onAuthenticationFailed() {
        showError(mIcon.resources.getString(
                R.string.fingerprint_not_recognized))
        mExecutor!!.execute { mCallback!!.onAuthenticationFailed() }
    }

    override fun onAuthenticationSucceeded(result: FingerprintManager.AuthenticationResult) {
        mErrorTextView.removeCallbacks(mResetErrorTextRunnable)
        mIcon.setImageResource(R.drawable.ic_fingerprint_success)
        mErrorTextView.setTextColor(
                mErrorTextView.resources.getColor(R.color.success_color, null))
        mErrorTextView.text = mErrorTextView.resources.getString(R.string.fingerprint_success)

        if (mCallback != null && mExecutor != null) {
        }
        mIcon.postDelayed({
            val cryptObject = BiometricPromptCompat.CryptoObject(result.cryptoObject.signature, result.cryptoObject.cipher, result.cryptoObject.mac)
            mExecutor!!.execute { mCallback!!.onAuthenticationSucceeded(BiometricPromptCompat.AuthenticationResult(cryptObject)) }
            stopListening()
            mDismissListener.onDismiss(null)
        }, SUCCESS_DELAY_MILLIS)
    }

    private fun showError(error: CharSequence) {
        mIcon.setImageResource(R.drawable.ic_fingerprint_error)
        mErrorTextView.text = error
        mErrorTextView.setTextColor(
                mErrorTextView.resources.getColor(R.color.warning_color, null))
        mErrorTextView.removeCallbacks(mResetErrorTextRunnable)
        mErrorTextView.postDelayed(mResetErrorTextRunnable, ERROR_TIMEOUT_MILLIS)
    }

    companion object {

        private val ERROR_TIMEOUT_MILLIS: Long = 1600
        private val SUCCESS_DELAY_MILLIS: Long = 1300
    }

}
