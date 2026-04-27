package com.miyabi0619.radiofieldrecorder.recorder

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Test

class RecorderPermissionsTest {
    @Test
    fun runtimePermissionsForSdk_usesLocationBeforeAndroid13() {
        assertEquals(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION),
            RecorderPermissions.runtimePermissionsForSdk(32),
        )
    }

    @Test
    fun runtimePermissionsForSdk_usesNearbyWifiAndNotificationsOnAndroid13Plus() {
        assertEquals(
            listOf(
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.POST_NOTIFICATIONS,
            ),
            RecorderPermissions.runtimePermissionsForSdk(33),
        )
    }
}
