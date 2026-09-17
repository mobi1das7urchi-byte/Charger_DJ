package uz.mobildev.chargerdj.presentation.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import uz.mobildev.chargerdj.presentation.sound.ChargerSoundEvent
import uz.mobildev.chargerdj.presentation.sound.ChargerSoundPlayer
import uz.mobildev.chargerdj.presentation.sound.ChargerSoundSettings

class PowerConnectionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ChargerSoundSettings.isServiceEnabled(context)) return

        val soundEvent = when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> ChargerSoundEvent.Connected
            Intent.ACTION_POWER_DISCONNECTED -> ChargerSoundEvent.Disconnected
            else -> return
        }

        val pendingResult = goAsync()
        ChargerSoundPlayer.play(context, soundEvent) {
            pendingResult.finish()
        }
    }
}
