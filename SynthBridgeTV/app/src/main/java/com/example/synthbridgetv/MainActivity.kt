package com.example.synthbridgetv

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private val synth = SynthEngine()
    private lateinit var midi: MidiInput
    private lateinit var status: TextView
    private lateinit var waveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        status = TextView(this).apply { textSize = 18f; setTextColor(Color.LTGRAY) }
        waveButton = Button(this).apply {
            text = waveLabel()
            setOnClickListener {
                val all = SynthEngine.Wave.values()
                synth.wave = all[(synth.wave.ordinal + 1) % all.size]
                text = waveLabel()
            }
        }
        val piano = PianoView(this) { note, on ->
            if (on) synth.noteOn(note, 100) else synth.noteOff(note)
        }
        midi = MidiInput(this, synth) { msg -> status.text = msg }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(18, 18, 26))
            setPadding(40, 30, 40, 30)
            addView(TextView(context).apply {
                text = "SynthBridge TV"
                textSize = 28f
                setTextColor(Color.WHITE)
            })
            addView(status)
            addView(waveButton)
            addView(piano, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
        piano.requestFocus()
    }

    override fun onStart() {
        super.onStart()
        synth.start()
        midi.start()
    }

    override fun onStop() {
        midi.stop()
        synth.stop()
        super.onStop()
    }

    private fun waveLabel() = "Wave: ${synth.wave.name}  (press to change)"
}
