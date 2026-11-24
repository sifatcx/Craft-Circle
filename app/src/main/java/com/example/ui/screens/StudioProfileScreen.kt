package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.auth.signOut
import com.example.model.CraftProject
import com.example.model.HobbyCategory
import com.example.model.MakerPublicProfile
import com.example.model.ProjectBookmark
import com.example.model.UiState
import com.example.model.UserFollowing
import com.example.model.UserProfile
import com.example.ui.components.CraftBadge
import com.example.ui.components.EmptyAtelierCard
import com.example.ui.components.MakerAvatar
import com.example.util.ImageAndFormatUtils
import com.example.viewmodel.CraftViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioProfileScreen(
    viewModel: CraftViewModel,
    onOpenProject: (String) -> Unit,
    onOpenCreateProject: () -> Unit,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val myProjectsState by viewModel.myProjectsState.collectAsStateWithLifecycle()
    val bookmarksState by viewModel.bookmarksState.collectAsStateWithLifecycle()
    val followingState by viewModel.followingState.collectAsStateWithLifecycle()
    val myFollowersCount by viewModel.myFollowersCount.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showEditDialog by remember { mutableStateOf(false) }

    val myProjects = (myProjectsState as? UiState.Success<List<CraftProject>>)?.data.orEmpty()
    val myProjectsCount = myProjects.size
    val totalLikesReceived = remember(myProjects) {
        myProjects.sumOf { it.effectiveLikesCount }
    }
    val bookmarks = (bookmarksState as? UiState.Success<List<ProjectBookmark>>)?.data.orEmpty()
    val bookmarksCount = bookmarks.size
    val followingCount = (followingState as? UiState.Success<List<UserFollowing>>)?.data?.size ?: 0
    val bookmarkedIds = remember(bookmarks) {
        bookmarks.map { it.projectId }.toSet()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .testTag("user_profile_list"),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "profile_header") {
                val displayName = profile?.displayName?.takeIf { it.isNotBlank() } ?: "Creative Maker"
                val handle = ImageAndFormatUtils.formatMakerUsername(displayName)
                val bio = profile?.bio.orEmpty().ifBlank {
                    "Add a short maker bio to introduce your craft and style."
                }
                PersonalBrandingCard(
                    displayName = displayName,
                    usernameHandle = handle,
                    subtitle = profile?.email,
                    photoUrl = profile?.photoUrl,
                    bio = bio,
                    specialties = profile?.specialties.orEmpty(),
                    statItems = listOf(
                        "Uploaded" to "$myProjectsCount",
                        "Followers" to "$myFollowersCount",
                        "Following" to "$followingCount",
                        "Likes" to "$totalLikesReceived"
                    ),
                    actions = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { showEditDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("edit_profile_button")
                            ) {
                                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Edit Profile")
                            }
                            OutlinedButton(
                                onClick = {
                                    signOut(credentialManager, scope, onSignOutComplete = onSignedOut)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("sign_out_button")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.Logout,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Sign Out")
                            }
                        }
                    }
                )
            }

            item(key = "studio_tabs") {
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Uploaded ($myProjectsCount)") },
                        modifier = Modifier.testTag("tab_my_builds")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Saved ($bookmarksCount)") },
                        modifier = Modifier.testTag("tab_saved_tutorials")
                    )
                }
            }

            if (selectedTab == 0) {
                when (val state = myProjectsState) {
                    is UiState.Loading -> {
                        item(key = "my_loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator() }
                        }
                    }
                    is UiState.Error -> {
                        item(key = "my_error") {
                            EmptyAtelierCard(title = "Could not load your uploaded projects", subtitle = state.message)
                        }
                    }
                    is UiState.Success -> {
                        if (state.data.isEmpty()) {
                            item(key = "my_empty") {
                                EmptyAtelierCard(
                                    title = "No uploaded projects yet",
                                    subtitle = "Showcase your creative builds and build your maker portfolio.",
                                    actionLabel = "Upload Project",
                                    onAction = onOpenCreateProject
                                )
                            }
                        } else {
                            items(state.data, key = { it.id }) { project ->
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
                                    onToggleBookmark = { viewModel.toggleBookmark(project, isBookmarked) }
                                )
                            }
                        }
                    }
                }
            } else {
                when (val state = bookmarksState) {
                    is UiState.Loading -> {
                        item(key = "bm_loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator() }
                        }
                    }
                    is UiState.Error -> {
                        item(key = "bm_error") {
                            EmptyAtelierCard(title = "Could not load saved posts", subtitle = state.message)
                        }
                    }
                    is UiState.Success -> {
                        if (state.data.isEmpty()) {
                            item(key = "bm_empty") {
                                EmptyAtelierCard(
                                    title = "Nothing saved yet",
                                    subtitle = "Tap Save on any project in the feed to keep it handy here."
                                )
                            }
                        } else {
                            items(state.data, key = { it.projectId }) { bookmark ->
                                BookmarkRowCard(
                                    bookmark = bookmark,
                                    onClick = { onOpenProject(bookmark.projectId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        EditProfileDialog(
            initial = profile,
            onDismiss = { showEditDialog = false },
            onSave = { name, bio, specs ->
                viewModel.updateProfile(name, bio, specs) { ok ->
                    if (ok) showEditDialog = false
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MakerProfileScreen(
    makerId: String,
    viewModel: CraftViewModel,
    onNavigateBack: () -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenConversation: (String) -> Unit
) {
    val makerState by viewModel.observeMakerProfile(makerId)
        .collectAsStateWithLifecycle(initialValue = UiState.Loading)
    val projectsState by viewModel.projectsState.collectAsStateWithLifecycle()
    val bookmarksState by viewModel.bookmarksState.collectAsStateWithLifecycle()
    val isFollowing by viewModel.observeIsFollowing(makerId)
        .collectAsStateWithLifecycle(initialValue = false)
    val followersCount by viewModel.observeMakerFollowersCount(makerId)
        .collectAsStateWithLifecycle(initialValue = 0)

    val bookmarkedIds = remember(bookmarksState) {
        (bookmarksState as? UiState.Success<List<ProjectBookmark>>)?.data?.map { it.projectId }?.toSet().orEmpty()
    }
    val allProjects = (projectsState as? UiState.Success<List<CraftProject>>)?.data.orEmpty()
    val makerProjects = remember(allProjects, makerId) {
        allProjects.filter { it.authorId == makerId }
    }
    val fallbackAuthor = makerProjects.firstOrNull()
    val maker = (makerState as? UiState.Success<MakerPublicProfile?>)?.data

    val resolvedName = maker?.displayName?.takeIf { it.isNotBlank() }
        ?: fallbackAuthor?.authorName?.takeIf { it.isNotBlank() }
        ?: "Creative Maker"
    val resolvedPhoto = maker?.photoUrl?.takeIf { it.isNotBlank() } ?: fallbackAuthor?.authorPhotoUrl
    val resolvedBio = maker?.bio?.takeIf { it.isNotBlank() }
        ?: "Sharing handmade builds and creative ideas on CraftCircle."
    val resolvedSpecs = maker?.specialties.orEmpty()
    val totalLikes = remember(makerProjects) { makerProjects.sumOf { it.effectiveLikesCount } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = ImageAndFormatUtils.formatMakerUsername(resolvedName),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("maker_profile_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp),
                contentPadding = PaddingValues(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "maker_header") {
                    PersonalBrandingCard(
                        displayName = resolvedName,
                        usernameHandle = ImageAndFormatUtils.formatMakerUsername(resolvedName),
                        subtitle = null,
                        photoUrl = resolvedPhoto,
                        bio = resolvedBio,
                        specialties = resolvedSpecs,
                        statItems = listOf(
                            "Projects" to "${makerProjects.size}",
                            "Followers" to "$followersCount",
                            "Likes" to "$totalLikes"
                        ),
                        actions = {
                            if (makerId.isNotBlank() && makerId != viewModel.currentUserId) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (isFollowing) {
                                        FilledTonalButton(
                                            onClick = {
                                                viewModel.toggleFollowMaker(
                                                    targetUserId = makerId,
                                                    targetName = resolvedName,
                                                    targetPhotoUrl = resolvedPhoto.orEmpty(),
                                                    currentlyFollowing = true
                                                )
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("maker_profile_follow_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text("Following")
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                viewModel.toggleFollowMaker(
                                                    targetUserId = makerId,
                                                    targetName = resolvedName,
                                                    targetPhotoUrl = resolvedPhoto.orEmpty(),
                                                    currentlyFollowing = false
                                                )
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("maker_profile_follow_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.PersonAdd,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text("Follow")
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            viewModel.openDirectChat(
                                                otherUserId = makerId,
                                                otherName = resolvedName,
                                                otherPhotoUrl = resolvedPhoto.orEmpty(),
                                                onReady = onOpenConversation
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("maker_profile_message_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.MailOutline,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("Message")
                                    }
                                }
                            }
                        }
                    )
                }

                item(key = "maker_projects_heading") {
                    Text(
                        text = "Uploaded Projects (${makerProjects.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                if (makerState is UiState.Loading && makerProjects.isEmpty()) {
                    item(key = "maker_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                    }
                } else if (makerProjects.isEmpty()) {
                    item(key = "maker_empty") {
                        EmptyAtelierCard(
                            title = "No public projects yet",
                            subtitle = "$resolvedName hasn't shared any public projects yet."
                        )
                    }
                } else {
                    items(makerProjects, key = { it.id }) { project ->
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
                            onToggleBookmark = { viewModel.toggleBookmark(project, isBookmarked) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonalBrandingCard(
    displayName: String,
    usernameHandle: String,
    subtitle: String?,
    photoUrl: String?,
    bio: String,
    specialties: List<String>,
    statItems: List<Pair<String, String>>,
    actions: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("personal_branding_card"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Editorial studio backdrop + overlapping avatar
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                ) {
                    AsyncImage(
                        model = R.drawable.img_studio_hero,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.12f),
                                        Color.Black.copy(alpha = 0.45f)
                                    )
                                )
                            )
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(3.dp, MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .padding(start = 16.dp, top = 52.dp)
                        .size(76.dp)
                ) {
                    MakerAvatar(
                        name = displayName,
                        photoUrl = photoUrl,
                        size = 76.dp
                    )
                }
            }

            Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 18.dp)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (!subtitle.isNullOrBlank()) "$usernameHandle · $subtitle" else usernameHandle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (specialties.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        specialties.forEach { spec ->
                            CraftBadge(text = spec)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 3-column creator metrics strip
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        statItems.forEach { (label, value) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                actions()
            }
        }
    }
}

@Composable
private fun BookmarkRowCard(
    bookmark: ProjectBookmark,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Bookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(bookmark.projectTitle, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "by ${bookmark.authorName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditProfileDialog(
    initial: UserProfile?,
    onDismiss: () -> Unit,
    onSave: (String, String, List<String>) -> Unit
) {
    var displayName by remember(initial) { mutableStateOf(initial?.displayName ?: "") }
    var bio by remember(initial) { mutableStateOf(initial?.bio ?: "") }
    val selectedSpecialties = remember(initial) {
        mutableStateListOf<String>().apply { addAll(initial?.specialties.orEmpty()) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Maker Profile", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { if (it.length <= 60) displayName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { if (it.length <= 280) bio = it },
                    label = { Text("Studio Bio") },
                    placeholder = { Text("Tell fellow makers what you love to create...") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Personal Focus Tags", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HobbyCategory.creatableCategories.forEach { cat ->
                        val active = cat.label in selectedSpecialties
                        FilterChip(
                            selected = active,
                            onClick = {
                                if (active) selectedSpecialties.remove(cat.label)
                                else if (selectedSpecialties.size < 8) selectedSpecialties.add(cat.label)
                            },
                            label = { Text(cat.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (displayName.isNotBlank()) {
                        onSave(displayName, bio, selectedSpecialties.toList())
                    }
                },
                modifier = Modifier.testTag("save_profile_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
