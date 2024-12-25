package com.rick.fps_app

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Handler
import android.widget.RemoteViews
import androidx.core.content.ContextCompat.registerReceiver
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException

class AppMonitorWidget : AppWidgetProvider() {

    private val handler = Handler()
    private var updateRunnable: Runnable? = null
    private lateinit var fpsMonitor: FPSMonitor

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        fpsMonitor = FPSMonitor(1000)
        fpsMonitor.startMonitoring()

        startPeriodicUpdates(context, appWidgetManager, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        stopPeriodicUpdates()
    }

    private fun startPeriodicUpdates(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateRunnable = object : Runnable {
            override fun run() {
                for (appWidgetId in appWidgetIds) {
                    updateWidget(context, appWidgetManager, appWidgetId)
                }
                handler.postDelayed(this, 1000) // Schedule the next update in 1 second
            }
        }
        handler.post(updateRunnable!!)
    }

    private fun stopPeriodicUpdates() {
        handler.removeCallbacks(updateRunnable!!)
    }

    @SuppressLint("RemoteViewLayout")
    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_layout)

        remoteViews.setTextViewText(R.id.cpuTempValue, getCpuTemperature() + " °C")
        remoteViews.setTextViewText(R.id.gpuTempValue, getGpuTemperature() + " °C")
        remoteViews.setTextViewText(R.id.gpuFanValue, getGpuFanSpeed() + " RPM")
        remoteViews.setTextViewText(R.id.batteryValue, getBatteryTemperature(context))

        val fps = fpsMonitor.calculateFPS()
        val roundedFps = fps.toInt()
        remoteViews.setTextViewText(R.id.fpsValues, "$roundedFps FPS")

        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
    }


    @SuppressLint("SetTextI18n")
    fun getBatteryTemperature(context: Context): String {
        val intent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temperature = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)

        val tempInCelsius = temperature?.div(10.0)?.toInt() ?: 0
        val tempInFahrenheit = ((tempInCelsius * 9) / 5 + 32).toInt()

        return "$tempInCelsius°C / $tempInFahrenheit°F"
    }

    private fun listThermalZones(): List<String> {
        val zones = mutableListOf<String>()
        try {
            val dir = File("/sys/class/thermal/")
            if (dir.exists()) {
                val files = dir.listFiles()
                files?.forEach { file ->
                    if (file.isDirectory) {
                        zones.add(file.name)
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return zones
    }

    private fun getGpuTemperature(): String? {
        return try {
            val zones = listThermalZones()
            val gpuZone = zones.find { it.contains("gpu", ignoreCase = true) } ?: zones.firstOrNull()

            gpuZone?.let {
                val reader = BufferedReader(FileReader("/sys/class/thermal/$it/temp"))
                val tempStr = reader.readLine()
                reader.close()

                val tempInCelsius = tempStr?.toFloatOrNull()?.div(1000)
                if (tempInCelsius != null) {
                    val tempInFahrenheit = tempInCelsius * 9 / 5 + 32
                    "${tempInCelsius}°C / ${tempInFahrenheit}°F"
                } else {
                    null
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun getCpuTemperature(): String? {
        return try {
            val reader = BufferedReader(FileReader("/sys/class/thermal/thermal_zone0/temp"))
            val tempStr = reader.readLine()
            reader.close()

            val tempInCelsius = tempStr?.toFloatOrNull()?.div(1000)
            if (tempInCelsius != null) {
                val tempInFahrenheit = tempInCelsius * 9 / 5 + 32
                "${tempInCelsius}°C / ${tempInFahrenheit}°F"
            } else {
                null
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun getGpuFanSpeed(): String? {
        try {
            val zones = listThermalZones()
            val fanZone = zones.find { it.contains("fan", ignoreCase = true) }
            fanZone?.let {
                val fanSpeedFile = File("/sys/class/thermal/$it/fan_speed")
                if (fanSpeedFile.exists()) {
                    val reader = BufferedReader(FileReader(fanSpeedFile))
                    val fanSpeed = reader.readLine()
                    reader.close()
                    return fanSpeed
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return "No Fan"
    }
}
