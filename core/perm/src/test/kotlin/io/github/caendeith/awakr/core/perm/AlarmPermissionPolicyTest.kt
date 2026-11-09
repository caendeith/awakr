package io.github.caendeith.awakr.core.perm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmPermissionPolicyTest {

    private fun required(sdkInt: Int, camera: Boolean) =
        AlarmPermissionPolicy.requirements(sdkInt, camera).filter { it.required }.map { it.permission }

    @Test
    fun oldDevicesStillAskForTheOverlay() {
        // The ring screen has to win over whatever is on screen on every version, so the overlay
        // is asked for from Marshmallow on; nothing else is needed that early.
        assertEquals(listOf(AlarmPermission.OVERLAY), required(26, camera = false))
    }

    @Test
    fun exactAlarmsAreRequiredFromAndroid12() {
        // Android 11 and older schedule exact alarms without asking.
        assertEquals(listOf(AlarmPermission.OVERLAY), required(30, camera = false))
        assertEquals(
            listOf(AlarmPermission.EXACT_ALARM, AlarmPermission.OVERLAY),
            required(31, camera = false),
        )
    }

    @Test
    fun notificationsAreRequiredFromAndroid13() {
        assertEquals(
            listOf(AlarmPermission.NOTIFICATIONS, AlarmPermission.EXACT_ALARM, AlarmPermission.OVERLAY),
            required(33, camera = false),
        )
    }

    @Test
    fun cameraIsRequiredOnlyWhileTheQrMissionIsOn() {
        assertTrue(AlarmPermission.CAMERA in required(34, camera = true))
        assertFalse(AlarmPermission.CAMERA in required(34, camera = false))
    }

    @Test
    fun snapshotIsReadyWhenEveryRequiredPermissionIsGranted() {
        val granted = setOf(
            AlarmPermission.NOTIFICATIONS,
            AlarmPermission.EXACT_ALARM,
            AlarmPermission.OVERLAY,
        )
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
            listOf(AlarmPermission.EXACT_ALARM, AlarmPermission.CAMERA, AlarmPermission.OVERLAY),
            snapshot.missingRequired,
        )
        assertFalse(snapshot.ready)
    }

    @Test
    fun optionalPermissionsDoNotBlockOnboarding() {
        val granted = setOf(
            AlarmPermission.NOTIFICATIONS,
            AlarmPermission.EXACT_ALARM,
            AlarmPermission.OVERLAY,
        )
        val snapshot = AlarmPermissionPolicy.snapshot(33, cameraMissionEnabled = false, granted = granted)
        assertFalse(snapshot.isGranted(AlarmPermission.CAMERA))
        assertTrue(snapshot.ready)
    }
}
