package com.uport.sdk.signer.encryption

import android.app.Activity
import android.content.Context
import android.support.v7.app.AppCompatActivity
import com.uport.sdk.signer.UportSigner
import com.uport.sdk.signer.UportSigner.Companion.ERR_ACTIVITY_DOES_NOT_EXIST
import com.uport.sdk.signer.encryption.AndroidKeyStoreHelper.generateWrappingKey
import java.security.InvalidKeyException

class KeyguardAsymmetricProtection(sessionTimeoutSeconds: Int = SESSION_TIMEOUT_SECONDS) : KeyProtection() {

    override
    val alias = "__keyguard_asymmetric_key_alias__"

    private var sessionTimeout: Int = sessionTimeoutSeconds

    override
    fun genKey(context: Context) {

        if (!KeyProtection.canUseKeychainAuthentication(context)) {
            throw IllegalStateException(UportSigner.ERR_KEYGUARD_NOT_CONFIGURED)
        }

        generateWrappingKey(context, alias, true, sessionTimeout)
    }

    override
    fun encrypt(context: Context, purpose: String, blob: ByteArray, callback: (err: Exception?, ciphertext: String) -> Unit) {
        try {
            val ciphertext = KeyProtection.encryptRaw(blob, alias)
            callback(null, ciphertext)
        } catch (ex: Exception) {
            callback(ex, "")
        }
    }


    override
    fun decrypt(context: Context, purpose: String, ciphertext: String, callback: (err: Exception?, cleartext: ByteArray) -> Unit) {

        try {
            val cleartextBytes = KeyProtection.decryptRaw(ciphertext, alias)
            callback(null, cleartextBytes)

        } catch (exception: InvalidKeyException) {
            //TODO: check if exception is UserNotAuthenticatedException on API 23+
            //keyguard needs an activity present
            if (context is AppCompatActivity) {
                showKeyguard(
                        context,
                        purpose,
                        object : KeyguardLaunchFragment.KeyguardCallback {
                            override fun onKeyguardResult(unlocked: Boolean) {
                                if (unlocked) {
                                    //it's safe to call this again
                                    decrypt(context, purpose, ciphertext, callback)
                                } else {
                                    callback(RuntimeException(UportSigner.ERR_AUTH_CANCELED), ByteArray(0))
                                }
                            }
                        })
            } else {
                callback(IllegalStateException(ERR_ACTIVITY_DOES_NOT_EXIST), ByteArray(0))
            }
        } catch (ex: Exception) {
            //TODO: possible scenario to address: if the device has just configured PIN and has never been unlocked, this may throw IllegalBlockSizeException
            callback(ex, ByteArray(0))
        }

    }

    private fun showKeyguard(activity: Activity, purpose: String, callback: KeyguardLaunchFragment.KeyguardCallback) {

        val supportFragmentManager = (activity as AppCompatActivity).supportFragmentManager
        KeyguardLaunchFragment.show(supportFragmentManager, purpose, callback)
    }

    companion object {
        private const val SESSION_TIMEOUT_SECONDS: Int = 30 //seconds
    }

}