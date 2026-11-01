package com.palettejourney

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated synthetic family only; no microphone or existing device credentials. */
@RunWith(AndroidJUnit4::class)
class VehicleConnectionDeviceTest {
 @Test fun vehicleAndMobileCredentialsSharePageOverEmulatorNetwork() {
  val owner=VehicleApi.randomToken()
  VehicleApi.request("/api/account","PUT",JSONObject(),owner)
  val session=VehicleApi.request("/api/sessions","POST",JSONObject(),owner)
  try {
   val vehicle=VehicleApi.randomToken()
   VehicleApi.request("/api/sessions/join","POST",JSONObject().put("code",session.getString("code")),vehicle)
   val invite=VehicleApi.request("/api/session/invitations","POST",JSONObject(),vehicle)
   val mobile=VehicleApi.randomToken()
   VehicleApi.request("/api/sessions/join","POST",JSONObject().put("code",invite.getString("code")),mobile)
   val library=VehicleApi.request("/api/session/library","GET",null,vehicle)
   val page=library.getJSONArray("pages").getJSONObject(0).getString("id")
   VehicleApi.request("/api/session/connection","PUT",JSONObject().put("pageId",page).put("revision",0),vehicle)
   assertEquals(page,VehicleApi.request("/api/session/connection","GET",null,mobile).getJSONObject("page").getString("id"))
   val context=InstrumentationRegistry.getInstrumentation().targetContext
   try {VehicleApi.save(context,"test-participant",vehicle);assertEquals(vehicle,VehicleApi.load(context,"test-participant"))}
   finally {context.getSharedPreferences("connection",0).edit().remove("test-participant").commit()}
  } finally {VehicleApi.request("/api/sessions/"+session.getString("id"),"DELETE",null,owner)}
 }
}
