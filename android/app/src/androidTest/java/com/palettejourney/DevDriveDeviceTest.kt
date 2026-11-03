package com.palettejourney
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
@RunWith(AndroidJUnit4::class)
class DevDriveDeviceTest {
 @Test fun familyConnectionHasDeveloperEntry(){
  val ins=InstrumentationRegistry.getInstrumentation();val ctx=ins.targetContext
  fun find(v:View,label:String):Button?{if(v is Button&&v.text.toString()==label)return v;if(v is ViewGroup)for(i in 0 until v.childCount){val b=find(v.getChildAt(i),label);if(b!=null)return b};return null}
  val first=ins.startActivitySync(Intent(ctx,VehicleConnectionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK));var opened:android.app.Activity?=null
  try{var ready=false;for(i in 0..100){ins.runOnMainSync{ready=find(first.window.decorView,"개발용 시연 ＋")?.isEnabled==true};if(ready)break;Thread.sleep(100)};assertTrue(ready);val monitor=ins.addMonitor(DevDriveActivity::class.java.name,null,false);ins.runOnMainSync{find(first.window.decorView,"개발용 시연 ＋")!!.performClick();val b=find(first.window.decorView,"개발자 주행 시연")!!;assertTrue(b.isShown);b.performClick()};opened=monitor.waitForActivityWithTimeout(10000);ins.removeMonitor(monitor);assertNotNull(opened)}finally{ins.runOnMainSync{opened?.finish();first.finish()}}
 }
 @Test fun consoleDriveTwoDestinationsAndFamilyReset(){
  val ins=InstrumentationRegistry.getInstrumentation();val ctx=ins.targetContext
  val oldDemo=VehicleApi.isDemo(ctx);val oldToken=VehicleApi.load(ctx,"demo-participant")
  val owner=VehicleApi.randomToken();VehicleApi.request("/api/account/demo","PUT",JSONObject(),owner)
  val session=VehicleApi.request("/api/sessions","POST",JSONObject(),owner);val member=VehicleApi.randomToken();val phone=VehicleApi.randomToken()
  for(t in listOf(member,phone))VehicleApi.request("/api/sessions/join","POST",JSONObject().put("code",session.getString("code")),t)
  var activity:android.app.Activity?=null
  fun waitFor(label:String,seconds:Int=30,condition:()->Boolean){for(i in 0 until seconds*5){if(condition())return;Thread.sleep(200)};fail(label)}
  fun find(v:View,label:String):Button?{if(v is Button&&v.text.toString()==label)return v;if(v is ViewGroup)for(i in 0 until v.childCount){val b=find(v.getChildAt(i),label);if(b!=null)return b};return null}
  fun mark(s:String){android.util.Log.i("DriveDemoTest",s);File(ctx.cacheDir,"drive-test-stage.txt").writeText(s)}
  try{
   VehicleApi.saveDemo(ctx,member);val first=ins.startActivitySync(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK));activity=first
   val monitor=ins.addMonitor(DevDriveActivity::class.java.name,null,false);ins.runOnMainSync{find(first.window.decorView,"개발자 주행 시연")!!.performClick()};val screen=monitor.waitForActivityWithTimeout(10000);assertNotNull(screen);ins.removeMonitor(monitor);activity=screen
   val browser=screen.window.decorView.findViewWithTag<WebView>("drive-preview")
   fun js(code:String):String{val l=CountDownLatch(1);var result="";ins.runOnMainSync{browser.evaluateJavascript(code){result=it;l.countDown()}};assertTrue(l.await(5,TimeUnit.SECONDS));return result}
   fun state()=VehicleApi.request("/api/session/visits/drive","GET",null,member)
   waitFor("destinations ready"){var ready=false;ins.runOnMainSync{ready=screen.window.decorView.findViewWithTag<Button>("drive-city-busan")?.isEnabled==true};ready}
   for((index,city) in listOf("busan","jeju").withIndex()){
    ins.runOnMainSync{screen.window.decorView.findViewWithTag<Button>("drive-city-$city").performClick()}
    waitFor("destination selected"){state().optString("regionId")==city}
    mark("READY_FOR_CONSOLE_${index+1}");waitFor("console route arrived",180){state().getBoolean("arrived")};mark("ARRIVED_${index+1}")
    waitFor("parked reward",120){state().getBoolean("collected")};waitFor("native cards reflect award"){js("document.querySelectorAll('.collection-badge.is-owned').length")=="5"}
    val cities=VehicleApi.request("/api/session/catalog","GET",null,phone).getJSONArray("cities");for(i in 0 until cities.length()){val c=cities.getJSONObject(i);assertEquals(if(c.getString("id")=="busan"||index==1&&c.getString("id")=="jeju")3 else 0,c.getJSONObject("colors").getInt("acquired"))}
    ins.waitForIdleSync();val bitmap=ins.uiAutomation.takeScreenshot();File(ctx.cacheDir,"drive-$city.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
   }
   waitFor("reset enabled"){var ready=false;ins.runOnMainSync{ready=find(screen.window.decorView,"지역 방문 초기화")!!.isEnabled};ready}
   ins.runOnMainSync{find(screen.window.decorView,"지역 방문 초기화")!!.performClick()};Thread.sleep(500)
   androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("초기화")).perform(androidx.test.espresso.action.ViewActions.click())
   waitFor("same family relocked"){val c=VehicleApi.request("/api/session/catalog","GET",null,phone).getJSONArray("cities");(0 until c.length()).all{c.getJSONObject(it).getJSONObject("colors").getInt("acquired")==0}}
   assertTrue(state().isNull("tripId"));mark("COMPLETE")
  }finally{
   if(activity!=null)ins.runOnMainSync{activity!!.finish()}
   if(oldToken==null)ctx.getSharedPreferences("connection",0).edit().remove("demo-participant").commit() else VehicleApi.save(ctx,"demo-participant",oldToken)
   VehicleApi.useDemo(ctx,oldDemo);VehicleApi.request("/api/sessions/"+session.getString("id"),"DELETE",null,owner)
  }
 }
}
