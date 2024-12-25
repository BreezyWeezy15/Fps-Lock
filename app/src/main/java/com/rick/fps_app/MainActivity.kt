package com.rick.fps_app

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.DragEvent
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.rick.fps_app.databinding.ActivityMainBinding
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var cpuInfoContainer : LinearLayout
    private lateinit var binding : ActivityMainBinding
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var fpsMonitor: FPSMonitor

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        if (!Settings.canDrawOverlays(this)) {
            val intent =
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, 1005)
        }

        showMain()

    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showMain() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.activity_main, null)
        val overlayImage = overlayView.findViewById<ImageView>(R.id.overlayImage)

        var isShown = false
        var isDragging = false // Flag to track dragging state

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 150

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        // Merged touch and click listener
        overlayImage.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Capture initial positions when touch begins
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false // Reset dragging flag on touch start
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    // Calculate the movement distance
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()

                    // If movement exceeds the threshold, treat it as dragging
                    if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                        if (!isDragging) {
                            isDragging = true // Mark as dragging once movement starts
                        }

                        // Update the position based on the touch movement
                        params.x = initialX + deltaX
                        params.y = initialY + deltaY

                        // Update the view's position in the window
                        if (overlayView.isAttachedToWindow) {
                            windowManager.updateViewLayout(overlayView, params)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    // Check if it's a click (no movement)
                    if (!isDragging) {
                        if (isShown) {
                            hideOverlayLayout()
                            isShown = false
                        } else {
                            showOverlayLayout()
                            displayCpuInfo()
                            isShown = true
                        }
                    }
                    // Reset dragging flag after action up
                    isDragging = false // Always reset dragging state
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    // Reset if the touch event is canceled
                    isDragging = false
                    true
                }

                else -> false
            }
        }

        // Add the view to the WindowManager
        windowManager.addView(overlayView, params)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlayLayout() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.widget_layout, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, // Full width
            WindowManager.LayoutParams.MATCH_PARENT, // Full height
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN, // Full screen
            PixelFormat.TRANSLUCENT
        )

        // Set gravity to top-left corner (default)
        params.gravity = Gravity.TOP or Gravity.START

        // Add the view to the WindowManager with the updated parameters
        windowManager.addView(overlayView, params)

        // Handle the close button click event
        val closeButton = overlayView.findViewById<Button>(R.id.close)
        closeButton.setOnClickListener {
            hideOverlayLayout() // Call the method to hide/remove the overlay
        }

        // For Android 11 and above, use WindowInsetsController to hide the status bar and navigation bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowInsetsController = window?.insetsController
            windowInsetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        } else {
            // For Android 10 and below, hide the status bar and navigation bar using systemUiVisibility
            overlayView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }

        // Start monitoring FPS (if required)
        fpsMonitor = FPSMonitor(1000)
        fpsMonitor.startMonitoring()

        // Update the overlay if required
        updateOverlay()
    }

    private fun hideOverlayLayout() {
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }
    private fun updateOverlay() {
        val cpuTempView = overlayView.findViewById<TextView>(R.id.cpuTempValue)
        val gpuTempView = overlayView.findViewById<TextView>(R.id.gpuTempValue)
        val gpuFanView = overlayView.findViewById<TextView>(R.id.gpuFanValue)
        val batteryView = overlayView.findViewById<TextView>(R.id.batteryValue)
        val batteryPercentage = overlayView.findViewById<TextView>(R.id.batteryPercentage)
        val fpsView = overlayView.findViewById<TextView>(R.id.fpsValues)
        val totalRam = overlayView.findViewById<TextView>(R.id.totalRam)
        val usedRam = overlayView.findViewById<TextView>(R.id.ramUsed)
        val availableRam = overlayView.findViewById<TextView>(R.id.ramAvailable)
        cpuInfoContainer = overlayView.findViewById(R.id.cpuInfoContainer)

        handler.postDelayed(object : Runnable {
            override fun run() {
                cpuTempView.text = getCpuTemperature()
                gpuTempView.text = getGpuTemperature()
                gpuFanView.text = getGpuFanSpeed()
                batteryView.text = getBatteryTemperature()
                batteryPercentage.text = "${getBatteryPercentage()}%"
                fpsView.text = "${fpsMonitor.calculateFPS()} FPS"
                totalRam.text = getTotalRam()
                usedRam.text = getUsedRam()
                availableRam.text = getAvailableRam()

                handler.postDelayed(this, 1000)
            }
        }, 1000)
    }
    private fun getBatteryTemperature(): String {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
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
    fun getBatteryPercentage(): Int {
        val intent: Intent = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            registerReceiver(null, ifilter)!!
        }
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)  // Battery level (0-100)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)  // Battery scale (max value)

        return if (level != -1 && scale != -1) {
            (level / scale.toFloat() * 100).toInt()  // Calculate the battery percentage
        } else {
            -1  // Error
        }
    }
    fun getTotalRam(): String {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val totalRam = memoryInfo.totalMem // Total RAM in bytes
        return formatRam(totalRam)
    }
    fun getAvailableRam(): String {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val availableRam = memoryInfo.availMem // Available RAM in bytes
        return formatRam(availableRam)
    }
    private fun getUsedRam(): String {
        val totalRam = getTotalRamInBytes()
        val availableRam = getAvailableRamInBytes()
        val usedRam = totalRam - availableRam
        return formatRam(usedRam)
    }
    private fun formatRam(bytes: Long): String {
        val gb = bytes.toDouble() / (1024 * 1024 * 1024)
        return String.format("%.1f GB", gb)
    }
    private fun getTotalRamInBytes(): Long {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.totalMem
    }
    private fun getAvailableRamInBytes(): Long {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.availMem
    }
    private fun displayCpuInfo() {
        val numberOfCores = getNumberOfCores()
        val cpuFrequencies = getCpuFrequencies(numberOfCores)

        // Temporary layout to hold rows of CPUs
        var rowLayout = LinearLayout(this)
        rowLayout.orientation = LinearLayout.HORIZONTAL
        rowLayout.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        rowLayout.setPadding(0, 0, 0, 16) // Add space between rows

        // Loop through each core and add a layout to display the information
        for (i in 0 until numberOfCores) {
            // Safely get the frequency, default to "Unknown" if out of bounds
            val frequency = cpuFrequencies.getOrNull(i) ?: "Unknown"

            // Skip "Unknown" frequencies
            if (frequency == "Unknown") continue

            // Create a vertical layout for each CPU and its frequency
            val cpuInfoLayout = LinearLayout(this)
            cpuInfoLayout.orientation = LinearLayout.VERTICAL
            cpuInfoLayout.layoutParams = LinearLayout.LayoutParams(
                0, // Use 0 width with weight to distribute space
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1f // Ensure the layout takes equal space in the row
            }
            cpuInfoLayout.setPadding(16, 0, 16, 0) // Add space between vertical layouts

            // Create TextViews for CPU core label and frequency
            val cpuCoreTextView = TextView(this)
            cpuCoreTextView.text = "CPU ${i + 1}"
            cpuCoreTextView.textSize = 16f
            cpuCoreTextView.setTextColor(Color.parseColor("#FFA500")) // Orange tint for CPU label

            val cpuFreqTextView = TextView(this)
            cpuFreqTextView.text = frequency
            cpuFreqTextView.textSize = 14f
            cpuFreqTextView.setTextColor(Color.WHITE) // White color for frequency value

            // Add TextViews to the CPU info layout
            cpuInfoLayout.addView(cpuCoreTextView)
            cpuInfoLayout.addView(cpuFreqTextView)

            // Add the vertical layout (with CPU info) to the row
            rowLayout.addView(cpuInfoLayout)

            // If the row has 3 CPU entries (or a set number), add it to the container and start a new row
            if (rowLayout.childCount == 3) {
                cpuInfoContainer.addView(rowLayout)
                rowLayout = LinearLayout(this)
                rowLayout.orientation = LinearLayout.HORIZONTAL
                rowLayout.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                rowLayout.setPadding(0, 0, 0, 16) // Add space between rows
            }
        }

        // Add any remaining rows (if the last one has less than 3 CPUs)
        if (rowLayout.childCount > 0) {
            cpuInfoContainer.addView(rowLayout)
        }
    }
    private fun getNumberOfCores(): Int {
        val path = "/sys/devices/system/cpu/"
        val coresFile = File(path)
        return coresFile.listFiles { file -> file.name.startsWith("cpu") }?.size ?: 1
    }
    private fun getCpuFrequencies(numberOfCores: Int): List<String> {
        val frequencies = mutableListOf<String>()
        for (i in 0 until numberOfCores) {
            val cpuFreqPath = "/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq"
            val cpuFreqFile = File(cpuFreqPath)
            if (cpuFreqFile.exists()) {
                try {
                    val frequency = cpuFreqFile.readText().trim()
                    val freqInGHz = (frequency.toInt() / 1000f).toString() // Convert to GHz
                    frequencies.add("$freqInGHz GHz")
                } catch (e: Exception) {
                    // If an error occurs, add an empty string to represent invalid data
                    frequencies.add("Unknown")
                }
            } else {
                // Add an empty string for cores that can't retrieve frequency data
                frequencies.add("Unknown")
            }
        }
        // Remove any empty or invalid entries
        return frequencies.filter { it != "Unknown" }
    }
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        windowManager.removeView(overlayView)
        fpsMonitor.stopMonitoring()
    }

}