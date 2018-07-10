package com.uport.sdk.signer.encryption

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.support.v7.app.AppCompatActivity
import com.uport.sdk.signer.DecryptionCallback
import com.uport.sdk.signer.EncryptionCallback
import com.uport.sdk.signer.UportSigner
import com.uport.sdk.signer.UportSigner.Companion.ERR_ACTIVITY_DOES_NOT_EXIST
import com.uport.sdk.signer.encryption.AndroidKeyStoreHelper.generateWrappingKey
import com.uport.sdk.signer.encryption.AndroidKeyStoreHelper.getWrappingCipher
import com.uport.sdk.signer.unpackCiphertext
import javax.crypto.Cipher

class FingerprintAsymmetricProtection : KeyProtection() {

    override
    val alias = "__fingerprint_asymmetric_key_alias__"

    override
    fun genKey(context: Context) {

        generateWrappingKey(context, alias, true)
    }

    override
    fun encrypt(context: Context, purpose: String, blob: ByteArray, callback: EncryptionCallback) {
        try {
            val ciphertext = encryptRaw(blob, alias)
            callback(null, ciphertext)
        } catch (ex: Exception) {
            callback(ex, "")
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    override
    fun decrypt(context: Context, purpose: String, ciphertext: String, callback: DecryptionCallback) {

        try {
            val (encryptedBytes) = unpackCiphertext(ciphertext)

            val cipher = getWrappingCipher(Cipher.DECRYPT_MODE, alias)

            if (context is AppCompatActivity) {
                showFingerprintDialog(context, purpose, cipher) { err, cryptoObject ->
                    if (err != null) {
                        callback(err, ByteArray(0))
                    } else {
                        val cleartextBytes = cryptoObject.cipher.doFinal(encryptedBytes)
                        callback(null, cleartextBytes)
                    }
                }
            } else {
                callback(IllegalStateException(ERR_ACTIVITY_DOES_NOT_EXIST), ByteArray(0))
            }
        } catch (ex: Exception) {
            callback(ex, ByteArray(0))
        }
    }


    private lateinit var fingerprintDialog: FingerprintDialog

    @TargetApi(Build.VERSION_CODES.M)
    private fun showFingerprintDialog(activity: Activity, purpose: String, cipher: Cipher, callback: (err: Exception?, FingerprintManager.CryptoObject) -> Unit) {

        fingerprintDialog = FingerprintDialog.create(purpose)
        if (activity.fragmentManager.findFragmentByTag(FingerprintDialog.TAG_FINGERPRINT_DIALOG) == null) {
            val cryptoObject = FingerprintManager.CryptoObject(cipher)
            fingerprintDialog.init(
                    cryptoObject,
                    object : FingerprintDialog.FingerprintDialogCallbacks {
                        override fun onFingerprintSuccess(cryptoObject: FingerprintManager.CryptoObject) {
                            callback(null, cryptoObject)
                            fingerprintDialog.dismiss()
                        }

                        override fun onFingerprintCancel() {
                            callback(RuntimeException(UportSigner.ERR_AUTH_CANCELED), cryptoObject)
                            fingerprintDialog.dismiss()
                        }

                    }
            )
            fingerprintDialog.show(activity.fragmentManager, FingerprintDialog.TAG_FINGERPRINT_DIALOG)
        }
    }

}