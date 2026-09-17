package uz.mobildev.chargerdj.presentation.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import uz.mobildev.chargerdj.R
import uz.mobildev.chargerdj.databinding.ItemAudiosBinding
import uz.mobildev.chargerdj.presentation.data.model.VoiceItemData

class VoiceAdapter(
    private val onSelect: (VoiceItemData) -> Unit,
    private val onPlay: (VoiceItemData) -> Unit,
    private val onDelete: ((VoiceItemData) -> Unit)? = null,
) : ListAdapter<VoiceItemData, VoiceAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ItemAudiosBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: VoiceItemData) = with(binding) {
            tvVoiceName.text = item.name
            tvVoiceType.text = item.type
            btnPlay.visibility = if (item.canPlay) View.VISIBLE else View.GONE

            radioVoice.setButtonDrawable(
                if (item.isSelected) {
                    R.drawable.icon_check_circle
                } else {
                    R.drawable.icon_check_box_outline
                }
            )

            val selectListener = View.OnClickListener { selectVoice(item.id) }
            radioVoice.setOnClickListener(selectListener)
            root.setOnClickListener(selectListener)

            btnPlay.setOnClickListener {
                onPlay(item)
            }

            root.setOnLongClickListener {
                if (item.isCustom) {
                    onDelete?.invoke(item)
                    true
                } else {
                    false
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAudiosBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private fun selectVoice(selectedId: String) {
        val selectedItem = currentList.firstOrNull { it.id == selectedId } ?: return
        submitList(
            currentList.map { voice ->
                voice.copy(isSelected = voice.id == selectedId)
            },
        )
        onSelect(selectedItem)
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<VoiceItemData>() {
            override fun areItemsTheSame(oldItem: VoiceItemData, newItem: VoiceItemData): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: VoiceItemData, newItem: VoiceItemData): Boolean {
                return oldItem == newItem
            }
        }
    }
}
