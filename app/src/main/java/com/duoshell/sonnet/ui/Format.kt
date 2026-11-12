package com.duoshell.sonnet.ui

import android.content.Context
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Fmt {
    fun hour(ctx: Context, ms: Long): String =
        SimpleDateFormat(if (DateFormat.is24HourFormat(ctx)) "HH" else "h", Locale.getDefault()).format(Date(ms))

    fun minute(ms: Long): String = SimpleDateFormat("mm", Locale.getDefault()).format(Date(ms))

    fun time(ctx: Context, ms: Long): String =
        SimpleDateFormat(if (DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(ms))

    fun ampm(ctx: Context, ms: Long): String =
        if (DateFormat.is24HourFormat(ctx)) "" else SimpleDateFormat("a", Locale.getDefault()).format(Date(ms))

    fun date(ms: Long): String = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(ms))

    fun minutes(min: Int): String {
        val h = min / 60
        val m = min % 60
        val ap = if (h < 12) "AM" else "PM"
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "%d:%02d %s".format(h12, m, ap)
    }
}
