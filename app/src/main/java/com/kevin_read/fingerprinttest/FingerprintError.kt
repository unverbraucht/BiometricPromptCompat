package com.kevin_read.fingerprinttest

import android.hardware.fingerprint.FingerprintManager

/**
 * Created by Kevin Read <me></me>@kevin-read.com> on 28.08.18 for FingerprintTest.
 * Copyright (c) 2018 ${ORGANIZATION_NAME}. All rights reserved.
 */
enum class FingerprintError {
    HARDWARE_NOT_PRESENT,
    HARDWARE_UNAVAILABLE,
    NO_ENROLLED,
    LOCKED_OUT,
    NO_SPACE,
    TIMEOUT,
    UNKNOWN,
    CANCELLED;


    companion object {

        fun fromAuthenticationError(errMsgId: Int): FingerprintError {
            return when (errMsgId) {
                FingerprintManager.FINGERPRINT_ERROR_CANCELED -> CANCELLED
                FingerprintManager.FINGERPRINT_ERROR_HW_NOT_PRESENT -> HARDWARE_NOT_PRESENT
                FingerprintManager.FINGERPRINT_ERROR_HW_UNAVAILABLE -> HARDWARE_UNAVAILABLE
                FingerprintManager.FINGERPRINT_ERROR_NO_FINGERPRINTS -> NO_ENROLLED
                FingerprintManager.FINGERPRINT_ERROR_LOCKOUT_PERMANENT -> LOCKED_OUT
                FingerprintManager.FINGERPRINT_ERROR_NO_SPACE -> NO_SPACE
                FingerprintManager.FINGERPRINT_ERROR_TIMEOUT -> TIMEOUT
                else -> UNKNOWN
            }
        }
    }
}
