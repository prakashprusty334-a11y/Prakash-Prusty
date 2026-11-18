package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PhotoFilter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ProviderCapabilityStatus
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusBanner
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BorderGlass
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RoseError
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAccent

private data class StudioEntry(
    val route: String,
    val title: String,
    val badge: String,
    val description: String,
    val icon: ImageVector,
    val accent: Color,
    val tag: String
)

@Composable
fun StudiosHubScreen(
    providerStatuses: List<ProviderCapabilityStatus>,
    onOpenStudio: (String) -> Unit
) {
    val entries = listOf(
        StudioEntry("studio_image", "AI Image Studio", "Nano Banana 2.1", "Text-to-image, logos, thumbnails & photo remixing", Icons.Filled.Image, ElectricBlue, "hub_image"),
        StudioEntry("studio_photo_editor", "Photo Editor Workspace", "On-Device", "Crop, rotate, color grading, filters & text overlays", Icons.Filled.PhotoFilter, LuminousCyan, "hub_photo_editor"),
        StudioEntry("studio_audio", "AI Audio Lab", "TTS • STT • SFX", "Voiceovers, WAV synthesis, speech dictation & SFX blueprints", Icons.Filled.GraphicEq, EmeraldSuccess, "hub_audio"),
        StudioEntry("studio_writing", "AI Writing Studio", "12 Tools", "Blogs, SEO, scripts, emails, grammar & translation", Icons.Filled.EditNote, VioletAccent, "hub_writing"),
        StudioEntry("studio_coding", "AI Coding Studio", "8 Languages", "Generate, explain, debug, review & format code", Icons.Filled.Code, LuminousCyan, "hub_coding"),
        StudioEntry("studio_document", "Document & Deck Studio", "PDF Export", "Structured reports, resumes, decks & summaries", Icons.Filled.Description, AmberWarning, "hub_document"),
        StudioEntry("studio_video", "AI Video Studio", "Storyboard", "Scene shot-list AI & local video metadata inspector", Icons.Filled.MovieCreation, RoseError, "hub_video")
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, BorderGlass, RoundedCornerShape(20.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_studio_showcase),
                    contentDescription = "Creative Studios Backdrop",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, ObsidianBg.copy(alpha = 0.92f))
                            )
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Column {
                        Text("AI TOOLS DIRECTORY", style = MaterialTheme.typography.labelSmall, color = LuminousCyan)
                        Text("Creative & Engineering Studios", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    }
                }
            }
        }

        items(entries, key = { it.route }) { entry ->
            Surface(
                onClick = { onOpenStudio(entry.route) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(entry.tag),
                shape = RoundedCornerShape(18.dp),
                color = ElevatedCard,
                border = BorderStroke(1.dp, entry.accent.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(entry.accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(entry.icon, contentDescription = entry.title, tint = entry.accent)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(entry.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = entry.accent.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = entry.badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = entry.accent,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(entry.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open ${entry.title}",
                        tint = TextSecondary
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            GlassCard {
                Text("AI Provider & Engine Status", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    providerStatuses.forEach { status ->
                        StatusBanner(
                            title = "${status.name} — ${status.provider}",
                            message = status.detail,
                            isOperational = status.operational
                        )
                    }
                }
            }
        }
    }
}
