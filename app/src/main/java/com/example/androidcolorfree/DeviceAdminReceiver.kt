package com.jzhuang.androidcolorfree

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * A minimal DeviceAdminReceiver. The mere presence of this class and its
 * registration in the manifest is what allows the app to become a device admin.
 */
class DeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        // Called when the user enables the app as a device admin.
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        // Called when the user disables the app as a device admin.
    }
}
