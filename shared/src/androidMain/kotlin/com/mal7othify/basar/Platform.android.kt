package com.mal7othify.basar

import android.os.Build

// TODO: Delete this file, lol!
class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()
