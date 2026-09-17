package uz.mobildev.chargerdj.presentation.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.Looper

object ChargerSoundPlayer {
    private const val TONE_DURATION_MS = 700

    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null

    fun play(
        context: Context,
        event: ChargerSoundEvent,
        onFinished: () -> Unit = {},
    ) {
        val selectedSoundId = ChargerSoundSettings.selectedSoundId(context, event)
        play(context, event, selectedSoundId, onFinished)
    }

    fun play(
        context: Context,
        event: ChargerSoundEvent,
        soundId: String,
        onFinished: () -> Unit = {},
    ) {
        releaseCurrentPlayer()

        val sound = BuiltInSound.fromId(soundId)
        when (sound) {
            BuiltInSound.None -> onFinished()
            BuiltInSound.Custom -> playCustomSound(context, event, onFinished)
            else -> sound.rawResId?.let { rawResId ->
                playRawSound(context, rawResId, onFinished)
            } ?: playTone(sound.toneType ?: ToneGenerator.TONE_PROP_BEEP, onFinished)
        }
    }

    private fun playRawSound(context: Context, rawResId: Int, onFinished: () -> Unit) {
        runCatching {
            val rawUri = Uri.parse("android.resource://${context.packageName}/$rawResId")
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context.applicationContext, rawUri)
                setOnCompletionListener {
                    releaseCurrentPlayer()
                    onFinished()
                }
                setOnErrorListener { _, _, _ ->
                    releaseCurrentPlayer()
                    playTone(ToneGenerator.TONE_PROP_BEEP, onFinished)
                    true
                }
                prepare()
                start()
            }
        }.onFailure {
            playTone(ToneGenerator.TONE_PROP_BEEP, onFinished)
        }
    }

    private fun playCustomSound(
        context: Context,
        event: ChargerSoundEvent,
        onFinished: () -> Unit,
    ) {
        val uri = ChargerSoundSettings.customSoundUri(context, event)?.let(Uri::parse)
        if (uri == null) {
            playTone(ToneGenerator.TONE_PROP_BEEP, onFinished)
            return
        }

        runCatching {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context.applicationContext, uri)
                setOnCompletionListener {
                    releaseCurrentPlayer()
                    onFinished()
                }
                setOnErrorListener { _, _, _ ->
                    releaseCurrentPlayer()
                    playTone(ToneGenerator.TONE_PROP_BEEP, onFinished)
                    true
                }
                prepare()
                start()
            }
        }.onFailure {
            playTone(ToneGenerator.TONE_PROP_BEEP, onFinished)
        }
    }

    private fun playTone(toneType: Int, onFinished: () -> Unit) {
        val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        toneGenerator.startTone(toneType, TONE_DURATION_MS)
        mainHandler.postDelayed({
            toneGenerator.release()
            onFinished()
        }, TONE_DURATION_MS.toLong())
    }

    private fun releaseCurrentPlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
