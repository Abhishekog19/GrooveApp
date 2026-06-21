package com.groove.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.groove.music.ui.theme.*

/**
 * Full-screen maintenance banner shown when the TIDAL proxy backend is down.
 * Matches the web app's ServiceGate component.
 */
@Composable
fun MaintenanceBanner(
    title: String = "Under Maintenance",
    subtitle: String = "The music streaming service is temporarily unavailable.\nPlease try again in a few minutes.",
    onRetry: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GrooveBg)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Maintenance icon
            Surface(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(20.dp),
                color = GrooveAmber.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Build,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = GrooveAmber
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = GrooveTextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = GrooveTextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Your local library and playback still work normally.",
                style = MaterialTheme.typography.bodySmall,
                color = GrooveTextSubtle,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            // Retry button
            OutlinedButton(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, GrooveAmber),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = GrooveAmber
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Check Again",
                    color = GrooveAmber,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Compact inline maintenance banner — for embedding at the top of a screen
 * that still has some functional content (e.g. showing cached results).
 */
@Composable
fun MaintenanceBannerCompact(
    message: String = "TIDAL streaming service is temporarily down",
    onRetry: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = GrooveAmber.copy(alpha = 0.10f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Build,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = GrooveAmber
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = GrooveAmber,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onRetry) {
                Text("Retry", color = GrooveAmber, fontSize = 12.sp)
            }
        }
    }
}
