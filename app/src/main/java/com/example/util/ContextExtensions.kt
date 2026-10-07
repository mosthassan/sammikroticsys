package com.example.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Traverses ContextWrapper hierarchy to find the underlying Activity.
 * Essential for Jetpack Compose where LocalContext.current is frequently
 * wrapped in ContextThemeWrapper.
 */
fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
