package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.CraftProject
import com.example.model.FeedTab
import com.example.model.MakerPublicProfile
import com.example.model.PostType
import com.example.model.ProjectBookmark
import com.example.model.UiState
import com.example.model.UserFollowing
import com.example.model.UserProfile
import com.example.ui.components.CraftBadge
import com.example.ui.components.EmptyAtelierCard
import com.example.ui.components.MakerAvatar
import com.example.ui.components.ProjectCoverArt
import com.example.util.ImageAndFormatUtils
import com.example.viewmodel.CraftViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: CraftViewModel,
    onOpenProject: (String) -> Unit,
    onOpenCreateProject: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenAuthor: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val projectsState by viewModel.projectsState.collectAsStateWithLifecycle()
    val bookmarksState by viewModel.bookmarksState.collectAsStateWithLifecycle()
    val makersState by viewModel.makersState.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFeedTab by viewModel.selectedFeedTab.collectAsStateWithLifecycle()
    val followingState by viewModel.followingState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    val bookmarkedIds = remember(bookmarksState) {
        (bookmarksState as? UiState.Success<List<ProjectBookmark>>)?.data?.map { it.projectId }?.toSet().orEmpty()
    }
    val followingIds = remember(followingState) {
        (followingState as? UiState.Success<List<UserFollowing>>)?.data?.map { it.targetUserId }?.toSet().orEmpty()
    }
    val authorTagsByUserId = remember(makersState, userProfile, viewModel.currentUserId) {
        buildMap<String, List<String>> {
            (makersState as? UiState.Success<List<MakerPublicProfile>>)?.data?.forEach { maker ->
                put(maker.userId, maker.specialties)
            }
            userProfile?.let { profile ->
                put(viewModel.currentUserId, profile.specialties)
            }
        }
    }
    val allPublicProjects = (projectsState as? UiState.Success<List<CraftProject>>)?.data.orEmpty()
    val tabScopedProjects = remember(allPublicProjects, selectedFeedTab, followingIds) {
        when (selectedFeedTab) {
            FeedTab.FOR_YOU -> allPublicProjects
            FeedTab.FOLLOWING -> allPublicProjects.filter { it.authorId in followingIds }
        }
    }
    val filteredProjects = remember(tabScopedProjects, authorTagsByUserId, searchQuery) {
        filterProjectsByTitleOrUserTags(
            projects = tabScopedProjects,
            authorTagsByUserId = authorTagsByUserId,
            rawQuery = searchQuery
        )
    }
    val pullToRefreshState = rememberPullToRefreshState()

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            PrimaryTabRow(
                selectedTabIndex = selectedFeedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.widthIn(max = 680.dp)
            ) {
                Tab(
                    selected = selectedFeedTab == FeedTab.FOR_YOU,
                    onClick = { viewModel.selectFeedTab(FeedTab.FOR_YOU) },
                    text = { Text(FeedTab.FOR_YOU.label) },
                    modifier = Modifier.testTag("feed_tab_for_you")
                )
                Tab(
                    selected = selectedFeedTab == FeedTab.FOLLOWING,
                    onClick = { viewModel.selectFeedTab(FeedTab.FOLLOWING) },
                    text = {
                        Text(
                            if (followingIds.isNotEmpty()) "${FeedTab.FOLLOWING.label} (${followingIds.size})"
                            else FeedTab.FOLLOWING.label
                        )
                    },
                    modifier = Modifier.testTag("feed_tab_following")
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refreshFeed,
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    containerColor = MaterialTheme.colorScheme.surface,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("feed_pull_to_refresh")
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp)
                        .testTag("feed_project_list"),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    item(key = "feed_search_bar") {
                        FeedSearchBar(
                            query = searchQuery,
                            onQueryChange = viewModel::updateSearchQuery
                        )
                    }

                    item(key = "composer_prompt") {
                        QuickPostPromptBar(
                            userProfile = userProfile,
                            onClick = onOpenCreateProject
                        )
                    }

                    when (val state = projectsState) {
                        is UiState.Loading -> {
                            item(key = "loading") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(48.dp)
                                        .testTag("feed_loading_indicator"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                        is UiState.Error -> {
                            item(key = "error") {
                                EmptyAtelierCard(
                                    title = "Could not load feed",
                                    subtitle = state.message,
                                    actionLabel = "Try Again",
                                    onAction = viewModel::refreshFeed
                                )
                            }
                        }
                        is UiState.Success -> {
                            if (state.data.isEmpty()) {
                                item(key = "empty") {
                                    EmptyAtelierCard(
                                        title = "No projects yet",
                                        subtitle = "Be the first maker to share a project photo or DIY guide with the community.",
                                        actionLabel = "Share a Project",
                                        onAction = onOpenCreateProject
                                    )
                                }
                            } else if (selectedFeedTab == FeedTab.FOLLOWING && followingIds.isEmpty()) {
                                item(key = "following_empty") {
                                    EmptyAtelierCard(
                                        title = "Follow creators to fill this feed",
                                        subtitle = "Open any creator's profile and tap Follow to see their projects here.",
                                        actionLabel = "Browse For You",
                                        onAction = { viewModel.selectFeedTab(FeedTab.FOR_YOU) }
                                    )
                                }
                            } else if (selectedFeedTab == FeedTab.FOLLOWING && tabScopedProjects.isEmpty()) {
                                item(key = "following_no_posts") {
                                    EmptyAtelierCard(
                                        title = "No posts from followed creators yet",
                                        subtitle = "When creators you follow upload new projects, they will appear right here.",
                                        actionLabel = "Browse For You",
                                        onAction = { viewModel.selectFeedTab(FeedTab.FOR_YOU) }
                                    )
                                }
                            } else if (filteredProjects.isEmpty()) {
                                item(key = "search_empty") {
                                    EmptyAtelierCard(
                                        title = "No matching projects",
                                        subtitle = "No projects match \"${searchQuery.trim()}\" by title or user tags.",
                                        actionLabel = "Clear Search",
                                        onAction = { viewModel.updateSearchQuery("") }
                                    )
                                }
                            } else {
                            items(filteredProjects, key = { it.id }) { project ->
                                val isLiked by viewModel.observeIsProjectLiked(project.id)
                                    .collectAsStateWithLifecycle(initialValue = false)
                                val isBookmarked = project.id in bookmarkedIds

                                ProjectFeedCard(
                                    project = project,
                                    currentUserId = viewModel.currentUserId,
                                    isLiked = isLiked,
                                    isBookmarked = isBookmarked,
                                    onOpenDetail = { onOpenProject(project.id) },
                                    onToggleLike = { viewModel.toggleLike(project, isLiked) },
                                    onToggleBookmark = { viewModel.toggleBookmark(project, isBookmarked) },
                                    onMessageAuthor = {
                                        viewModel.openDirectChat(
                                            otherUserId = project.authorId,
                                            otherName = project.authorName,
                                            otherPhotoUrl = project.authorPhotoUrl,
                                            onReady = onOpenConversation
                                        )
                                    },
                                    onOpenAuthor = if (project.authorId.isNotBlank()) {
                                        { onOpenAuthor(project.authorId) }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

private fun filterProjectsByTitleOrUserTags(
    projects: List<CraftProject>,
    authorTagsByUserId: Map<String, List<String>>,
    rawQuery: String
): List<CraftProject> {
    val trimmed = rawQuery.trim()
    if (trimmed.isEmpty()) return projects

    val tokens = trimmed
        .split(Regex("\\s+"))
        .map { it.trim().trimStart('#', '@').lowercase() }
        .filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return projects

    return projects.filter { project ->
        val titleLower = project.title.lowercase()
        val authorNameLower = project.authorName.lowercase()
        val usernameLower = ImageAndFormatUtils.formatMakerUsername(project.authorName)
            .removePrefix("@")
            .lowercase()
        val profileTags = authorTagsByUserId[project.authorId].orEmpty()
        val combinedTags = (profileTags + project.tags + project.materials)
            .map { it.trim().trimStart('#').lowercase() }

        tokens.all { token ->
            titleLower.contains(token) ||
                authorNameLower.contains(token) ||
                usernameLower.contains(token) ||
                combinedTags.any { tag -> tag.contains(token) }
        }
    }
}

@Composable
private fun FeedSearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = "Search by title or user tags...",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Search projects",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            AnimatedVisibility(visible = query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("feed_search_clear")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Clear search"
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp)
            .testTag("feed_search_input")
    )
}

@Composable
private fun QuickPostPromptBar(
    userProfile: UserProfile?,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick)
            .testTag("composer_prompt_card"),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MakerAvatar(
                name = userProfile?.displayName ?: "Maker",
                photoUrl = userProfile?.photoUrl,
                size = 38.dp
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Share a craft project or DIY update...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Outlined.PhotoCamera,
                contentDescription = "New post",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 6.dp)
            )
        }
    }
}

@Composable
fun ProjectFeedCard(
    project: CraftProject,
    currentUserId: String,
    isLiked: Boolean,
    isBookmarked: Boolean,
    onOpenDetail: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleBookmark: () -> Unit,
    onMessageAuthor: (() -> Unit)? = null,
    onOpenAuthor: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val postType = remember(project.postType) { PostType.fromRaw(project.postType) }
    val creatorName = remember(project.authorName) {
        project.authorName.trim().ifBlank { "Creative Maker" }
    }
    val creatorUsername = remember(creatorName) {
        ImageAndFormatUtils.formatMakerUsername(creatorName)
    }
    val displayLikes = project.effectiveLikesCount
    val heartScale by animateFloatAsState(
        targetValue = if (isLiked) 1.18f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "like_heart_scale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onOpenDetail)
            .testTag("project_card_${project.id}"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Creator header showing avatar, display name, @username, relative timestamp, and quick DM
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (onOpenAuthor != null) Modifier.clickable(onClick = onOpenAuthor)
                            else Modifier
                        )
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MakerAvatar(
                        name = creatorName,
                        photoUrl = project.authorPhotoUrl,
                        size = 42.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("project_author_${project.id}")
                    ) {
                        Text(
                            text = creatorName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$creatorUsername · ${ImageAndFormatUtils.formatRelativeTime(project.createdAt)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (onMessageAuthor != null && project.authorId.isNotBlank() && project.authorId != currentUserId) {
                    IconButton(
                        onClick = onMessageAuthor,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("message_author_${project.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MailOutline,
                            contentDescription = "Message $creatorName",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Project cover image centerpiece with layered category & tutorial badges
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("project_image_${project.id}")
            ) {
                ProjectCoverArt(
                    coverPreset = project.coverPreset,
                    coverImageData = project.coverImageData,
                    title = project.title,
                    height = 210.dp,
                    fallbackCategory = project.category,
                    alwaysShowFallback = true
                )

                if (project.steps.isNotEmpty() || postType == PostType.TUTORIAL) {
                    CraftBadge(
                        text = if (project.steps.isNotEmpty()) "${project.steps.size} Steps" else "DIY Guide",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.94f),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Project title
            Text(
                text = project.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("project_title_${project.id}")
            )

            if (project.summary.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = project.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (project.estimatedTime.isNotBlank() || project.steps.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (project.steps.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = buildString {
                            append(project.difficulty)
                            if (project.estimatedTime.isNotBlank()) {
                                append(" · ${project.estimatedTime}")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (project.steps.isNotEmpty()) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(2.dp))

            // Clean 3-button Social Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onToggleLike,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("like_button_${project.id}")
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isLiked) "Unlike project" else "Like project",
                        tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .scale(heartScale)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when {
                            isLiked && displayLikes > 0 -> "Liked ($displayLikes)"
                            displayLikes > 0 -> "Like ($displayLikes)"
                            isLiked -> "Liked"
                            else -> "Like"
                        },
                        color = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = onOpenDetail,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("comment_button_${project.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Comments",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (project.commentsCount > 0) "${project.commentsCount}" else "Comment",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = onToggleBookmark,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("bookmark_button_${project.id}")
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Save post",
                        tint = if (isBookmarked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isBookmarked) "Saved" else "Save",
                        color = if (isBookmarked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
