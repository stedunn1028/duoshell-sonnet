package com.duoshell.sonnet

import android.app.Application

class DuoApp : Application() {
    /** Process-wide controller: Home state survives Activity recreation (fold, rotation, theme). */
    val controller: LauncherController by lazy { LauncherController(this) }
}
