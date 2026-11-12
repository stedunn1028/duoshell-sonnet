package com.duoshell.sonnet.data

import android.content.Context
import com.duoshell.sonnet.core.LaunchEvent
import com.duoshell.sonnet.core.LaunchLogCodec
import com.duoshell.sonnet.core.Settings
import com.duoshell.sonnet.core.SettingsCodec

/** SharedPreferences-backed persistence. File name must match the backup rules (duoshell.xml). */
class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("duoshell", Context.MODE_PRIVATE)

    fun loadSettings(): Settings = SettingsCodec.decode(sp.getString("settings", null))
    fun saveSettings(s: Settings) { sp.edit().putString("settings", SettingsCodec.encode(s)).apply() }

    fun loadLog(): List<LaunchEvent> = LaunchLogCodec.decode(sp.getString("launchLog", null))
    fun saveLog(l: List<LaunchEvent>) { sp.edit().putString("launchLog", LaunchLogCodec.encode(l)).apply() }

    fun loadMemo(): String? = sp.getString("layoutMemo", null)
    fun saveMemo(s: String) { sp.edit().putString("layoutMemo", s).apply() }
}
