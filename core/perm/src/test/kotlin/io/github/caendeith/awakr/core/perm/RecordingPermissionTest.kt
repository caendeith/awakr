package io.github.caendeith.awakr.core.perm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingPermissionTest {

    @Test
    fun asARequestOfOnePermission() {
        assertEquals(1, RecordingPermission.request.size)
        assertTrue(RecordingPermission.request.single().endsWith("RECORD_AUDIO"))
    }

    @Test
    fun theRationaleOnlyAppearsAfterARefusal() {
        assertFalse(RecordingPermission.shouldExplain(requested = false, granted = false, canAskAgain = true))
        assertFalse(RecordingPermission.shouldExplain(requested = true, granted = true, canAskAgain = false))
        assertTrue(RecordingPermission.shouldExplain(requested = true, granted = false, canAskAgain = true))
    }
}
