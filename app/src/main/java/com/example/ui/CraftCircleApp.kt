package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.data.CraftRepository
import com.example.model.ActivityAlertType
import com.example.model.ProjectActivityAlert
import com.example.notifications.CraftNotificationHelper
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatThreadScreen
import com.example.ui.screens.CreateProjectScreen
import com.example.ui.screens.EventsScreen
import com.example.ui.screens.FeedScreen
import com.example.ui.screens.MakerProfileScreen
import com.example.ui.screens.MessagesScreen
import com.example.ui.screens.ProjectDetailScreen
import com.example.ui.screens.StudioProfileScreen
import com.example.viewmodel.CraftViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

object CraftRoutes {
    const val FEED = "feed"
    const val MESSAGES = "messages"
    const val EVENTS = "events"
    const val STUDIO = "studio"
    const val CREATE_PROJECT = "create_project"
    const val EDIT_PROJECT = "edit_project/{projectId}"
    const val PROJECT_DETAIL = "project_detail/{projectId}"
    const val CHAT_THREAD = "chat_thread/{conversationId}"
    const val MAKER_PROFILE = "maker_profile/{makerId}"

    private val SAFE_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]{1,128}$")

    fun isValidId(id: String?): Boolean = !id.isNullOrBlank() && SAFE_ID_REGEX.matches(id)
    fun editProject(projectId: String) = "edit_project/$projectId"
    fun projectDetail(projectId: String) = "project_detail/$projectId"
    fun chatThread(conversationId: String) = "chat_thread/$conversationId"
    fun makerProfile(makerId: String) = "maker_profile/$makerId"
}

private enum class TopDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    FEED(CraftRoutes.FEED, "Feed", Icons.Filled.DynamicFeed, Icons.Outlined.DynamicFeed),
    MESSAGES(CraftRoutes.MESSAGES, "Messages", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline),
    EVENTS(CraftRoutes.EVENTS, "Events", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    STUDIO(CraftRoutes.STUDIO, "Profile", Icons.Filled.Person, Icons.Outlined.Person)
}

