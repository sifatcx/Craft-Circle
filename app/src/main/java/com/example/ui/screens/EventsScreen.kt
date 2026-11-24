package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CalendarIntegration
import com.example.model.EventFormat
import com.example.model.HobbyCategory
import com.example.model.MakerEvent
import com.example.model.UiState
import com.example.ui.components.CraftBadge
import com.example.ui.components.EmptyAtelierCard
import com.example.ui.components.MakerAvatar
import com.example.ui.theme.SuccessGreen
import com.example.util.ImageAndFormatUtils
import com.example.viewmodel.CraftViewModel
import java.util.Calendar

private enum class EventFilter(val label: String) {
    ALL("All Upcoming"),
    LIVE_STREAM("Live Stream"),
    IN_PERSON("In-Person"),
    HYBRID("Hybrid")
}

@Composable
fun EventsScreen(
    viewModel: CraftViewModel,
    showCreateSheet: Boolean,
    onDismissCreateSheet: () -> Unit,
    onOpenCreateSheet: () -> Unit,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val eventsState by viewModel.eventsState.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf(EventFilter.ALL) }
    val context = LocalContext.current

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "event_filters") {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(EventFilter.entries, key = { it.name }) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label) }
                        )
                    }
                }
            }

            when (val state = eventsState) {
                is UiState.Loading -> {
                    item(key = "events_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is UiState.Error -> {
                    item(key = "events_error") {
                        EmptyAtelierCard(title = "Could not load schedule", subtitle = state.message)
                    }
                }
                is UiState.Success -> {
                    val filtered = when (selectedFilter) {
                        EventFilter.ALL -> state.data
                        EventFilter.LIVE_STREAM -> state.data.filter { it.format == EventFormat.LIVE_STREAM.label }
                        EventFilter.IN_PERSON -> state.data.filter { it.format == EventFormat.IN_PERSON.label }
                        EventFilter.HYBRID -> state.data.filter { it.format == EventFormat.HYBRID.label }
                    }
                    if (filtered.isEmpty()) {
                        item(key = "events_empty") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                EmptyAtelierCard(
                                    title = "No scheduled workshops yet",
                                    subtitle = "Host a live DIY stream, local craft swap, or hands-on meetup for fellow makers."
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = onOpenCreateSheet,
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("empty_host_event_button")
                                ) {
                                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Host Workshop")
                                }
                            }
                        }
                    } else {
                        items(filtered, key = { it.id }) { event ->
                            val isGoing by viewModel.observeIsEventGoing(event.id)
                                .collectAsStateWithLifecycle(initialValue = false)

                            MakerEventCard(
                                event = event,
                                currentUserId = viewModel.currentUserId,
                                isGoing = isGoing,
                                onToggleRsvp = { viewModel.toggleRsvp(event, isGoing) },
                                onAddToCalendar = { CalendarIntegration.addEventToCalendar(context, event) },
                                onMessageHost = {
                                    viewModel.openDirectChat(
                                        otherUserId = event.hostId,
                                        otherName = event.hostName,
                                        otherPhotoUrl = event.hostPhotoUrl,
                                        onReady = onOpenConversation
                                    )
                                },
                                onDeleteEvent = { viewModel.deleteEvent(event.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        CreateEventSheet(
            onDismiss = onDismissCreateSheet,
            onCreate = { title, desc, cat, fmt, loc, mat, startMillis, dur, maxAtt ->
                viewModel.createEvent(
                    title = title,
                    description = desc,
                    category = cat,
                    format = fmt,
                    locationOrLink = loc,
                    materialsNeeded = mat,
                    startTimeMillis = startMillis,
                    durationMinutes = dur,
                    maxAttendees = maxAtt
                ) { created ->
                    if (created != null) {
                        onDismissCreateSheet()
                    }
                }
            }
        )
    }
}

@Composable
fun MakerEventCard(
    event: MakerEvent,
    currentUserId: String,
    isGoing: Boolean,
    onToggleRsvp: () -> Unit,
    onAddToCalendar: () -> Unit,
    onMessageHost: () -> Unit,
    onDeleteEvent: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (monthBadge, dayBadge) = remember(event.startTimeMillis) {
        ImageAndFormatUtils.formatEventDayBadge(event.startTimeMillis)
    }
    val formattedDate = remember(event.startTimeMillis) {
        ImageAndFormatUtils.formatEventDateTime(event.startTimeMillis)
    }
    val isFull = !isGoing && event.attendeeCount >= event.maxAttendees

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("event_card_${event.id}"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.width(54.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = monthBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = dayBadge,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CraftBadge(text = event.format)
                        CraftBadge(
                            text = event.category,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MakerAvatar(
                            name = event.hostName,
                            photoUrl = event.hostPhotoUrl,
                            size = 22.dp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Hosted by ${event.hostName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (event.hostId == currentUserId) {
                    IconButton(onClick = onDeleteEvent) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Cancel event",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else if (event.hostId.isNotBlank()) {
                    IconButton(onClick = onMessageHost) {
                        Icon(
                            imageVector = Icons.Outlined.MailOutline,
                            contentDescription = "Message host ${event.hostName}",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = event.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "$formattedDate (${event.durationMinutes} min)",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = event.locationOrLink,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${event.attendeeCount} / ${event.maxAttendees} makers going",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (event.materialsNeeded.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "Bring: ${event.materialsNeeded}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onToggleRsvp,
                    enabled = !isFull && event.status == "upcoming",
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isGoing) SuccessGreen else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("rsvp_button_${event.id}")
                ) {
                    Icon(
                        imageVector = if (isGoing) Icons.Filled.CheckCircle else Icons.Outlined.EventAvailable,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            isGoing -> "Going"
                            isFull -> "Full"
                            else -> "RSVP"
                        }
                    )
                }

                OutlinedButton(
                    onClick = onAddToCalendar,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("calendar_button_${event.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = "Add to Calendar",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Calendar")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CreateEventSheet(
    onDismiss: () -> Unit,
    onCreate: (
        title: String,
        description: String,
        category: String,
        format: String,
        locationOrLink: String,
        materialsNeeded: String,
        startTimeMillis: Long,
        durationMinutes: Long,
        maxAttendees: Long
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(HobbyCategory.WOODWORKING) }
    var format by remember { mutableStateOf(EventFormat.LIVE_STREAM) }
    var locationOrLink by remember { mutableStateOf("") }
    var materialsNeeded by remember { mutableStateOf("") }
    var durationText by remember { mutableStateOf("60") }
    var maxAttendeesText by remember { mutableStateOf("25") }
    var errorText by remember { mutableStateOf<String?>(null) }

    val defaultCal = remember {
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
    }
    var selectedStartMillis by remember { mutableStateOf(defaultCal.timeInMillis) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Host Workshop or Meetup", style = MaterialTheme.typography.headlineSmall)

            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 100) title = it },
                label = { Text("Event Title") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("event_title_input")
            )
            OutlinedTextField(
                value = description,
                onValueChange = { if (it.length <= 1000) description = it },
                label = { Text("Details") },
                minLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("event_desc_input")
            )

            Text("Format", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EventFormat.entries.forEach { fmt ->
                    FilterChip(
                        selected = format == fmt,
                        onClick = { format = fmt },
                        label = { Text(fmt.label) }
                    )
                }
            }

            Text("Hobby Circle", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(HobbyCategory.creatableCategories, key = { it.name }) { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat.label) }
                    )
                }
            }

            OutlinedTextField(
                value = locationOrLink,
                onValueChange = { if (it.length <= 200) locationOrLink = it },
                label = {
                    Text(
                        if (format == EventFormat.LIVE_STREAM) "Stream Link"
                        else "Studio Address or Link"
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("event_location_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("Pick Date") }
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("Pick Time") }
            }
            Text(
                text = "Starts: ${ImageAndFormatUtils.formatEventDateTime(selectedStartMillis)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it.filter(Char::isDigit).take(3) },
                    label = { Text("Mins") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = maxAttendeesText,
                    onValueChange = { maxAttendeesText = it.filter(Char::isDigit).take(3) },
                    label = { Text("Max Seats") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = materialsNeeded,
                onValueChange = { if (it.length <= 300) materialsNeeded = it },
                label = { Text("Materials Needed (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (errorText != null) {
                Text(
                    text = errorText!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    if (title.trim().length < 3) {
                        errorText = "Title must be at least 3 characters."
                        return@Button
                    }
                    if (description.trim().length < 5) {
                        errorText = "Description must be at least 5 characters."
                        return@Button
                    }
                    if (locationOrLink.trim().length < 2) {
                        errorText = "Location or stream link is required."
                        return@Button
                    }
                    val dur = (durationText.toLongOrNull() ?: 60L).coerceIn(15L, 720L)
                    val seats = (maxAttendeesText.toLongOrNull() ?: 25L).coerceIn(1L, 500L)
                    onCreate(
                        title.trim(),
                        description.trim(),
                        category.label,
                        format.label,
                        locationOrLink.trim(),
                        materialsNeeded.trim(),
                        selectedStartMillis,
                        dur,
                        seats
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("confirm_create_event_button")
            ) {
                Text("Schedule Workshop")
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = selectedStartMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val picked = dateState.selectedDateMillis
                    if (picked != null) {
                        val oldCal = Calendar.getInstance().apply { timeInMillis = selectedStartMillis }
                        val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = picked
                        }
                        val merged = Calendar.getInstance().apply {
                            set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
                            set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
                            set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
                            set(Calendar.HOUR_OF_DAY, oldCal.get(Calendar.HOUR_OF_DAY))
                            set(Calendar.MINUTE, oldCal.get(Calendar.MINUTE))
                            set(Calendar.SECOND, 0)
                        }
                        selectedStartMillis = merged.timeInMillis
                    }
                    showDatePicker = false
                }) { Text("Set Date") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTimePicker) {
        val cal = remember(selectedStartMillis) {
            Calendar.getInstance().apply { timeInMillis = selectedStartMillis }
        }
        val timeState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE)
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Pick Start Time") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    val updated = Calendar.getInstance().apply {
                        timeInMillis = selectedStartMillis
                        set(Calendar.HOUR_OF_DAY, timeState.hour)
                        set(Calendar.MINUTE, timeState.minute)
                        set(Calendar.SECOND, 0)
                    }
                    selectedStartMillis = updated.timeInMillis
                    showTimePicker = false
                }) { Text("Set Time") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        )
    }
}
