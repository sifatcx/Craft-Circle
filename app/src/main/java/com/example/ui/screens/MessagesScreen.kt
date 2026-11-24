package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.Conversation
import com.example.model.MakerPublicProfile
import com.example.model.UiState
import com.example.ui.components.CraftBadge
import com.example.ui.components.EmptyAtelierCard
import com.example.ui.components.MakerAvatar
import com.example.util.ImageAndFormatUtils
import com.example.viewmodel.CraftViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MessagesScreen(
    viewModel: CraftViewModel,
    showNewChatSheet: Boolean,
    onDismissNewChatSheet: () -> Unit,
    onOpenNewChatSheet: () -> Unit,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val conversationsState by viewModel.conversationsState.collectAsStateWithLifecycle()
    val makersState by viewModel.makersState.collectAsStateWithLifecycle()
    val myUid = viewModel.currentUserId

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        when (val state = conversationsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                EmptyAtelierCard(
                    title = "Could not load messages",
                    subtitle = state.message
                )
            }
            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 600.dp)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        EmptyAtelierCard(
                            title = "No messages yet",
                            subtitle = "Chat with fellow hobbyists about build techniques, tools, or upcoming workshops."
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onOpenNewChatSheet,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("start_new_chat_button")
                        ) {
                            Icon(Icons.Outlined.AddComment, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Message a Maker")
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 680.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.data, key = { it.id }) { convo ->
                            ConversationRowCard(
                                conversation = convo,
                                myUid = myUid,
                                onClick = { onOpenConversation(convo.id) }
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }

    if (showNewChatSheet) {
        var searchQuery by remember { mutableStateOf("") }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = onDismissNewChatSheet,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Message a Maker",
                    style = MaterialTheme.typography.headlineSmall
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by name or craft...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_makers_input")
                )

                when (val mState = makersState) {
                    is UiState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                    }
                    is UiState.Error -> {
                        Text(
                            text = mState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    is UiState.Success -> {
                        val filtered = remember(mState.data, searchQuery) {
                            val q = searchQuery.trim()
                            if (q.isEmpty()) mState.data
                            else mState.data.filter { maker ->
                                maker.displayName.contains(q, ignoreCase = true) ||
                                    maker.specialties.any { it.contains(q, ignoreCase = true) }
                            }
                        }

                        if (filtered.isEmpty()) {
                            EmptyAtelierCard(
                                title = "No other makers found yet",
                                subtitle = "Once another hobbyist signs in to CraftCircle, they will appear here ready to chat!"
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(340.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filtered, key = { it.userId }) { maker ->
                                    MakerDirectoryRow(
                                        maker = maker,
                                        onSelect = {
                                            viewModel.openDirectChat(
                                                otherUserId = maker.userId,
                                                otherName = maker.displayName,
                                                otherPhotoUrl = maker.photoUrl
                                            ) { convoId ->
                                                onDismissNewChatSheet()
                                                onOpenConversation(convoId)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ConversationRowCard(
    conversation: Conversation,
    myUid: String,
    onClick: () -> Unit
) {
    val otherName = conversation.otherParticipantName(myUid)
    val otherPhoto = conversation.otherParticipantPhoto(myUid)
    val preview = when {
        conversation.lastMessageText.isBlank() -> "Tap to say hello"
        conversation.lastSenderId == myUid -> "You: ${conversation.lastMessageText}"
        else -> conversation.lastMessageText
    }
    val timeText = ImageAndFormatUtils.formatRelativeTime(
        conversation.lastMessageAt ?: conversation.createdAt
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("conversation_card_${conversation.id}"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MakerAvatar(
                name = otherName,
                photoUrl = otherPhoto,
                size = 48.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = otherName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MakerDirectoryRow(
    maker: MakerPublicProfile,
    onSelect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("maker_directory_row_${maker.userId}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MakerAvatar(
                name = maker.displayName,
                photoUrl = maker.photoUrl,
                size = 44.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = maker.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (maker.bio.isNotBlank()) {
                    Text(
                        text = maker.bio,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (maker.specialties.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        maker.specialties.take(3).forEach { spec ->
                            CraftBadge(text = spec)
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Outlined.ChatBubbleOutline,
                contentDescription = "Start chat with ${maker.displayName}",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
