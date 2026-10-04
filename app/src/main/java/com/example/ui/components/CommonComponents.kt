package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.CryptoManager
import com.example.ui.theme.VaultDanger
import com.example.ui.theme.VaultDarkBackground
import com.example.ui.theme.VaultSuccess
import com.example.ui.theme.VaultWarning

@Composable
fun PasswordStrengthBar(
    analysis: CryptoManager.PasswordAnalysis,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = (analysis.score / 100f).coerceIn(0f, 1f),
        label = "strength_progress"
    )

    val color by animateColorAsState(
        targetValue = when {
            analysis.score >= 80 -> VaultSuccess
            analysis.score >= 60 -> Color(0xFF38BDF8)
            analysis.score >= 40 -> VaultWarning
            else -> VaultDanger
        },
        label = "strength_color"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Strength: ${analysis.rating}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            Text(
                text = "${analysis.score}% (${analysis.entropyBits} bits entropy)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        if (analysis.suggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            analysis.suggestions.take(2).forEach { tip ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 1.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tip,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ServiceAvatar(
    title: String,
    category: String,
    modifier: Modifier = Modifier
) {
    val firstChar = title.firstOrNull()?.uppercaseChar()?.toString() ?: "V"
    val gradientColors = when (category.lowercase()) {
        "finance" -> listOf(Color(0xFF059669), Color(0xFF10B981))
        "social" -> listOf(Color(0xFF2563EB), Color(0xFF38BDF8))
        "work" -> listOf(Color(0xFF7C3AED), Color(0xFFA855F7))
        "entertainment" -> listOf(Color(0xFFDC2626), Color(0xFFF97316))
        else -> listOf(Color(0xFF0F766E), Color(0xFF14B8A6))
    }

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(gradientColors)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = firstChar,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
    }
}
