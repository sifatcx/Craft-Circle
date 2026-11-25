package com.example.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.example.model.MakerEvent

object CalendarIntegration {
    fun addEventToCalendar(context: Context, event: MakerEvent) {
        val endTime = event.startTimeMillis + (event.durationMinutes * 60_000L)
        val safeTitle = event.title.trim().take(120)
        val safeDescription = buildString {
            append(event.description.trim().take(1000))
            if (event.materialsNeeded.isNotBlank()) {
                append("\n\nMaterials: ${event.materialsNeeded.trim().take(300)}")
            }
        }
        val safeLocation = event.locationOrLink.trim().take(200)
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, safeTitle)
            putExtra(CalendarContract.Events.DESCRIPTION, safeDescription)
            putExtra(CalendarContract.Events.EVENT_LOCATION, safeLocation)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startTimeMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        runCatching {
            context.startActivity(intent)
        }
    }
}
