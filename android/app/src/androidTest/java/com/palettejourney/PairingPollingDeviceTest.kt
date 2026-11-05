package com.palettejourney

import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PairingPollingDeviceTest {
    @Test fun qrClickSurvivesBackgroundRefresh() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val previousDemo = VehicleApi.isDemo(context)
        val previousToken = VehicleApi.load(context, "demo-participant")
        val owner = VehicleApi.randomToken()
        VehicleApi.request("/api/account/demo", "PUT", JSONObject(), owner)
        val session = VehicleApi.request("/api/sessions", "POST", JSONObject(), owner)
        val member = VehicleApi.randomToken()
        VehicleApi.request("/api/sessions/join", "POST",
            JSONObject().put("code", session.getString("code")), member)
        val release = CountDownLatch(1)
        var activity: VehicleConnectionActivity? = null
        fun waitFor(label: String, condition: () -> Boolean) {
            for (attempt in 0..200) {
                if (condition()) return
                Thread.sleep(50)
            }
            fail(label)
        }
        try {
            VehicleApi.saveDemo(context, member)
            val screen = instrumentation.startActivitySync(
                Intent(context, VehicleConnectionActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ) as VehicleConnectionActivity
            activity = screen
            fun field(name: String) = VehicleConnectionActivity::class.java
                .getDeclaredField(name).apply { isAccessible = true }
            val issueQr = field("issueQr").get(screen) as Button
            val image = field("qr").get(screen) as ImageView
            waitFor("family loads") {
                var ready = false
                instrumentation.runOnMainSync { ready = issueQr.isEnabled }
                ready
            }
            val queue = field("executor").get(screen) as ExecutorService
            val entered = CountDownLatch(1)
            queue.execute {
                entered.countDown()
                release.await(15, TimeUnit.SECONDS)
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                VehicleConnectionActivity::class.java.getDeclaredMethod("pollConnection")
                    .apply { isAccessible = true }.invoke(screen)
                assertTrue("poll leaves QR button enabled", issueQr.isEnabled)
                issueQr.performClick()
                assertFalse("explicit QR operation blocks duplicates", issueQr.isEnabled)
            }
            release.countDown()
            waitFor("queued QR request produces an image") {
                var ready = false
                instrumentation.runOnMainSync {
                    ready = image.drawable != null && image.visibility == View.VISIBLE &&
                        issueQr.isEnabled
                }
                ready
            }
            Thread.sleep(5500)
            instrumentation.runOnMainSync {
                assertTrue(issueQr.isEnabled)
                assertNotNull("poll preserves QR", image.drawable)
                assertEquals(View.VISIBLE, image.visibility)
                assertEquals(View.GONE, (field("startPairing").get(screen) as Button).visibility)
            }
        } finally {
            release.countDown()
            instrumentation.runOnMainSync { activity?.finish() }
            if (previousToken == null)
                context.getSharedPreferences("connection", 0).edit()
                    .remove("demo-participant").commit()
            else VehicleApi.save(context, "demo-participant", previousToken)
            VehicleApi.useDemo(context, previousDemo)
            VehicleApi.request("/api/sessions/" + session.getString("id"), "DELETE", null, owner)
        }
    }
}
