package com.example.synthbridgetv

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/** Small polyphonic software synth: 16 voices, 4 waveforms, sustain pedal, pitch bend. */
class SynthEngine(private val sampleRate: Int = 44100) {

    enum class Wave { SINE, SAW, SQUARE, TRIANGLE }

    private class Voice {
        var note = -1
        var freq = 0.0
        var phase = 0.0
        var velocity = 0f
        var env = 0f
        var held = false       // key physically down
        var sustained = false  // key up, but held by the pedal
        var gate = false       // envelope in attack/sustain phase
        var age = 0L
    }

    private val lock = Any()
    private val voices = Array(16) { Voice() }
    private var clock = 0L
    private var bend = 0.0
    private var pedal = false
    private val attackStep = 1f / (0.008f * sampleRate)   // ~8 ms attack
    private val releaseStep = 1f / (0.25f * sampleRate)   // ~250 ms release
    private val block = 256

    @Volatile var wave = Wave.SAW
    @Volatile var volume = 0.3f
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val bytes = AudioTrack.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(block * 8)
            // Constructor form (not AudioTrack.Builder) so minSdk 23 works
            val track = AudioTrack(
                AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bytes, AudioTrack.MODE_STREAM
            )
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                running = false
                return@Thread
            }
            track.play()
            val buf = ShortArray(block)
            while (running) {
                synchronized(lock) { render(buf) }
                track.write(buf, 0, buf.size)   // blocking write paces the loop in real time
            }
            track.stop()
            track.release()
        }.also { it.start() }
    }

    fun stop() {
        running = false
        thread?.join(1000)
        thread = null
    }

    fun noteOn(note: Int, velocity: Int) {
        synchronized(lock) {
            val v = voices.firstOrNull { it.note == note }
                ?: voices.firstOrNull { it.note < 0 }
                // steal: prefer released voices, then the oldest held one
                ?: voices.minByOrNull { if (it.gate) it.age else -1L }!!
            if (v.note < 0) { v.env = 0f; v.phase = 0.0 }
            v.note = note
            v.freq = 440.0 * 2.0.pow((note - 69) / 12.0)
            v.velocity = velocity / 127f
            v.held = true
            v.sustained = false
            v.gate = true
            v.age = ++clock
        }
    }

    fun noteOff(note: Int) {
        synchronized(lock) {
            for (v in voices) {
                if (v.note == note && v.held) {
                    v.held = false
                    if (pedal) v.sustained = true else v.gate = false
                }
            }
        }
    }

    fun sustain(on: Boolean) {
        synchronized(lock) {
            pedal = on
            if (!on) {
                for (v in voices) {
                    if (v.sustained) {
                        v.sustained = false
                        if (!v.held) v.gate = false
                    }
                }
            }
        }
    }

    fun allNotesOff() {
        synchronized(lock) {
            for (v in voices) { v.held = false; v.sustained = false; v.gate = false }
        }
    }

    /** value is the raw 14-bit MIDI pitch-bend value (0..16383, center 8192). */
    fun pitchBend(value: Int) {
        synchronized(lock) { bend = (value - 8192) / 8192.0 * 2.0 }   // +/- 2 semitones
    }

    private fun render(out: ShortArray) {
        val bendRatio = 2.0.pow(bend / 12.0)
        val w = wave
        for (i in out.indices) {
            var mix = 0.0
            for (v in voices) {
                if (v.note < 0) continue
                if (v.gate) {
                    v.env = minOf(1f, v.env + attackStep)
                } else {
                    v.env -= releaseStep
                    if (v.env <= 0f) { v.env = 0f; v.note = -1; continue }
                }
                mix += osc(v.phase, w) * v.env * v.velocity
                v.phase += v.freq * bendRatio / sampleRate
                if (v.phase >= 1.0) v.phase -= 1.0
            }
            out[i] = (tanh(mix * volume) * 30000).toInt().toShort()   // soft clip
        }
    }

    private fun osc(p: Double, w: Wave): Double = when (w) {
        Wave.SINE -> sin(2.0 * PI * p)
        Wave.SAW -> 2.0 * p - 1.0
        Wave.SQUARE -> if (p < 0.5) 1.0 else -1.0
        Wave.TRIANGLE -> 4.0 * abs(p - 0.5) - 1.0
    }
}
