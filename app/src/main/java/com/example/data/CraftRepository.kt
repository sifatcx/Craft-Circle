package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.R
import com.example.model.Conversation
import com.example.model.CraftProject
import com.example.model.DirectMessage
import com.example.model.HobbyCategory
import com.example.model.MakerEvent
import com.example.model.MakerPublicProfile
import com.example.model.ProjectBookmark
import com.example.model.ProjectComment
import com.example.model.ProjectProgressUpdate
import com.example.model.ProjectSearchFilter
import com.example.model.TutorialStep
import com.example.model.UserFollowing
import com.example.model.UserProfile
import com.example.util.ImageAndFormatUtils
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class StorageUploadOutcome(
    val imageUrlOrData: String,
    val usedCloudStorage: Boolean,
    val notice: String? = null
)

class CraftRepository(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage = Firebase.storage
) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.storage
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- USER PROFILE & PUBLIC MAKER DIRECTORY ---

    suspend fun ensureUserProfile(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val path = "users/$uid"
        return try {
            val user = auth.currentUser
            val safeName = (user?.displayName?.takeIf { it.isNotBlank() } ?: "Creative Maker").take(60)
            val safePhoto = user?.photoUrl?.toString()?.take(500).orEmpty()
            val defaultBio = "Sharing creative projects & DIY tutorials on CraftCircle."
            val defaultSpecialties = listOf("Woodworking", "DIY Guides")

            val userRef = db.collection("users").document(uid)
            val existingUser = userRef.get().await()
            val makerBio: String
            val makerName: String
            val makerPhoto: String
            val makerSpecs: List<String>

            if (!existingUser.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to user?.email?.take(120),
                    "photoUrl" to safePhoto.takeIf { it.isNotBlank() },
                    "bio" to defaultBio,
                    "xHandle" to "",
                    "specialties" to defaultSpecialties,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                userRef.set(payload).await()
                makerName = safeName
                makerBio = defaultBio
                makerPhoto = safePhoto
                makerSpecs = defaultSpecialties
            } else {
                val parsed = existingUser.toObject(UserProfile::class.java)
                makerName = (parsed?.displayName?.takeIf { it.isNotBlank() } ?: safeName).take(60)
                makerBio = (parsed?.bio ?: defaultBio).take(280)
                makerPhoto = (parsed?.photoUrl ?: safePhoto).take(500)
                makerSpecs = parsed?.specialties ?: defaultSpecialties
            }

            // Ensure public non-PII maker card exists for direct messaging discovery
            val makerRef = db.collection("makers").document(uid)
            val existingMaker = makerRef.get().await()
            if (!existingMaker.exists()) {
                makerRef.set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to makerName,
                        "photoUrl" to makerPhoto,
                        "bio" to makerBio,
                        "specialties" to makerSpecs.take(8),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        require(uid == userId) { "Profile access restricted to owner." }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    logStreamError(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    suspend fun updateUserProfile(
        displayName: String,
        bio: String,
        specialties: List<String>
    ): Result<Unit> {
        var path = "users/unauthenticated"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            val trimmedName = displayName.trim()
            require(trimmedName.length in 1..60) { "Display name must be 1 to 60 characters." }
            val trimmedBio = bio.trim().take(280)
            val cleanSpecialties = specialties.map { it.trim() }.filter { it.isNotBlank() }.take(8)
            val photoUrl = auth.currentUser?.photoUrl?.toString()?.take(500).orEmpty()

            db.collection("users").document(uid).update(
                mapOf(
                    "displayName" to trimmedName,
                    "bio" to trimmedBio,
                    "specialties" to cleanSpecialties,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            db.collection("makers").document(uid).set(
                mapOf(
                    "userId" to uid,
                    "displayName" to trimmedName,
                    "photoUrl" to photoUrl,
                    "bio" to trimmedBio,
                    "specialties" to cleanSpecialties,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeMakers(): Flow<List<MakerPublicProfile>> = flow {
        val uid = requireUserId()
        val path = "makers"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(MakerPublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.userId != uid && it.userId.isNotBlank() }
                        .sortedBy { it.displayName.lowercase() }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeMakerProfile(userId: String): Flow<MakerPublicProfile?> = flow {
        val uid = requireUserId()
        require(userId.isNotBlank())
        val path = "makers/$userId"
        emitAll(
            db.collection("makers").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(MakerPublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    logStreamError(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    // --- FOLLOWING & FOLLOWERS ---

    fun observeFollowing(userId: String): Flow<List<UserFollowing>> = flow {
        val uid = requireUserId()
        require(uid == userId) { "Following list access restricted to owner." }
        val path = "users/$uid/following"
        emitAll(
            db.collection("users").document(uid).collection("following")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(UserFollowing::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeIsFollowing(targetUserId: String): Flow<Boolean> = flow {
        val uid = requireUserId()
        require(targetUserId.isNotBlank())
        val path = "users/$uid/following/$targetUserId"
        emitAll(
            db.collection("users").document(uid)
                .collection("following").document(targetUserId)
                .snapshots()
                .map { it.exists() }
                .catch { error ->
                    logStreamError(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    fun observeMakerFollowersCount(makerId: String): Flow<Int> = flow {
        val uid = requireUserId()
        require(makerId.isNotBlank())
        val path = "makers/$makerId/followers"
        emitAll(
            db.collection("makers").document(makerId)
                .collection("followers")
                .snapshots()
                .map { it.size() }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun toggleFollowMaker(
        targetUserId: String,
        targetName: String,
        targetPhotoUrl: String,
        currentlyFollowing: Boolean
    ): Result<Unit> {
        var path = "users/unauthenticated/following/$targetUserId"
        return try {
            val uid = requireUserId()
            require(targetUserId.isNotBlank() && targetUserId != uid) {
                "You cannot follow your own profile."
            }
            path = "users/$uid/following/$targetUserId"
            val followingRef = db.collection("users").document(uid)
                .collection("following").document(targetUserId)
            val followerRef = db.collection("makers").document(targetUserId)
                .collection("followers").document(uid)

            val batch = db.batch()
            if (currentlyFollowing) {
                batch.delete(followingRef)
                batch.delete(followerRef)
            } else {
                val safeName = targetName.trim().ifBlank { "Creative Maker" }.take(60)
                val safePhoto = targetPhotoUrl.trim().take(500)
                batch.set(
                    followingRef,
                    mapOf(
                        "userId" to uid,
                        "targetUserId" to targetUserId,
                        "targetName" to safeName,
                        "targetPhotoUrl" to safePhoto,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.set(
                    followerRef,
                    mapOf(
                        "followerId" to uid,
                        "targetUserId" to targetUserId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- DIRECT MESSAGING ---

    fun observeMyConversations(userId: String): Flow<List<Conversation>> = flow {
        val uid = requireUserId()
        require(uid == userId)
        val path = "conversations"
        emitAll(
            db.collection(path)
                .whereArrayContains("participantIds", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.lastMessageAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeConversation(conversationId: String): Flow<Conversation?> = flow {
        val uid = requireUserId()
        require(conversationId.isNotBlank())
        val path = "conversations/$conversationId"
        emitAll(
            db.collection("conversations").document(conversationId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    logStreamError(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    fun observeConversationMessages(conversationId: String): Flow<List<DirectMessage>> = flow {
        val uid = requireUserId()
        require(conversationId.isNotBlank())
        val path = "conversations/$conversationId/messages"
        emitAll(
            db.collection("conversations").document(conversationId)
                .collection("messages")
                .whereArrayContains("participantIds", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(DirectMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun startOrGetConversation(
        myProfile: UserProfile?,
        otherUserId: String,
        otherName: String,
        otherPhotoUrl: String
    ): Result<String> {
        var path = "conversations"
        return try {
            val uid = requireUserId()
            require(otherUserId.isNotBlank() && otherUserId != uid) {
                "Select another maker to start a conversation."
            }
            val participants = listOf(uid, otherUserId).sorted()
            val convoId = participants.joinToString("_")
            path = "conversations/$convoId"

            val convoRef = db.collection("conversations").document(convoId)
            val existing = convoRef.get().await()
            if (!existing.exists()) {
                val user = auth.currentUser
                val myName = (myProfile?.displayName?.takeIf { it.isNotBlank() }
                    ?: user?.displayName?.takeIf { it.isNotBlank() }
                    ?: "Maker").take(60)
                val myPhoto = (myProfile?.photoUrl ?: user?.photoUrl?.toString()).orEmpty().take(500)
                val safeOtherName = otherName.trim().ifBlank { "Maker" }.take(60)
                val safeOtherPhoto = otherPhotoUrl.trim().take(500)

                val payload = mapOf(
                    "participantIds" to participants,
                    "participantNames" to mapOf(
                        uid to myName,
                        otherUserId to safeOtherName
                    ),
                    "participantPhotos" to mapOf(
                        uid to myPhoto,
                        otherUserId to safeOtherPhoto
                    ),
                    "lastMessageText" to "",
                    "lastSenderId" to "",
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
                convoRef.set(payload).await()
            }
            Result.success(convoId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        conversationId: String,
        participantIds: List<String>,
        senderProfile: UserProfile?,
        text: String
    ): Result<Unit> {
        val path = "conversations/$conversationId/messages"
        return try {
            val uid = requireUserId()
            require(uid in participantIds && participantIds.size == 2) {
                "You are not a participant in this conversation."
            }
            val cleanText = text.trim()
            require(cleanText.length in 1..1000) { "Message must be 1 to 1000 characters." }

            val user = auth.currentUser
            val senderName = (senderProfile?.displayName?.takeIf { it.isNotBlank() }
                ?: user?.displayName?.takeIf { it.isNotBlank() }
                ?: "Maker").take(60)
            val senderPhoto = (senderProfile?.photoUrl ?: user?.photoUrl?.toString()).orEmpty().take(500)

            val convoRef = db.collection("conversations").document(conversationId)
            val msgRef = convoRef.collection("messages").document()

            val batch = db.batch()
            batch.set(
                msgRef,
                mapOf(
                    "conversationId" to conversationId,
                    "participantIds" to participantIds,
                    "senderId" to uid,
                    "senderName" to senderName,
                    "senderPhotoUrl" to senderPhoto,
                    "text" to cleanText,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                convoRef,
                mapOf(
                    "lastMessageText" to cleanText.take(500),
                    "lastSenderId" to uid,
                    "lastMessageAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- PROJECTS & DIY TUTORIALS ---

    fun observePublicProjects(): Flow<List<CraftProject>> = flow {
        val uid = requireUserId()
        val path = "projects"
        emitAll(
            db.collection(path)
                .whereEqualTo("visibility", "public")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(CraftProject::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeFilteredPublicProjects(
        filter: ProjectSearchFilter,
        rawQuery: String
    ): Flow<List<CraftProject>> {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) return observePublicProjects()

        return flow {
            val uid = requireUserId()
            val path = "projects"
            val baseQuery = db.collection(path).whereEqualTo("visibility", "public")

            val normalizedQuery = trimmed.trimStart('#', '@').lowercase()
            val tokens = normalizedQuery
                .split(Regex("\\s+"))
                .map { it.trim().trimStart('#', '@') }
                .filter { it.isNotEmpty() }
            if (tokens.isEmpty()) {
                emitAll(observePublicProjects())
                return@flow
            }

            val primaryToken = tokens.firstOrNull { it.length >= 2 } ?: tokens.first()
            val titleCasedQuery = trimmed.split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .joinToString(" ") { word ->
                    word.lowercase().replaceFirstChar { ch -> ch.uppercase() }
                }
            val exactCategoryLabel = HobbyCategory.creatableCategories
                .firstOrNull { it.label.equals(normalizedQuery, ignoreCase = true) }
                ?.label

            val queryStreams = mutableListOf<Flow<List<CraftProject>>>()
            when (filter) {
                ProjectSearchFilter.CATEGORY -> {
                    val categoryTarget = exactCategoryLabel ?: titleCasedQuery.ifBlank { trimmed }
                    queryStreams += observeProjectQueryWithFallback(
                        query = baseQuery.whereEqualTo("category", categoryTarget),
                        path = path,
                        uid = uid
                    )
                    if (!trimmed.equals(categoryTarget, ignoreCase = false)) {
                        queryStreams += observeProjectQueryWithFallback(
                            query = baseQuery.whereEqualTo("category", trimmed),
                            path = path,
                            uid = uid
                        )
                    }
                    queryStreams += observeProjectQueryWithFallback(
                        query = baseQuery.whereArrayContains("searchTokens", primaryToken),
                        path = path,
                        uid = uid
                    )
                }

                ProjectSearchFilter.MATERIAL -> {
                    queryStreams += observeProjectQueryWithFallback(
                        query = baseQuery.whereArrayContains("materials", trimmed),
                        path = path,
                        uid = uid
                    )
                    if (titleCasedQuery.isNotBlank() && titleCasedQuery != trimmed) {
                        queryStreams += observeProjectQueryWithFallback(
                            query = baseQuery.whereArrayContains("materials", titleCasedQuery),
                            path = path,
                            uid = uid
                        )
                    }
                    queryStreams += observeProjectQueryWithFallback(
                        query = baseQuery.whereArrayContains("searchTokens", primaryToken),
                        path = path,
                        uid = uid
                    )
                }

                ProjectSearchFilter.CREATOR -> {
                    queryStreams += observeProjectQueryWithFallback(
                        query = baseQuery.whereEqualTo("authorName", trimmed),
                        path = path,
                        uid = uid
                    )
                    if (titleCasedQuery.isNotBlank() && titleCasedQuery != trimmed) {
                        queryStreams += observeProjectQueryWithFallback(
                            query = baseQuery.whereEqualTo("authorName", titleCasedQuery),
                            path = path,
                            uid = uid
                        )
                    }
                    queryStreams += observeProjectQueryWithFallback(
                        query = baseQuery.whereArrayContains("searchTokens", primaryToken),
                        path = path,
                        uid = uid
                    )
                }

                ProjectSearchFilter.ALL -> {
                    queryStreams += observeProjectQueryWithFallback(
                        query = baseQuery.whereArrayContains("searchTokens", primaryToken),
                        path = path,
                        uid = uid
                    )
                    if (exactCategoryLabel != null) {
                        queryStreams += observeProjectQueryWithFallback(
                            query = baseQuery.whereEqualTo("category", exactCategoryLabel),
                            path = path,
                            uid = uid
                        )
                    } else if (titleCasedQuery.isNotBlank()) {
                        queryStreams += observeProjectQueryWithFallback(
                            query = baseQuery.whereEqualTo("authorName", trimmed),
                            path = path,
                            uid = uid
                        )
                        queryStreams += observeProjectQueryWithFallback(
                            query = baseQuery.whereArrayContains("materials", titleCasedQuery),
                            path = path,
                            uid = uid
                        )
                    }
                }
            }

            emitAll(
                combine(queryStreams) { resultsPerQuery ->
                    resultsPerQuery
                        .asSequence()
                        .flatMap { it.asSequence() }
                        .distinctBy { it.id }
                        .filter { project -> matchesSearchFilter(project, filter, tokens) }
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                        .toList()
                }
            )
        }
    }

    private fun observeProjectQueryWithFallback(
        query: Query,
        path: String,
        uid: String
    ): Flow<List<CraftProject>> =
        query.snapshots()
            .map { snapshot ->
                snapshot.toObjects(CraftProject::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError?.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                    Log.w("CraftRepository", "Composite index unavailable for search query; falling back to public visibility query", firestoreError)
                    emitAll(
                        db.collection(path)
                            .whereEqualTo("visibility", "public")
                            .snapshots()
                            .map { fallbackSnap ->
                                fallbackSnap.toObjects(CraftProject::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            }
                    )
                } else {
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
            }

    private fun matchesSearchFilter(
        project: CraftProject,
        filter: ProjectSearchFilter,
        tokens: List<String>
    ): Boolean {
        val categoryLower = project.category.lowercase()
        val authorLower = project.authorName.lowercase()
        val handleLower = ImageAndFormatUtils.formatMakerUsername(project.authorName)
            .removePrefix("@")
            .lowercase()
        val materialsLower = project.materials.map { it.trim().lowercase() }

        return when (filter) {
            ProjectSearchFilter.CATEGORY -> tokens.all { token -> categoryLower.contains(token) }
            ProjectSearchFilter.MATERIAL -> tokens.all { token ->
                materialsLower.any { material -> material.contains(token) }
            }
            ProjectSearchFilter.CREATOR -> tokens.all { token ->
                authorLower.contains(token) || handleLower.contains(token)
            }
            ProjectSearchFilter.ALL -> {
                val titleLower = project.title.lowercase()
                val tagsAndTokensLower = (project.tags + project.searchTokens)
                    .map { it.trim().trimStart('#').lowercase() }
                tokens.all { token ->
                    categoryLower.contains(token) ||
                        authorLower.contains(token) ||
                        handleLower.contains(token) ||
                        materialsLower.any { it.contains(token) } ||
                        titleLower.contains(token) ||
                        tagsAndTokensLower.any { it.contains(token) }
                }
            }
        }
    }

    private fun buildProjectSearchTokens(
        title: String,
        category: String,
        authorName: String,
        materials: List<String>
    ): List<String> {
        val tokenSet = linkedSetOf<String>()
        fun addTextTokens(raw: String) {
            val clean = raw.trim().lowercase()
            if (clean.isEmpty()) return
            if (clean.length >= 2) {
                tokenSet.add(clean)
            }
            clean.split(Regex("[^a-z0-9]+"))
                .filter { it.length >= 2 }
                .forEach { tokenSet.add(it) }
        }

        addTextTokens(category)
        addTextTokens(authorName)
        val handle = ImageAndFormatUtils.formatMakerUsername(authorName).removePrefix("@").lowercase()
        if (handle.length >= 2) tokenSet.add(handle)
        materials.forEach { addTextTokens(it) }
        addTextTokens(title)
        return tokenSet.take(60)
    }

    suspend fun fetchLatestPublicProjects(): Result<List<CraftProject>> {
        val path = "projects"
        return try {
            requireUserId()
            val snapshot = try {
                db.collection(path)
                    .whereEqualTo("visibility", "public")
                    .get(Source.SERVER)
                    .await()
            } catch (e: FirebaseFirestoreException) {
                if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                    db.collection(path)
                        .whereEqualTo("visibility", "public")
                        .get(Source.DEFAULT)
                        .await()
                } else {
                    throw e
                }
            }
            val projects = snapshot.toObjects(
                CraftProject::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            Result.success(projects)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    fun observeMyProjects(userId: String): Flow<List<CraftProject>> = flow {
        val uid = requireUserId()
        require(uid == userId) { "Mismatched user ID" }
        val path = "projects"
        emitAll(
            db.collection(path)
                .whereEqualTo("authorId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(CraftProject::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeProject(projectId: String): Flow<CraftProject?> = flow {
        val uid = requireUserId()
        require(projectId.isNotBlank())
        val path = "projects/$projectId"
        emitAll(
            db.collection("projects").document(projectId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(CraftProject::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    logStreamError(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    // --- FIREBASE STORAGE IMAGE UPLOADS ---

    suspend fun uploadProjectCoverImage(
        context: Context,
        imageUri: Uri
    ): Result<StorageUploadOutcome> {
        val uid = requireUserId()
        val path = "project_covers/$uid/${UUID.randomUUID()}.jpg"
        return uploadImageToStorage(context, imageUri, path)
    }

    suspend fun uploadProjectUpdateImage(
        context: Context,
        projectId: String,
        imageUri: Uri
    ): Result<StorageUploadOutcome> {
        val uid = requireUserId()
        require(projectId.isNotBlank()) { "Project ID is required." }
        val path = "project_updates/$uid/$projectId/${UUID.randomUUID()}.jpg"
        return uploadImageToStorage(context, imageUri, path)
    }

    private suspend fun uploadImageToStorage(
        context: Context,
        imageUri: Uri,
        storagePath: String
    ): Result<StorageUploadOutcome> {
        return try {
            requireUserId()
            val jpegBytes = ImageAndFormatUtils.encodeUriToStorageJpegBytes(context, imageUri)
                ?: return Result.failure(IllegalArgumentException("Could not process selected photo. Try another image."))

            val ref = storage.reference.child(storagePath)
            val metadata = StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .build()
            ref.putBytes(jpegBytes, metadata).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            Result.success(
                StorageUploadOutcome(
                    imageUrlOrData = downloadUrl,
                    usedCloudStorage = true
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val storageErr = e as? StorageException
            val isUnprovisionedOrBlocked = storageErr != null && (
                storageErr.errorCode == StorageException.ERROR_BUCKET_NOT_FOUND ||
                    storageErr.errorCode == StorageException.ERROR_PROJECT_NOT_FOUND ||
                    storageErr.errorCode == StorageException.ERROR_NOT_AUTHORIZED ||
                    storageErr.httpResultCode == 404 ||
                    storageErr.httpResultCode == 403
                )
            Log.w("CraftStorage", "Firebase Storage upload failed at $storagePath", e)
            if (isUnprovisionedOrBlocked) {
                val compact = ImageAndFormatUtils.encodeUriToCompactBase64(context, imageUri)
                if (compact != null) {
                    return Result.success(
                        StorageUploadOutcome(
                            imageUrlOrData = compact,
                            usedCloudStorage = false,
                            notice = "Enable Firebase Storage in Firebase Console for full-resolution cloud photos. Saved compact preview instead."
                        )
                    )
                }
            }
            Result.failure(e)
        }
    }

    private suspend fun deleteStorageImageBestEffort(imageUrlOrData: String?) {
        val url = imageUrlOrData?.trim().orEmpty()
        if (!url.startsWith("https://firebasestorage.googleapis.com", ignoreCase = true) &&
            !url.startsWith("gs://", ignoreCase = true)
        ) {
            return
        }
        runCatching {
            storage.getReferenceFromUrl(url).delete().await()
        }.onFailure { err ->
            Log.w("CraftStorage", "Best-effort image cleanup skipped for $url", err)
        }
    }

    suspend fun createProject(
        authorProfile: UserProfile?,
        title: String,
        summary: String,
        category: String,
        postType: String,
        difficulty: String,
        estimatedTime: String,
        materials: List<String>,
        steps: List<TutorialStep>,
        coverPreset: String,
        coverImageData: String?,
        visibility: String = "public"
    ): Result<String> {
        var path = "projects"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim()
            val cleanSummary = summary.trim()
            require(cleanTitle.length in 3..100) { "Title must be 3 to 100 characters." }
            require(cleanSummary.length in 5..1500) { "Description must be 5 to 1500 characters." }
            require(coverImageData == null || coverImageData.length <= 160_000) { "Cover image is too large." }

            val docRef = db.collection("projects").document()
            path = docRef.path
            val user = auth.currentUser
            val authorName = (authorProfile?.displayName?.takeIf { it.isNotBlank() }
                ?: user?.displayName?.takeIf { it.isNotBlank() }
                ?: "Maker").take(60)
            val authorPhoto = (authorProfile?.photoUrl ?: user?.photoUrl?.toString()).orEmpty().take(500)

            val cleanCategory = category.trim().ifBlank { "Project" }.take(40)
            val cleanMaterials = materials.map { it.trim() }.filter { it.isNotBlank() }.take(20)
            val searchTokens = buildProjectSearchTokens(
                title = cleanTitle,
                category = cleanCategory,
                authorName = authorName,
                materials = cleanMaterials
            )

            val payload = mapOf(
                "authorId" to uid,
                "authorName" to authorName,
                "authorPhotoUrl" to authorPhoto,
                "authorXHandle" to "",
                "title" to cleanTitle,
                "summary" to cleanSummary,
                "category" to cleanCategory,
                "postType" to postType,
                "difficulty" to difficulty,
                "estimatedTime" to estimatedTime.trim().take(40),
                "materials" to cleanMaterials,
                "searchTokens" to searchTokens,
                "steps" to steps.take(15).mapIndexed { index, step ->
                    step.copy(stepNumber = (index + 1).toLong()).toMap()
                },
                "coverPreset" to coverPreset,
                "coverImageData" to coverImageData?.takeIf { it.isNotBlank() },
                "visibility" to visibility,
                "status" to "published",
                "likes" to 0L,
                "likesCount" to 0L,
                "commentsCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateProject(
        projectId: String,
        authorProfile: UserProfile? = null,
        title: String,
        summary: String,
        category: String,
        postType: String,
        difficulty: String,
        estimatedTime: String,
        materials: List<String>,
        steps: List<TutorialStep>,
        coverPreset: String,
        coverImageData: String?,
        previousCoverImageData: String?
    ): Result<Unit> {
        val path = "projects/$projectId"
        return try {
            requireUserId()
            require(projectId.isNotBlank())
            val cleanTitle = title.trim()
            val cleanSummary = summary.trim()
            require(cleanTitle.length in 3..100) { "Title must be 3 to 100 characters." }
            require(cleanSummary.length in 5..1500) { "Description must be 5 to 1500 characters." }
            val cleanCover = coverImageData?.takeIf { it.isNotBlank() }
            require(cleanCover == null || cleanCover.length <= 160_000) { "Cover image is too large." }

            val cleanCategory = category.trim().ifBlank { "Project" }.take(40)
            val cleanMaterials = materials.map { it.trim() }.filter { it.isNotBlank() }.take(20)
            val user = auth.currentUser
            val authorName = (authorProfile?.displayName?.takeIf { it.isNotBlank() }
                ?: user?.displayName?.takeIf { it.isNotBlank() }
                ?: "Maker").take(60)
            val searchTokens = buildProjectSearchTokens(
                title = cleanTitle,
                category = cleanCategory,
                authorName = authorName,
                materials = cleanMaterials
            )

            val updates = mutableMapOf<String, Any>(
                "title" to cleanTitle,
                "summary" to cleanSummary,
                "category" to cleanCategory,
                "postType" to postType,
                "difficulty" to difficulty,
                "estimatedTime" to estimatedTime.trim().take(40),
                "materials" to cleanMaterials,
                "searchTokens" to searchTokens,
                "steps" to steps.take(15).mapIndexed { index, step ->
                    step.copy(stepNumber = (index + 1).toLong()).toMap()
                },
                "coverPreset" to coverPreset,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (cleanCover != null) {
                updates["coverImageData"] = cleanCover
            } else if (!previousCoverImageData.isNullOrBlank()) {
                updates["coverImageData"] = FieldValue.delete()
            }

            db.collection("projects").document(projectId).update(updates).await()
            if (!previousCoverImageData.isNullOrBlank() && previousCoverImageData != cleanCover) {
                deleteStorageImageBestEffort(previousCoverImageData)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteProject(projectId: String): Result<Unit> {
        val path = "projects/$projectId"
        return try {
            requireUserId()
            require(projectId.isNotBlank())
            val existingCover = runCatching {
                db.collection("projects").document(projectId).get().await()
                    .getString("coverImageData")
            }.getOrNull()
            db.collection("projects").document(projectId).delete().await()
            deleteStorageImageBestEffort(existingCover)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- LIKES & BOOKMARKS ---

    fun observeIsProjectLiked(projectId: String, userId: String): Flow<Boolean> = flow {
        val uid = requireUserId()
        require(uid == userId)
        val path = "projects/$projectId/likes/$uid"
        emitAll(
            db.collection("projects").document(projectId)
                .collection("likes").document(uid)
                .snapshots()
                .map { it.exists() }
                .catch { error ->
                    logStreamError(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    suspend fun toggleProjectLike(project: CraftProject, currentlyLiked: Boolean): Result<Unit> {
        val projectId = project.id
        val path = "projects/$projectId"
        return try {
            val uid = requireUserId()
            require(projectId.isNotBlank())
            val projectRef = db.collection("projects").document(projectId)
            val likeRef = projectRef.collection("likes").document(uid)

            val batch = db.batch()
            if (currentlyLiked) {
                batch.delete(likeRef)
                val updatedLikesValue: Any = if (project.likes != null) {
                    FieldValue.increment(-1L)
                } else {
                    (project.likesCount - 1L).coerceAtLeast(0L)
                }
                batch.update(
                    projectRef,
                    mapOf(
                        "likesCount" to FieldValue.increment(-1L),
                        "likes" to updatedLikesValue
                    )
                )
            } else {
                batch.set(
                    likeRef,
                    mapOf(
                        "userId" to uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                val updatedLikesValue: Any = if (project.likes != null) {
                    FieldValue.increment(1L)
                } else {
                    project.likesCount + 1L
                }
                batch.update(
                    projectRef,
                    mapOf(
                        "likesCount" to FieldValue.increment(1L),
                        "likes" to updatedLikesValue
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeMyBookmarks(userId: String): Flow<List<ProjectBookmark>> = flow {
        val uid = requireUserId()
        require(uid == userId)
        val path = "users/$uid/bookmarks"
        emitAll(
            db.collection("users").document(uid).collection("bookmarks")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ProjectBookmark::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun toggleBookmark(project: CraftProject, currentlyBookmarked: Boolean): Result<Unit> {
        var path = "users/unauthenticated/bookmarks/${project.id}"
        return try {
            val uid = requireUserId()
            path = "users/$uid/bookmarks/${project.id}"
            val docRef = db.collection("users").document(uid).collection("bookmarks").document(project.id)
            if (currentlyBookmarked) {
                docRef.delete().await()
            } else {
                docRef.set(
                    mapOf(
                        "projectId" to project.id,
                        "userId" to uid,
                        "projectTitle" to project.title.take(100),
                        "projectCategory" to project.category.take(40),
                        "authorName" to project.authorName.take(60),
                        "coverPreset" to project.coverPreset.take(30),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- COMMENTS ---

    fun observeProjectComments(projectId: String): Flow<List<ProjectComment>> = flow {
        val uid = requireUserId()
        require(projectId.isNotBlank())
        val path = "projects/$projectId/comments"
        emitAll(
            db.collection("projects").document(projectId).collection("comments")
                .whereEqualTo("projectVisibility", "public")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ProjectComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp.now() }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun fetchProjectComment(projectId: String, commentId: String): ProjectComment? {
        return try {
            requireUserId()
            if (projectId.isBlank() || commentId.isBlank()) return null
            val snapshot = db.collection("projects").document(projectId)
                .collection("comments").document(commentId)
                .get()
                .await()
            if (snapshot.exists()) {
                snapshot.toObject(ProjectComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    suspend fun addProjectComment(
        project: CraftProject,
        authorProfile: UserProfile?,
        text: String
    ): Result<Unit> {
        val path = "projects/${project.id}/comments"
        return try {
            val uid = requireUserId()
            require(project.id.isNotBlank())
            val cleanText = text.trim()
            require(cleanText.length in 1..500) { "Comment must be 1 to 500 characters." }

            val user = auth.currentUser
            val authorName = (authorProfile?.displayName?.takeIf { it.isNotBlank() }
                ?: user?.displayName?.takeIf { it.isNotBlank() }
                ?: "Maker").take(60)
            val authorPhoto = (authorProfile?.photoUrl ?: user?.photoUrl?.toString()).orEmpty().take(500)

            val projectRef = db.collection("projects").document(project.id)
            val commentRef = projectRef.collection("comments").document()

            val batch = db.batch()
            batch.set(
                commentRef,
                mapOf(
                    "projectId" to project.id,
                    "projectVisibility" to project.visibility,
                    "projectAuthorId" to project.authorId,
                    "authorId" to uid,
                    "authorName" to authorName,
                    "authorPhotoUrl" to authorPhoto,
                    "text" to cleanText,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                projectRef,
                mapOf(
                    "commentsCount" to FieldValue.increment(1L),
                    "lastCommentId" to commentRef.id
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteProjectComment(
        projectId: String,
        commentId: String
    ): Result<Unit> {
        val path = "projects/$projectId/comments/$commentId"
        return try {
            requireUserId()
            require(projectId.isNotBlank() && commentId.isNotBlank())
            val projectRef = db.collection("projects").document(projectId)
            val commentRef = projectRef.collection("comments").document(commentId)

            val batch = db.batch()
            batch.delete(commentRef)
            batch.update(
                projectRef,
                mapOf(
                    "commentsCount" to FieldValue.increment(-1L),
                    "lastDeletedCommentId" to commentId
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- PROJECT CHRONOLOGICAL PROGRESS UPDATES ---

    fun observeProjectUpdates(
        projectId: String,
        isOwner: Boolean
    ): Flow<List<ProjectProgressUpdate>> = flow {
        val uid = requireUserId()
        require(projectId.isNotBlank())
        val path = "projects/$projectId/updates"
        val query = if (isOwner) {
            db.collection("projects").document(projectId).collection("updates")
                .whereEqualTo("projectAuthorId", uid)
        } else {
            db.collection("projects").document(projectId).collection("updates")
                .whereEqualTo("projectVisibility", "public")
        }
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ProjectProgressUpdate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp.now() }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun addProjectUpdate(
        project: CraftProject,
        authorProfile: UserProfile?,
        statusLabel: String,
        message: String,
        photoData: String?
    ): Result<Unit> {
        val path = "projects/${project.id}/updates"
        return try {
            val uid = requireUserId()
            require(project.id.isNotBlank())
            require(project.authorId == uid) { "Only the project maker can post progress updates." }
            val cleanStatus = statusLabel.trim().ifBlank { "In Progress" }.take(30)
            val cleanMessage = message.trim()
            require(cleanMessage.length in 1..600) { "Update message must be 1 to 600 characters." }
            val cleanPhoto = photoData?.takeIf { it.isNotBlank() }
            require(cleanPhoto == null || cleanPhoto.length <= 160_000) { "Progress photo is too large." }

            val user = auth.currentUser
            val authorName = (authorProfile?.displayName?.takeIf { it.isNotBlank() }
                ?: user?.displayName?.takeIf { it.isNotBlank() }
                ?: "Maker").take(60)
            val authorPhoto = (authorProfile?.photoUrl ?: user?.photoUrl?.toString()).orEmpty().take(500)

            val payload = mapOf(
                "projectId" to project.id,
                "projectVisibility" to project.visibility,
                "projectAuthorId" to project.authorId,
                "authorId" to uid,
                "authorName" to authorName,
                "authorPhotoUrl" to authorPhoto,
                "statusLabel" to cleanStatus,
                "message" to cleanMessage,
                "photoData" to cleanPhoto,
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            db.collection("projects").document(project.id)
                .collection("updates")
                .document()
                .set(payload)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteProjectUpdate(
        projectId: String,
        updateId: String
    ): Result<Unit> {
        val path = "projects/$projectId/updates/$updateId"
        return try {
            requireUserId()
            require(projectId.isNotBlank() && updateId.isNotBlank())
            val existingPhoto = runCatching {
                db.collection("projects").document(projectId)
                    .collection("updates").document(updateId)
                    .get().await().getString("photoData")
            }.getOrNull()
            db.collection("projects").document(projectId)
                .collection("updates").document(updateId)
                .delete()
                .await()
            deleteStorageImageBestEffort(existingPhoto)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- SCHEDULED MAKER EVENTS & RSVPS ---

    fun observePublicEvents(): Flow<List<MakerEvent>> = flow {
        val uid = requireUserId()
        val path = "events"
        emitAll(
            db.collection(path)
                .whereEqualTo("visibility", "public")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(MakerEvent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.startTimeMillis }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeIsEventGoing(eventId: String, userId: String): Flow<Boolean> = flow {
        val uid = requireUserId()
        require(uid == userId)
        val path = "events/$eventId/rsvps/$uid"
        emitAll(
            db.collection("events").document(eventId)
                .collection("rsvps").document(uid)
                .snapshots()
                .map { it.exists() }
                .catch { error ->
                    logStreamError(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    suspend fun createMakerEvent(
        hostProfile: UserProfile?,
        title: String,
        description: String,
        category: String,
        format: String,
        locationOrLink: String,
        materialsNeeded: String,
        startTimeMillis: Long,
        durationMinutes: Long,
        maxAttendees: Long
    ): Result<MakerEvent> {
        var path = "events"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim()
            val cleanDesc = description.trim()
            val cleanLoc = locationOrLink.trim()
            require(cleanTitle.length in 3..100) { "Event title must be 3 to 100 characters." }
            require(cleanDesc.length in 5..1000) { "Description must be 5 to 1000 characters." }
            require(cleanLoc.length in 2..200) { "Location or live stream link is required." }
            require(startTimeMillis > 0L) { "Please pick a valid start date and time." }
            require(durationMinutes in 15L..720L) { "Duration must be 15 to 720 minutes." }
            require(maxAttendees in 1L..500L) { "Max attendees must be 1 to 500." }

            val docRef = db.collection("events").document()
            path = docRef.path
            val user = auth.currentUser
            val hostName = (hostProfile?.displayName?.takeIf { it.isNotBlank() }
                ?: user?.displayName?.takeIf { it.isNotBlank() }
                ?: "Host").take(60)
            val hostPhoto = (hostProfile?.photoUrl ?: user?.photoUrl?.toString()).orEmpty().take(500)

            val payload = mapOf(
                "hostId" to uid,
                "hostName" to hostName,
                "hostPhotoUrl" to hostPhoto,
                "hostXHandle" to "",
                "title" to cleanTitle,
                "description" to cleanDesc,
                "category" to category.take(40),
                "format" to format,
                "locationOrLink" to cleanLoc,
                "materialsNeeded" to materialsNeeded.trim().take(300),
                "startTimeMillis" to startTimeMillis,
                "durationMinutes" to durationMinutes,
                "maxAttendees" to maxAttendees,
                "attendeeCount" to 0L,
                "xHashtag" to "#CraftCircle",
                "visibility" to "public",
                "status" to "upcoming",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(
                MakerEvent(
                    id = docRef.id,
                    hostId = uid,
                    hostName = hostName,
                    hostPhotoUrl = hostPhoto,
                    title = cleanTitle,
                    description = cleanDesc,
                    category = category,
                    format = format,
                    locationOrLink = cleanLoc,
                    materialsNeeded = materialsNeeded.trim(),
                    startTimeMillis = startTimeMillis,
                    durationMinutes = durationMinutes,
                    maxAttendees = maxAttendees,
                    attendeeCount = 0L,
                    visibility = "public",
                    status = "upcoming"
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMakerEvent(eventId: String): Result<Unit> {
        val path = "events/$eventId"
        return try {
            requireUserId()
            require(eventId.isNotBlank())
            db.collection("events").document(eventId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleEventRsvp(
        event: MakerEvent,
        userProfile: UserProfile?,
        currentlyGoing: Boolean
    ): Result<Unit> {
        val path = "events/${event.id}"
        return try {
            val uid = requireUserId()
            val eventRef = db.collection("events").document(event.id)
            val rsvpRef = eventRef.collection("rsvps").document(uid)
            val userName = (userProfile?.displayName?.takeIf { it.isNotBlank() }
                ?: auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
                ?: "Maker").take(60)

            val batch = db.batch()
            if (currentlyGoing) {
                batch.delete(rsvpRef)
                batch.update(eventRef, mapOf("attendeeCount" to FieldValue.increment(-1L)))
            } else {
                batch.set(
                    rsvpRef,
                    mapOf(
                        "eventId" to event.id,
                        "userId" to uid,
                        "userName" to userName,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.update(eventRef, mapOf("attendeeCount" to FieldValue.increment(1L)))
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private fun logStreamError(error: Throwable, op: OperationType, path: String, expectedUid: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
    }
}
