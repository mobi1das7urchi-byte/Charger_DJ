package uz.mobildev.chargerdj

import android.annotation.SuppressLint
import android.Manifest
import android.content.BroadcastReceiver
import android.content.pm.PackageManager
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.OpenableColumns
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import uz.mobildev.chargerdj.sound.BuiltInSound
import uz.mobildev.chargerdj.sound.ChargerSoundEvent
import uz.mobildev.chargerdj.sound.ChargerSoundPlayer
import uz.mobildev.chargerdj.sound.ChargerSoundSettings
import uz.mobildev.chargerdj.power.ChargerSoundService
import androidx.core.net.toUri

class MainActivity : AppCompatActivity() {
    private var audioPickerTarget: ChargerSoundEvent = ChargerSoundEvent.Connected
    private lateinit var contentLayout: LinearLayout
    private lateinit var statusTitle: TextView
    private lateinit var statusSubtitle: TextView
    private var latestBatteryStatus: Intent? = null
    private var batteryStatusReceiverRegistered = false

    private val notificationPermissionRequest = registerForActivityResult(RequestPermission()) { }
    private val audioPermissionRequest = registerForActivityResult(RequestPermission()) {
        openAudioPicker()
    }

    private val audioPicker = registerForActivityResult(OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        if (!isAudioUri(uri)) {
            Toast.makeText(this, R.string.select_audio_only, Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }

        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ChargerSoundSettings.setCustomSound(
            this,
            audioPickerTarget,
            uri.toString(),
            audioDisplayName(uri) ?: getString(R.string.custom_sound_title),
        )
        renderScreen()
        ChargerSoundPlayer.play(this, audioPickerTarget, BuiltInSound.Custom.id)
    }

    private val batteryStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            latestBatteryStatus = intent
            if (::statusSubtitle.isInitialized) {
                statusSubtitle.text = serviceSubtitle(ChargerSoundSettings.isServiceEnabled(this@MainActivity))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        contentLayout = findViewById(R.id.contentLayout)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(24), systemBars.top + dp(20), dp(24), systemBars.bottom + dp(20))
            insets
        }

