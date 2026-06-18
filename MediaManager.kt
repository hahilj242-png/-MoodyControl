package com.mycontrol.mdm.managers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

class MediaManager(private val context: Context) {

    fun recordAudio(durationSec: Int, outputPath: String): String? {
        return try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioBitRate(128000)
                setOutputFile(outputPath)
                prepare()
                start()
            }

            Thread.sleep(durationSec * 1000L)
            recorder.stop()
            recorder.release()

            val file = File(outputPath)
            if (file.exists()) {
                val bytes = file.readBytes()
                file.delete()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } else null
        } catch (e: Exception) { null }
    }

    fun encodeImageToBase64(imageBytes: ByteArray): String {
        return try {
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            bitmap.recycle()
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) { "" }
    }
}
