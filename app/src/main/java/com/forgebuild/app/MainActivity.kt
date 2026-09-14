package com.forgebuild.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.forgebuild.engine.ui.theme.ForgeBuildTheme

/**
 * FORGEBUILD: the generating AI replaces the contents of setContent with the
 * real app UI, writing ordinary Kotlin/Compose code against the Engine theme
 * (com.forgebuild.engine.ui.theme) and icon set (com.forgebuild.engine.ui.icons).
 * This is a component/theme library — there is no fixed layout schema.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ForgeBuildTheme {
                // Starter surface — replaced per app.
                com.forgebuild.engine.StarterScreen()
            }
        }
    }
}
