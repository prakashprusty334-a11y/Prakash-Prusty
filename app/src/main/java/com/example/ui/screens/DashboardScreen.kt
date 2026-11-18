package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PhotoFilter
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.UiState
import com.example.data.model.AccountStatus
import com.example.data.model.AiProject
import com.example.data.model.AnnouncementSeverity
import com.example.data.model.SystemAnnouncement
import com.example.data.model.UserProfile
import com.example.ui.components.CreditBalanceChip
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusBanner
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BorderGlass
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RoseError
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAccent

data class StudioShortcut(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val tag: String
)

@Composable
fun DashboardScreen(
    profileState: UiState<UserProfile?>,
    projectsState: UiState<List<AiProject>>,
    announcements: List<SystemAnnouncement>,
    isAdmin: Boolean,
    onOpenRoute: (String) -> Unit,
    onDeleteProject: (String) -> Unit
) {
    val profile = (profileState as? UiState.Success)?.data
    val credits = profile?.creditBalance ?: 0L
    val used = profile?.totalCreditsUsed ?: 0L
    val plan = profile?.planId ?: "FREE"
    val displayName = profile?.displayName ?: "Creator"
    val isSuspended = profile?.resolvedStatus() == AccountStatus.SUSPENDED

    val shortcuts = listOf(
        StudioShortcut("chat", "AI Chat", "Multi-turn & PDF/Image", Icons.Filled.ChatBubble, LuminousCyan, "shortcut_chat"),
        StudioShortcut("studio_image", "Image Studio", "Nano Banana 2.1", Icons.Filled.Image, ElectricBlue, "shortcut_image"),
        StudioShortcut("studio_audio", "Audio Lab", "TTS, STT & SFX Briefs", Icons.Filled.GraphicEq, EmeraldSuccess, "shortcut_audio"),
        StudioShortcut("studio_writing", "Writing Studio", "12 Copy & Blog Tools", Icons.Filled.EditNote, VioletAccent, "shortcut_writing"),
        StudioShortcut("studio_coding", "Coding Studio", "Generate, Fix, Review", Icons.Filled.Code, LuminousCyan, "shortcut_coding"),
        StudioShortcut("studio_document", "Document & Deck", "Structured PDF Export", Icons.Filled.Description, AmberWarning, "shortcut_document"),
        StudioShortcut("studio_photo_editor", "Photo Editor", "Crop, Filters, Overlays", Icons.Filled.PhotoFilter, ElectricBlue, "shortcut_photo_editor"),
        StudioShortcut("studio_video", "Video Studio", "Storyboard & Inspector", Icons.Filled.MovieCreation, RoseError, "shortcut_video")
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CEREVIX AI",
                        style = MaterialTheme.typography.labelSmall,
                        color = LuminousCyan
                    )
                    Text(
                        text = "Welcome, $displayName",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                CreditBalanceChip(
                    credits = credits,
                    planName = plan,
                    onClick = { onOpenRoute("pricing") }
                )
            }
        }

        if (isSuspended) {
            item {
                StatusBanner(
                    title = "Account Suspended",
                    message = "AI generation is paused on this account. Please contact your administrator.",
                    isError = true
                )
            }
        }

        // System Announcements
        if (announcements.isNotEmpty()) {
            items(announcements, key = { it.id }) { ann ->
                StatusBanner(
                    title = ann.title,
                    message = ann.message,
                    isError = ann.resolvedSeverity() == AnnouncementSeverity.CRITICAL,
                    isOperational = if (ann.resolvedSeverity() == AnnouncementSeverity.INFO) true else false
                )
            }
        }

        // Hero Workspace Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, BorderGlass, RoundedCornerShape(22.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_cerevix_hero_banner),
                    contentDescription = "Cerevix Workspace Banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    ObsidianBg.copy(alpha = 0.94f),
                                    ObsidianBg.copy(alpha = 0.68f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "One Platform. Infinite Intelligence.",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$credits credits ready • $used lifetime credits used",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFont),
                                color = LuminousCyan
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { onOpenRoute("chat") },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                modifier = Modifier.testTag("dashboard_launch_chat")
                            ) {
                                Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open AI Chat")
                            }
                            if (isAdmin) {
                                Surface(
                                    onClick = { onOpenRoute("admin") },
                                    shape = RoundedCornerShape(12.dp),
                                    color = SlateSurface.copy(alpha = 0.9f),
                                    border = BorderStroke(1.dp, LuminousCyan.copy(alpha = 0.5f)),
                                    modifier = Modifier.testTag("dashboard_admin_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.AdminPanelSettings, null, tint = LuminousCyan, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Admin", style = MaterialTheme.typography.labelLarge, color = LuminousCyan)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick Metrics Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassCard(
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenRoute("billing") }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AccountBalanceWallet, null, tint = LuminousCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Billing & Plan", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(plan, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Text("Wallet, Razorpay & Plans", style = MaterialTheme.typography.bodySmall, color = LuminousCyan)
                }
                GlassCard(
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenRoute("developer") }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Api, null, tint = ElectricBlue, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("CEREVIX API", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    val count = (projectsState as? UiState.Success)?.data?.size ?: 0
                    Text("v1 Keys", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Text("Playground • $count saved", style = MaterialTheme.typography.bodySmall, color = EmeraldSuccess)
                }
            }
        }

        // Studio Directory Carousel / Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI Studios & Workspaces",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Text(
                    text = "View All",
                    style = MaterialTheme.typography.labelLarge,
                    color = LuminousCyan,
                    modifier = Modifier
                        .clickable { onOpenRoute("studios") }
                        .padding(4.dp)
                )
            }
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(shortcuts, key = { it.route }) { item ->
                    Surface(
                        onClick = { onOpenRoute(item.route) },
                        modifier = Modifier
                            .width(156.dp)
                            .testTag(item.tag),
                        shape = RoundedCornerShape(18.dp),
                        color = ElevatedCard,
                        border = BorderStroke(1.dp, item.accent.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(item.accent.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(item.icon, contentDescription = item.title, tint = item.accent)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Recent Saved Projects
        item {
            Text(
                text = "Recent Saved Creations",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        }

        when (projectsState) {
            is UiState.Loading -> item {
                Text("Loading cloud projects...", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            is UiState.Error -> item {
                StatusBanner("Projects Error", projectsState.message, isError = true)
            }
            is UiState.Success -> {
                val recent = projectsState.data.take(6)
                if (recent.isEmpty()) {
                    item {
                        GlassCard {
                            Text(
                                text = "No saved projects yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Generate an image, write an article, or build code in any studio and tap Save to sync it here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                } else {
                    items(recent, key = { it.id }) { proj ->
                        GlassCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = ElectricBlue.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = proj.category,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = LuminousCyan,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = proj.modelId,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = proj.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = proj.outputText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteProject(proj.id) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = "Delete project",
                                        tint = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
            else -> Unit
        }
    }
}
