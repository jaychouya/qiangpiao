package com.tickethunter

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusView: TextView
    private lateinit var btnResume: Button
    private val refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            statusView.text = TicketMonitorService.statusText
            btnResume.visibility = if (TicketMonitorService.monitorState == MonitorState.PAUSED) {
                View.VISIBLE
            } else {
                View.GONE
            }
            refreshHandler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val platformGroup = findViewById<RadioGroup>(R.id.platformGroup)
        val eventInput = findViewById<EditText>(R.id.eventInput)
        val intervalInput = findViewById<EditText>(R.id.intervalInput)
        val tierInput = findViewById<EditText>(R.id.tierInput)
        val quantityInput = findViewById<EditText>(R.id.quantityInput)
        statusView = findViewById(R.id.statusText)
        btnResume = findViewById(R.id.btnResume)

        intervalInput.setText("2000")

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnBattery).setOnClickListener {
            requestBatteryExemption()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            val platform = if (platformGroup.checkedRadioButtonId == R.id.radioMaoyan) {
                Platform.MAOYAN
            } else {
                Platform.DAMAI
            }
            val tier = tierInput.text.toString().toIntOrNull()
            if (tier == null) {
                Toast.makeText(this, "请输入指定票档", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val eventName = eventInput.text.toString().ifBlank { "未命名场次" }
            val interval = intervalInput.text.toString().toLongOrNull()?.coerceIn(1000, 5000) ?: 2000
            val quantity = quantityInput.text.toString().toIntOrNull()?.coerceIn(1, 6) ?: 1
            val task = TicketTask(platform, eventName, targetTier = tier, refreshIntervalMs = interval, quantity = quantity)
            TicketMonitorService.instance?.startMonitor(task)
                ?: Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            TicketMonitorService.instance?.stopMonitor()
        }

        btnResume.setOnClickListener {
            TicketMonitorService.instance?.resumeFromPause()
        }

        findViewById<Button>(R.id.btnOpenApp).setOnClickListener {
            val platform = if (platformGroup.checkedRadioButtonId == R.id.radioMaoyan) {
                Platform.MAOYAN
            } else {
                Platform.DAMAI
            }
            packageManager.getLaunchIntentForPackage(platform.packageName)?.let {
                startActivity(it)
            } ?: Toast.makeText(this, "未安装${platform.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestBatteryExemption() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, "已在白名单", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshHandler.post(refreshRunnable)
    }

    override fun onPause() {
        refreshHandler.removeCallbacks(refreshRunnable)
        super.onPause()
    }
}
