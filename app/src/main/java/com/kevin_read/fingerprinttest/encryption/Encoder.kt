package com.kevin_read.fingerprinttest.encryption

import android.util.Base64

/**
 * Created by Kevin Read <me@kevin-read.com> on 28.08.18 for FingerprintTest.
 * Copyright (c) 2018 ${ORGANIZATION_NAME}. All rights reserved.
 */

interface Encoder {
    fun encode(messageToEncode: ByteArray): String
    fun decode(messageToDecode: String): ByteArray
}

class Base64Encoder : Encoder {
    override fun encode(messageToEncode: ByteArray): String = Base64.encodeToString(messageToEncode, Base64.DEFAULT)

    override fun decode(messageToDecode: String): ByteArray = Base64.decode(messageToDecode, Base64.DEFAULT)
}