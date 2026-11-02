package com.palettejourney
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.property.CarPropertyManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
@RunWith(AndroidJUnit4::class)
class VehicleSignalsDeviceTest {
 @Test fun readOnlyPlatformSignals(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
  automation.grantRuntimePermission(context.packageName,"android.car.permission.CAR_SPEED")
  automation.grantRuntimePermission(context.packageName,"pleos.car.permission.FUSED_LOCATION")
  val car=Car.createCar(context)
  try{val p=car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
   val speed=p.getFloatProperty(VehiclePropertyIds.PERF_VEHICLE_SPEED,0)
   val gear=p.getIntProperty(VehiclePropertyIds.GEAR_SELECTION,0)
   assertTrue(speed.isFinite());assertTrue(gear>0)
   android.util.Log.i("PaletteSignalsTest","vehicle speed=$speed gear=$gear")
  }finally{car.disconnect()}
  val fused=FusedLocation(context);assertTrue("FusedLocation supported",fused.isSupported())
  val received=CountDownLatch(1);var result:LocationData?=null;var error:Exception?=null
  val listener=object:FusedLocationListener {
   override fun onChanged(data:LocationData?){result=data;received.countDown()}
   override fun onError(e:Exception){error=e;received.countDown()}
  }
  try{fused.initialize();fused.registerFusedLocationCallback(listener);fused.getLastKnownLocation(listener)
   assertTrue("location callback within 20s",received.await(20,TimeUnit.SECONDS));assertNull(error)
   assertNotNull("non-null emulator location",result)
   assertTrue(result!!.latitude in -90.0..90.0);assertTrue(result!!.longitude in -180.0..180.0)
   android.util.Log.i("PaletteSignalsTest","fused location received; coordinates not logged")
  }finally{fused.unregisterFusedLocationCallback(listener);fused.release()}
 }
}