@Composable
fun CraftCircleRoot(
    pendingProjectId: String? = null,
    onConsumePendingProject: () -> Unit = {}
) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
    } else {
        val uid = user.uid
        key(uid) {
            AuthenticatedCraftApp(
                currentUserId = uid,
                pendingProjectId = pendingProjectId,
                onConsumePendingProject = onConsumePendingProject,
                onSignedOut = { currentUser = Firebase.auth.currentUser }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedCraftApp(
    currentUserId: String,
    pendingProjectId: String?,
    onConsumePendingProject: () -> Unit,
    onSignedOut: () -> Unit
) {
    val viewModel: CraftViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                CraftViewModel(
                    repository = CraftRepository(db),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: CraftRoutes.FEED
    val showTopLevelChrome = TopDestination.entries.any { it.route == currentRoute }

    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val activityAlerts by viewModel.activityAlerts.collectAsStateWithLifecycle()
    val unreadAlertsCount by viewModel.unreadAlertsCount.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateEventSheet by remember { mutableStateOf(false) }
    var showNewChatSheet by remember { mutableStateOf(false) }
    var showAlertsSheet by remember { mutableStateOf(false) }
    var notificationsGranted by remember {
        mutableStateOf(CraftNotificationHelper.hasNotificationPermission(context))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
    }

    LaunchedEffect(Unit) {
        CraftNotificationHelper.ensureNotificationChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !CraftNotificationHelper.hasNotificationPermission(context)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(viewModel, context) {
        viewModel.localNotificationEvents.collect { alert ->
            CraftNotificationHelper.showActivityNotification(context, alert)
        }
    }

    LaunchedEffect(pendingProjectId) {
        val targetId = pendingProjectId
        if (!targetId.isNullOrBlank()) {
            if (CraftRoutes.isValidId(targetId)) {
                navController.navigate(CraftRoutes.projectDetail(targetId))
            }
            onConsumePendingProject()
        }
    }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (showTopLevelChrome) {
                    TopAppBar(
                        title = {
                            Text(
                                text = when (currentRoute) {
                                    CraftRoutes.MESSAGES -> "Messages"
                                    CraftRoutes.EVENTS -> "Workshops & Events"
                                    CraftRoutes.STUDIO -> "Profile"
                                    else -> "CraftCircle"
                                },
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (currentRoute == CraftRoutes.FEED) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onBackground
                                }
                            )
                        },
                        actions = {
                            when (currentRoute) {
                                CraftRoutes.FEED -> {
                                    IconButton(
                                        onClick = {
                                            showAlertsSheet = true
                                            viewModel.markAllAlertsRead()
                                        },
                                        modifier = Modifier
                                            .padding(end = 6.dp)
                                            .minimumInteractiveComponentSize()
                                            .testTag("notifications_bell_button")
                                    ) {
                                        BadgedBox(
                                            badge = {
                                                if (unreadAlertsCount > 0) {
                                                    Badge {
                                                        Text(if (unreadAlertsCount > 9) "9+" else "$unreadAlertsCount")
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Notifications,
                                                contentDescription = "Project notifications"
                                            )
                                        }
                                    }
                                }
                                CraftRoutes.MESSAGES -> {
                                    FilledTonalButton(
                                        onClick = { showNewChatSheet = true },
                                        modifier = Modifier
                                            .padding(end = 12.dp)
                                            .testTag("top_new_chat_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.AddComment,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("New Chat")
                                    }
                                }
                                CraftRoutes.EVENTS -> {
                                    FilledTonalButton(
                                        onClick = { showCreateEventSheet = true },
                                        modifier = Modifier
                                            .padding(end = 12.dp)
                                            .testTag("schedule_event_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Host")
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
            },
            bottomBar = {
                if (showTopLevelChrome && !useNavRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        TopDestination.entries.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                AnimatedVisibility(visible = currentRoute == CraftRoutes.FEED) {
                    FloatingActionButton(
                        onClick = { navController.navigate(CraftRoutes.CREATE_PROJECT) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("fab_new_project")
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "New post")
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showTopLevelChrome && useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        TopDestination.entries.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    NavHost(
                        navController = navController,
                        startDestination = CraftRoutes.FEED
                    ) {
                        composable(CraftRoutes.FEED) {
                            FeedScreen(
                                viewModel = viewModel,
                                onOpenProject = { id ->
                                    navController.navigate(CraftRoutes.projectDetail(id))
                                },
                                onOpenCreateProject = {
                                    navController.navigate(CraftRoutes.CREATE_PROJECT)
                                },
                                onOpenConversation = { convoId ->
                                    navController.navigate(CraftRoutes.chatThread(convoId))
                                },
                                onOpenAuthor = { authorId ->
                                    if (authorId == currentUserId) {
                                        navController.navigate(CraftRoutes.STUDIO) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    } else {
                                        navController.navigate(CraftRoutes.makerProfile(authorId))
                                    }
                                }
                            )
                        }
                        composable(CraftRoutes.MESSAGES) {
                            MessagesScreen(
                                viewModel = viewModel,
                                showNewChatSheet = showNewChatSheet,
                                onDismissNewChatSheet = { showNewChatSheet = false },
                                onOpenNewChatSheet = { showNewChatSheet = true },
                                onOpenConversation = { convoId ->
                                    navController.navigate(CraftRoutes.chatThread(convoId))
                                }
                            )
                        }
                        composable(CraftRoutes.EVENTS) {
                            EventsScreen(
                                viewModel = viewModel,
                                showCreateSheet = showCreateEventSheet,
                                onDismissCreateSheet = { showCreateEventSheet = false },
                                onOpenCreateSheet = { showCreateEventSheet = true },
                                onOpenConversation = { convoId ->
                                    navController.navigate(CraftRoutes.chatThread(convoId))
                                }
                            )
                        }
                        composable(CraftRoutes.STUDIO) {
                            StudioProfileScreen(
                                viewModel = viewModel,
                                onOpenProject = { id ->
                                    navController.navigate(CraftRoutes.projectDetail(id))
                                },
                                onOpenCreateProject = {
                                    navController.navigate(CraftRoutes.CREATE_PROJECT)
                                },
                                onSignedOut = onSignedOut
                            )
                        }
                        composable(CraftRoutes.CREATE_PROJECT) {
                            CreateProjectScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onProjectCreated = { _ ->
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable(
                            route = CraftRoutes.EDIT_PROJECT,
                            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
                        ) { entry ->
                            val projectId = entry.arguments?.getString("projectId").orEmpty()
                            CreateProjectScreen(
                                viewModel = viewModel,
                                editingProjectId = projectId,
                                onNavigateBack = { navController.popBackStack() },
                                onProjectCreated = { _ ->
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable(
                            route = CraftRoutes.PROJECT_DETAIL,
                            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
                        ) { entry ->
                            val projectId = entry.arguments?.getString("projectId").orEmpty()
                            ProjectDetailScreen(
                                projectId = projectId,
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onOpenConversation = { convoId ->
                                    navController.navigate(CraftRoutes.chatThread(convoId))
                                },
                                onOpenAuthor = { authorId ->
                                    if (authorId == currentUserId) {
                                        navController.navigate(CraftRoutes.STUDIO) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    } else {
                                        navController.navigate(CraftRoutes.makerProfile(authorId))
                                    }
                                },
                                onEditProject = { id ->
                                    navController.navigate(CraftRoutes.editProject(id))
                                }
                            )
                        }
                        composable(
                            route = CraftRoutes.CHAT_THREAD,
                            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
                        ) { entry ->
                            val conversationId = entry.arguments?.getString("conversationId").orEmpty()
                            ChatThreadScreen(
                                conversationId = conversationId,
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = CraftRoutes.MAKER_PROFILE,
                            arguments = listOf(navArgument("makerId") { type = NavType.StringType })
                        ) { entry ->
                            val makerId = entry.arguments?.getString("makerId").orEmpty()
                            MakerProfileScreen(
                                makerId = makerId,
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onOpenProject = { id ->
                                    navController.navigate(CraftRoutes.projectDetail(id))
                                },
                                onOpenConversation = { convoId ->
                                    navController.navigate(CraftRoutes.chatThread(convoId))
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAlertsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAlertsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            ActivityAlertsSheetContent(
                alerts = activityAlerts,
                notificationsEnabled = notificationsGranted,
                onRequestNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onOpenProject = { projectId ->
                    showAlertsSheet = false
                    navController.navigate(CraftRoutes.projectDetail(projectId))
                },
                onClearAll = viewModel::clearAlerts
            )
        }
    }
}

@Composable
private fun ActivityAlertsSheetContent(
    alerts: List<ProjectActivityAlert>,
    notificationsEnabled: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onOpenProject: (String) -> Unit,
    onClearAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
            .testTag("activity_alerts_sheet"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Notifications",
                style = MaterialTheme.typography.titleLarge
            )
            if (alerts.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text("Clear")
                }
            }
        }

        if (!notificationsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Device alerts are off",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Enable notifications to see new likes and comments in your status bar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    FilledTonalButton(
                        onClick = onRequestNotificationPermission,
                        modifier = Modifier.testTag("enable_notifications_button")
                    ) {
                        Text("Enable")
                    }
                }
            }
        }

        if (alerts.isEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "You're all caught up",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "New likes and comments on your projects will appear here and trigger local device alerts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(alerts, key = { it.id }) { alert ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenProject(alert.projectId) }
                            .testTag("alert_item_${alert.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (alert.type == ActivityAlertType.LIKE) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (alert.type == ActivityAlertType.LIKE) {
                                            Icons.Filled.Favorite
                                        } else {
                                            Icons.Outlined.ChatBubbleOutline
                                        },
                                        contentDescription = null,
                                        tint = if (alert.type == ActivityAlertType.LIKE) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.secondary
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = alert.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = alert.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
