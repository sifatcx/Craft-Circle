package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CraftRepository
import com.example.model.ActivityAlertType
import com.example.model.Conversation
import com.example.model.CraftProject
import com.example.model.DirectMessage
import com.example.model.FeedTab
import com.example.model.HobbyCategory
import com.example.model.MakerEvent
import com.example.model.MakerPublicProfile
import com.example.model.ProjectActivityAlert
import com.example.model.ProjectBookmark
import com.example.model.ProjectComment
import com.example.model.ProjectProgressUpdate
import com.example.model.ProjectSearchFilter
import com.example.model.TutorialStep
import com.example.model.UiState
import com.example.model.UserFollowing
import com.example.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CraftViewModel(
    private val repository: CraftRepository,
    val currentUserId: String
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow(HobbyCategory.ALL)
    val selectedCategory: StateFlow<HobbyCategory> = _selectedCategory.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedSearchFilter = MutableStateFlow(ProjectSearchFilter.ALL)
    val selectedSearchFilter: StateFlow<ProjectSearchFilter> = _selectedSearchFilter.asStateFlow()

    private val _selectedFeedTab = MutableStateFlow(FeedTab.FOR_YOU)
    val selectedFeedTab: StateFlow<FeedTab> = _selectedFeedTab.asStateFlow()

    private val _feedRefreshTrigger = MutableStateFlow(0)
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { error ->
            unwrapAndRethrowIfPureCancellation(error, "userProfile")
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val projectsState: StateFlow<UiState<List<CraftProject>>> = _feedRefreshTrigger
        .flatMapLatest {
            repository.observePublicProjects()
                .toUiStateFlow("Failed to load creative projects")
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val feedProjectsState: StateFlow<UiState<List<CraftProject>>> = combine(
        _feedRefreshTrigger,
        _searchQuery.debounce(250L).distinctUntilChanged(),
        _selectedSearchFilter
    ) { _, query, filter -> filter to query }
        .flatMapLatest { (filter, query) ->
            repository.observeFilteredPublicProjects(filter, query)
                .toUiStateFlow("Failed to load creative projects")
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val myProjectsState: StateFlow<UiState<List<CraftProject>>> = repository.observeMyProjects(currentUserId)
        .toUiStateFlow("Failed to load your studio projects")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val bookmarksState: StateFlow<UiState<List<ProjectBookmark>>> = repository.observeMyBookmarks(currentUserId)
        .toUiStateFlow("Failed to load saved tutorials")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val eventsState: StateFlow<UiState<List<MakerEvent>>> = repository.observePublicEvents()
        .toUiStateFlow("Failed to load scheduled events")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val makersState: StateFlow<UiState<List<MakerPublicProfile>>> = repository.observeMakers()
        .toUiStateFlow("Failed to load makers directory")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val conversationsState: StateFlow<UiState<List<Conversation>>> = repository.observeMyConversations(currentUserId)
        .toUiStateFlow("Failed to load messages")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val followingState: StateFlow<UiState<List<UserFollowing>>> = repository.observeFollowing(currentUserId)
        .toUiStateFlow("Failed to load followed creators")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val myFollowersCount: StateFlow<Int> = repository.observeMakerFollowersCount(currentUserId)
        .catch { error ->
            unwrapAndRethrowIfPureCancellation(error, "myFollowersCount")
            emit(0)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = 0
        )

    private val _activityAlerts = MutableStateFlow<List<ProjectActivityAlert>>(emptyList())
    val activityAlerts: StateFlow<List<ProjectActivityAlert>> = _activityAlerts.asStateFlow()

    val unreadAlertsCount: StateFlow<Int> = _activityAlerts
        .map { alerts -> alerts.count { !it.isRead } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = 0
        )

    private val _localNotificationEvents = MutableSharedFlow<ProjectActivityAlert>(extraBufferCapacity = 16)
    val localNotificationEvents: SharedFlow<ProjectActivityAlert> = _localNotificationEvents.asSharedFlow()

    private data class ProjectCounterSnapshot(
        val likes: Long,
        val commentsCount: Long,
        val lastCommentId: String?
    )

    private val suppressedSelfLikeProjectIds = mutableSetOf<String>()
    private var hasSeededProjectActivity = false
    private val knownProjectCounters = mutableMapOf<String, ProjectCounterSnapshot>()

    init {
        viewModelScope.launch {
            repository.ensureUserProfile()
        }
        startProjectActivityWatcher()
    }

    private fun startProjectActivityWatcher() {
        viewModelScope.launch {
            repository.observeMyProjects(currentUserId)
                .catch { error ->
                    unwrapAndRethrowIfPureCancellation(error, "activityWatcher")
                }
                .collect { myProjects ->
                    if (!hasSeededProjectActivity) {
                        myProjects.forEach { project ->
                            knownProjectCounters[project.id] = ProjectCounterSnapshot(
                                likes = project.effectiveLikesCount,
                                commentsCount = project.commentsCount,
                                lastCommentId = project.lastCommentId
                            )
                        }
                        hasSeededProjectActivity = true
                        return@collect
                    }

                    for (project in myProjects) {
                        val previous = knownProjectCounters[project.id]
                        if (previous != null) {
                            val currentLikes = project.effectiveLikesCount
                            if (currentLikes > previous.likes && project.id !in suppressedSelfLikeProjectIds) {
                                val likeAlert = ProjectActivityAlert(
                                    id = "like_${project.id}_$currentLikes",
                                    type = ActivityAlertType.LIKE,
                                    projectId = project.id,
                                    projectTitle = project.title,
                                    title = "New like on your project",
                                    message = "Someone liked \"${project.title}\" ($currentLikes ${if (currentLikes == 1L) "like" else "likes"})"
                                )
                                pushActivityAlert(likeAlert)
                            }

                            if (project.commentsCount > previous.commentsCount &&
                                !project.lastCommentId.isNullOrBlank() &&
                                project.lastCommentId != previous.lastCommentId
                            ) {
                                val commentId = project.lastCommentId
                                val comment = repository.fetchProjectComment(project.id, commentId)
                                if (comment == null || comment.authorId != currentUserId) {
                                    val commenterName = comment?.authorName?.takeIf { it.isNotBlank() } ?: "Someone"
                                    val preview = comment?.text?.takeIf { it.isNotBlank() }
                                        ?: "Posted a new comment on your project."
                                    val commentAlert = ProjectActivityAlert(
                                        id = "comment_${project.id}_$commentId",
                                        type = ActivityAlertType.COMMENT,
                                        projectId = project.id,
                                        projectTitle = project.title,
                                        title = "$commenterName commented on ${project.title}",
                                        message = preview
                                    )
                                    pushActivityAlert(commentAlert)
                                }
                            }
                        }
                        knownProjectCounters[project.id] = ProjectCounterSnapshot(
                            likes = project.effectiveLikesCount,
                            commentsCount = project.commentsCount,
                            lastCommentId = project.lastCommentId
                        )
                    }
                }
        }
    }

    private fun pushActivityAlert(alert: ProjectActivityAlert) {
        if (_activityAlerts.value.any { it.id == alert.id }) return
        _activityAlerts.value = (listOf(alert) + _activityAlerts.value).take(30)
        _localNotificationEvents.tryEmit(alert)
    }

    fun markAllAlertsRead() {
        _activityAlerts.value = _activityAlerts.value.map { it.copy(isRead = true) }
    }

    fun clearAlerts() {
        _activityAlerts.value = emptyList()
    }

    fun selectCategory(category: HobbyCategory) {
        _selectedCategory.value = category
    }

    fun selectFeedTab(tab: FeedTab) {
        _selectedFeedTab.value = tab
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectSearchFilter(filter: ProjectSearchFilter) {
        _selectedSearchFilter.value = filter
    }

    fun refreshFeed() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                if (projectsState.value is UiState.Error || feedProjectsState.value is UiState.Error) {
                    _feedRefreshTrigger.value += 1
                }
                repository.fetchLatestPublicProjects().onFailure { err ->
                    _statusBanner.value = err.message ?: "Could not refresh projects."
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun observeProject(projectId: String): Flow<UiState<CraftProject?>> =
        repository.observeProject(projectId).toUiStateFlow("Failed to load project details")

    fun observeProjectComments(projectId: String): Flow<UiState<List<ProjectComment>>> =
        repository.observeProjectComments(projectId).toUiStateFlow("Failed to load comments")

    fun observeProjectUpdates(projectId: String, isOwner: Boolean): Flow<UiState<List<ProjectProgressUpdate>>> =
        repository.observeProjectUpdates(projectId, isOwner).toUiStateFlow("Failed to load project updates")

    fun observeIsProjectLiked(projectId: String): Flow<Boolean> =
        repository.observeIsProjectLiked(projectId, currentUserId)
            .catch { error ->
                unwrapAndRethrowIfPureCancellation(error, "observeIsProjectLiked")
                emit(false)
            }

    fun observeIsEventGoing(eventId: String): Flow<Boolean> =
        repository.observeIsEventGoing(eventId, currentUserId)
            .catch { error ->
                unwrapAndRethrowIfPureCancellation(error, "observeIsEventGoing")
                emit(false)
            }

    fun observeConversation(conversationId: String): Flow<UiState<Conversation?>> =
        repository.observeConversation(conversationId).toUiStateFlow("Failed to load conversation")

    fun observeMakerProfile(makerId: String): Flow<UiState<MakerPublicProfile?>> =
        repository.observeMakerProfile(makerId).toUiStateFlow("Failed to load maker profile")

    fun observeIsFollowing(targetUserId: String): Flow<Boolean> =
        if (targetUserId.isBlank() || targetUserId == currentUserId) {
            kotlinx.coroutines.flow.flowOf(false)
        } else {
            repository.observeIsFollowing(targetUserId)
                .catch { error ->
                    unwrapAndRethrowIfPureCancellation(error, "observeIsFollowing")
                    emit(false)
                }
        }

    fun observeMakerFollowersCount(makerId: String): Flow<Int> =
        if (makerId.isBlank()) {
            kotlinx.coroutines.flow.flowOf(0)
        } else {
            repository.observeMakerFollowersCount(makerId)
                .catch { error ->
                    unwrapAndRethrowIfPureCancellation(error, "observeMakerFollowersCount")
                    emit(0)
                }
        }

    fun observeMessages(conversationId: String): Flow<UiState<List<DirectMessage>>> =
        repository.observeConversationMessages(conversationId).toUiStateFlow("Failed to load messages")

    fun openDirectChat(
        otherUserId: String,
        otherName: String,
        otherPhotoUrl: String,
        onReady: (String) -> Unit
    ) {
        if (otherUserId.isBlank() || otherUserId == currentUserId) {
            _statusBanner.value = "You can message other makers from their posts or the directory."
            return
        }
        viewModelScope.launch {
            repository.startOrGetConversation(
                myProfile = userProfile.value,
                otherUserId = otherUserId,
                otherName = otherName,
                otherPhotoUrl = otherPhotoUrl
            ).fold(
                onSuccess = { convoId -> onReady(convoId) },
                onFailure = { err ->
                    _statusBanner.value = err.message ?: "Could not start conversation."
                }
            )
        }
    }

    fun sendMessage(
        conversationId: String,
        participantIds: List<String>,
        text: String,
        onDone: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            repository.sendMessage(
                conversationId = conversationId,
                participantIds = participantIds,
                senderProfile = userProfile.value,
                text = text
            ).fold(
                onSuccess = { onDone(true) },
                onFailure = { err ->
                    _statusBanner.value = err.message ?: "Could not send message."
                    onDone(false)
                }
            )
        }
    }

    fun createProject(
        context: Context,
        title: String,
        summary: String,
        category: String,
        postType: String,
        difficulty: String,
        estimatedTime: String,
        materials: List<String>,
        steps: List<TutorialStep>,
        coverPreset: String,
        selectedImageUri: Uri?,
        coverImageData: String? = null,
        onComplete: (Boolean, String?, String?) -> Unit
    ) {
        viewModelScope.launch {
            var resolvedImage = coverImageData
            var storageNotice: String? = null
            if (selectedImageUri != null) {
                val uploadResult = repository.uploadProjectCoverImage(context, selectedImageUri)
                if (uploadResult.isFailure) {
                    val message = uploadResult.exceptionOrNull()?.localizedMessage
                        ?: "Could not upload project image to Firebase Storage."
                    _statusBanner.value = message
                    onComplete(false, null, message)
                    return@launch
                }
                val outcome = uploadResult.getOrThrow()
                resolvedImage = outcome.imageUrlOrData
                storageNotice = outcome.notice
            }

            val result = repository.createProject(
                authorProfile = userProfile.value,
                title = title,
                summary = summary,
                category = category,
                postType = postType,
                difficulty = difficulty,
                estimatedTime = estimatedTime,
                materials = materials,
                steps = steps,
                coverPreset = coverPreset,
                coverImageData = resolvedImage
            )
            result.fold(
                onSuccess = { newId ->
                    _statusBanner.value = storageNotice ?: "Project uploaded to CraftCircle!"
                    onComplete(true, newId, null)
                },
                onFailure = { err ->
                    val message = err.message ?: "Could not upload project."
                    _statusBanner.value = message
                    onComplete(false, null, message)
                }
            )
        }
    }

    fun updateProject(
        context: Context,
        projectId: String,
        title: String,
        summary: String,
        category: String,
        postType: String,
        difficulty: String,
        estimatedTime: String,
        materials: List<String>,
        steps: List<TutorialStep>,
        coverPreset: String,
        selectedImageUri: Uri?,
        coverImageData: String?,
        previousCoverImageData: String?,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            var resolvedImage = coverImageData
            var storageNotice: String? = null
            if (selectedImageUri != null) {
                val uploadResult = repository.uploadProjectCoverImage(context, selectedImageUri)
                if (uploadResult.isFailure) {
                    val message = uploadResult.exceptionOrNull()?.localizedMessage
                        ?: "Could not upload project image to Firebase Storage."
                    _statusBanner.value = message
                    onComplete(false, message)
                    return@launch
                }
                val outcome = uploadResult.getOrThrow()
                resolvedImage = outcome.imageUrlOrData
                storageNotice = outcome.notice
            }

            val result = repository.updateProject(
                projectId = projectId,
                authorProfile = userProfile.value,
                title = title,
                summary = summary,
                category = category,
                postType = postType,
                difficulty = difficulty,
                estimatedTime = estimatedTime,
                materials = materials,
                steps = steps,
                coverPreset = coverPreset,
                coverImageData = resolvedImage,
                previousCoverImageData = previousCoverImageData
            )
            result.fold(
                onSuccess = {
                    _statusBanner.value = storageNotice ?: "Project updated!"
                    onComplete(true, null)
                },
                onFailure = { err ->
                    val message = err.message ?: "Could not update project."
                    _statusBanner.value = message
                    onComplete(false, message)
                }
            )
        }
    }

    fun deleteProject(projectId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteProject(projectId).fold(
                onSuccess = {
                    _statusBanner.value = "Post removed."
                    onDeleted()
                },
                onFailure = { err ->
                    _statusBanner.value = err.message ?: "Failed to delete post."
                }
            )
        }
    }

    private val activeLikeMutations = mutableSetOf<String>()
    private val activeFollowMutations = mutableSetOf<String>()

    fun toggleFollowMaker(
        targetUserId: String,
        targetName: String,
        targetPhotoUrl: String,
        currentlyFollowing: Boolean
    ) {
        if (targetUserId.isBlank() || targetUserId == currentUserId) return
        if (!activeFollowMutations.add(targetUserId)) return
        viewModelScope.launch {
            try {
                repository.toggleFollowMaker(
                    targetUserId = targetUserId,
                    targetName = targetName,
                    targetPhotoUrl = targetPhotoUrl,
                    currentlyFollowing = currentlyFollowing
                ).fold(
                    onSuccess = {
                        val displayName = targetName.trim().ifBlank { "this creator" }
                        _statusBanner.value = if (currentlyFollowing) {
                            "Unfollowed $displayName"
                        } else {
                            "Following $displayName"
                        }
                    },
                    onFailure = { err ->
                        _statusBanner.value = err.message ?: "Could not update follow status."
                    }
                )
            } finally {
                activeFollowMutations.remove(targetUserId)
            }
        }
    }

    fun toggleLike(project: CraftProject, currentlyLiked: Boolean) {
        if (!activeLikeMutations.add(project.id)) return
        val isOwnLike = project.authorId == currentUserId && !currentlyLiked
        if (isOwnLike) {
            suppressedSelfLikeProjectIds.add(project.id)
        }
        viewModelScope.launch {
            try {
                repository.toggleProjectLike(project, currentlyLiked).onFailure { err ->
                    _statusBanner.value = err.message ?: "Could not update like."
                }
                if (isOwnLike) {
                    delay(4000L)
                }
            } finally {
                if (isOwnLike) {
                    suppressedSelfLikeProjectIds.remove(project.id)
                }
                activeLikeMutations.remove(project.id)
            }
        }
    }

    fun toggleBookmark(project: CraftProject, currentlyBookmarked: Boolean) {
        viewModelScope.launch {
            repository.toggleBookmark(project, currentlyBookmarked).fold(
                onSuccess = {
                    _statusBanner.value = if (currentlyBookmarked) {
                        "Removed from Saved Projects"
                    } else {
                        "Bookmarked to Saved Projects!"
                    }
                },
                onFailure = { err ->
                    _statusBanner.value = err.message ?: "Could not update bookmark."
                }
            )
        }
    }

    fun addProjectUpdate(
        context: Context,
        project: CraftProject,
        statusLabel: String,
        message: String,
        selectedPhotoUri: Uri?,
        onDone: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            var resolvedPhotoUrl: String? = null
            var storageNotice: String? = null
            if (selectedPhotoUri != null) {
                val uploadResult = repository.uploadProjectUpdateImage(context, project.id, selectedPhotoUri)
                if (uploadResult.isFailure) {
                    val errMsg = uploadResult.exceptionOrNull()?.localizedMessage
                        ?: "Could not upload progress photo to Firebase Storage."
                    _statusBanner.value = errMsg
                    onDone(false, errMsg)
                    return@launch
                }
                val outcome = uploadResult.getOrThrow()
                resolvedPhotoUrl = outcome.imageUrlOrData
                storageNotice = outcome.notice
            }

            repository.addProjectUpdate(
                project = project,
                authorProfile = userProfile.value,
                statusLabel = statusLabel,
                message = message,
                photoData = resolvedPhotoUrl
            ).fold(
                onSuccess = {
                    _statusBanner.value = storageNotice ?: "Progress update posted!"
                    onDone(true, null)
                },
                onFailure = { err ->
                    val errMsg = err.message ?: "Could not post progress update."
                    _statusBanner.value = errMsg
                    onDone(false, errMsg)
                }
            )
        }
    }

    fun deleteProjectUpdate(projectId: String, updateId: String) {
        viewModelScope.launch {
            repository.deleteProjectUpdate(projectId, updateId).fold(
                onSuccess = {
                    _statusBanner.value = "Progress update removed."
                },
                onFailure = { err ->
                    _statusBanner.value = err.message ?: "Could not remove progress update."
                }
            )
        }
    }

    fun addComment(project: CraftProject, text: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            repository.addProjectComment(project, userProfile.value, text).fold(
                onSuccess = { onDone(true) },
                onFailure = { err ->
                    _statusBanner.value = err.message ?: "Could not post comment."
                    onDone(false)
                }
            )
        }
    }

    fun deleteComment(projectId: String, commentId: String) {
        viewModelScope.launch {
            repository.deleteProjectComment(projectId, commentId).fold(
                onSuccess = {
                    _statusBanner.value = "Comment deleted."
                },
                onFailure = { err ->
                    _statusBanner.value = err.message ?: "Could not delete comment."
                }
            )
        }
    }

    fun createEvent(
        title: String,
        description: String,
        category: String,
        format: String,
        locationOrLink: String,
        materialsNeeded: String,
        startTimeMillis: Long,
        durationMinutes: Long,
        maxAttendees: Long,
        onDone: (MakerEvent?) -> Unit
    ) {
        viewModelScope.launch {
            repository.createMakerEvent(
                hostProfile = userProfile.value,
                title = title,
                description = description,
                category = category,
                format = format,
                locationOrLink = locationOrLink,
                materialsNeeded = materialsNeeded,
                startTimeMillis = startTimeMillis,
                durationMinutes = durationMinutes,
                maxAttendees = maxAttendees
            ).fold(
                onSuccess = { created ->
                    _statusBanner.value = "Event scheduled!"
                    onDone(created)
                },
                onFailure = { err ->
                    _statusBanner.value = err.message ?: "Could not schedule event."
                    onDone(null)
                }
            )
        }
    }

    fun deleteEvent(eventId: String) {
        viewModelScope.launch {
            repository.deleteMakerEvent(eventId).fold(
                onSuccess = { _statusBanner.value = "Event cancelled." },
                onFailure = { err -> _statusBanner.value = err.message ?: "Could not delete event." }
            )
        }
    }

    fun toggleRsvp(event: MakerEvent, currentlyGoing: Boolean) {
        viewModelScope.launch {
            repository.toggleEventRsvp(event, userProfile.value, currentlyGoing).fold(
                onSuccess = {
                    _statusBanner.value = if (currentlyGoing) "RSVP cancelled" else "You're going to ${event.title}!"
                },
                onFailure = { err ->
                    _statusBanner.value = err.message ?: "Could not update RSVP."
                }
            )
        }
    }

    fun updateProfile(
        displayName: String,
        bio: String,
        specialties: List<String>,
        onDone: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            repository.updateUserProfile(displayName, bio, specialties).fold(
                onSuccess = {
                    _statusBanner.value = "Maker profile updated!"
                    onDone(true)
                },
                onFailure = { err ->
                    _statusBanner.value = err.message ?: "Could not update profile."
                    onDone(false)
                }
            )
        }
    }

    private fun <T> Flow<T>.toUiStateFlow(fallbackMessage: String): Flow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, fallbackMessage, rootError)
                emit(UiState.Error(rootError.localizedMessage ?: fallbackMessage))
            }

    private fun unwrapAndRethrowIfPureCancellation(error: Throwable, tag: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        Log.w(TAG, "Stream error in $tag", firestoreError ?: error)
    }

    private companion object {
        const val TAG = "CraftVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
