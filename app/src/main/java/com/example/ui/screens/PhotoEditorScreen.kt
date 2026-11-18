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
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.CropRatio
import com.example.core.ExportUtils
import com.example.core.ImageEffects
import com.example.core.PhotoFilterPreset
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusBanner
import com.example.ui.components.StudioTopBar
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun PhotoEditorScreen(
    credits: Long,
    planName: String,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var selectedFilter by remember { mutableStateOf(PhotoFilterPreset.ORIGINAL) }
    var rotation by remember { mutableIntStateOf(0) }
    var flipH by remember { mutableStateOf(false) }
    var cropRatio by remember { mutableStateOf(CropRatio.FREE) }
    var overlayText by remember { mutableStateOf("") }

    val editedBitmap = remember(
        originalBitmap, brightness, contrast, saturation,
        selectedFilter, rotation, flipH, cropRatio, overlayText
    ) {
        originalBitmap?.let { src ->
            ImageEffects.applyEdits(
                source = src,
                brightness = brightness,
                contrast = contrast,
                saturation = saturation,
                filter = selectedFilter,
                rotationDegrees = rotation,
                flipHorizontal = flipH,
                cropRatio = cropRatio,
                overlayText = overlayText
            )
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val loaded = try {
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
            if (loaded != null) {
                // Downscale large photos for smooth real-time preview
                val maxDim = 1280
                val scale = minOf(maxDim.toFloat() / loaded.width, maxDim.toFloat() / loaded.height, 1f)
                originalBitmap = if (scale < 1f) {
                    Bitmap.createScaledBitmap(
                        loaded,
                        (loaded.width * scale).toInt(),
                        (loaded.height * scale).toInt(),
                        true
                    )
                } else loaded
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StudioTopBar(
            title = "Photo Editor",
            subtitle = "On-Device Engine • 0 Credits",
            credits = credits,
            planName = planName,
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                StatusBanner(
                    title = "On-Device Graphics Pipeline",
                    message = "Crop, rotate, flip, color grading, filters & text overlays run locally with zero credit cost.",
                    isOperational = true
                )
            }

            item {
                GlassCard {
                    if (editedBitmap != null) {
                        Image(
                            bitmap = editedBitmap.asImageBitmap(),
                            contentDescription = "Edited photo preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Button(
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("pick_photo_editor_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (editedBitmap == null) "Select Photo to Edit" else "Change Photo")
                    }
                }
            }

            if (editedBitmap != null) {
                item {
                    GlassCard {
                        Text("Transform & Crop", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { rotation = (rotation + 90) % 360 },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Filled.RotateRight, null, tint = LuminousCyan)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Rotate", color = TextPrimary)
                            }
                            OutlinedButton(
                                onClick = { flipH = !flipH },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Filled.Flip, null, tint = LuminousCyan)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Flip", color = TextPrimary)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CropRatio.entries.forEach { ratio ->
                                FilterChip(
                                    selected = cropRatio == ratio,
                                    onClick = { cropRatio = ratio },
                                    label = { Text(ratio.label) }
                                )
                            }
                        }
                    }
                }

                item {
                    GlassCard {
                        Text("Color Grading & Filters", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PhotoFilterPreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = selectedFilter == preset,
                                    onClick = { selectedFilter = preset },
                                    label = { Text(preset.label) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Brightness (${brightness.toInt()})", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        Slider(
                            value = brightness,
                            onValueChange = { brightness = it },
                            valueRange = -80f..80f,
                            colors = SliderDefaults.colors(thumbColor = LuminousCyan, activeTrackColor = ElectricBlue)
                        )
                        Text("Contrast (${"%.2f".format(contrast)}x)", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        Slider(
                            value = contrast,
                            onValueChange = { contrast = it },
                            valueRange = 0.5f..1.8f,
                            colors = SliderDefaults.colors(thumbColor = LuminousCyan, activeTrackColor = ElectricBlue)
                        )
                        Text("Saturation (${"%.2f".format(saturation)}x)", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        Slider(
                            value = saturation,
                            onValueChange = { saturation = it },
                            valueRange = 0f..2f,
                            colors = SliderDefaults.colors(thumbColor = LuminousCyan, activeTrackColor = ElectricBlue)
                        )
                    }
                }

                item {
                    GlassCard {
                        Text("Text Overlay & Export", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = overlayText,
                            onValueChange = { overlayText = it },
                            label = { Text("Watermark or caption overlay") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    val res = ExportUtils.saveBitmapToGallery(context, editedBitmap, "cerevix_edit")
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
                                Text("Save")
                            }
                            OutlinedButton(
                                onClick = { ExportUtils.shareBitmap(context, editedBitmap, "Edited Photo") },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Filled.Share, null, tint = LuminousCyan)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share", color = TextPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}
