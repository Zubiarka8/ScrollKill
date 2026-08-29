package com.ikasle.scrollkill.ui.home

/**
 * Friendly names for the packages the detectors watch. A small static map keeps the UI
 * unit-testable and avoids a PackageManager lookup; unknown packages show their raw id.
 */
object KnownApps {
    fun label(packageName: String): String = when (packageName) {
        "com.instagram.android" -> "Instagram"
        "com.google.android.youtube" -> "YouTube"
        "com.zhiliaoapp.musically" -> "TikTok"
        "com.facebook.katana" -> "Facebook"
        "com.facebook.lite" -> "Facebook Lite"
        else -> packageName
    }
}
