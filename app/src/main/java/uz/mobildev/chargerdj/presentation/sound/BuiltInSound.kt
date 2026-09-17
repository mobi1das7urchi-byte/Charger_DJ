package uz.mobildev.chargerdj.presentation.sound

import android.media.ToneGenerator
import uz.mobildev.chargerdj.R

enum class BuiltInSound(
    val id: String,
    val title: String,
    val subtitle: String,
    val toneType: Int?,
    val rawResId: Int? = null,
) {
    None("none", "None (Silent)", "No sound will play", null),
    Error("error", "error", "Built-in", ToneGenerator.TONE_PROP_NACK, R.raw.error),
    Faaah("faaah", "faaah", "Built-in", ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, R.raw.faaah),
    VineBoom("vine_boom", "vine_boom", "Built-in", ToneGenerator.TONE_CDMA_ABBR_ALERT, R.raw.vine_boom),
    Wow("wow", "wow", "Built-in", ToneGenerator.TONE_PROP_BEEP2, R.raw.wow),
    Custom("custom", "Custom sound", "From your phone", ToneGenerator.TONE_PROP_PROMPT);

    companion object {
        val visibleBuiltIns = listOf(None, Error, Faaah, VineBoom, Wow)

        fun fromId(id: String): BuiltInSound {
            return entries.firstOrNull { it.id == id } ?: Error
        }
    }
}
