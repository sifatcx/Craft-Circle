package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.CoverPreset
import com.example.model.HobbyCategory
import com.example.util.ImageAndFormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MakerAvatar(
    name: String,
    photoUrl: String?,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val initials = remember(name) {
        name.trim().split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "M" }
    }
    val safePhotoUrl = remember(photoUrl) {
        val trimmed = photoUrl?.trim().orEmpty()
        if (trimmed.startsWith("https://", ignoreCase = true)) trimmed else null
    }
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        if (safePhotoUrl != null) {
            AsyncImage(
                model = safePhotoUrl,
                contentDescription = "$name avatar",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ProjectCoverArt(
    coverPreset: String,
    coverImageData: String?,
    title: String,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    fallbackCategory: String? = null,
    alwaysShowFallback: Boolean = false,
    allowLocalPreviewUri: Boolean = false
) {
    val safeDirectImageUri = remember(coverImageData, allowLocalPreviewUri) {
        val trimmed = coverImageData?.trim().orEmpty()
        when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            allowLocalPreviewUri && (
                trimmed.startsWith("content://", ignoreCase = true) ||
                    trimmed.startsWith("file://", ignoreCase = true)
                ) -> trimmed
            else -> null
        }
    }
    val isSafeInlineDataImage = remember(coverImageData) {
        val trimmed = coverImageData?.trim().orEmpty()
        trimmed.startsWith("data:image/", ignoreCase = true) ||
            (!trimmed.contains("://") && !trimmed.contains(":"))
    }
    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null,
        coverImageData,
        safeDirectImageUri,
        isSafeInlineDataImage
    ) {
        value = if (coverImageData.isNullOrBlank() || safeDirectImageUri != null || !isSafeInlineDataImage) {
            null
        } else {
            withContext(Dispatchers.Default) {
                ImageAndFormatUtils.decodeBase64Bitmap(coverImageData)
            }
        }
    }
    val resolvedPreset = remember(coverPreset, fallbackCategory, alwaysShowFallback) {
        val parsed = CoverPreset.fromKey(coverPreset)
        if (parsed == CoverPreset.NONE && alwaysShowFallback) {
            CoverPreset.fromKey(ImageAndFormatUtils.defaultCoverKeyForCategory(fallbackCategory.orEmpty()))
        } else {
            parsed
        }
    }

    if (coverImageData.isNullOrBlank() && resolvedPreset == CoverPreset.NONE) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val decoded = bitmap
        when {
            safeDirectImageUri != null -> {
                AsyncImage(
                    model = safeDirectImageUri,
                    contentDescription = "$title project photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            decoded != null -> {
                Image(
                    bitmap = decoded.asImageBitmap(),
                    contentDescription = "$title project photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            resolvedPreset.drawableRes != null -> {
                AsyncImage(
                    model = resolvedPreset.drawableRes,
                    contentDescription = "$title project cover",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.34f)),
                        startY = 140f
                    )
                )
        )
    }
}

@Composable
fun CraftBadge(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun EmptyAtelierCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Handyman,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.FilledTonalButton(
                    onClick = onAction,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun AtelierStatusBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}
