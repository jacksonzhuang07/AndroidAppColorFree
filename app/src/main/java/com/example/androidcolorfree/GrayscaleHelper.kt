package com.jzhuang.colorfree

import android.content.Context
import android.provider.Settings

object GrayscaleHelper {

    private const val ACCESSIBILITY_DISPLAY_DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
    private const val ACCESSIBILITY_DISPLAY_DALTONIZER = "accessibility_display_daltonizer"

    // CORRECTED: The value for Grayscale (Monochromacy) is 0. This was the critical bug.
    private const val DALTONIZER_GRAYSCALE = 0

    fun setGrayscale(context: Context, enabled: Boolean) {
        try {
            Settings.Secure.putInt(
                context.contentResolver,
                ACCESSIBILITY_DISPLAY_DALTONIZER_ENABLED,
                if (enabled) 1 else 0
            )

            // This part is crucial: set the mode to grayscale when enabling.
            if (enabled) {
                Settings.Secure.putInt(
                    context.contentResolver,
                    ACCESSIBILITY_DISPLAY_DALTONIZER,
                    DALTONIZER_GRAYSCALE
                )
            }
            // When disabling, turning off the main ACCESSIBILITY_DISPLAY_DALTONIZER_ENABLED setting is sufficient.

        } catch (e: SecurityException) {
            // This will happen if WRITE_SECURE_SETTINGS is not granted.
            // The UI should guide the user to grant it via ADB.
            e.printStackTrace()
        }
    }
}
