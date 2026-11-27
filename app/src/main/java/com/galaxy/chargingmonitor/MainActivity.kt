package com.galaxy.chargingmonitor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var tvModelName: TextView
    private lateinit var tvMaxSpecBadge: TextView
    private lateinit var tvChargingStage: TextView
    private lateinit var tvLiveWatt: TextView
    private lateinit var tvChargingStatus: TextView
    private lateinit var progressWatt: ProgressBar
    private lateinit var tvWattRatio: TextView
    private lateinit var tvVoltage: TextView
    private lateinit var tvCurrent: TextView
    private lateinit var tvBatteryLevel: TextView
    private lateinit var tvTemperature: TextView
    private lateinit var tvDiagnostic: TextView

    private lateinit var viewMonitor: ScrollView
    private lateinit var viewGuide: ScrollView
    private lateinit var bottomNav: BottomNavigationView

    private val handler = Handler(Looper.getMainLooper())
    private var maxSupportedWatt = 45
    private var requires5aFor45w = false

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateBatteryStats()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvModelName = findViewById(R.id.tvModelName)
        tvMaxSpecBadge = findViewById(R.id.tvMaxSpecBadge)
        tvChargingStage = findViewById(R.id.tvChargingStage)
        tvLiveWatt = findViewById(R.id.tvLiveWatt)
        tvChargingStatus = findViewById(R.id.tvChargingStatus)
        progressWatt = findViewById(R.id.progressWatt)
        tvWattRatio = findViewById(R.id.tvWattRatio)
        tvVoltage = findViewById(R.id.tvVoltage)
        tvCurrent = findViewById(R.id.tvCurrent)
        tvBatteryLevel = findViewById(R.id.tvBatteryLevel)
        tvTemperature = findViewById(R.id.tvTemperature)
        tvDiagnostic = findViewById(R.id.tvDiagnostic)

        viewMonitor = findViewById(R.id.viewMonitor)
        viewGuide = findViewById(R.id.viewGuide)
        bottomNav = findViewById(R.id.bottomNav)

        applySystemBarInsets()
        setupDeviceModelHeader()

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_monitor -> {
                    viewMonitor.visibility = View.VISIBLE
                    viewGuide.visibility = View.GONE
                    true
                }
                R.id.nav_guide -> {
                    viewMonitor.visibility = View.GONE
                    viewGuide.visibility = View.VISIBLE
                    true
                }
                else -> false
            }
        }
    }

    // 상단 상태바 및 하단 네비게이션바 겹침 방지
    private fun applySystemBarInsets() {
        val rootLayout = (findViewById<ViewGroup>(android.R.id.content)).getChildAt(0) as? ViewGroup ?: return
        val headerLayout = rootLayout.getChildAt(0) ?: return
        val initialHeaderTop = headerLayout.paddingTop
        val initialNavBottom = bottomNav.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            headerLayout.setPadding(
                headerLayout.paddingLeft,
                initialHeaderTop + systemBars.top,
                headerLayout.paddingRight,
                headerLayout.paddingBottom
            )
            bottomNav.setPadding(
                bottomNav.paddingLeft,
                bottomNav.paddingTop,
                bottomNav.paddingRight,
                initialNavBottom + systemBars.bottom
            )
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }

    private fun setupDeviceModelHeader() {
        val rawModel = Build.MODEL.uppercase()
        val (displayName, maxW, need5a) = when {
            // Galaxy S23 Series
            rawModel.startsWith("SM-S911") -> Triple("Galaxy S23 ($rawModel)", 25, false)
            rawModel.startsWith("SM-S916") -> Triple("Galaxy S23+ ($rawModel)", 45, true)
            rawModel.startsWith("SM-S918") -> Triple("Galaxy S23 Ultra ($rawModel)", 45, true)
            // Galaxy S24 Series
            rawModel.startsWith("SM-S921") -> Triple("Galaxy S24 ($rawModel)", 25, false)
            rawModel.startsWith("SM-S926") -> Triple("Galaxy S24+ ($rawModel)", 45, true)
            rawModel.startsWith("SM-S928") -> Triple("Galaxy S24 Ultra ($rawModel)", 45, true)
            // Galaxy S25 Series
            rawModel.startsWith("SM-S931") -> Triple("Galaxy S25 ($rawModel)", 25, false)
            rawModel.startsWith("SM-S936") -> Triple("Galaxy S25+ ($rawModel)", 45, false)
            rawModel.startsWith("SM-S938") -> Triple("Galaxy S25 Ultra ($rawModel)", 45, false)
            // Galaxy S26 Series
            rawModel.startsWith("SM-S941") -> Triple("Galaxy S26 ($rawModel)", 25, false)
            rawModel.startsWith("SM-S946") -> Triple("Galaxy S26+ ($rawModel)", 45, false)
            rawModel.startsWith("SM-S948") -> Triple("Galaxy S26 Ultra ($rawModel)", 60, false)
            else -> Triple("${Build.MANUFACTURER} ${Build.MODEL}", 45, false)
        }

        maxSupportedWatt = maxW
        requires5aFor45w = need5a
        tvModelName.text = displayName
        tvMaxSpecBadge.text = "최대 ${maxSupportedWatt}W 지원"
    }

    private fun updateBatteryStats() {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = registerReceiver(null, intentFilter)
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
        val isFull = status == BatteryManager.BATTERY_STATUS_FULL

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPct = (level * 100) / scale

        val voltageMv = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val voltageV = voltageMv / 1000.0

        val tempTenth = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = tempTenth / 10.0

        val rawCurrent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa = if (abs(rawCurrent) > 20000) abs(rawCurrent) / 1000.0 else abs(rawCurrent).toDouble()
        val currentA = currentMa / 1000.0

        // 배터리 순수 유입 전력(W)
        val netBatteryWatt = if (isCharging) voltageV * currentA else 0.0

        // USB 테스터기 기준 추정 입력 전력(W): 변환 효율(약 88%) + 화면 켜짐 소비전력(약 2.5W) 보정
        val estimatedInputWatt = if (isCharging && netBatteryWatt > 0.5) {
            (netBatteryWatt / 0.88) + 2.5
        } else {
            0.0
        }

        tvBatteryLevel.text = "$batteryPct %"
        tvTemperature.text = String.format("%.1f °C", tempC)
        tvVoltage.text = String.format("%.2f V", voltageV)
        tvCurrent.text = String.format("%.0f mA", if (isCharging) currentMa else 0.0)
        tvLiveWatt.text = String.format("~%.1f W", estimatedInputWatt)

        val ratioPct = ((estimatedInputWatt / maxSupportedWatt.toDouble()) * 100).toInt().coerceIn(0, 100)
        progressWatt.progress = ratioPct
        tvWattRatio.text = String.format("배터리 순수 %.1fW  |  추정 입력 %.1fW / %dW", netBatteryWatt, estimatedInputWatt, maxSupportedWatt)

        when {
            isFull -> {
                setStageBadge("충전 완료", R.color.stage_super)
                tvChargingStatus.text = "배터리가 100% 충전되었습니다"
                tvDiagnostic.text = "충전이 완료되어 전력 유입이 차단되거나 최소화되어 있습니다."
            }
            !isCharging -> {
                setStageBadge("충전기 미연결", R.color.stage_idle)
                tvChargingStatus.text = "케이블을 연결하면 실시간 측정이 시작됩니다"
                tvDiagnostic.text = "현재 기기는 최대 ${maxSupportedWatt}W 유선 충전을 지원합니다. 충전기를 연결해 실시간 출력을 확인해 보세요."
            }
            estimatedInputWatt >= 48.0 -> {
                setStageBadge("초고속 충전 3.0 (60W급)", R.color.stage_super3)
                tvChargingStatus.text = "USB PD 3.0 PPS 60W 고속 충전 중"
                tvDiagnostic.text = "최고 출력 구간으로 정상 충전 중입니다. 배터리 잔량이 높아지거나 온도가 오르면 보호를 위해 출력이 자동 조절됩니다."
            }
            estimatedInputWatt >= 32.0 -> {
                setStageBadge("초고속 충전 2.0 (45W급)", R.color.stage_super2)
                tvChargingStatus.text = "USB PD 3.0 PPS 45W 고속 충전 중"
                tvDiagnostic.text = "45W 초고속 충전 2.0 구간으로 정상 작동 중입니다."
            }
            estimatedInputWatt >= 19.0 -> {
                setStageBadge("초고속 충전 (25W급)", R.color.stage_super)
                tvChargingStatus.text = "USB PD 3.0 PPS 25W 충전 중"
                tvDiagnostic.text = if (maxSupportedWatt >= 45 && batteryPct < 70) {
                    if (requires5aFor45w) {
                        "현재 25W 구간입니다. S23/S24+/Ultra에서 45W를 사용하려면 PPS 5A 충전기와 5A(100W급) 케이블이 모두 필요합니다."
                    } else {
                        "현재 25W 구간입니다. 45W 이상 출력을 원하시면 삼성 PPS 전압 구간(15V 3A 이상)을 지원하는 충전기인지 확인해 주세요."
                    }
                } else {
                    "25W 초고속 충전 구간으로 안정적으로 충전 중입니다."
                }
            }
            estimatedInputWatt >= 11.5 -> {
                setStageBadge("고속 충전 (15W급)", R.color.stage_fast)
                tvChargingStatus.text = "AFC / USB PD 2.0 15W 충전 중"
                tvDiagnostic.text = if (batteryPct >= 80) {
                    "고속 충전(15W) 구간입니다. 현재 배터리 잔량(${batteryPct}%)이 높아 배터리 보호를 위해 출력이 서서히 조절되고 있습니다."
                } else {
                    "고속 충전(15W) 상태입니다. 고출력 충전기라도 삼성 PPS 규격 미지원 시 15W로 제한될 수 있습니다."
                }
            }
            else -> {
                setStageBadge("일반 충전 (10W 이하)", R.color.stage_normal)
                tvChargingStatus.text = "일반 5V 또는 저속 보호 충전 중"
                tvDiagnostic.text = if (batteryPct >= 80 || tempC >= 39.0) {
                    "배터리 잔량(${batteryPct}%)이 높거나 온도(${String.format("%.1f", tempC)}°C) 보호로 인해 충전 전력이 자동 제한되고 있습니다."
                } else {
                    "일반 충전 상태입니다. 케이블 체결 상태 또는 충전기의 고속/PPS 지원 여부를 확인해 주세요."
                }
            }
        }
    }

    private fun setStageBadge(label: String, colorResId: Int) {
        tvChargingStage.text = label
        val color = ContextCompat.getColor(this, colorResId)
        tvChargingStage.backgroundTintList = ColorStateList.valueOf(color)
    }
}
