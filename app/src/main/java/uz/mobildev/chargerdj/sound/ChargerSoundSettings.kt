package uz.mobildev.chargerdj.sound

import android.content.Context

object ChargerSoundSettings {
    private const val PREFS_NAME = "charger_sound_settings"
    private const val KEY_SERVICE_ENABLED = "service_enabled"
    private const val DEFAULT_SOUND_ID = "error"

    fun isServiceEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SERVICE_ENABLED, false)
    }

    fun setServiceEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
    }

    fun selectedSoundId(context: Context, event: ChargerSoundEvent): String {
        return prefs(context).getString(event.preferenceKey, DEFAULT_SOUND_ID) ?: DEFAULT_SOUND_ID
    }

    fun setSelectedSoundId(context: Context, event: ChargerSoundEvent, soundId: String) {
        prefs(context).edit().putString(event.preferenceKey, soundId).apply()
    }

    fun customSoundUri(context: Context, event: ChargerSoundEvent): String? {
        return prefs(context).getString(event.customUriKey, null)
    }

    fun setCustomSound(context: Context, event: ChargerSoundEvent, uri: String, displayName: String) {
        prefs(context).edit()
            .putString(event.customUriKey, uri)
            .putString(event.customNameKey, displayName)
            .putString(event.preferenceKey, BuiltInSound.Custom.id)
            .apply()
    }

    fun customSoundName(context: Context, event: ChargerSoundEvent): String? {
        return prefs(context).getString(event.customNameKey, null)
    }

    fun removeCustomSoundUri(context: Context, event: ChargerSoundEvent) {
        prefs(context).edit()
            .remove(event.customUriKey)
            .remove(event.customNameKey)
            .putString(event.preferenceKey, DEFAULT_SOUND_ID)
            .apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
