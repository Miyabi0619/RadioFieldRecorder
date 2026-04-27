package com.miyabi0619.radiofieldrecorder.recorder

import android.Manifest
import android.os.Build

object RecorderPermissions {
    fun runtimePermissionsForSdk(sdkInt: Int): List<String> =
        buildList {
            if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

    fun runtimePermissions(): List<String> =
        runtimePermissionsForSdk(Build.VERSION.SDK_INT)
}
