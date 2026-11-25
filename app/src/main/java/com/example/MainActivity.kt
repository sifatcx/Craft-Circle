package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.auth.configureAppCheck
import com.example.notifications.CraftNotificationHelper
import com.example.ui.CraftCircleRoot
import com.example.ui.theme.CraftCircleTheme

class MainActivity : ComponentActivity() {
    private var pendingProjectId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        extractPendingProject(intent)
        setContent {
            CraftCircleTheme {
                CraftCircleRoot(
                    pendingProjectId = pendingProjectId,
                    onConsumePendingProject = { pendingProjectId = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractPendingProject(intent)
    }

    private fun extractPendingProject(intent: Intent?) {
        val rawProjectId = intent?.getStringExtra(CraftNotificationHelper.EXTRA_PROJECT_ID)?.trim()
        intent?.removeExtra(CraftNotificationHelper.EXTRA_PROJECT_ID)
        if (!rawProjectId.isNullOrEmpty() && VALID_DOCUMENT_ID_REGEX.matches(rawProjectId)) {
            pendingProjectId = rawProjectId
        }
    }

    private companion object {
        val VALID_DOCUMENT_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]{1,128}$")
    }
}
