package com.example.ui.components

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Android's own unlock: fingerprint or face, or the phone's PIN, pattern or password. Replaced the Vault's custom
 * pattern lock (2026-10-27), whose "Reset Pattern Lock" button worked from the locked screen, so anyone holding the
 * phone could set a new pattern and get in. Android keeps the secret and handles lockout; nothing is stored here.
 */
object DeviceUnlock {
    /** Biometric-or-device-credential. Android 10 and lower can't combine DEVICE_CREDENTIAL with BIOMETRIC_STRONG. */
    val authenticators: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** Why this phone can't unlock this way, or null when it can. */
    fun unavailableReason(context: Context): String? =
        when (BiometricManager.from(context).canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> null
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                "Set up a screen lock (PIN, pattern or fingerprint) in Android settings to use the Vault."
            else -> "This phone can't confirm it's you (no screen lock available), so the Vault stays locked."
        }

    fun findActivity(context: Context): FragmentActivity? {
        var c: Context? = context
        while (c is ContextWrapper) {
            if (c is FragmentActivity) return c
            c = c.baseContext
        }
        return null
    }

    /** Shows the prompt; onResult(true, null) when unlocked, (false, reason) otherwise (null reason: cancelled). */
    fun prompt(
        context: Context,
        title: String,
        subtitle: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        unavailableReason(context)?.let { onResult(false, it); return }
        val activity = findActivity(context) ?: run { onResult(false, "Couldn't show the unlock prompt here."); return }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                    onResult(true, null)

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_CANCELED
                    onResult(false, if (cancelled) null else errString.toString())
                }
                // onAuthenticationFailed (a finger that didn't match): the system prompt stays up and lets them retry
            })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(authenticators)   // with DEVICE_CREDENTIAL there's no negative button
                .build()
        )
    }
}
