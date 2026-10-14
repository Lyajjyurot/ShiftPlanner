package com.shiftplanner.app

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

class PlannerApp : Application() {

    override fun onCreate() {
        super.onCreate()

        runCatching { CrashRecorder.install(this) }

        val oldReceiver = Intent().setClassName(packageName, "$packageName.reminder.AlarmReceiver")
        val pending = PendingIntent.getBroadcast(
            this, 1001, oldReceiver,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            (getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
            pending.cancel()
        }
    }
}
