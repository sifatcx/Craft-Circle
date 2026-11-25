package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.CoverPreset
import com.example.model.CraftProject
import com.example.model.ProjectBookmark
import com.example.model.ProjectComment
import com.example.model.ProjectProgressUpdate
import com.example.model.ProjectUpdateStatus
import com.example.model.TutorialStep
import com.example.model.UiState
import com.example.ui.components.CraftBadge
import com.example.ui.components.EmptyAtelierCard
import com.example.ui.components.MakerAvatar
import com.example.ui.components.ProjectCoverArt
import com.example.ui.components.rememberMediaPickerController
import com.example.ui.theme.SuccessGreen
import com.example.util.ImageAndFormatUtils
import com.example.viewmodel.CraftViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProjectDetailScreen(
    projectId: String,
    viewModel: CraftViewModel,
    onNavigateBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenAuthor: (String) -> Unit = {},
    onEditProject: (String) -> Unit = {}
) {
    val projectState by viewModel.observeProject(projectId)
        .collectAsStateWithLifecycle(initialValue = UiState.Loading)
    val commentsState by viewModel.observeProjectComments(projectId)
        .collectAsStateWithLifecycle(initialValue = UiState.Loading)
    val isLiked by viewModel.observeIsProjectLiked(projectId)
        .collectAsStateWithLifecycle(initialValue = false)
    val bookmarksState by viewModel.bookmarksState.collectAsStateWithLifecycle()
    val currentUserProfile by viewModel.userProfile.collectAsStateWithLifecycle()

    val isBookmarked = remember(bookmarksState, projectId) {
        (bookmarksState as? UiState.Success<List<ProjectBookmark>>)?.data?.any { it.projectId == projectId } == true
    }

    val completedSteps = remember { mutableStateMapOf<Long, Boolean>() }
    var commentDraft by remember { mutableStateOf("") }
    var isSendingComment by remember { mutableStateOf(false) }
    var pendingDeleteComment by remember { mutableStateOf<ProjectComment?>(null) }
    var pendingDeleteUpdate by remember { mutableStateOf<ProjectProgressUpdate?>(null) }
    var showPostUpdateSheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val loadedComments = (commentsState as? UiState.Success<List<ProjectComment>>)?.data
    val loadedProject = (projectState as? UiState.Success<CraftProject?>)?.data
    val displayedCommentsCount = loadedComments?.size ?: (loadedProject?.commentsCount?.toInt() ?: 0)

    fun submitComment(project: CraftProject) {
        val text = commentDraft.trim()
        if (text.isNotEmpty() && !isSendingComment) {
            isSendingComment = true
            viewModel.addComment(project, text) { ok ->
                isSendingComment = false
                if (ok) {
                    commentDraft = ""
                    focusManager.clearFocus()
                    coroutineScope.launch {
                        val lastIndex = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                        listState.animateScrollToItem(lastIndex)
                    }
                }
            }
        }
    }

    pendingDeleteComment?.let { commentToDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeleteComment = null },
            title = { Text("Delete comment?") },
            text = { Text("This comment will be permanently removed from this project.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteComment(projectId, commentToDelete.id)
                        pendingDeleteComment = null
                    },
                    modifier = Modifier.testTag("confirm_delete_comment_button")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteComment = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    pendingDeleteUpdate?.let { updateToDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeleteUpdate = null },
            title = { Text("Remove progress update?") },
            text = { Text("This update will be permanently removed from your project timeline.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProjectUpdate(projectId, updateToDelete.id)
                        pendingDeleteUpdate = null
                    },
                    modifier = Modifier.testTag("confirm_delete_update_button")
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteUpdate = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Project Details",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to feed"
                        )
                    }
                },
                actions = {
                    if (loadedProject != null) {
                        IconButton(
                            onClick = { viewModel.toggleBookmark(loadedProject, isBookmarked) },
                            modifier = Modifier.testTag("detail_bookmark_button")
                        ) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = if (isBookmarked) "Remove bookmark" else "Bookmark project",
                                tint = if (isBookmarked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (loadedProject.authorId == viewModel.currentUserId) {
                            IconButton(
                                onClick = { onEditProject(loadedProject.id) },
                                modifier = Modifier.testTag("detail_edit_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "Edit project",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = { viewModel.deleteProject(loadedProject.id, onNavigateBack) },
                                modifier = Modifier.testTag("detail_delete_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = "Delete post",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (loadedProject != null) {
                Surface(
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .testTag("comment_composer_bar")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MakerAvatar(
                                name = currentUserProfile?.displayName?.takeIf { it.isNotBlank() } ?: "Maker",
                                photoUrl = currentUserProfile?.photoUrl,
                                size = 36.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            OutlinedTextField(
                                value = commentDraft,
                                onValueChange = { if (it.length <= 500) commentDraft = it },
                                placeholder = {
                                    Text(
                                        text = "Write a comment...",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("comment_input"),
                                minLines = 1,
                                maxLines = 3,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.background,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.background
                                ),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Send
                                ),
                                keyboardActions = KeyboardActions(
                                    onSend = { submitComment(loadedProject) }
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            FilledIconButton(
                                onClick = { submitComment(loadedProject) },
                                enabled = commentDraft.isNotBlank() && !isSendingComment,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("send_comment_button")
                            ) {
                                if (isSendingComment) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Post comment"
                                    )
                                }
                            }
                        }
                        if (commentDraft.length >= 400) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${commentDraft.length}/500",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.End)
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
            when (val state = projectState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    EmptyAtelierCard(title = "Unable to open post", subtitle = state.message)
                }
                is UiState.Success -> {
                    val project = state.data
                    if (project == null) {
                        EmptyAtelierCard(
                            title = "Post not found",
                            subtitle = "This project may have been removed by its maker."
                        )
                    } else {
                        val isOwner = project.authorId == viewModel.currentUserId
                        val updatesFlow = remember(project.id, isOwner) {
                            viewModel.observeProjectUpdates(project.id, isOwner)
                        }
                        val updatesState by updatesFlow.collectAsStateWithLifecycle(initialValue = UiState.Loading)
                        val loadedUpdates = (updatesState as? UiState.Success<List<ProjectProgressUpdate>>)?.data.orEmpty()
                        val updatesItemCount = when (updatesState) {
                            is UiState.Success -> if (loadedUpdates.isEmpty()) 1 else loadedUpdates.size
                            else -> 1
                        }
                        val updatesHeaderIndex = remember(project.materials.size, project.steps.size) {
                            1 +
                                (if (project.materials.isNotEmpty()) 1 else 0) +
                                (if (project.steps.isNotEmpty()) 1 + project.steps.size else 0)
                        }
                        val commentsHeaderIndex = updatesHeaderIndex + 1 + updatesItemCount

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 680.dp)
                                .testTag("project_detail_list"),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            item(key = "header") {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .then(
                                                    if (project.authorId.isNotBlank()) {
                                                        Modifier.clickable { onOpenAuthor(project.authorId) }
                                                    } else Modifier
                                                ),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            MakerAvatar(
                                                name = project.authorName,
                                                photoUrl = project.authorPhotoUrl,
                                                size = 44.dp
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = project.authorName,
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                                Text(
                                                    text = "${ImageAndFormatUtils.formatMakerUsername(project.authorName)} · ${ImageAndFormatUtils.formatRelativeTime(project.createdAt)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        if (project.authorId != viewModel.currentUserId && project.authorId.isNotBlank()) {
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.openDirectChat(
                                                        otherUserId = project.authorId,
                                                        otherName = project.authorName,
                                                        otherPhotoUrl = project.authorPhotoUrl,
                                                        onReady = onOpenConversation
                                                    )
                                                },
                                                modifier = Modifier.testTag("detail_message_author_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.MailOutline,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text("Message")
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(14.dp))

                                    Text(
                                        text = project.title,
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )

                                    if (project.steps.isNotEmpty() || project.estimatedTime.isNotBlank()) {
                                        Spacer(Modifier.height(8.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CraftBadge(
                                                text = project.difficulty,
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (project.estimatedTime.isNotBlank()) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Schedule,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        text = project.estimatedTime,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (!project.coverImageData.isNullOrBlank() || project.coverPreset != "none") {
                                        Spacer(Modifier.height(12.dp))
                                        ProjectCoverArt(
                                            coverPreset = project.coverPreset,
                                            coverImageData = project.coverImageData,
                                            title = project.title,
                                            height = 220.dp
                                        )
                                    }

                                    Spacer(Modifier.height(12.dp))

                                    Text(
                                        text = project.summary,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )

                                    Spacer(Modifier.height(14.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { viewModel.toggleLike(project, isLiked) },
                                            contentPadding = PaddingValues(horizontal = 10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("detail_like_button"),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                            )
                                        ) {
                                            Icon(
                                                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                                contentDescription = null,
                                                tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(5.dp))
                                            Text(
                                                text = "${project.effectiveLikesCount}",
                                                maxLines = 1
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    listState.animateScrollToItem(updatesHeaderIndex)
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp),
                                            modifier = Modifier
                                                .weight(1.2f)
                                                .height(48.dp)
                                                .testTag("detail_jump_to_updates_button"),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Timeline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(5.dp))
                                            Text(
                                                text = "Updates (${loadedUpdates.size})",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    listState.animateScrollToItem(commentsHeaderIndex)
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp),
                                            modifier = Modifier
                                                .weight(1.2f)
                                                .height(48.dp)
                                                .testTag("detail_jump_to_comments_button"),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.ChatBubbleOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(5.dp))
                                            Text(
                                                text = "Notes ($displayedCommentsCount)",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            if (project.materials.isNotEmpty()) {
                                item(key = "materials") {
                                    Surface(
                                        shape = MaterialTheme.shapes.large,
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = "Materials & Tools",
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Spacer(Modifier.height(10.dp))
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                project.materials.forEach { item ->
                                                    CraftBadge(
                                                        text = item,
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                        contentColor = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (project.steps.isNotEmpty()) {
                                item(key = "steps_header") {
                                    Text(
                                        text = "Step-by-Step DIY Guide",
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                }
                                items(project.steps, key = { "step_${it.stepNumber}_${it.title}" }) { step ->
                                    val done = completedSteps[step.stepNumber] == true
                                    TutorialStepCard(
                                        step = step,
                                        completed = done,
                                        onToggle = { completedSteps[step.stepNumber] = !done }
                                    )
                                }
                            }

                            // --- CHRONOLOGICAL PROJECT UPDATE FEED ---
                            item(key = "updates_header") {
                                Column(modifier = Modifier.testTag("updates_section_header")) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Spacer(Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Timeline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = "Update Feed (${loadedUpdates.size})",
                                                    style = MaterialTheme.typography.titleLarge
                                                )
                                                Text(
                                                    text = "Chronological build log & progress photos",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        if (isOwner) {
                                            FilledTonalButton(
                                                onClick = { showPostUpdateSheet = true },
                                                modifier = Modifier
                                                    .height(40.dp)
                                                    .testTag("add_progress_update_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Add,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text("Post Update")
                                            }
                                        }
                                    }
                                }
                            }

                            when (val uState = updatesState) {
                                is UiState.Loading -> {
                                    item(key = "updates_loading") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        }
                                    }
                                }
                                is UiState.Error -> {
                                    item(key = "updates_error") {
                                        Surface(
                                            shape = MaterialTheme.shapes.medium,
                                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = uState.message,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(14.dp)
                                            )
                                        }
                                    }
                                }
                                is UiState.Success -> {
                                    if (uState.data.isEmpty()) {
                                        item(key = "updates_empty") {
                                            Surface(
                                                shape = MaterialTheme.shapes.medium,
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("updates_empty_state")
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(18.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Timeline,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(26.dp)
                                                    )
                                                    Spacer(Modifier.height(6.dp))
                                                    Text(
                                                        text = "No progress updates yet",
                                                        style = MaterialTheme.typography.titleSmall
                                                    )
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(
                                                        text = if (isOwner) {
                                                            "Document your build milestones with progress photos and status notes."
                                                        } else {
                                                            "The maker hasn't posted chronological progress updates yet."
                                                        },
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    if (isOwner) {
                                                        Spacer(Modifier.height(12.dp))
                                                        FilledTonalButton(
                                                            onClick = { showPostUpdateSheet = true },
                                                            modifier = Modifier.testTag("empty_post_update_button")
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Outlined.AddPhotoAlternate,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Spacer(Modifier.width(6.dp))
                                                            Text("Post First Update")
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        itemsIndexed(
                                            items = uState.data,
                                            key = { _, update -> "update_${update.id}" }
                                        ) { index, update ->
                                            ProjectUpdateTimelineRow(
                                                stepNumber = index + 1,
                                                isLast = index == uState.data.lastIndex,
                                                update = update,
                                                isOwnUpdate = update.authorId == viewModel.currentUserId,
                                                onDelete = { pendingDeleteUpdate = update }
                                            )
                                        }
                                    }
                                }
                            }

                            item(key = "comments_header") {
                                Column(modifier = Modifier.testTag("comments_section_header")) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Spacer(Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Outlined.ChatBubbleOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "Comments ($displayedCommentsCount)",
                                                style = MaterialTheme.typography.titleLarge
                                            )
                                        }
                                        Text(
                                            text = "Community discussion",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            when (val cState = commentsState) {
                                is UiState.Loading -> {
                                    item(key = "comments_loading") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        }
                                    }
                                }
                                is UiState.Error -> {
                                    item(key = "comments_error") {
                                        Surface(
                                            shape = MaterialTheme.shapes.medium,
                                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = cState.message,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(14.dp)
                                            )
                                        }
                                    }
                                }
                                is UiState.Success -> {
                                    if (cState.data.isEmpty()) {
                                        item(key = "comments_empty") {
                                            Surface(
                                                shape = MaterialTheme.shapes.medium,
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("comments_empty_state")
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(20.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.ChatBubbleOutline,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                    Spacer(Modifier.height(8.dp))
                                                    Text(
                                                        text = "No comments yet",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(
                                                        text = "Be the first to share encouragement or ask a question below.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        items(cState.data, key = { it.id }) { comment ->
                                            CommentRow(
                                                comment = comment,
                                                isProjectCreator = comment.authorId == project.authorId && project.authorId.isNotBlank(),
                                                isOwnComment = comment.authorId == viewModel.currentUserId,
                                                onOpenAuthor = if (comment.authorId.isNotBlank()) {
                                                    { onOpenAuthor(comment.authorId) }
                                                } else null,
                                                onDelete = { pendingDeleteComment = comment }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (showPostUpdateSheet) {
                            val detailContext = LocalContext.current
                            ModalBottomSheet(
                                onDismissRequest = { showPostUpdateSheet = false },
                                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                            ) {
                                PostProgressUpdateSheetContent(
                                    onDismiss = { showPostUpdateSheet = false },
                                    onSubmit = { statusLabel, message, selectedPhotoUri, onDone ->
                                        viewModel.addProjectUpdate(
                                            context = detailContext,
                                            project = project,
                                            statusLabel = statusLabel,
                                            message = message,
                                            selectedPhotoUri = selectedPhotoUri
                                        ) { ok, errMsg ->
                                            onDone(ok, errMsg)
                                            if (ok) {
                                                showPostUpdateSheet = false
                                                coroutineScope.launch {
                                                    listState.animateScrollToItem(updatesHeaderIndex)
                                                }
                                            }
                                        }
                                    }
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
private fun ProjectUpdateTimelineRow(
    stepNumber: Int,
    isLast: Boolean,
    update: ProjectProgressUpdate,
    isOwnUpdate: Boolean,
    onDelete: () -> Unit
) {
    val isCompletedStatus = update.statusLabel.equals(ProjectUpdateStatus.COMPLETED.label, ignoreCase = true)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .testTag("project_update_item_${update.id}")
    ) {
        // Chronological vertical timeline rail
        Column(
            modifier = Modifier
                .width(30.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = if (isCompletedStatus) SuccessGreen else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "$stepNumber",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .padding(top = 4.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        CraftBadge(
                            text = update.statusLabel,
                            containerColor = if (isCompletedStatus) {
                                SuccessGreen.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                            contentColor = if (isCompletedStatus) {
                                SuccessGreen
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        )
                        Text(
                            text = ImageAndFormatUtils.formatRelativeTime(update.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isOwnUpdate) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("delete_update_${update.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Remove update",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = update.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!update.photoData.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    ProjectCoverArt(
                        coverPreset = CoverPreset.NONE.key,
                        coverImageData = update.photoData,
                        title = "Progress update #$stepNumber",
                        height = 180.dp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostProgressUpdateSheetContent(
    onDismiss: () -> Unit,
    onSubmit: (statusLabel: String, message: String, selectedPhotoUri: Uri?, onDone: (Boolean, String?) -> Unit) -> Unit
) {
    var selectedStatus by remember { mutableStateOf(ProjectUpdateStatus.IN_PROGRESS) }
    var message by remember { mutableStateOf("") }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val mediaPicker = rememberMediaPickerController(
        onImagePicked = { uri ->
            selectedPhotoUri = uri
            errorMessage = null
        },
        onPermissionDenied = { msg ->
            errorMessage = msg
        }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
            .imePadding()
            .testTag("post_update_sheet"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Post Progress Update",
                style = MaterialTheme.typography.titleLarge
            )
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }

        Text(
            text = "Build Status",
            style = MaterialTheme.typography.labelLarge
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ProjectUpdateStatus.entries.forEach { status ->
                FilterChip(
                    selected = selectedStatus == status,
                    onClick = { selectedStatus = status },
                    label = { Text(status.label) },
                    modifier = Modifier.testTag("update_status_chip_${status.name}")
                )
            }
        }

        OutlinedTextField(
            value = message,
            onValueChange = {
                if (it.length <= 600) {
                    message = it
                    if (errorMessage != null) errorMessage = null
                }
            },
            label = { Text("Status Update") },
            placeholder = { Text("Share what you accomplished, tools used, or next steps...") },
            minLines = 3,
            maxLines = 5,
            supportingText = { Text("${message.length}/600") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("update_message_input")
        )

        // Optional Progress Photo (Camera or Gallery -> Firebase Storage)
        Text(
            text = "Progress Photo (Optional)",
            style = MaterialTheme.typography.labelLarge
        )
        if (selectedPhotoUri != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
            ) {
                ProjectCoverArt(
                    coverPreset = CoverPreset.NONE.key,
                    coverImageData = selectedPhotoUri.toString(),
                    title = "Progress photo preview",
                    height = 170.dp,
                    allowLocalPreviewUri = true
                )
                FilledTonalButton(
                    onClick = { selectedPhotoUri = null },
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .testTag("update_clear_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Remove progress photo",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Remove")
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { mediaPicker.captureWithCamera() },
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("update_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoCamera,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Camera")
                }
                OutlinedButton(
                    onClick = { mediaPicker.pickFromGallery() },
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("update_pick_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Gallery")
                }
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Button(
            onClick = {
                val trimmed = message.trim()
                if (trimmed.isEmpty()) {
                    errorMessage = "Please describe your progress update."
                    return@Button
                }
                isSubmitting = true
                onSubmit(selectedStatus.label, trimmed, selectedPhotoUri) { ok, errMsg ->
                    isSubmitting = false
                    if (!ok) {
                        errorMessage = errMsg ?: "Could not post progress update. Try again."
                    }
                }
            },
            enabled = message.isNotBlank() && !isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("submit_update_button")
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text(if (selectedPhotoUri != null) "Uploading photo..." else "Posting...")
            } else {
                Text("Post Update")
            }
        }
    }
}

@Composable
private fun TutorialStepCard(
    step: TutorialStep,
    completed: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = MaterialTheme.shapes.medium,
        color = if (completed) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (completed) SuccessGreen else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = CircleShape,
                color = if (completed) SuccessGreen else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (completed) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Step completed",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            text = "${step.stepNumber}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.titleSmall,
                        textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None
                    )
                    Icon(
                        imageVector = if (completed) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = if (completed) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = step.instruction,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (step.tip.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lightbulb,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Tip: ${step.tip}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: ProjectComment,
    isProjectCreator: Boolean,
    isOwnComment: Boolean,
    onOpenAuthor: (() -> Unit)?,
    onDelete: () -> Unit
) {
    val commenterName = comment.authorName.trim().ifBlank { "Creative Maker" }
    val commenterUsername = remember(commenterName) {
        ImageAndFormatUtils.formatMakerUsername(commenterName)
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("comment_item_${comment.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = if (isOwnComment) 4.dp else 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = if (onOpenAuthor != null) Modifier.clickable(onClick = onOpenAuthor) else Modifier
            ) {
                MakerAvatar(
                    name = commenterName,
                    photoUrl = comment.authorPhotoUrl,
                    size = 36.dp
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (onOpenAuthor != null) Modifier.clickable(onClick = onOpenAuthor) else Modifier
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = commenterName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isProjectCreator) {
                            CraftBadge(
                                text = "Creator",
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Text(
                        text = ImageAndFormatUtils.formatRelativeTime(comment.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = commenterUsername,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = comment.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (isOwnComment) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("delete_comment_${comment.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete comment",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
