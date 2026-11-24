package com.example.model

import androidx.annotation.Keep
import com.example.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class HobbyCategory(val label: String) {
    ALL("All Crafts"),
    WOODWORKING("Woodworking"),
    ELECTRONICS("Electronics"),
    CERAMICS("Ceramics"),
    FIBER("Fiber & Sewing"),
    PRINTING_3D("3D Printing"),
    BOTANICALS("Botanicals"),
    MINIATURES("Miniature Art"),
    MIXED_MEDIA("Mixed Media");

    companion object {
        val creatableCategories: List<HobbyCategory>
            get() = entries.filter { it != ALL }
    }
}

enum class PostType(val value: String, val label: String) {
    PROJECT("PROJECT", "Project"),
    TUTORIAL("TUTORIAL", "DIY Guide"),
    WIP("WIP", "WIP");

    companion object {
        fun fromRaw(raw: String): PostType =
            entries.firstOrNull { it.value == raw || it.name == raw } ?: PROJECT
    }
}

enum class DifficultyLevel(val label: String) {
    Beginner("Beginner"),
    Intermediate("Intermediate"),
    Advanced("Advanced");

    companion object {
        fun fromRaw(raw: String): DifficultyLevel =
            entries.firstOrNull { it.label == raw || it.name == raw } ?: Beginner
    }
}

enum class CoverPreset(val key: String, val label: String, val drawableRes: Int?) {
    STUDIO("studio", "Atelier", R.drawable.img_studio_hero),
    WOODCRAFT("woodcraft", "Wood", R.drawable.img_cover_woodcraft),
    MAKERTECH("makertech", "Tech", R.drawable.img_cover_makertech),
    CERAMICS("ceramics", "Clay", R.drawable.img_cover_ceramics),
    NONE("none", "No Cover", null);

    companion object {
        fun fromKey(key: String): CoverPreset =
            entries.firstOrNull { it.key == key } ?: STUDIO
    }
}

enum class EventFormat(val label: String) {
    LIVE_STREAM("Live Stream"),
    IN_PERSON("In-Person"),
    HYBRID("Hybrid");

    companion object {
        fun fromRaw(raw: String): EventFormat =
            entries.firstOrNull { it.label == raw || it.name == raw } ?: LIVE_STREAM
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val photoUrl: String? = null,
    val bio: String = "",
    val xHandle: String = "",
    val specialties: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MakerPublicProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val bio: String = "",
    val specialties: List<String> = emptyList(),
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class TutorialStep(
    val stepNumber: Long = 1L,
    val title: String = "",
    val instruction: String = "",
    val tip: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "stepNumber" to stepNumber,
        "title" to title,
        "instruction" to instruction,
        "tip" to tip
    )
}

@Keep
@IgnoreExtraProperties
data class CraftProject(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorPhotoUrl: String = "",
    val authorXHandle: String = "",
    val title: String = "",
    val summary: String = "",
    val category: String = HobbyCategory.WOODWORKING.label,
    val postType: String = PostType.PROJECT.value,
    val difficulty: String = DifficultyLevel.Beginner.label,
    val estimatedTime: String = "",
    val materials: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val steps: List<TutorialStep> = emptyList(),
    val coverPreset: String = CoverPreset.STUDIO.key,
    val coverImageData: String? = null,
    val xPostUrl: String? = null,
    val visibility: String = "public",
    val status: String = "published",
    val likes: Long? = null,
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L,
    val lastCommentId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val effectiveLikesCount: Long
        get() = likes ?: likesCount
}

@Keep
@IgnoreExtraProperties
data class ProjectComment(
    @DocumentId val id: String = "",
    val projectId: String = "",
    val projectVisibility: String = "public",
    val projectAuthorId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorPhotoUrl: String = "",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ProjectBookmark(
    @DocumentId val id: String = "",
    val projectId: String = "",
    val userId: String = "",
    val projectTitle: String = "",
    val projectCategory: String = "",
    val authorName: String = "",
    val coverPreset: String = CoverPreset.STUDIO.key,
    @ServerTimestamp val createdAt: Timestamp? = null
)

enum class FeedTab(val label: String) {
    FOR_YOU("For You"),
    FOLLOWING("Following")
}

@Keep
@IgnoreExtraProperties
data class UserFollowing(
    @DocumentId val id: String = "",
    val userId: String = "",
    val targetUserId: String = "",
    val targetName: String = "",
    val targetPhotoUrl: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MakerFollower(
    @DocumentId val id: String = "",
    val followerId: String = "",
    val targetUserId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val participantIds: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantPhotos: Map<String, String> = emptyMap(),
    val lastMessageText: String = "",
    val lastSenderId: String = "",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun otherParticipantId(myUid: String): String =
        participantIds.firstOrNull { it != myUid }.orEmpty()

    fun otherParticipantName(myUid: String): String {
        val otherId = otherParticipantId(myUid)
        return participantNames[otherId]?.takeIf { it.isNotBlank() } ?: "Fellow Maker"
    }

    fun otherParticipantPhoto(myUid: String): String {
        val otherId = otherParticipantId(myUid)
        return participantPhotos[otherId].orEmpty()
    }
}

@Keep
@IgnoreExtraProperties
data class DirectMessage(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val participantIds: List<String> = emptyList(),
    val senderId: String = "",
    val senderName: String = "",
    val senderPhotoUrl: String = "",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MakerEvent(
    @DocumentId val id: String = "",
    val hostId: String = "",
    val hostName: String = "",
    val hostPhotoUrl: String = "",
    val hostXHandle: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = HobbyCategory.WOODWORKING.label,
    val format: String = EventFormat.LIVE_STREAM.label,
    val locationOrLink: String = "",
    val materialsNeeded: String = "",
    val startTimeMillis: Long = 0L,
    val durationMinutes: Long = 60L,
    val maxAttendees: Long = 25L,
    val attendeeCount: Long = 0L,
    val xHashtag: String = "#CraftCircle",
    val visibility: String = "public",
    val status: String = "upcoming",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class ActivityAlertType {
    LIKE,
    COMMENT
}

data class ProjectActivityAlert(
    val id: String,
    val type: ActivityAlertType,
    val projectId: String,
    val projectTitle: String,
    val title: String,
    val message: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
