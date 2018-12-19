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

import android.app.DialogFragment
import android.content.DialogInterface
import android.hardware.fingerprint.FingerprintManager
import android.os.Bundle
import android.os.CancellationSignal
import android.support.v7.app.AppCompatDialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView

import java.util.concurrent.Executor

/**
 * A dialog which uses fingerprint APIs to authenticate the user, and falls back to password
 * authentication if fingerprint is not available.
 */
internal class FingerprintAuthenticationDialogFragment : AppCompatDialogFragment() {

    private var cancelButton: Button? = null
    private var fingerprintContent: View? = null

    private var cryptoObject: FingerprintManager.CryptoObject? = null
    private var fingerprintUiHelper: FingerprintUiHelper? = null

    private lateinit var resultCallback: BiometricPromptCompat.AuthenticationCallback
    private lateinit var bundle: Bundle
    private lateinit var executor: Executor
    private lateinit var cancellationSignal: CancellationSignal
    private var negativeButtonInfo: BiometricPromptCompat.ButtonInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Do not create a new Fragment when the Activity is re-created such as orientation changes.
        retainInstance = true
        @Suppress("DEPRECATION")
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Material_Light_Dialog)
    }

    override fun onCancel(dialog: DialogInterface?) {
        super.onCancel(dialog)

        onError(BiometricPromptCompat.BIOMETRIC_ERROR_CANCELED, null)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        dialog.setTitle(bundle.getCharSequence(BiometricPromptCompat.KEY_TITLE))
        val v = inflater.inflate(R.layout.fingerprint_dialog_container, container, false)

        v.findViewById<TextView>(R.id.fingerprint_description).text = bundle.getCharSequence(BiometricPromptCompat.KEY_DESCRIPTION)

        // We want to be notified of cancellation
        dialog.setCanceledOnTouchOutside(true)

        cancelButton = v.findViewById<View>(R.id.cancel_button) as Button
        cancelButton!!.setOnClickListener {
            negativeButtonInfo!!.executor.execute { negativeButtonInfo!!.listener.onClick(null, BiometricPromptCompat.DISMISSED_REASON_NEGATIVE) }

            dismissAllowingStateLoss()
        }

        cancelButton!!.text = bundle.getCharSequence(BiometricPromptCompat.KEY_NEGATIVE_TEXT)

        fingerprintContent = v.findViewById(R.id.fingerprint_container)
        @Suppress("DEPRECATION")
        fingerprintUiHelper = FingerprintUiHelper(
                activity!!.getSystemService(FingerprintManager::class.java)!!,
                v.findViewById<View>(R.id.fingerprint_icon) as ImageView,
                v.findViewById<View>(R.id.fingerprint_status) as TextView, DialogInterface.OnDismissListener { dismissAllowingStateLoss() }, cancellationSignal, executor, resultCallback)

        // If fingerprint authentication is not available, return error immediately and exit
        // Note: This should not happen, BiometricPromptCompat should prevent this
        if (!fingerprintUiHelper!!.isHardwareAvailable) {
            onError(BiometricPromptCompat.BIOMETRIC_ERROR_HW_NOT_PRESENT, null)
            dismissAllowingStateLoss()
        } else {
            if (!fingerprintUiHelper!!.isFingerprintAuthAvailable) {
                onError(BiometricPromptCompat.BIOMETRIC_ERROR_NO_BIOMETRICS, null)
                dismissAllowingStateLoss()
            }
        }
        return v
    }



    override fun onResume() {
        super.onResume()
        fingerprintUiHelper!!.startListening(cryptoObject)
    }

    override fun onPause() {
        super.onPause()
        fingerprintUiHelper!!.stopListening()
    }

    fun onError(error: Int, errString: CharSequence?) {
        fingerprintUiHelper!!.stopListening()
        executor.execute { resultCallback.onAuthenticationError(error, errString) }
    }

    internal fun setData(cryptoObject: FingerprintManager.CryptoObject?,
                         bundle: Bundle, cancel: CancellationSignal, executor: Executor,
                         callback: BiometricPromptCompat.AuthenticationCallback,
                         negativeButtonInfo: BiometricPromptCompat.ButtonInfo) {
        this.cryptoObject = cryptoObject
        this.bundle = bundle
        this.cancellationSignal = cancel
        this.executor = executor
        this.resultCallback = callback
        this.negativeButtonInfo = negativeButtonInfo
    }
}