        if (ChargerSoundSettings.isServiceEnabled(this)) {
            ChargerSoundService.start(this)
        }
        renderScreen()
    }

    override fun onResume() {
        super.onResume()
        startBatteryStatusUpdates()
        if (::contentLayout.isInitialized) renderScreen()
    }

    override fun onPause() {
        if (batteryStatusReceiverRegistered) {
            unregisterReceiver(batteryStatusReceiver)
            batteryStatusReceiverRegistered = false
        }
        super.onPause()
    }

    private fun renderScreen() {
        contentLayout.removeAllViews()
        addHeader()
        addServiceCard()
        addSoundSection(getString(R.string.connect_sound), ChargerSoundEvent.Connected)
        addSoundSection(getString(R.string.disconnect_sound), ChargerSoundEvent.Disconnected)
        addSystemSection()
    }

    private fun addHeader() {
        contentLayout.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 30f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
        }, verticalParams(bottom = 24))
    }

    private fun addServiceCard() {
        val enabled = ChargerSoundSettings.isServiceEnabled(this)
        val card = card()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        statusTitle = TextView(this).apply {
            text = if (enabled) getString(R.string.service_active) else getString(R.string.service_inactive)
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
        }
        statusSubtitle = TextView(this).apply {
            text = serviceSubtitle(enabled)
            textSize = 16f
        }
        textColumn.addView(statusTitle)
        textColumn.addView(statusSubtitle)

        val serviceSwitch = MaterialSwitch(this).apply {
            isChecked = enabled
            setOnCheckedChangeListener { _, isChecked ->
                ChargerSoundSettings.setServiceEnabled(this@MainActivity, isChecked)
                if (isChecked) {
                    requestNotificationPermissionIfNeeded()
                    ChargerSoundService.start(this@MainActivity)
                } else {
                    ChargerSoundService.stop(this@MainActivity)
                }
                statusTitle.text = if (isChecked) getString(R.string.service_active) else getString(R.string.service_inactive)
                statusSubtitle.text = serviceSubtitle(isChecked)
            }
        }

        row.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(serviceSwitch)
        card.addView(row)
        contentLayout.addView(card, verticalParams(bottom = 24))
    }

    private fun addSoundSection(title: String, event: ChargerSoundEvent) {
        contentLayout.addView(sectionTitle(title), verticalParams(bottom = 10))

        val card = card()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actionRow.addView(TextView(this).apply {
            text = getString(R.string.hide_sounds)
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actionRow.addView(MaterialButton(this).apply {
            text = getString(R.string.add)
            setOnClickListener {
                audioPickerTarget = event
                requestAudioPermissionThenOpenPicker()
            }
        })
        container.addView(actionRow, verticalParams(bottom = 8))

        BuiltInSound.visibleBuiltIns.forEach { sound ->
            container.addView(soundRow(event, sound))
        }

        if (ChargerSoundSettings.customSoundUri(this, event) != null) {
            container.addView(soundRow(event, BuiltInSound.Custom))
        }

        card.addView(container)
        contentLayout.addView(card, verticalParams(bottom = 24))
    }

    private fun soundRow(event: ChargerSoundEvent, sound: BuiltInSound): View {
        val selected = ChargerSoundSettings.selectedSoundId(this, event) == sound.id
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
            setOnClickListener {
                ChargerSoundSettings.setSelectedSoundId(this@MainActivity, event, sound.id)
                renderScreen()
            }
        }

        row.addView(RadioButton(this).apply {
            isChecked = selected
            isClickable = false
        })

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(10), 0)
        }
        textColumn.addView(TextView(this).apply {
            text = soundTitle(event, sound)
            textSize = 18f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTypeface(typeface, Typeface.BOLD)
        })
        textColumn.addView(TextView(this).apply {
            text = soundSubtitle(event, sound)
            textSize = 14f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        row.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        if (sound != BuiltInSound.None) {
            row.addView(MaterialButton(this).apply {
                text = getString(R.string.play)
                minWidth = dp(64)
                minimumWidth = dp(64)
                setOnClickListener {
                    ChargerSoundPlayer.play(this@MainActivity, event, sound.id)
                }
            })
        }

        if (sound == BuiltInSound.Custom) {
            row.addView(MaterialButton(this).apply {
                text = getString(R.string.delete)
                minWidth = dp(72)
                minimumWidth = dp(72)
                setOnClickListener {
                    ChargerSoundSettings.removeCustomSoundUri(this@MainActivity, event)
                    renderScreen()
                }
            })
        }

        return row
    }

    private fun soundTitle(event: ChargerSoundEvent, sound: BuiltInSound): String {
        return when (sound) {
            BuiltInSound.None -> getString(R.string.silent_sound_title)
            BuiltInSound.Custom -> customSoundDisplayName(event) ?: getString(R.string.custom_sound_title)
            else -> sound.title
        }
    }

    private fun soundSubtitle(event: ChargerSoundEvent, sound: BuiltInSound): String {
        return when (sound) {
            BuiltInSound.None -> getString(R.string.silent_sound_subtitle)
            BuiltInSound.Custom -> getString(R.string.custom_sound_subtitle)
            else -> getString(R.string.built_in_sound_subtitle)
        }
    }

    private fun customSoundDisplayName(event: ChargerSoundEvent): String? {
        ChargerSoundSettings.customSoundName(this, event)?.let { return it }
        val uri = ChargerSoundSettings.customSoundUri(this, event)?.let(Uri::parse) ?: return null
        return audioDisplayName(uri)
    }

    private fun audioDisplayName(uri: Uri): String? {
        return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
        } ?: uri.lastPathSegment
    }

    private fun addSystemSection() {
        contentLayout.addView(sectionTitle(getString(R.string.system)), verticalParams(bottom = 10))

        val card = card()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        container.addView(systemRow(
            getString(R.string.battery_optimization),
            getString(R.string.battery_optimization_desc),
            getString(R.string.fix),
        ) {
            openBatteryOptimizationSettings()
        })
        container.addView(systemRow(
            getString(R.string.app_hibernation),
            getString(R.string.app_hibernation_desc),
            getString(R.string.open),
        ) {
            openAppSettings()
        })
        card.addView(container)
        contentLayout.addView(card, verticalParams(bottom = 24))
    }

    private fun systemRow(title: String, subtitle: String, buttonText: String, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        textColumn.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        })
        textColumn.addView(TextView(this).apply {
            text = subtitle
            textSize = 14f
        })
        row.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(MaterialButton(this).apply {
            text = buttonText
            setOnClickListener { onClick() }
        })
        return row
    }

    private fun serviceSubtitle(enabled: Boolean): String {
        val batteryStatus = latestBatteryStatus
            ?: registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val chargingStatus = batteryStatus?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN,
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else 0
        val isConnected = plugged != 0 || chargingStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
            chargingStatus == BatteryManager.BATTERY_STATUS_FULL
        val batteryText = when {
            isConnected && chargingStatus == BatteryManager.BATTERY_STATUS_FULL -> {
                getString(R.string.charging_status_full, percent)
            }
            isConnected && chargingStatus == BatteryManager.BATTERY_STATUS_CHARGING -> {
                getString(R.string.charging_status_charging, percent)
            }
            isConnected -> getString(R.string.charging_status_connected, percent)
            else -> getString(R.string.charging_status_disconnected, percent)
        }
        val serviceText = if (enabled) getString(R.string.service_running) else getString(R.string.service_paused)
        return "$serviceText • $batteryText"
    }

    private fun startBatteryStatusUpdates() {
        if (batteryStatusReceiverRegistered) return
        latestBatteryStatus = ContextCompat.registerReceiver(
            this,
            batteryStatusReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        batteryStatusReceiverRegistered = true
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return

        val preferences = getSharedPreferences(PERMISSION_PREFERENCES, MODE_PRIVATE)
        if (!preferences.getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false)) {
            preferences.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, true).apply()
            notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestAudioPermissionThenOpenPicker() {
        val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, audioPermission) == PackageManager.PERMISSION_GRANTED) {
            openAudioPicker()
            return
        }

        val preferences = getSharedPreferences(PERMISSION_PREFERENCES, MODE_PRIVATE)
        if (!preferences.getBoolean(KEY_AUDIO_PERMISSION_REQUESTED, false)) {
            preferences.edit().putBoolean(KEY_AUDIO_PERMISSION_REQUESTED, true).apply()
            audioPermissionRequest.launch(audioPermission)
        } else {
            openAudioPicker()
        }
    }

    private fun openAudioPicker() {
        audioPicker.launch(arrayOf("audio/*"))
    }

    @SuppressLint("BatteryLife")
    private fun openBatteryOptimizationSettings() {
        val packageUri = "package:$packageName".toUri()
        val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
        runCatching {
            startActivity(requestIntent)
        }.onFailure {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:$packageName".toUri()))
    }

    private fun isAudioUri(uri: Uri): Boolean {
        val mimeType = contentResolver.getType(uri)
        return mimeType?.startsWith("audio/") == true
    }

    private fun sectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            letterSpacing = 0.08f
            setTypeface(typeface, Typeface.BOLD)
        }
    }

    private fun card(): MaterialCardView {
        return MaterialCardView(this).apply {
            radius = dp(8).toFloat()
            strokeWidth = 1
            setContentPadding(0, 0, 0, 0)
        }
    }

    private fun verticalParams(bottom: Int = 0): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            bottomMargin = bottom
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val PERMISSION_PREFERENCES = "permission_preferences"
        private const val KEY_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
        private const val KEY_AUDIO_PERMISSION_REQUESTED = "audio_permission_requested"
    }
}