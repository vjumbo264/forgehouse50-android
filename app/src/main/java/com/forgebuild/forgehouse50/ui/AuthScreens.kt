package com.forgebuild.forgehouse50.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.forgebuild.forgehouse50.data.Repository
import java.net.UnknownHostException
import kotlinx.coroutines.launch

/**
 * Auth screens — the same email/password + OTP-via-Brevo flow as the web
 * app, against the same endpoints:
 *   signup -> 6-digit code emailed -> verify -> session
 *   login  -> session            (unverified accounts route to OTP)
 */
@Composable
fun AuthScreen(
    repo: Repository,
    registrationOpen: Boolean,
    onAuthenticated: () -> Unit,
) {
    var mode by remember { mutableStateOf<Mode>(Mode.Login) }
    when (val m = mode) {
        Mode.Login -> LoginForm(repo, registrationOpen,
            onSignup = { mode = Mode.Signup },
            onNeedsVerification = { mode = Mode.Otp(it) },
            onAuthenticated = onAuthenticated)
        Mode.Signup -> SignupForm(repo, onBack = { mode = Mode.Login }, onNeedsVerification = { mode = Mode.Otp(it) })
        is Mode.Otp -> OtpForm(repo, m.email, onAuthenticated, onBack = { mode = Mode.Login })
    }
}

private sealed interface Mode {
    data object Login : Mode
    data object Signup : Mode
    data class Otp(val email: String) : Mode
}

@Composable
private fun AuthScaffold(subtitle: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        Icon(
            Icons.Filled.MenuBook, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("ForgeHouse 50", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        content()
    }
}


/**
 * ISSUE 6a: map a low-level network/DNS failure (e.g. an UnknownHostException
 * such as 'Unable to resolve host "forgehouse50.pages.dev"') to a clear,
 * friendly, actionable message — an ordinary user should never see the raw
 * exception text the operator screenshotted at registration.
 */
private fun friendlyNetError(e: Throwable, fallback: String): String {
    val chain = generateSequence<Throwable>(e) { it.cause }
    val net = chain.any {
        it is UnknownHostException ||
            (it.message?.contains("Unable to resolve host", true) == true) ||
            (it.message?.contains("No address associated", true) == true) ||
            (it.message?.contains("failed to connect", true) == true) ||
            (it.message?.contains("timeout", true) == true)
    }
    return if (net) "Couldn't connect — check your internet connection and try again."
    else e.message ?: fallback
}

@Composable
private fun LoginForm(
    repo: Repository,
    registrationOpen: Boolean,
    onSignup: () -> Unit,
    onNeedsVerification: (String) -> Unit,
    onAuthenticated: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AuthScaffold("Read the New Testament in 50 reading days.") {
        OutlinedTextField(
            value = email, onValueChange = { email = it }, label = { Text("Email") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it }, label = { Text("Password") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                scope.launch {
                    busy = true; error = null
                    try {
                        val res = repo.api.login(email.trim(), password)
                        if (res.ok) {
                            repo.session.userName = res.name
                            repo.session.userRole = res.role
                            onAuthenticated()
                        } else if (res.needs_verification) {
                            onNeedsVerification(res.email ?: email.trim())
                        } else {
                            error = res.error ?: "Sign-in failed"
                        }
                    } catch (e: Exception) {
                        val m = e.message ?: "Sign-in failed"
                        if (m.contains("not verified", true)) onNeedsVerification(email.trim()) else error = friendlyNetError(e, "Sign-in failed")
                    } finally { busy = false }
                }
            },
            enabled = !busy && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Sign in")
        }
        if (registrationOpen) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSignup) { Text("New here? Create an account") }
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                "Registration for this programme run is closed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SignupForm(
    repo: Repository,
    onBack: () -> Unit,
    onNeedsVerification: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var surname by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    // combined_fixes_v1 Issue 2: avatar picker is part of registration again and
    // renders from BUNDLED assets (the v1 wiring bug hardcoded avatar-01, no picker).
    var avatarId by remember { mutableStateOf(AvatarAssets.IDS.first()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AuthScaffold("Create your account — a verification code will be emailed to you.") {
        OutlinedTextField(
            value = name, onValueChange = { name = it }, label = { Text("Given name") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = surname, onValueChange = { surname = it }, label = { Text("Surname (last name)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = email, onValueChange = { email = it }, label = { Text("Email") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Password (8+ characters)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Spacer(Modifier.height(16.dp))
        Text("Choose your avatar", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.fillMaxWidth().height(220.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(AvatarAssets.IDS) { id ->
                val selected = id == avatarId
                Image(
                    painter = painterResource(AvatarAssets.resFor(id)),
                    contentDescription = id,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        )
                        .clickable { avatarId = id },
                )
            }
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                scope.launch {
                    busy = true; error = null
                    try {
                        val res = repo.api.signup(email.trim(), password, name.trim(), surname.trim(), avatarId)
                        if (res.ok) onNeedsVerification(email.trim())
                        else error = res.error ?: "Could not create the account"
                    } catch (e: Exception) { error = friendlyNetError(e, "Something went wrong") } finally { busy = false }
                }
            },
            enabled = !busy && name.isNotBlank() && surname.isNotBlank() && email.isNotBlank() && password.length >= 8,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Create account")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) { Text("Back to sign in") }
    }
}

@Composable
private fun OtpForm(
    repo: Repository,
    email: String,
    onAuthenticated: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var resent by remember { mutableStateOf(false) }

    AuthScaffold("Enter the 6-digit code emailed to $email.") {
        OutlinedTextField(
            value = code, onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) code = it },
            label = { Text("Verification code") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (resent) {
            Spacer(Modifier.height(12.dp))
            Text("A new code was sent.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                scope.launch {
                    busy = true; error = null
                    try {
                        val res = repo.api.verify(email, code)
                        if (res.ok) onAuthenticated() else error = res.error ?: "Verification failed"
                    } catch (e: Exception) { error = friendlyNetError(e, "Something went wrong") } finally { busy = false }
                }
            },
            enabled = !busy && code.length == 6,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Verify")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                scope.launch {
                    resent = false
                    runCatching { repo.api.resend(email) }.onSuccess { resent = true }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Resend code") }
        TextButton(onClick = onBack) { Text("Back") }
    }
}
