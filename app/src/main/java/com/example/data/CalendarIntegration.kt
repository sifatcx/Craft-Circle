package com.example.data

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.example.model.MakerEvent

object CalendarIntegration {
    fun addEventToCalendar(context: Context, event: MakerEvent) {
        val endTime = event.startTimeMillis + (event.durationMinutes * 60_000L)
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, event.title)
            putExtra(
                CalendarContract.Events.DESCRIPTION,
                buildString {
                    append(event.description)
                    if (event.materialsNeeded.isNotBlank()) {
                        append("\n\nMaterials: ${event.materialsNeeded}")
                    }
                }
            )
            putExtra(CalendarContract.Events.EVENT_LOCATION, event.locationOrLink)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startTimeMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
        }
        runCatching {
            context.startActivity(intent)
        }
    }
}
