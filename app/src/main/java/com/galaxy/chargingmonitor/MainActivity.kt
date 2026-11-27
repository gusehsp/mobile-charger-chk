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
import android.view.WindowManager
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

        // 실시간 측정 중 화면 꺼짐 방지
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

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

        // 60W 초고속 충전(최대 ~15,000mA)까지 대응하도록 uA/mA 자동 판별 (25,000 초과 시 uA)
        val rawCurrent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa = if (abs(rawCurrent) > 25000) abs(rawCurrent) / 1000.0 else abs(rawCurrent).toDouble()
        val currentA = currentMa / 1000.0

        // 1) 배터리 셀 순수 유입 전력 (W)
        val netBatteryWatt = if (isCharging) voltageV * currentA else 0.0

        // 2) USB 테스터기 기준 추정 입력 전력 (W)
        // - PD 3.0 PPS 고출력(>16W 순수 유입): 삼성 차지펌프 직결 효율 약 93% + 화면/시스템 소비 약 3.2W
        // - 일반/고속(<=16W 순수 유입): 일반 컨버터 효율 약 85% + 화면/시스템 소비 약 3.2W
        val estimatedInputWatt = when {
            !isCharging || netBatteryWatt <= 0.3 -> 0.0
            netBatteryWatt > 16.0 -> (netBatteryWatt / 0.93) + 3.2
            else -> (netBatteryWatt / 0.85) + 3.2
        }

        val lossAndSystemWatt = if (estimatedInputWatt > netBatteryWatt) {
            estimatedInputWatt - netBatteryWatt
        } else {
            0.0
        }

        tvBatteryLevel.text = "$batteryPct %"
        tvTemperature.text = String.format("%.1f °C", tempC)
        tvVoltage.text = String.format("%.2f V", voltageV)
        tvCurrent.text = String.format("%.0f mA", if (isCharging) currentMa else 0.0)
        tvLiveWatt.text = String.format("%.1f W", estimatedInputWatt)

        val ratioPct = ((estimatedInputWatt / maxSupportedWatt.toDouble()) * 100).toInt().coerceIn(0, 100)
        progressWatt.progress = ratioPct
        tvWattRatio.text = String.format(
            "테스터기 환산 %.1fW (배터리 순수 %.1fW) / 최대 %dW",
            estimatedInputWatt, netBatteryWatt, maxSupportedWatt
        )

        val diffReasonText = if (isCharging && netBatteryWatt > 0.3) {
            String.format(
                "\n\n[수치 차이 안내] 충전기 입력(%.1fW) 중 약 %.1fW는 전압 변환 손실과 현재 켜진 화면·앱에서 사용되며, 나머지 %.1fW가 배터리에 순수 저장됩니다.",
                estimatedInputWatt, lossAndSystemWatt, netBatteryWatt
            )
        } else {
            ""
        }

        when {
            isFull -> {
                setStageBadge("충전 완료 (100%)", R.color.stage_super)
                tvChargingStatus.text = "배터리가 가득 찼습니다"
                tvDiagnostic.text = "충전이 완료되어 전력 유입이 최소화되어 있습니다."
            }
            !isCharging -> {
                setStageBadge("충전기 미연결", R.color.stage_idle)
                tvChargingStatus.text = "충전 케이블을 연결해 주세요"
                tvDiagnostic.text = "현재 기기는 최대 ${maxSupportedWatt}W 유선 충전을 지원합니다. 케이블을 연결하면 충전 속도를 바로 알려드립니다."
            }
            estimatedInputWatt >= 48.0 -> {
                setStageBadge("초고속 충전 3.0 · 최상급 (60W)", R.color.stage_super3)
                tvChargingStatus.text = "PD 3.0 PPS 60W 최고 속도로 충전 중입니다"
                tvDiagnostic.text = "최고 출력 구간으로 정상 작동 중입니다. 배터리가 차오르거나 온도가 오르면 보호를 위해 속도가 자동 조절됩니다.$diffReasonText"
            }
            estimatedInputWatt >= 32.0 -> {
                setStageBadge("초고속 충전 2.0 · 매우 빠름 (45W)", R.color.stage_super2)
                tvChargingStatus.text = "PD 3.0 PPS 45W 초고속으로 충전 중입니다"
                tvDiagnostic.text = "45W 초고속 충전 2.0이 정상 작동 중입니다.$diffReasonText"
            }
            estimatedInputWatt >= 19.5 -> {
                setStageBadge("초고속 충전 · 빠름 (25W)", R.color.stage_super)
                tvChargingStatus.text = "PD 3.0 PPS 25W 초고속으로 충전 중입니다"
                val tip = if (maxSupportedWatt >= 45 && batteryPct < 70) {
                    if (requires5aFor45w) {
                        "현재 25W 초고속 충전 중입니다. 45W를 쓰려면 PPS 5A 충전기와 5A(100W급) 케이블이 모두 필요합니다."
                    } else {
                        "현재 25W 초고속 충전 중입니다. 45W 이상을 쓰려면 삼성 PPS(15V 3A 이상) 지원 충전기인지 확인해 주세요."
                    }
                } else {
                    "25W 초고속 충전이 안정적으로 작동 중입니다."
                }
                tvDiagnostic.text = "$tip$diffReasonText"
            }
            estimatedInputWatt >= 11.5 -> {
                setStageBadge("고속 충전 · 보통 (15W)", R.color.stage_fast)
                tvChargingStatus.text = "일반 고속 충전(15W)으로 충전 중입니다"
                val tip = if (batteryPct >= 80) {
                    "현재 잔량(${batteryPct}%)이 높아 배터리 보호를 위해 고속 충전(15W) 구간으로 속도가 낮아졌습니다."
                } else {
                    "고속 충전(15W) 중입니다. 초고속 충전(25W 이상)을 원하시면 USB PD 3.0 PPS 지원 충전기를 사용해 주세요."
                }
                tvDiagnostic.text = "$tip$diffReasonText"
            }
            else -> {
                setStageBadge("일반 충전 · 느림 (10W 이하)", R.color.stage_normal)
                tvChargingStatus.text = "저속 일반 충전으로 충전 중입니다"
                val tip = if (batteryPct >= 80 || tempC >= 39.0) {
                    "배터리 잔량(${batteryPct}%)이 높거나 온도(${String.format("%.1f", tempC)}°C) 보호로 인해 충전 속도가 자동 제한되고 있습니다."
                } else {
                    "일반 충전 상태입니다. 충전기 출력이나 케이블 접촉 상태를 확인해 주세요."
                }
                tvDiagnostic.text = "$tip$diffReasonText"
            }
        }
    }

    private fun setStageBadge(label: String, colorResId: Int) {
        tvChargingStage.text = label
        val color = ContextCompat.getColor(this, colorResId)
        tvChargingStage.backgroundTintList = ColorStateList.valueOf(color)
    }
}
