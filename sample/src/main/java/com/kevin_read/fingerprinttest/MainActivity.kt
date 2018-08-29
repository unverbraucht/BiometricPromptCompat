package com.kevin_read.fingerprinttest

import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.security.keystore.KeyProperties
import android.support.annotation.RequiresApi
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.kevinread.fingerprintcompat.FingerprintAuthenticationDialogFragment
import com.kevinread.fingerprintcompat.FingerprintCompat
import com.kevinread.fingerprintcompat.FingerprintError
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




class MainActivity : AppCompatActivity(), FingerprintAuthenticationDialogFragment.Result {

    val encoder = Base64Encoder()

    private fun onAuthSuccess(cipher: Cipher) {
        if (needsNewKey) {
            val messageToEncrypt = "Hello"
            try {
                val messageToEncryptBytes = cipher.doFinal(messageToEncrypt.toByteArray(charset("UTF-8")))
                val ivBytes = cipher.parameters.getParameterSpec(IvParameterSpec::class.java).iv

                val encryptedMessage = EncryptionData(messageToEncryptBytes, ivBytes, encoder)
                val encryptedAsString = encryptedMessage.print()
                pref.edit().putString(KEY, encryptedAsString).apply()
                Log.d(TAG, "Encoded: " + encryptedAsString)
                Toast.makeText(this, "Hello $encryptedAsString", Toast.LENGTH_SHORT).show()

                this.cipher = getDecryptingCipher(ivBytes)
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

    private fun onAuthFailed(error: FingerprintError, msg: CharSequence?) {
        if (msg != null) {
            Toast.makeText(this, "Error: " + msg, Toast.LENGTH_SHORT).show()
        } else {
            when (error) {
                FingerprintError.CANCELLED -> Toast.makeText(this, "Aborted", Toast.LENGTH_SHORT).show()
                else -> Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show()
            }

        }
    }

    override fun onSuccess(fragment: FingerprintAuthenticationDialogFragment, cipher: Cipher) {
        onAuthSuccess(cipher)
        fragment.dismiss()
    }

    override fun onAbort(fragment: FingerprintAuthenticationDialogFragment, error: FingerprintError, msg: CharSequence?) {
        onAuthFailed(error, msg)
        fragment.dismiss()
    }

    private var fingerprintCompat: FingerprintCompat? = null

    private var needsNewKey: Boolean = true

    private lateinit var pref: SharedPreferences

    private var cipher: Cipher? = null

    private fun ensureCipher() {
        if (cipher == null) {
            try {
                cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                        + KeyProperties.BLOCK_MODE_CBC + "/"
                        + KeyProperties.ENCRYPTION_PADDING_PKCS7)
                fingerprintCompat?.initCipher(cipher!!, KEY)
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
            fingerprintCompat?.initDecryptingCipher(cipher!!, KEY, encryptedIVs)
            return cipher
        } catch (e: NoSuchAlgorithmException) {
            throw e
        } catch (e: NoSuchPaddingException) {
            throw RuntimeException("Failed to get an instance of Cipher", e)
        }
    }

    private var decryptionData: EncryptionData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        pref = getSharedPreferences(KEY, Context.MODE_PRIVATE)

        var enabled = false
        if (Build.VERSION.SDK_INT >= 23) {
            fingerprintCompat = FingerprintCompat(applicationContext)
            enabled = fingerprintCompat?.areFingerprintsEnabled() == true

            if (fingerprintCompat?.hasKey(KEY) == true) {
                ensureCipher()
                val needsReenroll = fingerprintCompat?.initCipher(cipher!!, KEY) == false
                if (needsReenroll) {
                    Toast.makeText(this, "Fingerprints have changed, please re-authenticate", Toast.LENGTH_SHORT).show()
                    needsNewKey = true
                    fingerprintCompat?.createKey(KEY, true)
                    pref.edit().remove(KEY).apply()
                } else {
                    needsNewKey = !pref.contains(KEY)
                    if (!needsNewKey) {
                        val messageToDecrypt = pref.getString(KEY, "")!!
                        decryptionData = EncryptionData(messageToDecrypt, encoder)
                        if (decryptionData?.dataIsCorrect() == false) {
                            needsNewKey = true
                        } else {
                            cipher = getDecryptingCipher(decryptionData?.decodedIVs())
                            fab.setImageDrawable(resources.getDrawable(R.drawable.ic_lock_outline_black_24dp, theme))
                        }
                    }
                }
            } else if (enabled) {
                fingerprintCompat?.createKey(KEY, true)
                pref.edit().remove(KEY).apply()
            }
        }

        fab.isEnabled = enabled

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            fab.setOnClickListener { view ->
                try {
                    if (Build.VERSION.SDK_INT >= 28) {
                        createBiometricPrompt()
                    } else {
                        val fragment = FingerprintAuthenticationDialogFragment()
                        fragment.setCryptoObject(cipher, fingerprintCompat)
                        fragment.setResultCallback(this)

                        fragment.show(supportFragmentManager, DIALOG_FRAGMENT_TAG)
                    }
                } catch (e: NoSuchAlgorithmException) {
                    throw RuntimeException("Failed to get an instance of Cipher", e)
                } catch (e: NoSuchPaddingException) {
                    throw RuntimeException("Failed to get an instance of Cipher", e)
                }
            }
        }
    }

    @RequiresApi(28)
    private fun createBiometricPrompt() {
        val biometricPrompt = BiometricPrompt.Builder(this)
                .setDescription("Wow")
                .setTitle(getString(R.string.sign_in))
                .setNegativeButton(getText(R.string.cancel), mainExecutor, DialogInterface.OnClickListener { dialogInterface, i ->
                    Toast.makeText(this, "Aborted", Toast.LENGTH_SHORT).show()
                }).build()
        val cancellationSignal = CancellationSignal()
        biometricPrompt.authenticate(BiometricPrompt.CryptoObject(cipher!!), cancellationSignal, mainExecutor, object: BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                onAuthFailed(FingerprintError.fromBiometricPromptError(errorCode), errString)
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                onAuthSuccess(result?.cryptoObject?.cipher!!)
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
        private const val KEY = "consors"
        private const val DIALOG_FRAGMENT_TAG = "dialog"
    }
}
