package com.kevinread.fingerprintcompat

import android.Manifest.permission.USE_FINGERPRINT
import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.content.res.Resources
import android.hardware.biometrics.BiometricPrompt
import android.hardware.fingerprint.FingerprintManager
import android.os.*
import android.support.annotation.CheckResult
import android.support.annotation.RequiresApi
import android.support.annotation.RequiresPermission
import android.support.v4.app.FragmentActivity
import android.support.v4.app.FragmentManager
import java.security.Signature
import java.util.concurrent.Executor
import javax.crypto.Cipher
import javax.crypto.Mac


/**
 * Created by Kevin Read <me@kevin-read.com> on 05.09.18 for FingerprintTest.
 * Copyright (c) 2018 Kevin Read. All rights reserved.
 */

class BiometricPromptCompat private constructor(ctx: FragmentActivity,
                                                val bundle: Bundle,
                                                val positiveButtonInfo: ButtonInfo?,
                                                val negativeButtonInfo: ButtonInfo,
                                                val underlying: BiometricPrompt?) {

    internal class ButtonInfo internal constructor(internal var executor: Executor, internal var listener: DialogInterface.OnClickListener)

    abstract class AuthenticationCallback {
        abstract fun onAuthenticationError(errorCode: Int, errString: CharSequence?)

        open fun onAuthenticationFailed() {

        }

        open fun onAuthenticationHelp(helpCode: Int, helpString: CharSequence?) {

        }

        abstract fun onAuthenticationSucceeded(result: AuthenticationResult)
    }

    /**
     * Container for callback data from {@link #authenticate( CancellationSignal, Executor,
     * AuthenticationCallback)} and {@link #authenticate(CryptoObject, CancellationSignal, Executor,
     * AuthenticationCallback)}
     * @property cryptoObject The {@link CryptoObject} that contains the cryptographic operation
     */

    class AuthenticationResult internal constructor (val cryptoObject: CryptoObject) {

        @RequiresApi(28)
        constructor(biometricObject: BiometricPrompt.AuthenticationResult) : this(CryptoObject(biometricObject.cryptoObject?.signature, biometricObject.cryptoObject?.cipher, biometricObject.cryptoObject?.mac))
    }

    /**
     * A wrapper around a cryptographic operation you wanted to use after the Biometric was successful.
     * @property signature {@link Signature} object or null if this doesn't contain one.
     * @property cipher {@link Cipher} object or null if this doesn't contain one.
     * @property mac {@link Mac} object or null if this doesn't contain one.
     */
    class CryptoObject constructor(val signature: Signature? = null, val cipher: Cipher? = null, val mac: Mac? = null) {
        @RequiresApi(28)
        fun toBiometricPrompt(): BiometricPrompt.CryptoObject {
            if (signature != null) {
                return BiometricPrompt.CryptoObject(signature)
            } else if (cipher != null) {
                return BiometricPrompt.CryptoObject(cipher)
            } else if (mac != null) {
                return BiometricPrompt.CryptoObject(mac)
            } else {
                throw IllegalArgumentException("illegal cryptoObject")
            }
        }

        @Suppress("DEPRECATION")
        fun toFingerprintManager(): FingerprintManager.CryptoObject {
            if (signature != null) {
                return FingerprintManager.CryptoObject(signature)
            } else if (cipher != null) {
                return FingerprintManager.CryptoObject(cipher)
            } else if (mac != null) {
                return FingerprintManager.CryptoObject(mac)
            } else {
                throw IllegalArgumentException("illegal cryptoObject")
            }
        }

        init {
            if (signature == null && cipher == null && mac == null) {
                throw IllegalArgumentException("one of either crypto objects must be non-null")
            }
//            if (Build.VERSION.SDK_INT >= 28) {
//                if (fingerprintObject != null || biometricObject == null) {
//                    throw IllegalArgumentException("For Pie and beyond (SDK level 28) please use a BiometricPrompt.CryptoObject")
//                }
//            }
        }
    }

    class Builder(ctx: Context) {

        private fun asActivity(cont: Context?): FragmentActivity {
            if (cont == null)
                throw IllegalArgumentException("The passed Context is not an SupportActivity.")
            else if (cont is FragmentActivity)
                return cont
            else if (cont is ContextWrapper)
                return asActivity(cont.baseContext)

            throw IllegalArgumentException("The passed Context is not an SupportActivity.")
        }

        private val underlying: Any?
        private var activity: FragmentActivity

        init {
            if (Build.VERSION.SDK_INT >= 28) {
                underlying = BiometricPrompt.Builder(ctx)
            } else {
                underlying = null
            }
            activity = asActivity(ctx)
        }

        private val bundle = Bundle()

        @SuppressLint("NewApi")
                /**
         * Creates a {@link BiometricPromptCompat}.
         * @return a {@link BiometricPromptCompat}
         * @throws IllegalArgumentException if any of the required fields are not set.
         */
        fun build(): BiometricPromptCompat {
            if (bundle.getCharSequence(KEY_TITLE)?.isEmpty() == true) {
                throw IllegalArgumentException("Title must be set and non-empty")
            } else if (bundle.getCharSequence(KEY_NEGATIVE_TEXT)?.isEmpty() == true) {
                throw IllegalArgumentException("Negative text must be set and non-empty")
            }

            val negativeButtonInfo = negativeButtonInfo ?: throw IllegalArgumentException("negative button info must be set")
            try {
                if (underlying != null) {
                    return BiometricPromptCompat(activity, bundle, positiveButtonInfo, negativeButtonInfo, (underlying as BiometricPrompt.Builder).build())

                }
                return BiometricPromptCompat(activity, bundle, positiveButtonInfo, negativeButtonInfo, null)
            } catch (e: UninitializedPropertyAccessException) {
                throw IllegalArgumentException("missing parameters")
            }
        }


        /**
         * Optional: Set the description to display.
         * @param description
         * @return
         */
        @SuppressLint("NewApi")
        @CheckResult
        fun setDescription(description: CharSequence): Builder {
            if (underlying != null) {
                (underlying as BiometricPrompt.Builder).setDescription(description)
            }
            bundle.putCharSequence(KEY_DESCRIPTION, description)
            return this
        }

        private var positiveButtonInfo: ButtonInfo? = null
        private var negativeButtonInfo: ButtonInfo? = null

        /**
         * Optional: Set the text for the positive button. If not set, the positive button
         * will not show.
         * @param text
         * @return
         * @hide
         */
        @CheckResult
        fun setPositiveButton(text: CharSequence, executor: Executor, listener: DialogInterface.OnClickListener): Builder {
            if (text.isEmpty()) {
                throw IllegalArgumentException("Text must be set and non-empty")
            }

            bundle.putCharSequence(KEY_POSITIVE_TEXT, text)
            positiveButtonInfo = ButtonInfo(executor, listener)

            return this
        }

        /**
         * Required: Set the text for the negative button. This would typically be used as a
         * "Cancel" button, but may be also used to show an alternative method for authentication,
         * such as screen that asks for a backup password.
         * @param text
         * @return
         */
        @SuppressLint("NewApi")
        @CheckResult
        fun setNegativeButton(text: CharSequence, executor: Executor, listener: DialogInterface.OnClickListener): Builder {
            if (text.isEmpty()) {
                throw IllegalArgumentException("Text must be set and non-empty")
            }

            if (underlying != null) {
                (underlying as BiometricPrompt.Builder).setNegativeButton(text, executor, listener)
            }

            bundle.putCharSequence(KEY_NEGATIVE_TEXT, text)
            negativeButtonInfo = ButtonInfo(executor, listener)

            return this
        }

        @SuppressLint("NewApi")
        @CheckResult
        fun setSubtitle(subtitle: CharSequence): Builder {
            if (underlying != null) {
                (underlying as BiometricPrompt.Builder).setSubtitle(subtitle)
            }
            bundle.putCharSequence(KEY_SUBTITLE, subtitle)
            return this
        }

        @SuppressLint("NewApi")
        @CheckResult
        fun setTitle(title: CharSequence): Builder {
            if (underlying != null) {
                (underlying as BiometricPrompt.Builder).setTitle(title)
            }
            bundle.putCharSequence(KEY_TITLE, title)
            return this
        }
    }

    private var packageManager: PackageManager

    private var fingerPrintManager: FingerprintManager

    private var fragmentManager: FragmentManager

    init {
        packageManager = ctx.packageManager
        fingerPrintManager = ctx.getSystemService(FingerprintManager::class.java)
        fragmentManager = ctx.supportFragmentManager
    }

    @RequiresPermission(USE_FINGERPRINT)
    fun authenticate(cancel: CancellationSignal, executor: Executor, callback: AuthenticationCallback) {
        if (handlePreAuthenticationErrors(callback, executor)) {
            return
        }

    }

    /**
     * This call warms up the fingerprint hardware, displays a system-provided dialog, and starts
     * scanning for a fingerprint. It terminates when {@link
     * AuthenticationCallback#onAuthenticationError(int, CharSequence)} is called, when {@link
     * AuthenticationCallback#onAuthenticationSucceeded( AuthenticationResult)}, or when the user
     * dismisses the system-provided dialog, at which point the crypto object becomes invalid. This
     * operation can be canceled by using the provided cancel object. The application will receive
     * authentication errors through {@link AuthenticationCallback}, and button events through the
     * corresponding callback set in {@link Builder#setNegativeButton(CharSequence, Executor,
     * DialogInterface.OnClickListener)}. It is safe to reuse the {@link BiometricPrompt} object,
     * and calling {@link BiometricPrompt#authenticate( CancellationSignal, Executor,
     * AuthenticationCallback)} while an existing authentication attempt is occurring will stop the
     * previous client and start a new authentication. The interrupted client will receive a
     * cancelled notification through {@link AuthenticationCallback#onAuthenticationError(int,
     * CharSequence)}.
     *
     * @throws IllegalArgumentException If any of the arguments are null
     *
     * @param crypto Object associated with the call
     * @param cancel An object that can be used to cancel authentication
     * @param executor An executor to handle callback events
     * @param callback An object to receive authentication events
     */
    @RequiresPermission(USE_FINGERPRINT)
    fun authenticate(cryptoObject: CryptoObject, cancel: CancellationSignal, executor: Executor, callback: AuthenticationCallback) {
        if (Build.VERSION.SDK_INT >= 28 && underlying != null) {
            val underlyingCallback = @SuppressLint("NewApi")
            object: BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    callback.onAuthenticationError(errorCode, errString)
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    callback.onAuthenticationSucceeded(AuthenticationResult(result))
                }

                override fun onAuthenticationHelp(helpCode: Int, helpString: CharSequence?) {
                    callback.onAuthenticationHelp(helpCode, helpString)
                }

                override fun onAuthenticationFailed() {
                    callback.onAuthenticationFailed()
                }
            }
            underlying.authenticate(cryptoObject.toBiometricPrompt(), cancel, executor, underlyingCallback)
            return
        }
        if (handlePreAuthenticationErrors(callback, executor)) {
            return
        }
        val fragment = FingerprintAuthenticationDialogFragment()
        fragment.setData(cryptoObject, bundle, cancel, executor, callback, negativeButtonInfo)

        fragment.show(fragmentManager, DIALOG_FRAGMENT_TAG)
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
        } else if (!fingerPrintManager.hasEnrolledFingerprints()) {
            sendError(BIOMETRIC_ERROR_NO_BIOMETRICS, callback,
                    executor)
            return true
        }
        return false
    }

    private fun sendError(error: Int, callback: AuthenticationCallback, executor: Executor) {
        val errorString: String?
        val string = when (error) {
            BIOMETRIC_ERROR_NO_BIOMETRICS -> "com.android.internal.R.string.fingerprint_error_no_fingerprints"
            BIOMETRIC_ERROR_HW_NOT_PRESENT -> "com.android.internal.R.string.fingerprint_error_hw_not_present"
            BIOMETRIC_ERROR_HW_UNAVAILABLE -> "com.android.internal.R.string.fingerprint_error_hw_not_available"
            else -> null
        }
        if (string != null) {
            errorString = Resources.getSystem().getString(Resources.getSystem().getIdentifier(string, "string", "android"))
        } else {
            errorString = null
        }
        executor.execute {
            callback.onAuthenticationError(error, errorString)
        }
    }

    class UiThreadExecutor : Executor {
        private val mHandler = Handler(Looper.getMainLooper())

        override fun execute(command: Runnable) {
            mHandler.post(command)
        }
    }

    companion object {

        private const val DIALOG_FRAGMENT_TAG = "com.kevinread.fingerprintcompat.BiometricCompat"

        @Suppress("DEPRECATION")
        fun errorCodeFromFingerprintManager(code: Int): Int {
            return when (code) {
                FingerprintManager.FINGERPRINT_ERROR_CANCELED -> BIOMETRIC_ERROR_CANCELED
                FingerprintManager.FINGERPRINT_ERROR_HW_NOT_PRESENT -> BIOMETRIC_ERROR_HW_NOT_PRESENT
                FingerprintManager.FINGERPRINT_ERROR_HW_UNAVAILABLE -> BIOMETRIC_ERROR_HW_UNAVAILABLE
                FingerprintManager.FINGERPRINT_ERROR_NO_FINGERPRINTS -> BIOMETRIC_ERROR_NO_BIOMETRICS
                FingerprintManager.FINGERPRINT_ERROR_LOCKOUT -> BIOMETRIC_ERROR_LOCKOUT
                FingerprintManager.FINGERPRINT_ERROR_LOCKOUT_PERMANENT -> BIOMETRIC_ERROR_LOCKOUT_PERMANENT
                FingerprintManager.FINGERPRINT_ERROR_NO_SPACE -> BIOMETRIC_ERROR_NO_SPACE
                FingerprintManager.FINGERPRINT_ERROR_TIMEOUT -> BIOMETRIC_ERROR_TIMEOUT
                FingerprintManager.FINGERPRINT_ERROR_UNABLE_TO_PROCESS -> BIOMETRIC_ERROR_UNABLE_TO_PROCESS
                FingerprintManager.FINGERPRINT_ERROR_USER_CANCELED -> BIOMETRIC_ERROR_USER_CANCELED
                else -> code
            }
        }

        fun getExecutorForCurrentThread(): Executor {
            return UiThreadExecutor()
        }

        internal fun errorCodeFromBiometricPrompt(code: Int): Int {
            // We use the same constants
            return code
        }

        //
        // Error messages from biometric hardware during initilization, enrollment, authentication or
        // removal.
        //
        /**
         * The hardware is unavailable. Try again later.
         */
        const val BIOMETRIC_ERROR_HW_UNAVAILABLE = 1
        /**
         * Error state returned when the sensor was unable to process the current image.
         */
        const val BIOMETRIC_ERROR_UNABLE_TO_PROCESS = 2
        /**
         * Error state returned when the current request has been running too long. This is intended to
         * prevent programs from waiting for the biometric sensor indefinitely. The timeout is platform
         * and sensor-specific, but is generally on the order of 30 seconds.
         */
        const val BIOMETRIC_ERROR_TIMEOUT = 3
        /**
         * Error state returned for operations like enrollment; the operation cannot be completed
         * because there's not enough storage remaining to complete the operation.
         */
        const val BIOMETRIC_ERROR_NO_SPACE = 4
        /**
         * The operation was canceled because the biometric sensor is unavailable. For example, this may
         * happen when the user is switched, the device is locked or another pending operation prevents
         * or disables it.
         */
        const val BIOMETRIC_ERROR_CANCELED = 5
        /**
         * The [BiometricManager.remove] call failed. Typically this will happen when the provided
         * biometric id was incorrect.
         *
         * @hide
         */
        const val BIOMETRIC_ERROR_UNABLE_TO_REMOVE = 6
        /**
         * The operation was canceled because the API is locked out due to too many attempts.
         * This occurs after 5 failed attempts, and lasts for 30 seconds.
         */
        const val BIOMETRIC_ERROR_LOCKOUT = 7
        /**
         * Hardware vendors may extend this list if there are conditions that do not fall under one of
         * the above categories. Vendors are responsible for providing error strings for these errors.
         * These messages are typically reserved for internal operations such as enrollment, but may be
         * used to express vendor errors not otherwise covered. Applications are expected to show the
         * error message string if they happen, but are advised not to rely on the message id since they
         * will be device and vendor-specific
         */
        const val BIOMETRIC_ERROR_VENDOR = 8
        /**
         * The operation was canceled because BIOMETRIC_ERROR_LOCKOUT occurred too many times.
         * Biometric authentication is disabled until the user unlocks with strong authentication
         * (PIN/Pattern/Password)
         */
        const val BIOMETRIC_ERROR_LOCKOUT_PERMANENT = 9
        /**
         * The user canceled the operation. Upon receiving this, applications should use alternate
         * authentication (e.g. a password). The application should also provide the means to return to
         * biometric authentication, such as a "use <biometric>" button.
        </biometric> */
        const val BIOMETRIC_ERROR_USER_CANCELED = 10
        /**
         * The user does not have any biometrics enrolled.
         */
        const val BIOMETRIC_ERROR_NO_BIOMETRICS = 11
        /**
         * The device does not have a biometric sensor.
         */
        const val BIOMETRIC_ERROR_HW_NOT_PRESENT = 12
        /**
         * @hide
         */
        const val BIOMETRIC_ERROR_VENDOR_BASE = 1000
        //
        // Image acquisition messages.
        //
        /**
         * The image acquired was good.
         */
        const val BIOMETRIC_ACQUIRED_GOOD = 0
        /**
         * Only a partial biometric image was detected. During enrollment, the user should be informed
         * on what needs to happen to resolve this problem, e.g. "press firmly on sensor." (for
         * fingerprint)
         */
        const val BIOMETRIC_ACQUIRED_PARTIAL = 1
        /**
         * The biometric image was too noisy to process due to a detected condition or a possibly dirty
         * sensor (See [.BIOMETRIC_ACQUIRED_IMAGER_DIRTY]).
         */
        const val BIOMETRIC_ACQUIRED_INSUFFICIENT = 2
        /**
         * The biometric image was too noisy due to suspected or detected dirt on the sensor.  For
         * example, it's reasonable return this after multiple [.BIOMETRIC_ACQUIRED_INSUFFICIENT]
         * or actual detection of dirt on the sensor (stuck pixels, swaths, etc.). The user is expected
         * to take action to clean the sensor when this is returned.
         */
        const val BIOMETRIC_ACQUIRED_IMAGER_DIRTY = 3
        /**
         * The biometric image was unreadable due to lack of motion.
         */
        const val BIOMETRIC_ACQUIRED_TOO_SLOW = 4
        /**
         * The biometric image was incomplete due to quick motion. For example, this could also happen
         * if the user moved during acquisition. The user should be asked to repeat the operation more
         * slowly.
         */
        const val BIOMETRIC_ACQUIRED_TOO_FAST = 5
        /**
         * Hardware vendors may extend this list if there are conditions that do not fall under one of
         * the above categories. Vendors are responsible for providing error strings for these errors.
         * @hide
         */
        const val BIOMETRIC_ACQUIRED_VENDOR = 6
        /**
         * @hide
         */
        const val BIOMETRICT_ACQUIRED_VENDOR_BASE = 1000
        /**
         * @hide
         */
        const val KEY_TITLE = "title"
        /**
         * @hide
         */
        const val KEY_SUBTITLE = "subtitle"
        /**
         * @hide
         */
        const val KEY_DESCRIPTION = "description"
        /**
         * @hide
         */
        const val KEY_POSITIVE_TEXT = "positive_text"
        /**
         * @hide
         */
        const val KEY_NEGATIVE_TEXT = "negative_text"
        /**
         * Error/help message will show for this amount of time.
         * For error messages, the dialog will also be dismissed after this amount of time.
         * Error messages will be propagated back to the application via AuthenticationCallback
         * after this amount of time.
         * @hide
         */
        private const val HIDE_DIALOG_DELAY = 2000 // ms
        /**
         * @hide
         */
        const val DISMISSED_REASON_POSITIVE = 1
        /**
         * @hide
         */
        const val DISMISSED_REASON_NEGATIVE = 2
        /**
         * @hide
         */
        const val DISMISSED_REASON_USER_CANCEL = 3
    }

}
