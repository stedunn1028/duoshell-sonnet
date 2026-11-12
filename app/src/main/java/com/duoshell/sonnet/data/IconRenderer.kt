package com.duoshell.sonnet.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable

/**
 * Renders any app icon into a continuous-corner rounded tile (iOS-style proportions) from the
 * adaptive icon's own background + foreground layers, so the device's circle/squircle mask is ignored.
 */
object IconRenderer {
    fun render(drawable: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = size * 0.2237f // iOS app-icon corner ratio
        val path = Path().apply { addRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), r, r, Path.Direction.CW) }
        c.save()
        c.clipPath(path)
        try {
            if (drawable is AdaptiveIconDrawable) {
                val bg = drawable.background
                val fg = drawable.foreground
                val inset = (size * 0.25f).toInt() // layers are 108dp, visible area 72dp
                val bounds = intArrayOf(-inset, -inset, size + inset, size + inset)
                if (bg != null) { bg.setBounds(bounds[0], bounds[1], bounds[2], bounds[3]); bg.draw(c) }
                else c.drawColor(Color.WHITE)
                if (fg != null) { fg.setBounds(bounds[0], bounds[1], bounds[2], bounds[3]); fg.draw(c) }
            } else {
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.shader = LinearGradient(0f, 0f, 0f, size.toFloat(), Color.rgb(250, 250, 252), Color.rgb(222, 224, 232), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
                val pad = (size * 0.15f).toInt()
                drawable.setBounds(pad, pad, size - pad, size - pad)
                drawable.draw(c)
            }
        } catch (t: Throwable) {
            c.drawColor(Color.rgb(120, 124, 140)) // never leave a blank tile if an app ships a broken icon
        }
        c.restore()
        return bmp
    }
}
