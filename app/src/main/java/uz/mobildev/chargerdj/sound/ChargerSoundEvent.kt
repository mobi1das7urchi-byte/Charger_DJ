package uz.mobildev.chargerdj.sound

enum class ChargerSoundEvent(
    val extraValue: String,
    val preferenceKey: String,
    val customUriKey: String,
    val customNameKey: String,
) {
    Connected("connected", "connected_sound", "connected_custom_uri", "connected_custom_name"),
    Disconnected("disconnected", "disconnected_sound", "disconnected_custom_uri", "disconnected_custom_name");

    companion object {
        fun fromExtra(value: String?): ChargerSoundEvent {
            return entries.firstOrNull { it.extraValue == value } ?: Connected
        }
    }
}
