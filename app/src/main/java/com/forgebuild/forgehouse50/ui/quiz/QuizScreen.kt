package com.forgebuild.forgehouse50.ui.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forgebuild.forgehouse50.data.QuizResponse
import com.forgebuild.forgehouse50.data.QuizSubmitResponse
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.ui.ConfettiOverlay
import kotlinx.coroutines.launch

/**
 * Quiz screen. ONE attempt, no gate, NO TIMER (operator decision) — the
 * only constraint is the server-side one-attempt rule. FLAG_SECURE is
 * applied to this route by the NavHost (screenshots blocked here only).
 * When the day becomes fully complete (reading + quiz done), confetti.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    repo: Repository,
    day: Int,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var quiz by remember { mutableStateOf<QuizResponse?>(null) }
    var result by remember { mutableStateOf<QuizSubmitResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var showConfetti by remember { mutableStateOf(false) }
    var readingCompleted by remember { mutableStateOf(false) }
    val answers = remember { mutableStateMapOf<Int, Int>() }

    LaunchedEffect(day) {
        runCatching { repo.api.day(day) }.onSuccess { readingCompleted = it.progress.completed }
        runCatching { repo.api.quiz(day) }
            .onSuccess { quiz = it }
            .onFailure { error = it.message }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Day $day quiz") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                when {
                    loading -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    result != null -> ResultView(result!!, dayFullyComplete = readingCompleted)
                    quiz?.attempt != null -> ExistingAttemptView(quiz!!)
                    quiz != null && quiz!!.can_attempt -> {
                        Text(
                            "One attempt — take your time, there is no timer.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        quiz!!.questions.forEach { q ->
                            Text("${q.index + 1}. ${q.q}", style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            q.options.forEachIndexed { oi, option ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .selectable(
                                            selected = answers[q.index] == oi,
                                            onClick = { answers[q.index] = oi },
                                        )
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = answers[q.index] == oi, onClick = { answers[q.index] = oi })
                                    Text(option, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    val ordered = quiz!!.questions.map { answers[it.index] ?: -1 }
                                    runCatching { repo.api.submitQuiz(day, ordered) }
                                        .onSuccess { res ->
                                            result = res
                                            if (res.ok && readingCompleted) showConfetti = true
                                        }
                                        .onFailure { error = it.message }
                                    busy = false
                                }
                            },
                            enabled = !busy && answers.size == quiz!!.questions.size,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text("Submit (final attempt)")
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Submitting is final — this attempt cannot be retaken.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(32.dp))
                    }
                    else -> Text(
                        quiz?.block_reason ?: "The quiz is not available for this day yet.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            ConfettiOverlay(visible = showConfetti, onFinished = { showConfetti = false })
        }
    }
}

@Composable
private fun ResultView(res: QuizSubmitResponse, dayFullyComplete: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(24.dp))
        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text("Quiz submitted", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("You scored ${res.score} / ${res.total} — ${res.quiz_points} of ${res.quiz_points_possible} points",
            style = MaterialTheme.typography.titleMedium)
        if (res.message.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(res.message, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (dayFullyComplete) {
            Spacer(Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Text(
                    "Day fully complete — reading and quiz done. Well done.",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ExistingAttemptView(quiz: QuizResponse) {
    val a = quiz.attempt ?: return
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(24.dp))
        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text("Attempt already recorded", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("You scored ${a.score} / ${a.total}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text("Each day's quiz allows exactly one attempt.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
