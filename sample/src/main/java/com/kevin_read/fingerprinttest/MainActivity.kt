package com.kevin_read.fingerprinttest

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.security.keystore.KeyProperties
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.kevinread.fingerprintcompat.BiometricPromptCompat
import com.kevinread.fingerprintcompat.FingerprintCompat
import com.kevinread.fingerprintcompat.encryption.Base64Encoder
import com.kevinread.fingerprintcompat.encryption.EncryptionData
import kotlinx.android.synthetic.main.activity_main.*
import java.io.UnsupportedEncodingException
import java.security.NoSuchAlgorithmException
import java.security.spec.InvalidParameterSpecException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException
import javax.crypto.spec.IvParameterSpec




class MainActivity : AppCompatActivity() {

    private val encoder = Base64Encoder()

    @SuppressLint("LogNotTimber")
    private fun onAuthSuccess(cipher: Cipher) {
        if (needsNewKey) {
            val messageToEncrypt = "Hello"
            try {
                val messageToEncryptBytes = cipher.doFinal(messageToEncrypt.toByteArray(charset("UTF-8")))
                val ivBytes = cipher.parameters.getParameterSpec(IvParameterSpec::class.java).iv

                val encryptedMessage = EncryptionData(messageToEncryptBytes, ivBytes, encoder)
                val encryptedAsString = encryptedMessage.print()
                pref.edit().putString(KEY, encryptedAsString).apply()
                Log.d(TAG, "Encoded: $encryptedAsString")
                Toast.makeText(this, "Hello $encryptedAsString", Toast.LENGTH_SHORT).show()

                this.cipher = getDecryptingCipher(ivBytes)
                checkForDecryptable()
            } catch (e: UnsupportedEncodingException) {
                Log.e(TAG, "cannot encrypt", e)
            } catch (e: InvalidParameterSpecException) {
                Log.e(TAG, "cannot encrypt", e)
            } catch (e: BadPaddingException) {
                Log.e(TAG, "cannot encrypt", e)
            } catch (e: IllegalBlockSizeException) {
                Log.e(TAG, "cannot encrypt", e)
            }
        } else {
            val decryptionData = decryptionData!!
            try {
                val encryptedMessage = decryptionData.decodedMessage()
                val decryptedMessageBytes = cipher.doFinal(encryptedMessage)
                val decryptedMessage = String(decryptedMessageBytes)
                Log.d(TAG, "encrypted raw string is $decryptedMessage")
                Toast.makeText(this, "Hello again $decryptedMessage", Toast.LENGTH_SHORT).show()

                this.cipher = getDecryptingCipher(decryptionData.decodedIVs())
            } catch (e: IllegalBlockSizeException) {
                Log.e(TAG, "cannot decrypt", e)
            } catch (e: BadPaddingException) {
                Log.e(TAG, "cannot decrypt", e)
            }
        }


    }

    private fun onAuthFailed(error: Int, msg: CharSequence?) {
        if (msg != null) {
            Toast.makeText(this, "Error: " + msg, Toast.LENGTH_SHORT).show()
        } else {
            when (error) {
                BiometricPromptCompat.BIOMETRIC_ERROR_USER_CANCELED -> Toast.makeText(this, "Aborted", Toast.LENGTH_SHORT).show()
                else -> Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show()
            }

        }
    }

    private lateinit var fingerprintCompat: FingerprintCompat

    private var needsNewKey: Boolean = true

    private lateinit var pref: SharedPreferences

    private var cipher: Cipher? = null

    private fun ensureCipher() {
        if (cipher == null) {
            try {
                cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                        + KeyProperties.BLOCK_MODE_CBC + "/"
                        + KeyProperties.ENCRYPTION_PADDING_PKCS7)
                fingerprintCompat.initCipher(cipher!!, KEY)
            } catch (e: NoSuchAlgorithmException) {
                throw e
            } catch (e: NoSuchPaddingException) {
                throw RuntimeException("Failed to get an instance of Cipher", e)
            }
        }
    }

    private fun getDecryptingCipher(encryptedIVs: ByteArray?): Cipher {
        try {
            val cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                    + KeyProperties.BLOCK_MODE_CBC + "/"
                    + KeyProperties.ENCRYPTION_PADDING_PKCS7)
            fingerprintCompat.initDecryptingCipher(cipher!!, KEY, encryptedIVs)
            return cipher
        } catch (e: NoSuchAlgorithmException) {
            throw e
        } catch (e: NoSuchPaddingException) {
            throw RuntimeException("Failed to get an instance of Cipher", e)
        }
    }

    private var decryptionData: EncryptionData? = null

    /**
     * Check if the data in the prefs is in a format to be decrypted
     */
    private fun checkForDecryptable() {
        val messageToDecrypt = pref.getString(KEY, "")!!
        decryptionData = EncryptionData(messageToDecrypt, encoder)
        if (decryptionData?.dataIsCorrect() == false) {
            needsNewKey = true
        } else {
            // It is, create the cipher and set the icon
            needsNewKey = false
            cipher = getDecryptingCipher(decryptionData?.decodedIVs())
            fab.setImageDrawable(resources.getDrawable(R.drawable.ic_lock_outline_black_24dp, theme))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        pref = getSharedPreferences(KEY, Context.MODE_PRIVATE)

        fingerprintCompat = FingerprintCompat(applicationContext)
        val enabled = fingerprintCompat.areFingerprintsEnabled() == true

        if (fingerprintCompat.hasKey(KEY) == true) {
            ensureCipher()
            val needsReenroll = fingerprintCompat.initCipher(cipher!!, KEY) == false
            if (needsReenroll) {
                Toast.makeText(this, "Fingerprints have changed, please re-authenticate", Toast.LENGTH_SHORT).show()
                needsNewKey = true
                fingerprintCompat.createKey(KEY, true)
                pref.edit().remove(KEY).apply()
            } else {
                needsNewKey = !pref.contains(KEY)
                if (!needsNewKey) {
                    checkForDecryptable()
                }
            }
        } else if (enabled) {
            fingerprintCompat.createKey(KEY, true)
            ensureCipher()
            pref.edit().remove(KEY).apply()
        } else {
            Toast.makeText(this, R.string.no_sensors, Toast.LENGTH_LONG).show()
        }

        fab.isEnabled = enabled

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            fab.setOnClickListener {
                createBiometricPrompt()
                try {
                } catch (e: NoSuchAlgorithmException) {
                    throw RuntimeException("Failed to get an instance of Cipher", e)
                } catch (e: NoSuchPaddingException) {
                    throw RuntimeException("Failed to get an instance of Cipher", e)
                }
            }
        }
    }

    private fun createBiometricPrompt() {
        val executor = if (Build.VERSION.SDK_INT >= 28) mainExecutor else BiometricPromptCompat.getExecutorForCurrentThread()
        val biometricPrompt = BiometricPromptCompat.Builder(this)
                .setDescription("This is a description")
                .setTitle(getString(R.string.sign_in))
                .setNegativeButton(getText(R.string.cancel), executor, DialogInterface.OnClickListener { _, i ->
                    Toast.makeText(this, "Aborted", Toast.LENGTH_SHORT).show()
                }).build()
        val cancellationSignal = CancellationSignal()
        biometricPrompt.authenticate(BiometricPromptCompat.CryptoObject(cipher=cipher!!), cancellationSignal, executor, object: BiometricPromptCompat.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                onAuthFailed(errorCode, errString)
            }

            override fun onAuthenticationSucceeded(result: BiometricPromptCompat.AuthenticationResult) {
                onAuthSuccess(result.cryptoObject.cipher!!)
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
        private const val KEY = "secret"
    }
}
