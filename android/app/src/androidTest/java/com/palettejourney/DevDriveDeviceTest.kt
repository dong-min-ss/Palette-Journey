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
  fun waitFor(label:String,seconds:Int=30,condition:()->Boolean){val deadline=android.os.SystemClock.elapsedRealtime()+seconds*1000L;while(android.os.SystemClock.elapsedRealtime()<deadline){if(condition())return;Thread.sleep(200)};fail(label)}
  fun find(v:View,label:String):Button?{if(v is Button&&v.text.toString()==label)return v;if(v is ViewGroup)for(i in 0 until v.childCount){val b=find(v.getChildAt(i),label);if(b!=null)return b};return null}
  val runId=InstrumentationRegistry.getArguments().getString("driveRunId", "manual")
  fun mark(s:String){android.util.Log.i("DriveDemoTest","$runId:$s");File(ctx.cacheDir,"drive-test-stage.txt").writeText(s)}
  try{
   val initialLibrary=VehicleApi.request("/api/session/library","GET",null,phone)
   val pagePath="/api/session/pages/"+initialLibrary.getJSONArray("pages").getJSONObject(0).getString("id")
   val draft=VehicleApi.request(pagePath,"GET",null,phone).put("text","주행 초기화 뒤에도 보존할 합성 검증 기록")
   val saved=VehicleApi.request(pagePath,"PUT",draft,phone)
   VehicleApi.saveDemo(ctx,member);val first=ins.startActivitySync(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK));activity=first
   val monitor=ins.addMonitor(DevDriveActivity::class.java.name,null,false);ins.runOnMainSync{find(first.window.decorView,"개발자 주행 시연")!!.performClick()};var screen=monitor.waitForActivityWithTimeout(10000);assertNotNull(screen);ins.removeMonitor(monitor);activity=screen
   var browser=screen.window.decorView.findViewWithTag<WebView>("drive-preview")
   // A collection refresh can replace the document and discard an in-flight callback.
   // Keep the real DOM assertion, but retry it within waitFor's wall-clock deadline.
   var lastJs="not queried"
   fun js(code:String):String{val l=CountDownLatch(1);var result="null";ins.runOnMainSync{
    if(screen.isDestroyed){
     val resumed=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).firstOrNull{it is DevDriveActivity}
     if(resumed!=null){screen=resumed;activity=resumed;browser=resumed.window.decorView.findViewWithTag("drive-preview");mark("VIEW_RECREATED")}
    }
    browser.evaluateJavascript(code){result=it;l.countDown()}
   };lastJs=if(l.await(2,TimeUnit.SECONDS))result else "NO_CALLBACK";return lastJs}
   fun state()=VehicleApi.request("/api/session/visits/drive","GET",null,member)
   waitFor("destinations ready"){var ready=false;ins.runOnMainSync{ready=screen.window.decorView.findViewWithTag<Button>("drive-city-busan")?.isEnabled==true};ready}
   for((index,city) in listOf("busan","jeju").withIndex()){
    ins.runOnMainSync{screen.window.decorView.findViewWithTag<Button>("drive-city-$city").performClick()}
    waitFor("destination selected"){state().optString("regionId")==city}
    mark("READY_FOR_CONSOLE_${index+1}");waitFor("console route arrived",180){state().getBoolean("arrived")};assertFalse("No reward before parking",state().getBoolean("collected"));mark("ARRIVED_${index+1}")
    waitFor("parked reward",120){state().getBoolean("collected")};mark("AWAIT_CARDS_${index+1}")
    try{waitFor("native cards reflect award"){js("document.querySelectorAll('.collection-badge.is-owned').length")=="5"}}
    catch(error:AssertionError){
     var details="";ins.runOnMainSync{
      fun tree(v:View,depth:Int=0):String{val own="\n"+" ".repeat(depth)+v.javaClass.simpleName+" tag="+v.tag+" shown="+v.isShown+" size="+v.width+"x"+v.height+" "+(if(v is android.widget.TextView)v.text.toString().take(55) else "");return own+(if(v is ViewGroup)(0 until v.childCount).joinToString(""){tree(v.getChildAt(it),depth+1)} else "")}
      details="focus=${screen.hasWindowFocus()} finishing=${screen.isFinishing} destroyed=${screen.isDestroyed} browserVisible=${browser.isShown} attached=${browser.isAttachedToWindow} url=${browser.url}"+tree(screen.window.decorView)
     }
     val screenshot=ins.uiAutomation.takeScreenshot();File(ctx.getExternalFilesDir(null),"drive-failure.png").outputStream().use{screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
     throw AssertionError("Collection DOM=$lastJs; $details",error)
    }
    val cities=VehicleApi.request("/api/session/catalog","GET",null,phone).getJSONArray("cities");for(i in 0 until cities.length()){val c=cities.getJSONObject(i);val acquired=c.getString("id")=="busan"||index==1&&c.getString("id")=="jeju";assertEquals(if(acquired)3 else 0,c.getJSONObject("colors").getInt("acquired"));assertEquals(if(acquired)2 else 0,c.getJSONObject("stickers").getInt("acquired"))}
    assertEquals(index+1,VehicleApi.request("/api/session/visits","GET",null,phone).getJSONArray("rewards").length());mark("COLLECTED_${index+1}")
    ins.waitForIdleSync();val bitmap=ins.uiAutomation.takeScreenshot();File(ctx.cacheDir,"drive-$city.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
   }
   waitFor("reset enabled"){var ready=false;ins.runOnMainSync{ready=find(screen.window.decorView,"지역 방문 초기화")!!.isEnabled};ready}
   ins.runOnMainSync{find(screen.window.decorView,"지역 방문 초기화")!!.performClick()};Thread.sleep(500)
   androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("초기화")).perform(androidx.test.espresso.action.ViewActions.click())
   waitFor("same family relocked"){val c=VehicleApi.request("/api/session/catalog","GET",null,phone).getJSONArray("cities");(0 until c.length()).all{c.getJSONObject(it).getJSONObject("colors").getInt("acquired")==0&&c.getJSONObject(it).getJSONObject("stickers").getInt("acquired")==0}}
   assertEquals(0,VehicleApi.request("/api/session/visits","GET",null,phone).getJSONArray("rewards").length())
   assertEquals(11,VehicleApi.request("/api/session/palette","GET",null,phone).getJSONArray("colors").length())
   val restored=VehicleApi.request(pagePath,"GET",null,phone);assertEquals(saved.getString("text"),restored.getString("text"));assertEquals(saved.getInt("revision"),restored.getInt("revision"))
   assertTrue(state().isNull("tripId"));mark("COMPLETE")
  }finally{
   if(activity!=null)ins.runOnMainSync{activity!!.finish()}
   if(oldToken==null)ctx.getSharedPreferences("connection",0).edit().remove("demo-participant").commit() else VehicleApi.save(ctx,"demo-participant",oldToken)
   VehicleApi.useDemo(ctx,oldDemo);VehicleApi.request("/api/sessions/"+session.getString("id"),"DELETE",null,owner)
  }
 }
}
