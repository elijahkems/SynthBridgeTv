package com.example.synthbridgetv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View

/** Two-octave keyboard: multi-touch on phones/tablets, D-pad + OK on a TV remote. */
class PianoView(context: Context, private val onNote: (note: Int, on: Boolean) -> Unit) : View(context) {

    private val firstNote = 48   // C3
    private val lastNote = 72    // C5
    private val whiteNotes = (firstNote..lastNote).filter { !isBlack(it) }
    private val whiteRects = HashMap<Int, RectF>()
    private val blackRects = HashMap<Int, RectF>()
    private val pressed = HashMap<Int, Int>()   // pointer id -> note
    private var cursor = 60
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    private fun isBlack(n: Int) = (n % 12) in intArrayOf(1, 3, 6, 8, 10)

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        whiteRects.clear()
        blackRects.clear()
        val ww = w.toFloat() / whiteNotes.size
        whiteNotes.forEachIndexed { i, n -> whiteRects[n] = RectF(i * ww, 0f, (i + 1) * ww, h.toFloat()) }
        val bw = ww * 0.6f
        for (n in firstNote..lastNote) {
            if (!isBlack(n)) continue
            val right = (whiteNotes.indexOf(n - 1) + 1) * ww   // the white key just below
            blackRects[n] = RectF(right - bw / 2, 0f, right + bw / 2, h * 0.62f)
        }
    }

    override fun onDraw(c: Canvas) {
        val held = pressed.values.toSet()
        for (n in whiteNotes) {
            val r = whiteRects[n] ?: continue
            fill.color = if (n in held) Color.rgb(120, 200, 255) else Color.WHITE
            c.drawRect(r, fill)
            line.color = Color.DKGRAY
            c.drawRect(r, line)
        }
        for ((n, r) in blackRects) {
            fill.color = if (n in held) Color.rgb(40, 120, 200) else Color.rgb(25, 25, 25)
            c.drawRect(r, fill)
        }
        if (hasFocus()) {
            (whiteRects[cursor] ?: blackRects[cursor])?.let {
                line.color = Color.YELLOW
                line.strokeWidth = 6f
                c.drawRect(it, line)
            }
        }
    }

    private fun keyAt(x: Float, y: Float): Int {
        blackRects.entries.firstOrNull { it.value.contains(x, y) }?.let { return it.key }
        return whiteRects.entries.firstOrNull { it.value.contains(x, y) }?.key ?: -1
    }

    private fun press(id: Int, note: Int) {
        if (note < 0) return
        pressed[id] = note
        onNote(note, true)
        invalidate()
    }

    private fun release(id: Int) {
        val note = pressed.remove(id) ?: return
        onNote(note, false)
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                press(e.getPointerId(i), keyAt(e.getX(i), e.getY(i)))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val id = e.getPointerId(i)
                    val note = keyAt(e.getX(i), e.getY(i))
                    if (pressed[id] != note) { release(id); press(id, note) }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> release(e.getPointerId(e.actionIndex))
            MotionEvent.ACTION_CANCEL -> pressed.keys.toList().forEach { release(it) }
        }
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> { cursor = (cursor + 1).coerceAtMost(lastNote) }
            KeyEvent.KEYCODE_DPAD_LEFT -> { cursor = (cursor - 1).coerceAtLeast(firstNote) }
            KeyEvent.KEYCODE_DPAD_UP -> { cursor = (cursor + 12).coerceAtMost(lastNote) }
            KeyEvent.KEYCODE_DPAD_DOWN -> { cursor = (cursor - 12).coerceAtLeast(firstNote) }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (event.repeatCount == 0) press(DPAD_ID, cursor)
                return true
            }
            else -> return super.onKeyDown(keyCode, event)
        }
        invalidate()
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            release(DPAD_ID)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    companion object {
        private const val DPAD_ID = -1
    }
}
