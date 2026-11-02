package com.palettejourney
import android.content.Intent
import android.widget.Button
import android.widget.TextView
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class GpsVisitDeviceTest {
 @Test fun realTimeSdkLocationAwardsFamilyOnce(){
  val instrument=InstrumentationRegistry.getInstrumentation();val context=instrument.targetContext
  instrument.uiAutomation.grantRuntimePermission(context.packageName,"pleos.car.permission.FUSED_LOCATION")
  instrument.uiAutomation.grantRuntimePermission(context.packageName,"android.car.permission.CAR_SPEED")
  val previousDemo=VehicleApi.isDemo(context);val previousToken=VehicleApi.load(context,"demo-participant")
  val owner=VehicleApi.randomToken();VehicleApi.request("/api/account/demo","PUT",JSONObject(),owner)
  val session=VehicleApi.request("/api/sessions","POST",JSONObject(),owner)
  val vehicle=VehicleApi.randomToken();val mobile=VehicleApi.randomToken()
  for(t in listOf(vehicle,mobile))VehicleApi.request("/api/sessions/join","POST",JSONObject().put("code",session.getString("code")),t)
  VehicleApi.saveDemo(context,vehicle)
  var activity=instrument.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
  fun button(v:View,label:String):Button?{if(v is Button&&v.text.toString()==label)return v;if(v is ViewGroup)for(i in 0 until v.childCount){val found=button(v.getChildAt(i),label);if(found!=null)return found};return null}
  val familyMonitor=instrument.addMonitor(VehicleConnectionActivity::class.java.name,null,false)
  instrument.runOnMainSync{button(activity.window.decorView,context.getString(R.string.family_connection))!!.performClick()}
  activity=familyMonitor.waitForActivityWithTimeout(10000);assertNotNull(activity);instrument.removeMonitor(familyMonitor)
  for(i in 0..200){var ready=false;instrument.runOnMainSync{ready=button(activity.window.decorView,context.getString(R.string.vehicle_signals))?.isEnabled==true};if(ready)break;Thread.sleep(100)}
  val diagnosticMonitor=instrument.addMonitor(VehicleSignalsActivity::class.java.name,null,false)
  instrument.runOnMainSync{button(activity.window.decorView,context.getString(R.string.vehicle_signals))!!.performClick()}
  activity=diagnosticMonitor.waitForActivityWithTimeout(10000);assertNotNull(activity);instrument.removeMonitor(diagnosticMonitor)
  fun assets():String{var value="";instrument.runOnMainSync{value=(activity.window.decorView.findViewWithTag<View>("reward-assets") as TextView).text.toString()};return value}
  fun capture(name:String){val bitmap=instrument.uiAutomation.takeScreenshot();java.io.File(context.cacheDir,name).outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
  fun find(v:View):Button?{if(v is Button&&v.text.toString()==context.getString(R.string.gps_reward_simulation))return v;if(v is ViewGroup)for(i in 0 until v.childCount){val found=find(v.getChildAt(i));if(found!=null)return found};return null}
  try{
   for(i in 0..29){if(assets().contains("경주 색 0/3 · 스티커 0/2"))break;Thread.sleep(1000)}
   assertTrue(assets().contains("경주 색 0/3 · 스티커 0/2"));assertEquals(5,Regex("잠김").findAll(assets()).count());capture("demo-locked.png")
   instrument.runOnMainSync{find(activity.window.decorView)!!.performClick()}
   android.util.Log.i("PaletteDemoTest","READY_FOR_GYEONGJU")
   for(i in 0..159){if(assets().contains("경주 색 3/3 · 스티커 2/2"))break;Thread.sleep(1000)}
   assertTrue(assets().contains("경주 색 3/3 · 스티커 2/2"));capture("demo-unlocked.png")
   assertEquals(14,VehicleApi.request("/api/session/palette","GET",null,mobile).getJSONArray("colors").length())
   assertEquals(1,VehicleApi.request("/api/session/visits","GET",null,mobile).getJSONArray("rewards").length())
  }finally{
   instrument.runOnMainSync{activity.finish()}
   if(previousToken!=null)VehicleApi.save(context,"demo-participant",previousToken)
   VehicleApi.useDemo(context,previousDemo)
   VehicleApi.request("/api/sessions/"+session.getString("id"),"DELETE",null,owner)
  }
 }
}
