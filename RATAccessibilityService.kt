package com.mycontrol.mdm.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent

class RATAccessibilityService : AccessibilityService() {

    private val touchReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val x = intent.getDoubleExtra("x", 0.0).toFloat()
            val y = intent.getDoubleExtra("y", 0.0).toFloat()
            val action = intent.getStringExtra("action") ?: "TAP"
            performTouch(x, y, action)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val filter = IntentFilter("com.mycontrol.mdm.TOUCH_EVENT")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(touchReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(touchReceiver, filter)
        }
    }

    private fun performTouch(x: Float, y: Float, action: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply { moveTo(x, y) }
            val strokeDesc = GestureDescription.StrokeDescription(path, 0, 100)
            val gesture = GestureDescription.Builder().addStroke(strokeDesc).build()
            dispatchGesture(gesture, null, null)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(touchReceiver) } catch (e: Exception) {}
    }
}
