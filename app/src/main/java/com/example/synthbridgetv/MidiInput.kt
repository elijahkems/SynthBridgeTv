package com.example.synthbridgetv

import android.content.Context
import android.content.pm.PackageManager
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper

/**
 * Connects to every MIDI device Android exposes (USB host controllers, and
 * Bluetooth MIDI devices the system has already paired) and feeds the synth.
 */
class MidiInput(
    context: Context,
    private val synth: SynthEngine,
    private val onStatus: (String) -> Unit
) {
    private val manager = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
    private val hasMidi = context.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)
    private val handler = Handler(Looper.getMainLooper())
    private val devices = HashMap<Int, MidiDevice>()
    private var runningStatus = 0

    private val receiver = object : MidiReceiver() {
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            parse(msg, offset, count)
        }
    }

    private val callback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) {
            open(info)
        }
        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            devices.remove(info.id)?.close()
            onStatus("Disconnected: ${nameOf(info)}")
        }
    }

    fun start() {
        val mm = manager
        if (mm == null || !hasMidi) {
            onStatus("This box has no MIDI support. Touch, D-pad and the synth still work.")
            return
        }
        mm.registerDeviceCallback(callback, handler)
        onStatus("Waiting for a MIDI device (USB or paired Bluetooth)...")
        mm.devices.forEach { open(it) }
    }

    fun stop() {
        manager?.unregisterDeviceCallback(callback)
        devices.values.forEach { it.close() }
        devices.clear()
    }

    private fun open(info: MidiDeviceInfo) {
        val mm = manager ?: return
        // An output port on the device is where its data comes from, so we need at least one.
        if (info.outputPortCount == 0 || devices.containsKey(info.id)) return
        mm.openDevice(info, { device ->
            if (device == null) {
                onStatus("Could not open ${nameOf(info)}")
            } else {
                devices[info.id] = device
                device.openOutputPort(0)?.connect(receiver)
                onStatus("Connected: ${nameOf(info)}")
            }
        }, handler)
    }

    private fun nameOf(info: MidiDeviceInfo): String =
        info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "MIDI device ${info.id}"

    /** Parses a raw MIDI byte stream, including running status. Channel is ignored (omni). */
    private fun parse(data: ByteArray, offset: Int, count: Int) {
        var i = offset
        val end = offset + count
        while (i < end) {
            val b = data[i].toInt() and 0xFF
            if (b >= 0xF8) { i++; continue }                       // real-time: ignore
            if (b >= 0xF0) { runningStatus = 0; i++; continue }    // sysex / system common
            val status = when {
                b >= 0x80 -> { i++; b }
                runningStatus != 0 -> runningStatus
                else -> { i++; continue }
            }
            runningStatus = status
            val len = if ((status and 0xF0) == 0xC0 || (status and 0xF0) == 0xD0) 1 else 2
            if (i + len > end) break
            val d1 = data[i].toInt() and 0xFF
            val d2 = if (len == 2) data[i + 1].toInt() and 0xFF else 0
            i += len
            handle(status and 0xF0, d1, d2)
        }
    }

    private fun handle(type: Int, d1: Int, d2: Int) {
        when (type) {
            0x90 -> if (d2 > 0) synth.noteOn(d1, d2) else synth.noteOff(d1)
            0x80 -> synth.noteOff(d1)
            0xB0 -> when (d1) {
                64 -> synth.sustain(d2 >= 64)
                120, 123 -> synth.allNotesOff()
            }
            0xE0 -> synth.pitchBend(d1 or (d2 shl 7))
            0xC0 -> { synth.wave = SynthEngine.Wave.values()[d1 % SynthEngine.Wave.values().size] }
        }
    }
}
