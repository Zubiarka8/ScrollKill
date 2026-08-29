package com.ikasle.scrollkill.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils

/**
 * Whether [ScrollKillAccessibilityService] is currently enabled by the user.
 *
 * Reads the system's colon-separated list of enabled accessibility services. This needs no
 * permission and is the standard way to check; it is a best-effort string match, so callers
 * should re-check on resume rather than cache it.
 */
fun isScrollKillAccessibilityEnabled(context: Context): Boolean {
    val expected = ComponentName(context, ScrollKillAccessibilityService::class.java)
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false

    val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
    for (entry in splitter) {
        val component = ComponentName.unflattenFromString(entry) ?: continue
        if (component == expected) return true
    }
    return false
}

/** Intent to the system Accessibility settings screen where the service is toggled on. */
fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
