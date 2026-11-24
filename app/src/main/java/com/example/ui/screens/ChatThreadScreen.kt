package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.Conversation
import com.example.model.DirectMessage
import com.example.model.UiState
import com.example.ui.components.EmptyAtelierCard
import com.example.ui.components.MakerAvatar
import com.example.util.ImageAndFormatUtils
import com.example.viewmodel.CraftViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatThreadScreen(
    conversationId: String,
    viewModel: CraftViewModel,
    onNavigateBack: () -> Unit
) {
    val conversationState by viewModel.observeConversation(conversationId)
        .collectAsStateWithLifecycle(initialValue = UiState.Loading)
    val messagesState by viewModel.observeMessages(conversationId)
        .collectAsStateWithLifecycle(initialValue = UiState.Loading)

    val myUid = viewModel.currentUserId
    val conversation = (conversationState as? UiState.Success<Conversation?>)?.data
    val otherName = conversation?.otherParticipantName(myUid) ?: "Maker Chat"
    val otherPhoto = conversation?.otherParticipantPhoto(myUid)

    var messageDraft by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val messageList = (messagesState as? UiState.Success<List<DirectMessage>>)?.data.orEmpty()
    LaunchedEffect(messageList.size) {
        if (messageList.isNotEmpty()) {
            listState.animateScrollToItem(messageList.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MakerAvatar(
                            name = otherName,
                            photoUrl = otherPhoto,
                            size = 34.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = otherName,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to messages"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (conversation != null) {
                Surface(
                    tonalElevation = 2.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = messageDraft,
                            onValueChange = { if (it.length <= 1000) messageDraft = it },
                            placeholder = { Text("Message $otherName...") },
                            maxLines = 4,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_message_input")
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(
                            onClick = {
                                val text = messageDraft.trim()
                                if (text.isNotEmpty() && !isSending) {
                                    isSending = true
                                    viewModel.sendMessage(
                                        conversationId = conversationId,
                                        participantIds = conversation.participantIds,
                                        text = text
                                    ) { ok ->
                                        isSending = false
                                        if (ok) messageDraft = ""
                                    }
                                }
                            },
                            enabled = messageDraft.isNotBlank() && !isSending,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("send_message_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send message"
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when (val mState = messagesState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    EmptyAtelierCard(title = "Could not load chat", subtitle = mState.message)
                }
                is UiState.Success -> {
                    if (mState.data.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyAtelierCard(
                                title = "Say hello to $otherName",
                                subtitle = "Ask about a project, swap tool recommendations, or plan a craft collab."
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 680.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(mState.data, key = { it.id }) { msg ->
                                MessageBubble(
                                    message = msg,
                                    isMine = msg.senderId == myUid
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: DirectMessage,
    isMine: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isMine) 18.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 18.dp
            ),
            color = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            border = if (isMine) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = ImageAndFormatUtils.formatRelativeTime(message.createdAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}
