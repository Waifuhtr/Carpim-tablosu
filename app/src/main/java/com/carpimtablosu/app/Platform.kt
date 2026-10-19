package com.carpimtablosu.app

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/** Uygulama aynı APK ile telefonda ve Android TV'de çalışır. */
object Platform {
    fun isTv(context: Context): Boolean {
        val type = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        return type == Configuration.UI_MODE_TYPE_TELEVISION ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
}
