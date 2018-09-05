BiometricPrintCompat
=================

A library for biometric authentification on Android.

It wraps the functionality of the new BiometricPrompt API introduced by Android Pie (9.0) so that it degrades
gracefully and uses the older FingerPrintManager API to achieve similar functionality on older versions of Android
starting with Marshmallow (6.0). 

It is based on the excellent [FingerPrintManager](https://github.com/JesusM/FingerprintManager) which in turn is based
on [Googles official FingerPrint sample](https://github.com/googlesamples/android-FingerprintDialog).

## Use

This library allows you to show a dialog prompting the user to use a biometric authentication method. It also allows
you to query for the existance and function of biometric credentials.
