package com.mycontrol.mdm.managers

import android.content.Context
import com.mycontrol.mdm.utils.DeviceUtils

class DeviceManager(private val context: Context) {
    fun getDeviceInfo() = DeviceUtils.getDeviceInfo(context)
    fun getBattery() = DeviceUtils.getBatteryInfo(context)
    fun getNetwork() = DeviceUtils.getNetworkInfo(context)
    fun getPackages() = DeviceUtils.getInstalledPackages(context)
    fun getUptime() = DeviceUtils.getUptime()
}
