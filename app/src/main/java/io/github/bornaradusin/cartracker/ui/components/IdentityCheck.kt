package io.github.bornaradusin.cartracker.ui.components

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Asks for the phone's fingerprint, face or screen lock before something that matters (cutting or
 * restoring fuel). Returns a function: call it with a title and what to do once confirmed. When
 * [enabled] is off, or the phone has no screen lock, it goes straight ahead.
 */
@Composable
fun rememberIdentityCheck(enabled: Boolean): (title: String, onConfirmed: () -> Unit) -> Unit {
    val context = LocalContext.current
    val waiting = remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val next = waiting.value
        waiting.value = null
        if (result.resultCode == Activity.RESULT_OK) next?.invoke()
    }
    val on = rememberUpdatedState(enabled)
    return remember(context, launcher) {
        { title: String, onConfirmed: () -> Unit ->
            val keyguard = context.getSystemService(KeyguardManager::class.java)
            when {
                !on.value || keyguard == null || !keyguard.isDeviceSecure -> onConfirmed()
                Build.VERSION.SDK_INT >= 29 -> prompt(context, title, onConfirmed)
                else -> {
                    @Suppress("DEPRECATION")
                    val intent = keyguard.createConfirmDeviceCredentialIntent(title, "Confirm it's you")
                    if (intent == null) {
                        onConfirmed()
                    } else {
                        waiting.value = onConfirmed
                        launcher.launch(intent)
                    }
                }
            }
        }
    }
}

@RequiresApi(29)
private fun prompt(context: Context, title: String, onConfirmed: () -> Unit) {
    val builder = BiometricPrompt.Builder(context)
        .setTitle(title)
        .setSubtitle("Confirm it's you")
    if (Build.VERSION.SDK_INT >= 30) {
        builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
    } else {
        @Suppress("DEPRECATION")
        builder.setDeviceCredentialAllowed(true)
    }
    runCatching {
        builder.build().authenticate(
            CancellationSignal(),
            context.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onConfirmed()
                }
            },
        )
    }
}
