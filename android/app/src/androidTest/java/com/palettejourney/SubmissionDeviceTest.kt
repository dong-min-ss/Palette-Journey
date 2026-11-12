package com.palettejourney

import android.content.pm.ApplicationInfo
import android.security.NetworkSecurityPolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

/** Exercise the installed submission binary, not just the Gradle variant's name. */
@RunWith(AndroidJUnit4::class)
class SubmissionDeviceTest {
    @Test fun signedReleaseIsNotDebuggableAndOnlyAllowsTheEmulatorServer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("Submission release only", context.packageName.endsWith(".submission"))
        assertFalse("Run this check against the submission release", BuildConfig.DEBUG)
        assertTrue(BuildConfig.EMULATOR_DEMO)
        assertTrue(context.packageName.endsWith(".submission"))
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)
        assertTrue(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("10.0.2.2"))
        assertFalse(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("example.com"))
        assertFalse(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("192.168.0.10"))
    }
}
