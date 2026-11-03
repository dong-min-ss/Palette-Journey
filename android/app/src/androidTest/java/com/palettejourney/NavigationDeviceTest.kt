package com.palettejourney

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NavigationDeviceTest {
 @Test fun fixedBackReturnsFromEveryScreenAndNestedFamilyConnection(){
  val ins=InstrumentationRegistry.getInstrumentation();val ctx=ins.targetContext
  val oldDemo=VehicleApi.isDemo(ctx);val oldToken=VehicleApi.load(ctx,"demo-participant")
  val owner=VehicleApi.randomToken();VehicleApi.request("/api/account/demo","PUT",JSONObject(),owner)
  val session=VehicleApi.request("/api/sessions","POST",JSONObject(),owner);val token=VehicleApi.randomToken()
  VehicleApi.request("/api/sessions/join","POST",JSONObject().put("code",session.getString("code")),token)
  var current:Activity?=null;var home:Activity?=null
  fun resumed(a:Activity){for(i in 0..100){var yes=false;ins.runOnMainSync{yes=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).contains(a)};if(yes)return;Thread.sleep(100)};fail("previous screen did not resume")}
  fun open(from:Activity,type:Class<out Activity>):Activity{val monitor=ins.addMonitor(type.name,null,false);ins.runOnMainSync{from.startActivity(Intent(from,type))};val a=monitor.waitForActivityWithTimeout(10000);ins.removeMonitor(monitor);assertNotNull(a);return a}
  fun scroll(v:View){if(v is ScrollView)v.fullScroll(View.FOCUS_DOWN);if(v is ViewGroup)for(i in 0 until v.childCount)scroll(v.getChildAt(i))}
  fun back(a:Activity){ins.runOnMainSync{scroll(a.window.decorView)};ins.waitForIdleSync();ins.runOnMainSync{val b=a.window.decorView.findViewWithTag<Button>("vehicle-back");assertNotNull(b);assertTrue(b.isShown);assertTrue(b.isEnabled);assertTrue(b.top>=0);b.performClick()}}
  try{
   VehicleApi.saveDemo(ctx,token);home=ins.startActivitySync(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
   ins.waitForIdleSync();val homeImage=ins.uiAutomation.takeScreenshot();File(ctx.cacheDir,"ui-vehicle-home.png").outputStream().use{homeImage.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
   for(type in listOf(VehicleConnectionActivity::class.java,VehicleSignalsActivity::class.java,CollectionActivity::class.java,JourneyBookActivity::class.java,RecordingsActivity::class.java,UploadStoryActivity::class.java,DevDriveActivity::class.java)){
    current=open(home!!,type);back(current!!);resumed(home!!)
   }
   val drive=open(home!!,DevDriveActivity::class.java);current=drive;val family=open(drive,VehicleConnectionActivity::class.java);current=family
   ins.waitForIdleSync();val bitmap=ins.uiAutomation.takeScreenshot();File(ctx.cacheDir,"vehicle-back.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
   back(family);resumed(drive);current=drive;back(drive);resumed(home!!)
  }finally{
   ins.runOnMainSync{current?.finish();home?.finish()}
   if(oldToken==null)ctx.getSharedPreferences("connection",0).edit().remove("demo-participant").commit() else VehicleApi.save(ctx,"demo-participant",oldToken)
   VehicleApi.useDemo(ctx,oldDemo);VehicleApi.request("/api/sessions/"+session.getString("id"),"DELETE",null,owner)
  }
 }
}
