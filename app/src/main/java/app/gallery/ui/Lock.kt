package app.gallery.ui

import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.gallery.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow

/** App lock: asks for fingerprint, face or the phone's screen lock. */
object AppLock {
    val locked = MutableStateFlow(false)
    private var leftAt = 0L

    fun onAppStart() {
        if (Settings.appLock.value) locked.value = true
    }

    fun onLeave() {
        leftAt = SystemClock.elapsedRealtime()
    }

    fun onReturn() {
        if (!Settings.appLock.value) return
        val away = (SystemClock.elapsedRealtime() - leftAt) / 1000
        if (leftAt != 0L && away >= Settings.lockAfter.value) locked.value = true
    }

    const val AUTH = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    fun canUse(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

    /** Shows Android's own unlock sheet. Nothing about your fingerprint or PIN reaches this app. */
    fun prompt(activity: FragmentActivity, title: String, subtitle: String? = null, onOk: () -> Unit, onFail: (String) -> Unit = {}) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail(errString.toString())
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { if (subtitle != null) setSubtitle(subtitle) }
            .setAllowedAuthenticators(AUTH)
            .build()
        prompt.authenticate(info)
    }
}

@Composable
fun LockScreen() {
    val activity = LocalContext.current as FragmentActivity
    var message by remember { mutableStateOf<String?>(null) }
    var pulse by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (pulse) 1.08f else 1f, label = "pulse")
    fun ask() = AppLock.prompt(activity, "Unlock Gallery", onOk = { AppLock.locked.value = false }, onFail = { message = it })
    LaunchedEffect(Unit) { pulse = true; ask() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(32.dp)) {
            Box(
                Modifier.size(96.dp).scale(s).clip(RoundedCornerShape(32.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(44.dp)) }
            Text("Gallery is locked", style = MaterialTheme.typography.headlineSmall)
            Text(
                message ?: "Use your fingerprint, face or screen lock to open it.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            Button(onClick = { message = null; ask() }) {
                Icon(Icons.Rounded.Fingerprint, null, modifier = Modifier.padding(end = 8.dp))
                Text("Unlock")
            }
        }
    }
}
