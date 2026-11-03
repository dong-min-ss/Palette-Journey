package com.palettejourney

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.TextView
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
class CollectionDeviceTest {
 @Test fun hierarchyAndCityOwnershipUpdateWithoutMixingCities(){
  val instrument=InstrumentationRegistry.getInstrumentation();val context=instrument.targetContext
  val previousDemo=VehicleApi.isDemo(context);val previousToken=VehicleApi.load(context,"demo-participant")
  val owner=VehicleApi.randomToken();VehicleApi.request("/api/account/demo","PUT",JSONObject(),owner)
  val session=VehicleApi.request("/api/sessions","POST",JSONObject(),owner);val member=VehicleApi.randomToken();val phone=VehicleApi.randomToken()
  for(t in listOf(member,phone))VehicleApi.request("/api/sessions/join","POST",JSONObject().put("code",session.getString("code")),t)
  var activity:android.app.Activity?=null
  fun button(v:View,label:String):Button?{if(v is Button&&v.text.toString()==label)return v;if(v is ViewGroup)for(i in 0 until v.childCount){val b=button(v.getChildAt(i),label);if(b!=null)return b};return null}
  try{
   VehicleApi.saveDemo(context,member);val first=instrument.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK));activity=first
   val monitor=instrument.addMonitor(CollectionActivity::class.java.name,null,false)
   instrument.runOnMainSync{val open=button(first.window.decorView,"색·스티커 사전")!!;assertTrue(open.isShown);open.performClick()}
   val screen=monitor.waitForActivityWithTimeout(10000);assertNotNull(screen);instrument.removeMonitor(monitor);activity=screen
   val browser=screen.window.decorView.findViewWithTag<WebView>("collection-browser")
   fun js(code:String):String{val latch=CountDownLatch(1);var result="";instrument.runOnMainSync{browser.evaluateJavascript(code){result=it;latch.countDown()}};assertTrue(latch.await(5,TimeUnit.SECONDS));return result}
   fun waitFor(label:String,condition:()->Boolean){for(i in 0..200){if(condition())return;Thread.sleep(100)};fail(label)}
   fun capture(name:String){instrument.waitForIdleSync();Thread.sleep(200);val bitmap=instrument.uiAutomation.takeScreenshot();File(context.cacheDir,name).outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
   fun click(selector:String){js("document.querySelector("+JSONObject.quote(selector)+").click()");instrument.waitForIdleSync()}
   waitFor("native catalog load complete"){var ready=false;instrument.runOnMainSync{ready=screen.window.decorView.findViewWithTag<TextView>("collection-status").text.toString()=="가족 공유 · 모의 방문 컬렉션"};ready}
   waitFor("catalog ready"){js("document.body.dataset.ready==='true'")=="true"}
   assertEquals("8",js("document.querySelectorAll('[data-collection-group]').length"));capture("collection-root.png")
   click("[data-collection-group='gyeongsang']");assertEquals("2",js("document.querySelectorAll('[data-collection-city]').length"))
   click("[data-collection-city='busan']");assertEquals("5",js("document.querySelectorAll('.collection-card').length"));assertEquals("0",js("document.querySelectorAll('.collection-badge.is-owned').length"))
   waitFor("busan images loaded"){js("Array.from(document.querySelectorAll('.collection-card img')).every(i=>i.complete&&i.naturalWidth>0)")=="true"}
   val start=System.currentTimeMillis();for(i in 0..8)VehicleApi.request("/api/session/visits/mock","POST",JSONObject().put("source","simulation").put("latitude",35.1532).put("longitude",129.1186).put("timestamp",start+i*15000),member)
   waitFor("busan reward reflected by polling"){js("document.querySelectorAll('.collection-badge.is-owned').length")=="5"}
   assertEquals("\"busan\"",js("window.collection.getSelection().cityId"));capture("collection-busan.png")
   val shared=VehicleApi.request("/api/session/catalog","GET",null,phone).getJSONArray("cities");for(i in 0 until shared.length()){val city=shared.getJSONObject(i);assertEquals(if(city.getString("id")=="busan")3 else 0,city.getJSONObject("colors").getInt("acquired"))}
   for((group,city) in listOf("jeju" to "jeju","gangwon" to "gangneung","seoul" to "seoul")){
    click(".collection-trail button");click("[data-collection-group='$group']");click("[data-collection-city='$city']")
    assertEquals("5",js("document.querySelectorAll('.collection-card').length"));assertEquals("0",js("document.querySelectorAll('.collection-badge.is-owned').length"));waitFor("all new SVGs load"){js("Array.from(document.querySelectorAll('.collection-card img')).every(i=>i.complete&&i.naturalWidth>0)")=="true"}
    if(city=="jeju")capture("collection-jeju.png")
   }
   click(".collection-trail button");click("[data-collection-group='basic']");assertEquals("13",js("document.querySelectorAll('.collection-card').length"));assertEquals("13",js("document.querySelectorAll('.collection-badge.is-owned').length"))
   assertEquals("\"undefined\"",js("typeof window.Android"))
  }finally{
   if(activity!=null)instrument.runOnMainSync{activity!!.finish()}
   if(previousToken==null)context.getSharedPreferences("connection",0).edit().remove("demo-participant").commit() else VehicleApi.save(context,"demo-participant",previousToken)
   VehicleApi.useDemo(context,previousDemo);VehicleApi.request("/api/sessions/"+session.getString("id"),"DELETE",null,owner)
  }
 }
}
