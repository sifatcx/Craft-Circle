package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.auth.attemptAutoSignIn
import com.example.auth.onGoogleSignInClicked
import kotlinx.coroutines.delay

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        delay(450L)
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = { /* Ready for interactive sign-in */ },
            scope = scope
        )
    }

    val backdropColor = MaterialTheme.colorScheme.primary
    val foregroundColor = MaterialTheme.colorScheme.onPrimary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backdropColor)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Minimal centered circular monogram & brand wordmark
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(2.5.dp, foregroundColor),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "C",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = foregroundColor
                            )
                        }
                    }

                    Text(
                        text = "CRAFTCIRCLE",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.6.sp,
                        color = foregroundColor
                    )
                }

                Text(
                    text = "Creative hobby projects & DIY community",
                    style = MaterialTheme.typography.bodyMedium,
                    color = foregroundColor.copy(alpha = 0.82f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(visible = errorMessage != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        errorMessage = null
                        isLoading = true
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onAuthError = { msg ->
                                isLoading = false
                                errorMessage = msg
                            },
                            scope = scope,
                            onAuthCancelled = {
                                isLoading = false
                            }
                        )
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("google_sign_in_button"),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = foregroundColor,
                        contentColor = backdropColor,
                        disabledContainerColor = foregroundColor.copy(alpha = 0.72f),
                        disabledContentColor = backdropColor
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = backdropColor,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text(
                            text = "Get started",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Continue with Google",
                    style = MaterialTheme.typography.labelSmall,
                    color = foregroundColor.copy(alpha = 0.72f)
                )
            }
        }
    }
}
