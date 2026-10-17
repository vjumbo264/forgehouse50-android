package com.forgebuild.forgehouse50.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.RowScope

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface

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
import androidx.compose.material3.Icon
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.IconButton

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.forgebuild.forgehouse50.data.Repository
import java.net.UnknownHostException
import kotlinx.coroutines.launch
import com.forgebuild.forgehouse50.ui.ExpressiveButtonLoader

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
    signupLabel: String = "Joining closes on 21 Oct 2026",
    onAuthenticated: () -> Unit,
) {
    var mode by remember { mutableStateOf<Mode>(Mode.Login) }
    when (val m = mode) {
        Mode.Login -> LoginForm(repo, registrationOpen, signupLabel,
            onSignup = { mode = Mode.Signup },
            onForgotPassword = { mode = Mode.ForgotPassword(it) },
            onAdminLogin = { mode = Mode.AdminLogin(it) },
            onNeedsVerification = { mode = Mode.Otp(it) },
            onAuthenticated = onAuthenticated)
        Mode.Signup -> SignupForm(repo, registrationOpen, signupLabel, onBack = { mode = Mode.Login }, onNeedsVerification = { mode = Mode.Otp(it) })
        is Mode.Otp -> OtpForm(repo, m.email, onAuthenticated, onBack = { mode = Mode.Login })
        is Mode.AdminLogin -> AdminLoginForm(repo, initialUsername = m.initialUsername, onBack = { mode = Mode.Login }, onAuthenticated = onAuthenticated)
        is Mode.ForgotPassword -> ForgotPasswordForm(repo, initialEmail = m.initialEmail, onCodeSent = { mode = Mode.ResetPassword(it) }, onBack = { mode = Mode.Login })
        is Mode.ResetPassword -> ResetPasswordForm(repo, email = m.email, onSuccess = onAuthenticated, onBack = { mode = Mode.Login })
    }
}

private sealed interface Mode {
    data object Login : Mode
    data object Signup : Mode
    data class Otp(val email: String) : Mode
    data class AdminLogin(val initialUsername: String = "") : Mode
    data class ForgotPassword(val initialEmail: String = "") : Mode
    data class ResetPassword(val email: String) : Mode
}

