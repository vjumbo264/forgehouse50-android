package com.forgebuild.engine

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.forgebuild.engine.ui.icons.EngineIcons

/** Starter placeholder shown until the generating AI writes the real UI. */
@Composable
fun StarterScreen() {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(EngineIcons.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("ForgeBuild Engine ready", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Replace StarterScreen() with this app's real UI. Material 3 theme " +
                "(dynamic color, light/dark) and Material Symbols icons are wired.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
