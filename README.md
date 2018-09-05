BiometricPrintCompat
=================

A library for biometric authentification on Android.

It wraps the functionality of the new BiometricPrompt API introduced by Android Pie (9.0) so that it degrades
gracefully and uses the older FingerPrintManager API to achieve similar functionality on older versions of Android
starting with Marshmallow (6.0). 

It is based on the excellent [FingerPrintManager](https://github.com/JesusM/FingerprintManager) which in turn is based
on [Googles official FingerPrint sample](https://github.com/googlesamples/android-FingerprintDialog).

![Release](https://jitpack.io/v/unverbraucht/BiometricPromptCompat.svg)]
(https://jitpack.io/#unverbraucht/BiometricPromptCompat)

## Use

This library allows you to show a dialog prompting the user to use a biometric authentication method. It also allows
you to query for the existance and function of biometric credentials. 

Make sure that you have enabled jitpack.io as maven repository by adding this to your root build.grade:
```groovy
allprojects {
    repositories {
        // All your other maven sources...
        maven { url 'https://jitpack.io' }
    }
}
```

Add the library dependency to your app's build.gradle:
```groovy
dependencies {
    implementation 'com.github.unverbraucht:BiometricPromptCompat:v1.0.0'
}
```


Sample use:

```kotlin
    private fun createBiometricPrompt() {
        // Get the executor for the current / UI thread
        val executor = if (Build.VERSION.SDK_INT >= 28) mainExecutor else BiometricPromptCompat.getExecutorForCurrentThread()
        
        // Create a BiometricPromptCompat through it's Builder, same interface as BiometricPrompt
        val biometricPrompt = BiometricPromptCompat.Builder(this)
                .setDescription("This is a description")
                .setTitle("Dialog title")
                .setNegativeButton("Cancel", executor, DialogInterface.OnClickListener { _, i ->
                    Toast.makeText(this, "Aborted", Toast.LENGTH_SHORT).show()
                }).build()
        val cancellationSignal = CancellationSignal()
        
        // Authenticate the fingerprint
        biometricPrompt.authenticate(BiometricPromptCompat.CryptoObject(cipher=myCipher), cancellationSignal, executor, object: BiometricPromptCompat.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                onAuthFailed(errorCode, errString)
            }

            override fun onAuthenticationSucceeded(result: BiometricPromptCompat.AuthenticationResult) {
                onAuthSuccess(result.cryptoObject.cipher!!)
            }
        })
    }
```