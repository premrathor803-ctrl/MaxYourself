package com.elevate.launcher.privacy

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Thin wrapper around Android's own fingerprint/face prompt. Elevate never sees the fingerprint or face data. */
object Bio {
    private const val AUTH = BiometricManager.Authenticators.BIOMETRIC_WEAK

    /** Null when fingerprint or face can be used, otherwise a plain-language reason. */
    fun unavailableReason(ctx: Context): String? =
        when (BiometricManager.from(ctx).canAuthenticate(AUTH)) {
            BiometricManager.BIOMETRIC_SUCCESS -> null
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No fingerprint or face is saved on this phone yet. Add one in Android Settings > Security, then come back."
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "This phone has no fingerprint sensor or face unlock Elevate can use."
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "The fingerprint sensor is busy or unavailable right now. Try again in a moment."
            else -> "Fingerprint or face unlock is not available on this phone."
        }

    fun prompt(act: FragmentActivity, title: String, subtitle: String, onOk: () -> Unit) {
        if (unavailableReason(act) != null) return
        val cb = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onOk() }
        }
        val prompt = BiometricPrompt(act, ContextCompat.getMainExecutor(act), cb)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(AUTH)
            .build()
        prompt.authenticate(info)
    }
}
