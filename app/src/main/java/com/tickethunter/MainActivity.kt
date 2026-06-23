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
    private lateinit var btnStart: Button
    private lateinit var radioDamai: RadioButton
    private lateinit var radioMaoyan: RadioButton
    private val refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            statusView.text = TicketMonitorService.statusText
            btnResume.visibility = if (TicketMonitorService.monitorState == MonitorState.PAUSED) {
                View.VISIBLE
            } else {
                View.GONE
            }
            updateInstallStatus()
            updateStartButton()
            refreshHandler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val platformGroup = findViewById<RadioGroup>(R.id.platformGroup)
        val eventInput = findViewById<EditText>(R.id.eventInput)
        val intervalMinInput = findViewById<EditText>(R.id.intervalMinInput)
        val intervalMaxInput = findViewById<EditText>(R.id.intervalMaxInput)
        val tierInput = findViewById<EditText>(R.id.tierInput)
        val quantityInput = findViewById<EditText>(R.id.quantityInput)
        statusView = findViewById(R.id.statusText)
        btnResume = findViewById(R.id.btnResume)
        btnStart = findViewById(R.id.btnStart)
        radioDamai = findViewById(R.id.radioDamai)
        radioMaoyan = findViewById(R.id.radioMaoyan)

        intervalMinInput.setText("2000")
        intervalMaxInput.setText("6000")
        tierInput.setText("580,380")

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnBattery).setOnClickListener {
            requestBatteryExemption()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        platformGroup.setOnCheckedChangeListener { _, _ ->
            updateInstallStatus()
            updateStartButton()
        }

        btnStart.setOnClickListener {
            val platform = selectedPlatform()
            if (!AppDetector.isInstalled(packageManager, platform)) {
                Toast.makeText(this, "未安装${platform.label}", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val tiers = TierParser.parse(tierInput.text.toString())
            if (tiers.isEmpty()) {
                Toast.makeText(this, "请输入票档，如 580,380", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val eventName = eventInput.text.toString().ifBlank { "未命名场次" }
            val minInterval = intervalMinInput.text.toString().toLongOrNull()?.coerceIn(1000, 10000) ?: 2000
            val maxInterval = intervalMaxInput.text.toString().toLongOrNull()?.coerceIn(1000, 10000) ?: 6000
            val quantity = quantityInput.text.toString().toIntOrNull()?.coerceIn(1, 6) ?: 1
            val task = TicketTask(
                platform = platform,
                eventName = eventName,
                targetTiers = tiers,
                refreshMinMs = minInterval,
                refreshMaxMs = maxInterval,
                quantity = quantity
            )
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
            val platform = selectedPlatform()
            val intent = AppDetector.launchIntent(packageManager, platform)
            if (intent != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "未安装${platform.label}", Toast.LENGTH_SHORT).show()
            }
        }

        updateInstallStatus()
        updateStartButton()
    }

    private fun selectedPlatform(): Platform =
        if (findViewById<RadioGroup>(R.id.platformGroup).checkedRadioButtonId == R.id.radioMaoyan) {
            Platform.MAOYAN
        } else {
            Platform.DAMAI
        }

    private fun updateInstallStatus() {
        val damaiOk = AppDetector.isInstalled(packageManager, Platform.DAMAI)
        val maoyanOk = AppDetector.isInstalled(packageManager, Platform.MAOYAN)
        radioDamai.text = if (damaiOk) "大麦 ✅" else "大麦 ❌"
        radioMaoyan.text = if (maoyanOk) "猫眼 ✅" else "猫眼 ❌"
    }

    private fun updateStartButton() {
        val platform = selectedPlatform()
        val installed = AppDetector.isInstalled(packageManager, platform)
        btnStart.isEnabled = installed
        btnStart.alpha = if (installed) 1f else 0.5f
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
        updateInstallStatus()
        updateStartButton()
        refreshHandler.post(refreshRunnable)
    }

    override fun onPause() {
        refreshHandler.removeCallbacks(refreshRunnable)
        super.onPause()
    }
}
