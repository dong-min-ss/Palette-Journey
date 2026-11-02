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
  val activity=instrumentation.startActivitySync(Intent(context,VehicleSignalsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  var drive=false;var moving=false;var parkAfterDrive=false
  try{for(i in 0..59){var value="";instrumentation.runOnMainSync{value=text(activity.window.decorView)}
   drive=drive||value.contains("기어 값 8")
   val speed=Regex("차량 속도 ([0-9.]+) m/s").find(value)?.groupValues?.get(1)?.toFloatOrNull()?:0f
   moving=moving||speed>1f
   parkAfterDrive=parkAfterDrive||(drive&&value.contains("기어 값 4"))
   if(drive&&moving&&parkAfterDrive)break
   Thread.sleep(1000)
  }
  assertTrue("screen receives D",drive);assertTrue("screen receives moving speed",moving);assertTrue("same screen receives P after D",parkAfterDrive)
  }finally{instrumentation.runOnMainSync{activity.finish()}}
 }
}
