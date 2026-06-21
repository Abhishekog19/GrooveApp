package com.groove.music.ui.screens.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.groove.music.ui.theme.*

/**
 * First-launch setup screen — shown when the app has no music yet.
 * Mirrors the setup folder flow in the web app (FolderPermissionBanner.jsx).
 */
@Composable
fun SetupScreen(onSetupComplete: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = GrooveBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = GroovePurple
            )

            Spacer(Modifier.height(24.dp))

            Text(
                "Welcome to Groove",
                style      = MaterialTheme.typography.headlineLarge,
                color      = GrooveTextPrimary,
                fontWeight = FontWeight.Black,
                textAlign  = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "Your smart music player. Start by granting access to your music library.",
                style     = MaterialTheme.typography.bodyMedium,
                color     = GrooveTextMuted,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(40.dp))

            Button(
                onClick = onSetupComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GroovePurple),
                shape  = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Get Started",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
