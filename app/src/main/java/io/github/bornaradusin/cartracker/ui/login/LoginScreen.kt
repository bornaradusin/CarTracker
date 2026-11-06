package io.github.bornaradusin.cartracker.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bornaradusin.cartracker.ui.AppViewModel
import io.github.bornaradusin.cartracker.ui.ServerCheck
import io.github.bornaradusin.cartracker.ui.components.BigButton
import io.github.bornaradusin.cartracker.ui.theme.Ic
import io.github.bornaradusin.cartracker.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(vm: AppViewModel) {
    val c = LocalAppColors.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val check by vm.serverCheck.collectAsStateWithLifecycle()
    var server by rememberSaveable { mutableStateOf(settings.server) }
    var email by rememberSaveable { mutableStateOf(settings.email) }
    var password by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var working by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (working) return
        working = true
        error = null
        scope.launch {
            error = vm.signIn(server, email, password)
            working = false
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.accentOutline,
        unfocusedBorderColor = c.line,
        focusedLabelColor = c.accentText,
        cursorColor = c.accentOutline,
        focusedContainerColor = c.surface,
        unfocusedContainerColor = c.surface,
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)).background(c.accent),
                contentAlignment = Alignment.Center,
            ) { Icon(Ic.car, null, tint = c.onAccent, modifier = Modifier.size(48.dp)) }
            Text("CarTracker", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp), color = c.ink)
            Text("Your car, on your own Traccar server.", style = MaterialTheme.typography.bodyLarge, color = c.muted, textAlign = TextAlign.Center)
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.surface).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = server,
                onValueChange = {
                    server = it
                    vm.checkServer(it)
                },
                label = { Text("Server address") },
                placeholder = { Text("track.example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                colors = fieldColors,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            when (val s = check) {
                ServerCheck.Checking -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = c.muted, modifier = Modifier.size(14.dp))
                    Text("Looking for the server…", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                is ServerCheck.Found -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Ic.check, null, tint = c.good, modifier = Modifier.size(14.dp))
                    Text(
                        if (s.version.isNullOrBlank()) "Traccar server found" else "Traccar ${s.version.substringBefore('-')} found",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.good,
                    )
                }
                is ServerCheck.Problem -> Text(s.message, style = MaterialTheme.typography.bodySmall, color = c.warnText)
                ServerCheck.Idle -> Unit
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                colors = fieldColors,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                colors = fieldColors,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.warnText) }
            Spacer(Modifier.height(2.dp))
            BigButton(if (working) "Signing in…" else "Sign in", Modifier.fillMaxWidth(), enabled = !working) { submit() }
        }

        Text(
            "Works with any Traccar server, at home or online.\nCarTracker is an independent app, not made by Traccar.",
            style = MaterialTheme.typography.bodySmall,
            color = c.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
