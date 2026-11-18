package com.example.ui.screens

import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.UiState
import com.example.data.model.AiModelCatalog
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.SenderRole
import com.example.ui.components.CreditBalanceChip
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusBanner
import com.example.ui.theme.BorderGlass
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.ChatAttachment
import com.example.ui.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    credits: Long,
    planName: String,
    accountSuspended: Boolean
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val conversationsState by viewModel.conversationsState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val activeConvId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val selectedModelId by viewModel.selectedModelId.collectAsStateWithLifecycle()
    val streamingState by viewModel.streamingState.collectAsStateWithLifecycle()
    val pendingAttachment by viewModel.pendingAttachment.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    var promptText by remember { mutableStateOf("") }
    var showConversationsSheet by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Conversation?>(null) }
    var renameInput by remember { mutableStateOf("") }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val bitmap = try {
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
            if (bitmap != null) {
                viewModel.setAttachment(ChatAttachment(label = "Image attached", bitmap = bitmap))
            } else {
                Toast.makeText(context, "Could not decode selected image.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri) ?: "application/pdf"
            var name = "Document"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) name = cursor.getString(idx)
            }
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            when {
                bytes == null -> Toast.makeText(context, "Could not read selected file.", Toast.LENGTH_SHORT).show()
                bytes.size > 18 * 1024 * 1024 -> Toast.makeText(context, "File exceeds 18 MB limit.", Toast.LENGTH_SHORT).show()
                else -> viewModel.setAttachment(
                    ChatAttachment(
                        label = name,
                        documentBytes = bytes,
                        mimeType = mime
                    )
                )
            }
        }
    }

    val listState = rememberLazyListState()
    val messages = (messagesState as? UiState.Success)?.data ?: emptyList()

    val isStreamingNow = streamingState is UiState.Streaming
    LaunchedEffect(messages.size, isStreamingNow) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Chat Top Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = { showConversationsSheet = true },
                shape = RoundedCornerShape(12.dp),
                color = SlateSurface,
                border = BorderStroke(1.dp, BorderGlass),
                modifier = Modifier
                    .weight(1f)
                    .testTag("open_conversations_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Forum, contentDescription = "Chats", tint = LuminousCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    val convTitle = (conversationsState as? UiState.Success)?.data
                        ?.firstOrNull { it.id == activeConvId }?.title ?: "New Conversation"
                    Text(
                        text = convTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { viewModel.createNewConversation() },
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ElectricBlue)
                    .testTag("new_chat_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New chat", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            CreditBalanceChip(credits = credits, planName = planName)
        }

        // Model Picker Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AiModelCatalog.textModels.forEach { model ->
                val selected = model.id == selectedModelId
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.selectModel(model.id) },
                    label = {
                        Text(
                            text = "${model.label} (${model.creditCost}⚡)",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricBlue.copy(alpha = 0.28f),
                        selectedLabelColor = LuminousCyan,
                        containerColor = SlateSurface,
                        labelColor = TextSecondary
                    )
                )
            }
        }

        if (streamingState is UiState.Error) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                StatusBanner(
                    title = "AI Chat Error",
                    message = (streamingState as UiState.Error).message,
                    isError = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (messages.any { it.resolvedSenderRole() == SenderRole.user }) {
                        TextButton(onClick = { viewModel.regenerateLastResponse(credits, accountSuspended) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = LuminousCyan)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry", color = LuminousCyan)
                        }
                    }
                    TextButton(onClick = { viewModel.dismissError() }) {
                        Text("Dismiss", color = TextSecondary)
                    }
                }
            }
        }

        // Message Stream
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.isEmpty() && streamingState !is UiState.Streaming && streamingState !is UiState.Loading) {
                item {
                    GlassCard {
                        Text(
                            text = "Ask CEREVIX Intelligence",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Start a multi-turn conversation, attach an image for vision understanding, or upload a PDF document for deep analysis.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                ChatMessageBubble(
                    message = msg,
                    isLastModelMessage = msg.id == messages.lastOrNull { it.resolvedSenderRole() == SenderRole.model }?.id,
                    onCopy = { clipboard.setText(AnnotatedString(msg.content)) },
                    onRegenerate = { viewModel.regenerateLastResponse(credits, accountSuspended) }
                )
            }

            if (streamingState is UiState.Streaming) {
                val partial = (streamingState as UiState.Streaming).partialText
                item(key = "streaming_bubble") {
                    ChatMessageBubble(
                        message = ChatMessage(
                            id = "streaming",
                            senderRole = SenderRole.model.name,
                            content = partial,
                            modelId = selectedModelId
                        ),
                        isLastModelMessage = false,
                        onCopy = {},
                        onRegenerate = {}
                    )
                }
            } else if (streamingState is UiState.Loading) {
                item(key = "loading_bubble") {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = LuminousCyan,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Thinking with $selectedModelId...", style = MaterialTheme.typography.bodySmall, color = LuminousCyan)
                    }
                }
            }
        }

        // Attachment Preview Chip
        if (pendingAttachment != null) {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = ElectricBlue.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, LuminousCyan.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Attached: ${pendingAttachment?.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = LuminousCyan,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.setAttachment(null) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove attachment", tint = TextPrimary)
                    }
                }
            }
        }

        // Composer Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MidnightNavy)
                .border(1.dp, BorderGlass)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Filled.Image, contentDescription = "Attach image", tint = LuminousCyan)
            }
            IconButton(
                onClick = { documentPicker.launch(arrayOf("application/pdf", "text/*")) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Filled.AttachFile, contentDescription = "Attach PDF or document", tint = TextSecondary)
            }
            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it },
                placeholder = { Text("Message Cerevix AI...", color = TextSecondary) },
                maxLines = 4,
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input"),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LuminousCyan,
                    unfocusedBorderColor = BorderGlass,
                    focusedContainerColor = ObsidianBg,
                    unfocusedContainerColor = ObsidianBg,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            val isBusy = streamingState is UiState.Loading || streamingState is UiState.Streaming
            val selectedModelOpt = AiModelCatalog.textModels.firstOrNull { it.id == selectedModelId }
                ?: AiModelCatalog.DEFAULT_TEXT_MODEL
            IconButton(
                onClick = {
                    if (!isBusy) {
                        val toSend = promptText
                        if (!accountSuspended && credits >= selectedModelOpt.creditCost) {
                            promptText = ""
                        }
                        viewModel.sendMessage(toSend, credits, accountSuspended)
                    }
                },
                enabled = !isBusy && (promptText.isNotBlank() || pendingAttachment != null),
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (!isBusy) ElectricBlue else SlateSurface)
                    .testTag("chat_send_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message", tint = Color.White)
            }
        }
    }

    // Conversations Sheet
    if (showConversationsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showConversationsSheet = false },
            containerColor = MidnightNavy,
            contentColor = TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Conversations", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    TextButton(onClick = {
                        viewModel.createNewConversation()
                        showConversationsSheet = false
                    }) {
                        Text("+ New Chat", color = LuminousCyan)
                    }
                }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary) },
                    placeholder = { Text("Search chat history...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                val convList = (conversationsState as? UiState.Success)?.data ?: emptyList()
                LazyColumn(
                    modifier = Modifier.height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(convList, key = { it.id }) { conv ->
                        val isActive = conv.id == activeConvId
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectConversation(conv.id)
                                    showConversationsSheet = false
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isActive) ElectricBlue.copy(alpha = 0.22f) else SlateSurface,
                            border = BorderStroke(1.dp, if (isActive) LuminousCyan else BorderGlass)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = conv.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = conv.lastMessagePreview,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        renameTarget = conv
                                        renameInput = conv.title
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = "Rename chat", tint = TextSecondary)
                                }
                                IconButton(
                                    onClick = { viewModel.deleteConversation(conv.id) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete chat", tint = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = MidnightNavy,
            title = { Text("Rename Conversation", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = renameTarget
                    if (target != null && renameInput.isNotBlank()) {
                        viewModel.renameConversation(target.id, renameInput)
                    }
                    renameTarget = null
                }) {
                    Text("Save", color = LuminousCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    isLastModelMessage: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit
) {
    val isUser = message.resolvedSenderRole() == SenderRole.user
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            color = if (isUser) ElectricBlue.copy(alpha = 0.28f) else ElevatedCard,
            border = BorderStroke(
                1.dp,
                if (isUser) ElectricBlue.copy(alpha = 0.6f) else BorderGlass
            ),
            modifier = Modifier.fillMaxWidth(if (isUser) 0.88f else 0.96f)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "You" else "CEREVIX • ${message.modelId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) TextPrimary else LuminousCyan,
                        fontWeight = FontWeight.Bold
                    )
                    if (!message.attachmentLabel.isNullOrBlank()) {
                        Text(
                            text = message.attachmentLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = LuminousCyan
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                FormattedAiContent(message.content)

                if (!isUser) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onCopy) {
                            Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(15.dp), tint = TextSecondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        }
                        if (isLastModelMessage) {
                            TextButton(onClick = onRegenerate) {
                                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(15.dp), tint = LuminousCyan)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Regenerate", style = MaterialTheme.typography.labelMedium, color = LuminousCyan)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FormattedAiContent(text: String) {
    // Splits markdown fenced code blocks for clean monospace rendering
    val segments = remember(text) {
        val list = mutableListOf<Pair<Boolean, String>>()
        val parts = text.split("```")
        parts.forEachIndexed { idx, part ->
            if (part.isNotBlank()) {
                val isCode = idx % 2 == 1
                list.add(isCode to part.trim())
            }
        }
        list
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (segments.isEmpty()) {
            Text(text = text, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
        } else {
            segments.forEach { (isCode, segment) ->
                if (isCode) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ObsidianBg)
                            .border(1.dp, BorderGlass, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = segment,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFont),
                            color = LuminousCyan
                        )
                    }
                } else {
                    Text(
                        text = segment,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}
