package com.palettejourney

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Uses a synthetic family and the emulator's real Vehicle SDK. Never controls a real car. */
@RunWith(AndroidJUnit4::class)
class AmbientDeviceTest {
    private fun console(path: String, method: String = "PUT") {
        val connection = URL("http://10.0.2.2:8088$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 4000
        connection.readTimeout = 4000
        try { assertEquals("Console $path", 200, connection.responseCode) }
        finally { connection.disconnect() }
    }

    @Test fun releaseAppliesFourColorsAndBlocksDriveGear() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        instrument.uiAutomation.grantRuntimePermission(context.packageName, "android.car.permission.CAR_SPEED")
        val previousDemo = VehicleApi.isDemo(context)
        val previousToken = VehicleApi.load(context, "demo-participant")
        val owner = VehicleApi.randomToken()
        VehicleApi.request("/api/account", "PUT", JSONObject(), owner)
        val session = VehicleApi.request("/api/sessions", "POST", JSONObject(), owner)
        val member = VehicleApi.randomToken()
        VehicleApi.request("/api/sessions/join", "POST", JSONObject().put("code", session.getString("code")), member)
        val heartbeat = Executors.newSingleThreadScheduledExecutor()
        var activity: android.app.Activity? = null
        fun command(color: String) = VehicleApi.request("/api/session/ambient", "PUT", JSONObject().put("enabled", true).put("colorId", color), member)
        fun waitStatus(color: String, expected: String, hex: String? = null) {
            var state = JSONObject()
            for (i in 0..80) {
                command(color)
                state = VehicleApi.request("/api/session/ambient", "GET", null, member)
                if (state.optString("status") == expected && (hex == null || state.optString("actualHex") == hex)) return
                Thread.sleep(500)
            }
            fail("$color / $expected: $state")
        }
        try {
            console("/browser/session?client=palette-release-test", "POST")
            heartbeat.scheduleAtFixedRate({ try { console("/browser/session?client=palette-release-test", "POST") } catch (_: Exception) {} }, 0, 650, TimeUnit.MILLISECONDS)
            console("/vehicle/controls?speed=0&accelerator=0&brake=0")
            console("/vehicle/gear?gear=Park")
            VehicleApi.saveDemo(context, member)
            activity = instrument.startActivitySync(Intent(context, AmbientActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            for ((color, hex) in listOf("blue" to "#2196F3", "purple" to "#9C27B0", "red" to "#F44336", "white" to "#FFFFFF")) {
                waitStatus(color, "applied", hex)
            }
            console("/vehicle/gear?gear=Drive")
            Thread.sleep(1000)
            waitStatus("blue", "blocked")
            console("/vehicle/gear?gear=Park")
            waitStatus("blue", "applied", "#2196F3")
        } finally {
            if (activity != null) instrument.runOnMainSync { activity!!.finish() }
            VehicleApi.request("/api/session/ambient", "PUT", JSONObject().put("enabled", false), member)
            console("/vehicle/controls?speed=0&accelerator=0&brake=0")
            console("/vehicle/gear?gear=Park")
            heartbeat.shutdownNow()
            console("/browser/session?client=palette-release-test&closing=1", "POST")
            if (previousToken == null) context.getSharedPreferences("connection", 0).edit().remove("demo-participant").commit()
            else VehicleApi.save(context, "demo-participant", previousToken)
            VehicleApi.useDemo(context, previousDemo)
            VehicleApi.request("/api/sessions/${session.getString("id")}", "DELETE", null, owner)
        }
    }
}
