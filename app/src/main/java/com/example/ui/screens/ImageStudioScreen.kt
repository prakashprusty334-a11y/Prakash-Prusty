package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ExportUtils
import com.example.core.UiState
import com.example.data.model.AiModelCatalog
import com.example.ui.components.GlassCard
import com.example.ui.components.ShimmerLoadingCard
import com.example.ui.components.StatusBanner
import com.example.ui.components.StudioTopBar
import com.example.ui.theme.BorderGlass
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.CreativeStudiosViewModel
import com.google.firebase.ai.type.AspectRatio

@Composable
fun ImageStudioScreen(
    viewModel: CreativeStudiosViewModel,
    credits: Long,
    planName: String,
    accountSuspended: Boolean,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val imageState by viewModel.imageState.collectAsStateWithLifecycle()
    val history by viewModel.imageHistory.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    var prompt by remember { mutableStateOf("") }
    var selectedModelId by remember { mutableStateOf(AiModelCatalog.DEFAULT_IMAGE_MODEL.id) }
    var selectedRatioLabel by remember { mutableStateOf("1:1") }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val ratioMap = mapOf(
        "1:1" to AspectRatio.SQUARE_1x1,
        "16:9" to AspectRatio.LANDSCAPE_16x9,
        "9:16" to AspectRatio.PORTRAIT_9x16,
        "4:3" to AspectRatio.LANDSCAPE_4x3,
        "3:4" to AspectRatio.PORTRAIT_3x4
    )

    val presets = listOf(
        "Logo" to "Minimalist geometric futuristic tech brand logo on deep obsidian background, crisp vector symmetry",
        "YouTube Thumb" to "High-contrast dramatic YouTube tech thumbnail background with glowing cyan neon circuits",
        "Product Shot" to "Studio luxury product photography of sleek wireless headphones on dark reflective obsidian",
        "Social Art" to "Editorial cyberpunk Tokyo skyline at midnight with cobalt and cyan reflections"
    )

    LaunchedEffect(imageState) {
        if (imageState is UiState.Loading || imageState is UiState.Success || imageState is UiState.Error) {
            listState.animateScrollToItem(0)
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val decoded = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            } catch (_: Exception) {
                null
            }
            if (decoded != null) {
                sourceBitmap = decoded
            } else {
                Toast.makeText(context, "Could not load selected reference image.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StudioTopBar(
            title = "AI Image Studio",
            subtitle = "Text-to-Image, Logos & AI Variations",
            credits = credits,
            planName = planName,
            onBack = onBack
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Live Output / Loading / Error right at the top so user sees feedback immediately
            item {
                when (val state = imageState) {
                    is UiState.Loading -> ShimmerLoadingCard("Synthesizing image with $selectedModelId (may take 30–60s)...")
                    is UiState.Error -> {
                        StatusBanner("Image Generation Failed", state.message, isError = true)
                    }
                    is UiState.Success -> {
                        val bmp = state.data
                        GlassCard(borderColor = LuminousCyan.copy(alpha = 0.45f)) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Generated AI image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        val res = ExportUtils.saveBitmapToGallery(context, bmp, "cerevix_ai")
                                        Toast.makeText(
                                            context,
                                            if (res.isSuccess) "Saved to Pictures/CerevixAI" else "Save failed",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                                ) {
                                    Icon(Icons.Filled.Download, null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Download")
                                }
                                OutlinedButton(
                                    onClick = { ExportUtils.shareBitmap(context, bmp, "Cerevix AI Artwork") },
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Icon(Icons.Filled.Share, null, tint = LuminousCyan)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share", color = TextPrimary)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { sourceBitmap = bmp },
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("Use as Input for AI Variation / Edit", color = LuminousCyan)
                            }
                        }
                    }
                    else -> Unit
                }
            }

            // 2. Prompt & Generate Card (always immediately visible)
            item {
                GlassCard {
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = {
                            Text(
                                if (sourceBitmap != null) "Describe image edit / variation"
                                else "Describe the image to generate"
                            )
                        },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("image_prompt_input"),
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (sourceBitmap != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Image(
                                bitmap = sourceBitmap!!.asImageBitmap(),
                                contentDescription = "Source image for editing",
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Text(
                                "Reference image attached (AI Edit mode)",
                                style = MaterialTheme.typography.bodySmall,
                                color = LuminousCyan,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { sourceBitmap = null }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear source image", tint = TextSecondary)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = LuminousCyan)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reference", color = TextPrimary)
                        }
                        Button(
                            onClick = {
                                viewModel.generateOrEditImage(
                                    prompt = prompt,
                                    modelId = selectedModelId,
                                    aspectRatio = ratioMap[selectedRatioLabel],
                                    sourceBitmap = sourceBitmap,
                                    currentCredits = credits,
                                    accountSuspended = accountSuspended
                                )
                            },
                            enabled = imageState !is UiState.Loading,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("generate_image_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = Color.White)
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (sourceBitmap != null) "Edit Image" else "Generate Image")
                        }
                    }
                }
            }

            // 3. Model, Ratio & Presets
            item {
                GlassCard {
                    Text("Model & Aspect Ratio", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AiModelCatalog.imageModels.forEach { m ->
                            FilterChip(
                                selected = selectedModelId == m.id,
                                onClick = { selectedModelId = m.id },
                                label = { Text("${m.label} (${m.creditCost}⚡)") }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ratioMap.keys.forEach { r ->
                            FilterChip(
                                selected = selectedRatioLabel == r,
                                onClick = { selectedRatioLabel = r },
                                label = { Text(r) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Quick Presets", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEach { (label, presetPrompt) ->
                            OutlinedButton(
                                onClick = { prompt = presetPrompt },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, BorderGlass)
                            ) {
                                Text(label, color = LuminousCyan)
                            }
                        }
                    }
                }
            }

            if (history.isNotEmpty()) {
                item {
                    Text("Session History", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(history) { item ->
                            Image(
                                bitmap = item.bitmap.asImageBitmap(),
                                contentDescription = item.prompt,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(92.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, BorderGlass, RoundedCornerShape(12.dp))
                                    .clickable { viewModel.selectHistoryImage(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}
