package com.example.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.CoverPreset
import com.example.model.CraftProject
import com.example.model.DifficultyLevel
import com.example.model.HobbyCategory
import com.example.model.PostType
import com.example.model.TutorialStep
import com.example.model.UiState
import com.example.ui.components.EmptyAtelierCard
import com.example.ui.components.ImageSourcePickerSheet
import com.example.ui.components.ProjectCoverArt
import com.example.ui.components.rememberMediaPickerController
import com.example.viewmodel.CraftViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateProjectScreen(
    viewModel: CraftViewModel,
    editingProjectId: String? = null,
    onNavigateBack: () -> Unit,
    onProjectCreated: (String) -> Unit
) {
    val isEditMode = !editingProjectId.isNullOrBlank()
    val context = LocalContext.current

    val editProjectState by if (isEditMode) {
        remember(editingProjectId) { viewModel.observeProject(editingProjectId!!) }
            .collectAsStateWithLifecycle(initialValue = UiState.Loading)
    } else {
        remember { mutableStateOf<UiState<CraftProject?>>(UiState.Success(null)) }
    }

    var hasPopulatedEditFields by remember(editingProjectId) { mutableStateOf(!isEditMode) }
    var selectedCategory by remember { mutableStateOf("Project") }
    var initialCoverImageData by remember { mutableStateOf<String?>(null) }

    var title by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var selectedCoverPreset by remember { mutableStateOf(CoverPreset.STUDIO) }
    var existingCoverImageData by remember { mutableStateOf<String?>(null) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showImageSourceSheet by remember { mutableStateOf(false) }

    // Optional DIY Tutorial details
    var showTutorialDetails by remember { mutableStateOf(false) }
    var selectedDifficulty by remember { mutableStateOf(DifficultyLevel.Beginner) }
    var estimatedTime by remember { mutableStateOf("") }
    var materialInput by remember { mutableStateOf("") }
    val materials = remember { mutableStateListOf<String>() }
    val steps = remember { mutableStateListOf<TutorialStep>() }

    var isSubmitting by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var attemptedSubmit by remember { mutableStateOf(false) }

    LaunchedEffect(editProjectState, editingProjectId) {
        if (isEditMode && !hasPopulatedEditFields) {
            val loaded = (editProjectState as? UiState.Success<CraftProject?>)?.data
            if (loaded != null) {
                title = loaded.title
                summary = loaded.summary
                selectedCategory = loaded.category.ifBlank { "Project" }
                selectedCoverPreset = CoverPreset.fromKey(loaded.coverPreset)
                existingCoverImageData = loaded.coverImageData
                initialCoverImageData = loaded.coverImageData
                selectedDifficulty = DifficultyLevel.fromRaw(loaded.difficulty)
                estimatedTime = loaded.estimatedTime
                materials.clear()
                materials.addAll(loaded.materials)
                steps.clear()
                steps.addAll(loaded.steps)
                if (loaded.materials.isNotEmpty() || loaded.steps.isNotEmpty() || loaded.estimatedTime.isNotBlank()) {
                    showTutorialDetails = true
                }
                hasPopulatedEditFields = true
            }
        }
    }

    val mediaPicker = rememberMediaPickerController(
        onImagePicked = { uri ->
            selectedImageUri = uri
            validationError = null
        },
        onPermissionDenied = { msg ->
            validationError = msg
        }
    )

    val previewImageSource = selectedImageUri?.toString() ?: existingCoverImageData
    val hasCustomPhoto = !previewImageSource.isNullOrBlank()
    val titleTooShort = attemptedSubmit && title.trim().length < 3
    val summaryTooShort = attemptedSubmit && summary.trim().length < 5

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditMode) "Edit Project" else "Upload Project",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("create_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Cancel"
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
                .padding(innerPadding)
                .navigationBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (isEditMode && !hasPopulatedEditFields) {
                when (val s = editProjectState) {
                    is UiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Error -> {
                        EmptyAtelierCard(title = "Unable to open editor", subtitle = s.message)
                    }
                    is UiState.Success -> {
                        if (s.data == null) {
                            EmptyAtelierCard(
                                title = "Project not found",
                                subtitle = "This post may have been removed."
                            )
                        }
                    }
                }
                return@Box
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Interactive Project Image Picker (Camera + Gallery -> Firebase Storage)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Project Image", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (selectedImageUri != null) {
                            "Ready to upload to Cloud Storage"
                        } else {
                            "Camera or Gallery"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selectedImageUri != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(195.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(enabled = !isSubmitting) {
                            showImageSourceSheet = true
                        }
                        .testTag("pick_photo_card")
                ) {
                    if (hasCustomPhoto || selectedCoverPreset != CoverPreset.NONE) {
                        ProjectCoverArt(
                            coverPreset = selectedCoverPreset.key,
                            coverImageData = previewImageSource,
                            title = title.ifBlank { "Project Preview" },
                            height = 195.dp,
                            allowLocalPreviewUri = selectedImageUri != null
                        )
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Tap to capture or choose a photo",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hasCustomPhoto) {
                            FilledTonalButton(
                                onClick = {
                                    selectedImageUri = null
                                    existingCoverImageData = null
                                },
                                enabled = !isSubmitting,
                                modifier = Modifier
                                    .height(40.dp)
                                    .testTag("clear_photo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Remove custom photo",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Clear")
                            }
                        }
                        FilledTonalButton(
                            onClick = { mediaPicker.captureWithCamera() },
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("camera_photo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoCamera,
                                contentDescription = "Take photo with camera",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("Camera")
                        }
                        FilledTonalButton(
                            onClick = { mediaPicker.pickFromGallery() },
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("pick_photo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoLibrary,
                                contentDescription = "Choose photo from gallery",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("Gallery")
                        }
                    }

                    if (isSubmitting && selectedImageUri != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color.White)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Uploading image...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                if (!hasCustomPhoto) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CoverPreset.entries, key = { it.key }) { preset ->
                            FilterChip(
                                selected = selectedCoverPreset == preset,
                                onClick = { selectedCoverPreset = preset },
                                label = { Text(preset.label) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        if (it.length <= 100) {
                            title = it
                            if (validationError != null) validationError = null
                        }
                    },
                    label = { Text("Project Title") },
                    placeholder = { Text("e.g., Hand-Carved Walnut Espresso Tray") },
                    singleLine = true,
                    isError = titleTooShort,
                    supportingText = {
                        Text(
                            if (titleTooShort) "Enter at least 3 characters"
                            else "${title.length}/100"
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_title_input")
                )

                OutlinedTextField(
                    value = summary,
                    onValueChange = {
                        if (it.length <= 1500) {
                            summary = it
                            if (validationError != null) validationError = null
                        }
                    },
                    label = { Text("Project Description") },
                    placeholder = { Text("Describe your build, techniques, or story...") },
                    minLines = 3,
                    maxLines = 6,
                    isError = summaryTooShort,
                    supportingText = {
                        Text(
                            if (summaryTooShort) "Enter at least 5 characters"
                            else "${summary.length}/1500"
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_summary_input")
                )

                Text(
                    text = "Category (optional)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("create_category_row")
                ) {
                    items(HobbyCategory.creatableCategories, key = { it.name }) { hobbyCat ->
                        val isSelected = selectedCategory.equals(hobbyCat.label, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedCategory = if (isSelected) "Project" else hobbyCat.label
                            },
                            label = { Text(hobbyCat.label) },
                            modifier = Modifier.testTag("create_category_${hobbyCat.name}")
                        )
                    }
                }

                // Optional DIY Steps & Materials Drawer
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showTutorialDetails = !showTutorialDetails
                                    if (showTutorialDetails && steps.isEmpty()) {
                                        steps.add(TutorialStep(stepNumber = 1L, title = "", instruction = "", tip = ""))
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Add DIY Steps & Materials (optional)",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Expand only if you want to include a step-by-step guide",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = if (showTutorialDetails) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = "Toggle DIY tutorial fields"
                            )
                        }

                        AnimatedVisibility(visible = showTutorialDetails) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    DifficultyLevel.entries.forEach { level ->
                                        FilterChip(
                                            selected = selectedDifficulty == level,
                                            onClick = { selectedDifficulty = level },
                                            label = { Text(level.label) }
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = estimatedTime,
                                    onValueChange = { if (it.length <= 40) estimatedTime = it },
                                    label = { Text("Estimated Build Time") },
                                    placeholder = { Text("e.g., 2 hours") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = materialInput,
                                        onValueChange = { if (it.length <= 60) materialInput = it },
                                        placeholder = { Text("Add material or tool") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            val clean = materialInput.trim()
                                            if (clean.isNotEmpty() && materials.size < 20) {
                                                materials.add(clean)
                                                materialInput = ""
                                            }
                                        },
                                        modifier = Modifier.height(52.dp)
                                    ) {
                                        Text("Add")
                                    }
                                }

                                if (materials.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        materials.forEachIndexed { idx, mat ->
                                            InputChip(
                                                selected = false,
                                                onClick = { materials.removeAt(idx) },
                                                label = { Text(mat) },
                                                trailingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Close,
                                                        contentDescription = "Remove $mat",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Steps", style = MaterialTheme.typography.titleSmall)
                                    if (steps.size < 15) {
                                        OutlinedButton(
                                            onClick = {
                                                steps.add(
                                                    TutorialStep(
                                                        stepNumber = (steps.size + 1).toLong(),
                                                        title = "",
                                                        instruction = "",
                                                        tip = ""
                                                    )
                                                )
                                            }
                                        ) {
                                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Add Step")
                                        }
                                    }
                                }

                                steps.forEachIndexed { index, step ->
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.background,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Step ${index + 1}",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                IconButton(
                                                    onClick = { steps.removeAt(index) },
                                                    modifier = Modifier.minimumInteractiveComponentSize()
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.DeleteOutline,
                                                        contentDescription = "Remove step",
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                            OutlinedTextField(
                                                value = step.title,
                                                onValueChange = { v -> steps[index] = step.copy(title = v.take(80)) },
                                                label = { Text("Step Title") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            OutlinedTextField(
                                                value = step.instruction,
                                                onValueChange = { v -> steps[index] = step.copy(instruction = v.take(400)) },
                                                label = { Text("Instructions") },
                                                minLines = 2,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (validationError != null) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = validationError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        attemptedSubmit = true
                        val cleanTitle = title.trim()
                        val cleanSummary = summary.trim()
                        if (cleanTitle.length < 3) {
                            validationError = "Please enter a title of at least 3 characters."
                            return@Button
                        }
                        if (cleanSummary.length < 5) {
                            validationError = "Please enter a project description of at least 5 characters."
                            return@Button
                        }
                        validationError = null
                        isSubmitting = true
                        val validSteps = steps.filter { it.title.isNotBlank() || it.instruction.isNotBlank() }
                            .mapIndexed { idx, st ->
                                st.copy(
                                    stepNumber = (idx + 1).toLong(),
                                    title = st.title.trim().ifBlank { "Step ${idx + 1}" },
                                    instruction = st.instruction.trim().ifBlank { st.title.trim() },
                                    tip = st.tip.trim()
                                )
                            }
                        val derivedPostType = if (validSteps.isNotEmpty()) PostType.TUTORIAL.value else PostType.PROJECT.value

                        if (isEditMode && editingProjectId != null) {
                            viewModel.updateProject(
                                context = context,
                                projectId = editingProjectId,
                                title = cleanTitle,
                                summary = cleanSummary,
                                category = selectedCategory,
                                postType = derivedPostType,
                                difficulty = selectedDifficulty.label,
                                estimatedTime = estimatedTime.trim(),
                                materials = materials.toList(),
                                steps = validSteps,
                                coverPreset = selectedCoverPreset.key,
                                selectedImageUri = selectedImageUri,
                                coverImageData = existingCoverImageData,
                                previousCoverImageData = initialCoverImageData
                            ) { ok, errorMsg ->
                                isSubmitting = false
                                if (ok) {
                                    onProjectCreated(editingProjectId)
                                } else if (!errorMsg.isNullOrBlank()) {
                                    validationError = errorMsg
                                }
                            }
                        } else {
                            viewModel.createProject(
                                context = context,
                                title = cleanTitle,
                                summary = cleanSummary,
                                category = selectedCategory,
                                postType = derivedPostType,
                                difficulty = selectedDifficulty.label,
                                estimatedTime = estimatedTime.trim(),
                                materials = materials.toList(),
                                steps = validSteps,
                                coverPreset = selectedCoverPreset.key,
                                selectedImageUri = selectedImageUri
                            ) { ok, newId, errorMsg ->
                                isSubmitting = false
                                if (ok && newId != null) {
                                    onProjectCreated(newId)
                                } else if (!errorMsg.isNullOrBlank()) {
                                    validationError = errorMsg
                                }
                            }
                        }
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("publish_project_button"),
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = if (selectedImageUri != null) "Uploading photo..." else "Saving...",
                            style = MaterialTheme.typography.titleMedium
                        )
                    } else {
                        Icon(
                            imageVector = if (isEditMode) Icons.Outlined.Check else Icons.Outlined.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isEditMode) "Save Changes" else "Upload Project",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (showImageSourceSheet) {
        ImageSourcePickerSheet(
            title = if (isEditMode) "Update Project Photo" else "Add Project Photo",
            hasExistingPhoto = hasCustomPhoto,
            onDismiss = { showImageSourceSheet = false },
            onPickCamera = { mediaPicker.captureWithCamera() },
            onPickGallery = { mediaPicker.pickFromGallery() },
            onRemovePhoto = {
                selectedImageUri = null
                existingCoverImageData = null
            }
        )
    }
}
