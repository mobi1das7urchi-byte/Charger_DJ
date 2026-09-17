package uz.mobildev.chargerdj.presentation.data.model

data class VoiceItemData(
    val id: String,
    val name: String,
    val type: String,
    val audioPath: String = "",
    val isSelected: Boolean = false,
    val canPlay: Boolean = true,
    val isCustom: Boolean = false,
)
