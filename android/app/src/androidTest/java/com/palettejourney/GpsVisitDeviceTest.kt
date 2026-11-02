package com.palettejourney
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
@RunWith(AndroidJUnit4::class)
class GpsVisitDeviceTest {
 @Test fun realTimeSdkLocationAwardsFamilyOnce(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName,"pleos.car.permission.FUSED_LOCATION")
  val owner=VehicleApi.randomToken();VehicleApi.request("/api/account","PUT",JSONObject(),owner)
  val session=VehicleApi.request("/api/sessions","POST",JSONObject(),owner)
  val vehicle=VehicleApi.randomToken();val mobile=VehicleApi.randomToken()
  for(t in listOf(vehicle,mobile))VehicleApi.request("/api/sessions/join","POST",JSONObject().put("code",session.getString("code")),t)
  val fused=FusedLocation(context)
  try{
   assertEquals(11,VehicleApi.request("/api/session/palette","GET",null,mobile).getJSONArray("colors").length())
   fused.initialize();var result=JSONObject()
   for(i in 0..26){
    val latch=CountDownLatch(1);var location:LocationData?=null
    fused.getLastKnownLocation(object:FusedLocationListener{
     override fun onChanged(value:LocationData?){location=value;latch.countDown()}
     override fun onError(exception:Exception){latch.countDown()}
    })
    assertTrue(latch.await(10,TimeUnit.SECONDS));val point=location;assertNotNull(point)
    assertTrue(kotlin.math.abs(point!!.latitude-35.8347)<0.001)
    result=VehicleApi.request("/api/session/visits/vehicle","POST",JSONObject().put("source","pleos-emulator").put("valid",true).put("latitude",point.latitude).put("longitude",point.longitude).put("measuredAt",point.timestampMillis),vehicle)
    assertTrue("fresh SDK timestamp delta="+(System.currentTimeMillis()-point.timestampMillis),result.getBoolean("locationFresh"))
    if(result.getJSONArray("rewards").length()>0)break
    Thread.sleep(5000)
   }
   assertEquals(1,result.getJSONArray("rewards").length())
   assertEquals(14,VehicleApi.request("/api/session/palette","GET",null,mobile).getJSONArray("colors").length())
   val stickers=VehicleApi.request("/api/session/catalog","GET",null,mobile).getJSONArray("stickers")
   var earned=0;for(i in 0 until stickers.length()){val s=stickers.getJSONObject(i);if(s.optString("rewardSource")=="simulation"&&s.getBoolean("unlocked"))earned++};assertEquals(2,earned)
  }finally{fused.release();VehicleApi.request("/api/sessions/"+session.getString("id"),"DELETE",null,owner)}
 }
}
