package com.palettejourney
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class VehicleSignalsScreenDeviceTest {
 private fun text(v:View):String=if(v is TextView)v.text.toString() else if(v is ViewGroup)(0 until v.childCount).joinToString(" "){text(v.getChildAt(it))} else ""
 @Test fun displayedValuesChangeWhileScreenRemainsOpen(){
  val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
  instrumentation.uiAutomation.grantRuntimePermission(context.packageName,"android.car.permission.CAR_SPEED")
  instrumentation.uiAutomation.grantRuntimePermission(context.packageName,"pleos.car.permission.FUSED_LOCATION")
  var activity=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
  fun find(v:View,label:String):View? {if(v is android.widget.Button && v.text.toString()==label)return v;if(v is ViewGroup)for(i in 0 until v.childCount){val found=find(v.getChildAt(i),label);if(found!=null)return found};return null}
  val monitor=instrumentation.addMonitor(VehicleConnectionActivity::class.java.name,null,false)
  instrumentation.runOnMainSync{find(activity.window.decorView,context.getString(R.string.family_connection))!!.performClick()}
  activity=monitor.waitForActivityWithTimeout(10000);assertNotNull(activity);instrumentation.removeMonitor(monitor)
  for(wait in 0..200){var ready=false;instrumentation.runOnMainSync{ready=find(activity.window.decorView,context.getString(R.string.vehicle_signals))?.isEnabled==true};if(ready)break;Thread.sleep(100)}
  val signals=instrumentation.addMonitor(VehicleSignalsActivity::class.java.name,null,false)
  instrumentation.runOnMainSync{find(activity.window.decorView,context.getString(R.string.vehicle_signals))!!.performClick()}
  activity=signals.waitForActivityWithTimeout(10000);assertNotNull(activity);instrumentation.removeMonitor(signals)
  var drive=false;var moving=false;var parkAfterDrive=false;var captured=false
  try{for(i in 0..119){var value="";instrumentation.runOnMainSync{value=text(activity.window.decorView)}
   drive=drive||value.contains("기어 값 8")
   val speed=Regex("차량 속도 ([0-9.]+) m/s").find(value)?.groupValues?.get(1)?.toFloatOrNull()?:0f
   moving=moving||speed>1f
   if(drive&&speed>1f&&!captured){val bitmap=instrumentation.uiAutomation.takeScreenshot();java.io.File(context.cacheDir,"diagnostic-drive.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};captured=true}
   parkAfterDrive=parkAfterDrive||(drive&&value.contains("기어 값 4"))
   if(drive&&moving&&parkAfterDrive)break
   Thread.sleep(1000)
  }
  assertTrue("screen receives D",drive);assertTrue("screen receives moving speed",moving);assertTrue("same screen receives P after D",parkAfterDrive)
  }finally{instrumentation.runOnMainSync{activity.finish()}}
 }
}

