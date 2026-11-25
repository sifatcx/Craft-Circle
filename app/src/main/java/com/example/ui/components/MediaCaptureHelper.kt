package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.util.ImageAndFormatUtils

@Stable
class MediaPickerController(
    private val onLaunchGallery: () -> Unit,
    private val onLaunchCamera: () -> Unit
) {
    fun pickFromGallery() = onLaunchGallery()
    fun captureWithCamera() = onLaunchCamera()
}

@Composable
fun rememberMediaPickerController(
    onImagePicked: (Uri) -> Unit,
    onPermissionDenied: (String) -> Unit
): MediaPickerController {
    val context = LocalContext.current
    var pendingCameraUriString by rememberSaveable { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onImagePicked(uri)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uriStr = pendingCameraUriString
        pendingCameraUriString = null
        if (success && !uriStr.isNullOrBlank()) {
            onImagePicked(Uri.parse(uriStr))
        }
    }

    val launchCameraWithUri = remember(context, cameraLauncher) {
        {
            runCatching {
                val uri = ImageAndFormatUtils.createCameraCaptureUri(context)
                pendingCameraUriString = uri.toString()
                cameraLauncher.launch(uri)
            }.onFailure { err ->
                pendingCameraUriString = null
                onPermissionDenied(err.localizedMessage ?: "No camera app is available on this device.")
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraWithUri()
        } else {
            onPermissionDenied("Camera permission is required to take a photo. You can grant it in Settings or choose from your gallery.")
        }
    }

    return remember(context, galleryLauncher, cameraPermissionLauncher, launchCameraWithUri) {
        MediaPickerController(
            onLaunchGallery = {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onLaunchCamera = {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    launchCameraWithUri()
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageSourcePickerSheet(
    title: String = "Add Project Photo",
    hasExistingPhoto: Boolean,
    onDismiss: () -> Unit,
    onPickCamera: () -> Unit,
    onPickGallery: () -> Unit,
    onRemovePhoto: (() -> Unit)? = null
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("image_source_picker_sheet"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }

            ImageSourceOptionCard(
                icon = Icons.Outlined.PhotoCamera,
                title = "Take Photo",
                subtitle = "Use your device camera to capture your craft",
                onClick = {
                    onDismiss()
                    onPickCamera()
                },
                testTag = "sheet_take_photo_option"
            )

            ImageSourceOptionCard(
                icon = Icons.Outlined.PhotoLibrary,
                title = "Choose from Gallery",
                subtitle = "Select an image from your device photo library",
                onClick = {
                    onDismiss()
                    onPickGallery()
                },
                testTag = "sheet_pick_gallery_option"
            )

            if (hasExistingPhoto && onRemovePhoto != null) {
                ImageSourceOptionCard(
                    icon = Icons.Outlined.DeleteOutline,
                    title = "Remove Photo",
                    subtitle = "Clear the currently selected image",
                    isDestructive = true,
                    onClick = {
                        onDismiss()
                        onRemovePhoto()
                    },
                    testTag = "sheet_remove_photo_option"
                )
            }
        }
    }
}

@Composable
private fun ImageSourceOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
    testTag: String
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (isDestructive) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isDestructive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
