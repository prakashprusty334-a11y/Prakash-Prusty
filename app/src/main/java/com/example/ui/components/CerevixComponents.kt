package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BorderGlass
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.RoseError
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = BorderGlass,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val baseModifier = modifier
        .clip(shape)
        .background(
            Brush.linearGradient(
                colors = listOf(
                    ElevatedCard.copy(alpha = 0.92f),
                    SlateSurface.copy(alpha = 0.85f)
                )
            )
        )
        .border(1.dp, borderColor.copy(alpha = 0.7f), shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(16.dp)

    Column(modifier = baseModifier, content = content)
}

@Composable
fun CreditBalanceChip(
    credits: Long,
    planName: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Surface(
        onClick = onClick,
        modifier = modifier.testTag("credit_balance_chip"),
        shape = RoundedCornerShape(50),
        color = SlateSurface,
        border = BorderStroke(1.dp, LuminousCyan.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = "Credits",
                tint = LuminousCyan,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$credits",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = JetBrainsMonoFont,
                    fontWeight = FontWeight.Bold
                ),
                color = TextPrimary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(ElectricBlue.copy(alpha = 0.25f))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = planName,
                    style = MaterialTheme.typography.labelSmall,
                    color = LuminousCyan
                )
            }
        }
    }
}

@Composable
fun StudioTopBar(
    title: String,
    subtitle: String,
    credits: Long,
    planName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(SlateSurface)
                .border(1.dp, BorderGlass, CircleShape)
                .testTag("studio_back_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Navigate back",
                tint = TextPrimary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        CreditBalanceChip(credits = credits, planName = planName)
    }
}

@Composable
fun ShimmerLoadingCard(
    label: String,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_shift"
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            MidnightNavy,
            ElectricBlue.copy(alpha = 0.25f),
            LuminousCyan.copy(alpha = 0.18f),
            MidnightNavy
        ),
        start = Offset(shift - 300f, 0f),
        end = Offset(shift, 300f)
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(brush)
            .border(1.dp, LuminousCyan.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = LuminousCyan
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Powered by Firebase AI Logic",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun StatusBanner(
    title: String,
    message: String,
    isError: Boolean = false,
    isOperational: Boolean? = null,
    modifier: Modifier = Modifier
) {
    val accent = when {
        isError -> RoseError
        isOperational == true -> EmeraldSuccess
        isOperational == false -> AmberWarning
        else -> LuminousCyan
    }
    val icon: ImageVector = when {
        isError -> Icons.Filled.ErrorOutline
        isOperational == true -> Icons.Filled.CheckCircle
        else -> Icons.Filled.Info
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = accent
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary.copy(alpha = 0.9f)
                )
            }
        }
    }
}
