package com.palettejourney
import android.content.Intent
import android.widget.Button
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DrivePollingDeviceTest {
 @Test fun pollingKeepsButtonsStableAndAcceptsDestinationDuringSlowRefresh(){
  val ins=InstrumentationRegistry.getInstrumentation();val ctx=ins.targetContext
  val oldDemo=VehicleApi.isDemo(ctx);val oldToken=VehicleApi.load(ctx,"demo-participant")
  val owner=VehicleApi.randomToken();VehicleApi.request("/api/account/demo","PUT",JSONObject(),owner)
  val session=VehicleApi.request("/api/sessions","POST",JSONObject(),owner);val token=VehicleApi.randomToken()
  VehicleApi.request("/api/sessions/join","POST",JSONObject().put("code",session.getString("code")),token)
  VehicleApi.request("/api/session/visits/drive","POST",JSONObject().put("action","select").put("regionId","gyeongju"),token)
  var activity:DevDriveActivity?=null;val release=CountDownLatch(1)
  fun waitFor(label:String,condition:()->Boolean){for(i in 0..200){if(condition())return;Thread.sleep(50)};fail(label)}
  try{
   VehicleApi.saveDemo(ctx,token);val screen=ins.startActivitySync(Intent(ctx,DevDriveActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as DevDriveActivity;activity=screen
   var busan:Button?=null;waitFor("buttons loaded"){var ready=false;ins.runOnMainSync{busan=screen.window.decorView.findViewWithTag("drive-city-busan");ready=busan?.isEnabled==true};ready}
   var tint:android.content.res.ColorStateList?=null;ins.runOnMainSync{tint=busan!!.backgroundTintList}
   Thread.sleep(3200);ins.runOnMainSync{assertTrue(busan!!.isEnabled);assertSame("poll must not recreate button tint",tint,busan!!.backgroundTintList)}
   val entered=CountDownLatch(1);val busy=DevDriveActivity::class.java.getDeclaredField("busy").apply{isAccessible=true}
   val dispatch=DevDriveActivity::class.java.getDeclaredMethod("dispatch",DevDriveActivity.Work::class.java,java.lang.Boolean.TYPE).apply{isAccessible=true}
   var sent=false;waitFor("start delayed refresh"){ins.runOnMainSync{if(!busy.getBoolean(screen)){dispatch.invoke(screen,DevDriveActivity.Work{val state=VehicleApi.request("/api/session/visits/drive","GET",null,token);entered.countDown();assertTrue(release.await(10,TimeUnit.SECONDS));state},false);sent=true}};sent}
   assertTrue(entered.await(5,TimeUnit.SECONDS));ins.runOnMainSync{assertTrue("background refresh must keep city buttons usable",busan!!.isEnabled);assertTrue(screen.window.decorView.findViewWithTag<Button>("drive-pause").isEnabled);busan!!.performClick();assertFalse("explicit command blocks duplicates",busan!!.isEnabled)}
   release.countDown();waitFor("destination command not dropped during polling"){VehicleApi.request("/api/session/visits/drive","GET",null,token).optString("regionId")=="busan"}
   waitFor("command completes"){var ready=false;ins.runOnMainSync{ready=busan!!.isEnabled};ready}
  }finally{
   release.countDown();ins.runOnMainSync{activity?.finish()}
   if(oldToken==null)ctx.getSharedPreferences("connection",0).edit().remove("demo-participant").commit() else VehicleApi.save(ctx,"demo-participant",oldToken)
   VehicleApi.useDemo(ctx,oldDemo);VehicleApi.request("/api/sessions/"+session.getString("id"),"DELETE",null,owner)
  }
 }
}