@Composable
private fun AuthScaffold(subtitle: String, content: @Composable () -> Unit) {
    // Part G (2026-09-18): pin the login/sign-up/OTP background to the active
    // theme's surface color. Auth renders inside the app NavHost, which — on
    // the older navigation-compose line — does not draw its own window
    // background, so the composable could fall through to a dark default
    // window regardless of the system light/dark setting (the operator's
    // "always stuck in a broken dark mode"). Reading from MaterialTheme keeps
    // it following the same dynamic theme as every other screen.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .imePadding()
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
    signupLabel: String,
    onSignup: () -> Unit,
    onForgotPassword: (String) -> Unit,
    onAdminLogin: (String) -> Unit,
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
            shape = RoundedCornerShape(12.dp),
            value = email, onValueChange = { email = it; error = null }, label = { Text("Email") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        val looksLikeAdmin = email.isNotBlank() && !email.contains("@")
        if (looksLikeAdmin) {
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAdminLogin(email.trim()) }
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Info, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Looks like an admin username. Tap to use Admin sign in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            shape = RoundedCornerShape(12.dp),
            value = password, onValueChange = { password = it }, label = { Text("Password") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { onForgotPassword(email.trim()) }) {
                Text(
                    "Forgot password?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
        ExpressiveButton(
            onClick = {
                if (!email.contains("@")) {
                    error = "Email address must contain '@'. If you are an administrator, use Admin sign in."
                    return@ExpressiveButton
                }
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
            busy = busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sign in")
        }
        if (registrationOpen) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSignup) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("New here? Create an account")
                    Text(
                        signupLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                "Joining has closed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { onAdminLogin(if (looksLikeAdmin) email.trim() else "") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Admin sign in")
        }
    }
}

@Composable
private fun SignupForm(
    repo: Repository,
    registrationOpen: Boolean,
    signupLabel: String,
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

    val subtitle = if (registrationOpen) "Create your account — $signupLabel" else "Joining has closed"
    AuthScaffold(subtitle) {
        if (!registrationOpen) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Registration for this ForgeHouse 50 run has closed — the programme is already underway.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Return to sign in")
            }
            return@AuthScaffold
        }
        OutlinedTextField(
            shape = RoundedCornerShape(12.dp),
            value = name, onValueChange = { name = it }, label = { Text("Given name") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            shape = RoundedCornerShape(12.dp),
            value = surname, onValueChange = { surname = it }, label = { Text("Surname (last name)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            shape = RoundedCornerShape(12.dp),
            value = email, onValueChange = { email = it }, label = { Text("Email") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            shape = RoundedCornerShape(12.dp),
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
        ExpressiveButton(
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
            busy = busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Create account")
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
            shape = RoundedCornerShape(12.dp),
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
        ExpressiveButton(
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
            busy = busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Verify")
        }
        Spacer(Modifier.height(8.dp))
        ExpressiveButton(
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

@Composable
private fun AdminLoginForm(
    repo: Repository,
    initialUsername: String = "",
    onBack: () -> Unit,
    onAuthenticated: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AuthScaffold("Administrator access only") {
        OutlinedTextField(
            shape = RoundedCornerShape(12.dp),
            value = username,
            onValueChange = { username = it; error = null },
            label = { Text("Admin username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            shape = RoundedCornerShape(12.dp),
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("Password") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password",
                    )
                }
            },
        )
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
        ExpressiveButton(
            onClick = {
                scope.launch {
                    busy = true
                    error = null
                    try {
                        val res = repo.api.adminLogin(username.trim(), password)
                        if (res.ok) {
                            repo.session.userName = res.username ?: username.trim()
                            repo.session.userRole = "admin"
                            if (res.token != null) {
                                repo.session.sessionToken = res.token
                            }
                            onAuthenticated()
                        } else {
                            error = res.error ?: "Sign-in failed"
                        }
                    } catch (e: Exception) {
                        error = friendlyNetError(e, "Admin sign-in failed")
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy && username.isNotBlank() && password.isNotBlank(),
            busy = busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sign in as admin")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) {
            Text("Back to participant sign in")
        }
    }
}


@Composable
private fun ForgotPasswordForm(
    repo: Repository,
    initialEmail: String,
    onCodeSent: (String) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(initialEmail) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AuthScaffold("Reset your password") {
        Text(
            "Enter your email address and we will send you a 6-digit verification code to reset your password.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        OutlinedTextField(
            shape = RoundedCornerShape(12.dp),
            value = email,
            onValueChange = { email = it; error = null },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
        ExpressiveButton(
            onClick = {
                if (!email.contains("@")) {
                    error = "A valid email address is required"
                    return@ExpressiveButton
                }
                scope.launch {
                    busy = true
                    error = null
                    try {
                        val res = repo.api.forgotPassword(email.trim())
                        if (res.ok) {
                            onCodeSent(email.trim())
                        } else {
                            error = res.error ?: "Unable to send reset code"
                        }
                    } catch (e: Exception) {
                        error = friendlyNetError(e, "Unable to send reset code")
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy && email.isNotBlank(),
            busy = busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Send reset code")
        }
        Spacer(Modifier.height(12.dp))
        TextButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to sign in")
        }
    }
}

@Composable
private fun ResetPasswordForm(
    repo: Repository,
    email: String,
    onSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var resendBusy by remember { mutableStateOf(false) }
    var infoMessage by remember { mutableStateOf<String?>("Code sent to $email") }
    var error by remember { mutableStateOf<String?>(null) }

    AuthScaffold("Choose a new password") {
        infoMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        OutlinedTextField(
            shape = RoundedCornerShape(12.dp),
            value = code,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) { code = it; error = null } },
            label = { Text("6-digit code") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            shape = RoundedCornerShape(12.dp),
            value = newPassword,
            onValueChange = { newPassword = it; error = null },
            label = { Text("New password (8+ characters)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            }
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            shape = RoundedCornerShape(12.dp),
            value = confirmPassword,
            onValueChange = { confirmPassword = it; error = null },
            label = { Text("Confirm new password") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
        ExpressiveButton(
            onClick = {
                if (code.length != 6) {
                    error = "Enter the 6-digit code from your email"
                    return@ExpressiveButton
                }
                if (newPassword.length < 8) {
                    error = "Password must be at least 8 characters"
                    return@ExpressiveButton
                }
                if (newPassword != confirmPassword) {
                    error = "Passwords do not match"
                    return@ExpressiveButton
                }
                scope.launch {
                    busy = true
                    error = null
                    try {
                        val res = repo.api.resetPassword(email, code, newPassword)
                        if (res.ok) {
                            repo.session.userName = res.name
                            repo.session.userRole = res.role
                            onSuccess()
                        } else {
                            error = res.error ?: "Password reset failed"
                        }
                    } catch (e: Exception) {
                        error = friendlyNetError(e, "Password reset failed")
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy && code.length == 6 && newPassword.length >= 8 && confirmPassword.isNotBlank(),
            busy = busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Reset password")
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    scope.launch {
                        resendBusy = true
                        error = null
                        try {
                            val res = repo.api.forgotPassword(email)
                            if (res.ok) {
                                infoMessage = "A fresh 6-digit code was sent to $email"
                            } else {
                                error = res.error ?: "Unable to resend code"
                            }
                        } catch (e: Exception) {
                            error = friendlyNetError(e, "Unable to resend code")
                        } finally {
                            resendBusy = false
                        }
                    }
                },
                enabled = !resendBusy
            ) {
                Text(if (resendBusy) "Sending..." else "Resend code")
            }
            TextButton(onClick = onBack) {
                Text("Back to sign in")
            }
        }
    }
}
