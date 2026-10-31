package io.github.caendeith.awakr.core.perm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmPermissionPolicyTest {

    private fun required(sdkInt: Int, camera: Boolean) =
        AlarmPermissionPolicy.requirements(sdkInt, camera).filter { it.required }.map { it.permission }

    @Test
    fun oldDevicesHaveNothingToAsk() {
        assertEquals(emptyList<AlarmPermission>(), required(26, camera = false))
    }

    @Test
    fun exactAlarmsAreRequiredFromAndroid12() {
        // Android 11 and older schedule exact alarms without asking.
        assertEquals(emptyList<AlarmPermission>(), required(30, camera = false))
        assertEquals(listOf(AlarmPermission.EXACT_ALARM), required(31, camera = false))
    }

    @Test
    fun notificationsAreRequiredFromAndroid13() {
        assertEquals(
            listOf(AlarmPermission.NOTIFICATIONS, AlarmPermission.EXACT_ALARM),
            required(33, camera = false),
        )
    }

    @Test
    fun cameraIsRequiredOnlyWhileTheQrMissionIsOn() {
        assertTrue(AlarmPermission.CAMERA in required(34, camera = true))
        assertFalse(AlarmPermission.CAMERA in required(34, camera = false))
    }

    @Test
    fun overlayIsNeverRequired() {
        assertTrue(AlarmPermissionPolicy.requirements(34, true).none {
            it.permission == AlarmPermission.OVERLAY && it.required
        })
    }

    @Test
    fun snapshotIsReadyWhenEveryRequiredPermissionIsGranted() {
        val granted = setOf(AlarmPermission.NOTIFICATIONS, AlarmPermission.EXACT_ALARM)
        val snapshot = AlarmPermissionPolicy.snapshot(33, cameraMissionEnabled = false, granted = granted)
        assertTrue(snapshot.ready)
        assertEquals(emptyList<AlarmPermission>(), snapshot.missingRequired)
    }

    @Test
    fun snapshotListsMissingRequiredPermissions() {
        val snapshot = AlarmPermissionPolicy.snapshot(
            sdkInt = 34,
            cameraMissionEnabled = true,
            granted = setOf(AlarmPermission.NOTIFICATIONS),
        )
        assertEquals(
            listOf(AlarmPermission.EXACT_ALARM, AlarmPermission.CAMERA),
            snapshot.missingRequired,
        )
        assertFalse(snapshot.ready)
    }

    @Test
    fun optionalPermissionsDoNotBlockOnboarding() {
        val granted = setOf(AlarmPermission.NOTIFICATIONS, AlarmPermission.EXACT_ALARM)
        val snapshot = AlarmPermissionPolicy.snapshot(33, cameraMissionEnabled = false, granted = granted)
        assertFalse(snapshot.isGranted(AlarmPermission.OVERLAY))
        assertTrue(snapshot.ready)
    }
}
