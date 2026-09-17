package uz.mobildev.chargerdj.presentation.ui.screen.activity

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import uz.mobildev.chargerdj.R
import uz.mobildev.chargerdj.databinding.ActivityMainBinding
import uz.mobildev.chargerdj.presentation.adapter.VoiceAdapter
import uz.mobildev.chargerdj.presentation.data.model.VoiceItemData
import uz.mobildev.chargerdj.presentation.power.ChargerSoundService
import uz.mobildev.chargerdj.presentation.sound.BuiltInSound
import uz.mobildev.chargerdj.presentation.sound.ChargerSoundEvent
import uz.mobildev.chargerdj.presentation.sound.ChargerSoundPlayer
import uz.mobildev.chargerdj.presentation.sound.ChargerSoundSettings

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var chargingAdapter: VoiceAdapter
    private lateinit var unchargedAdapter: VoiceAdapter

    private var audioPickerTarget: ChargerSoundEvent = ChargerSoundEvent.Connected
    private var latestBatteryStatus: Intent? = null
    private var batteryStatusReceiverRegistered = false

    private val notificationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val audioPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            openAudioPicker()
        }

    private val audioPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
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
        refreshSoundLists()
        ChargerSoundPlayer.play(this, audioPickerTarget, BuiltInSound.Custom.id)
    }

    private val batteryStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            latestBatteryStatus = intent
            if (::binding.isInitialized) {
                bindBatteryStatus()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top, view.paddingRight, systemBars.bottom)
            insets
        }

        setupAdapters()
        setupClicks()
        bindServiceSwitch()
        refreshSoundLists()
        bindBatteryStatus()

        if (ChargerSoundSettings.isServiceEnabled(this)) {
            ChargerSoundService.start(this)
        }
    }

    override fun onResume() {
        super.onResume()
        startBatteryStatusUpdates()
        bindServiceSwitch()
        refreshSoundLists()
        bindBatteryStatus()
    }

    override fun onPause() {
        if (batteryStatusReceiverRegistered) {
            unregisterReceiver(batteryStatusReceiver)
            batteryStatusReceiverRegistered = false
        }
        super.onPause()
    }

    private fun setupAdapters() {
        chargingAdapter = VoiceAdapter(
            onSelect = { item ->
                ChargerSoundSettings.setSelectedSoundId(this, ChargerSoundEvent.Connected, item.id)
            },
            onPlay = { item ->
                ChargerSoundPlayer.play(this, ChargerSoundEvent.Connected, item.id)
            },
            onDelete = {
                ChargerSoundSettings.removeCustomSoundUri(this, ChargerSoundEvent.Connected)
                refreshSoundLists()
            },
        )
        unchargedAdapter = VoiceAdapter(
            onSelect = { item ->
                ChargerSoundSettings.setSelectedSoundId(this, ChargerSoundEvent.Disconnected, item.id)
            },
            onPlay = { item ->
                ChargerSoundPlayer.play(this, ChargerSoundEvent.Disconnected, item.id)
            },
            onDelete = {
                ChargerSoundSettings.removeCustomSoundUri(this, ChargerSoundEvent.Disconnected)
                refreshSoundLists()
            },
        )

        binding.rvCharging.apply {
            adapter = chargingAdapter
            isNestedScrollingEnabled = false
        }
        binding.rvUncharged.apply {
            adapter = unchargedAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupClicks() {
        binding.btnAddCharging.setOnClickListener {
            audioPickerTarget = ChargerSoundEvent.Connected
            requestAudioPermissionThenOpenPicker()
        }
        binding.btnAddUncharged.setOnClickListener {
            audioPickerTarget = ChargerSoundEvent.Disconnected
            requestAudioPermissionThenOpenPicker()
        }
    }

    private fun bindServiceSwitch() {
        val enabled = ChargerSoundSettings.isServiceEnabled(this)
        binding.switchVoice.setOnCheckedChangeListener(null)
        binding.switchVoice.isChecked = enabled
        binding.switchVoice.setOnCheckedChangeListener { _, isChecked ->
            ChargerSoundSettings.setServiceEnabled(this, isChecked)
            if (isChecked) {
                requestNotificationPermissionIfNeeded()
                ChargerSoundService.start(this)
            } else {
                ChargerSoundService.stop(this)
            }
            bindServiceStatus(isChecked)
        }
        bindServiceStatus(enabled)
    }

    private fun bindServiceStatus(enabled: Boolean) {
        binding.tvStatus.text = if (enabled) {
            getString(R.string.service_active)
        } else {
            getString(R.string.service_inactive)
        }
        binding.tvStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (enabled) R.color.green else R.color.vivid_red,
            ),
        )
    }

    private fun refreshSoundLists() {
        chargingAdapter.submitList(voiceItemsFor(ChargerSoundEvent.Connected))
        unchargedAdapter.submitList(voiceItemsFor(ChargerSoundEvent.Disconnected))
    }

    private fun voiceItemsFor(event: ChargerSoundEvent): List<VoiceItemData> {
        val selectedId = ChargerSoundSettings.selectedSoundId(this, event)
        val items = BuiltInSound.visibleBuiltIns.map { sound ->
            VoiceItemData(
                id = sound.id,
                name = soundTitle(event, sound),
                type = soundSubtitle(event, sound),
                isSelected = sound.id == selectedId,
                canPlay = sound != BuiltInSound.None,
            )
        }
        val customUri = ChargerSoundSettings.customSoundUri(this, event)
        return if (customUri != null) {
            items + VoiceItemData(
                id = BuiltInSound.Custom.id,
                name = soundTitle(event, BuiltInSound.Custom),
                type = soundSubtitle(event, BuiltInSound.Custom),
                audioPath = customUri,
                isSelected = selectedId == BuiltInSound.Custom.id,
                canPlay = true,
                isCustom = true,
            )
        } else {
            items
        }
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

    private fun bindBatteryStatus() {
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
        val isConnected = plugged != 0 ||
            chargingStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
            chargingStatus == BatteryManager.BATTERY_STATUS_FULL

        binding.tvBatteryPercent.text = "$percent%"
        binding.tvBatteryLabel.text = when {
            isConnected && chargingStatus == BatteryManager.BATTERY_STATUS_FULL -> {
                getString(R.string.charging_status_full, percent).substringBefore(':')
            }
            isConnected && chargingStatus == BatteryManager.BATTERY_STATUS_CHARGING -> {
                getString(R.string.charging_status_charging, percent).substringBefore(':')
            }
            isConnected -> getString(R.string.charging_status_connected, percent).substringBefore(':')
            else -> getString(R.string.charging_status_disconnected, percent).substringBefore(':')
        }

        val icon = if (isConnected) {
            R.drawable.icon_battery_charging
        } else {
            R.drawable.icon_battery_uncharged
        }
        binding.ivBatteryStatus.setImageResource(icon)
//        binding.ivBatteryStatus.imageTintList = ContextCompat.getColorStateList(
//            this,
//            if (isConnected) R.color.white else R.color.vivid_red,
//        )
        binding.cardBatteryIcon.setCardBackgroundColor(
            ContextCompat.getColor(this, if (isConnected) R.color.green else R.color.vivid_red),
        )
        binding.layoutBatteryRow.backgroundTintList = ContextCompat.getColorStateList(
            this,
            if (isConnected) R.color.white_green else R.color.white_red,
        )
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

    private fun isAudioUri(uri: Uri): Boolean {
        val mimeType = contentResolver.getType(uri)
        return mimeType?.startsWith("audio/") == true
    }

    companion object {
        private const val PERMISSION_PREFERENCES = "permission_preferences"
        private const val KEY_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
        private const val KEY_AUDIO_PERMISSION_REQUESTED = "audio_permission_requested"
    }
}
