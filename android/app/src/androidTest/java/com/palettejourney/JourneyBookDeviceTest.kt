package com.palettejourney

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Synthetic family, picture and tone. Never opens a microphone or reads personal archives. */
@RunWith(AndroidJUnit4::class)
class JourneyBookDeviceTest {
 @Test fun familyDrawingAndConversationRenderAndStopWhenPageChanges(){
  val instrument=InstrumentationRegistry.getInstrumentation();val context=instrument.targetContext
  val previousDemo=VehicleApi.isDemo(context);val previousToken=VehicleApi.load(context,"demo-participant")
  val owner=VehicleApi.randomToken();VehicleApi.request("/api/account/demo","PUT",JSONObject(),owner)
  val session=VehicleApi.request("/api/sessions","POST",JSONObject(),owner);val member=VehicleApi.randomToken()
  VehicleApi.request("/api/sessions/join","POST",JSONObject().put("code",session.getString("code")),member)
  var activity:android.app.Activity?=null
  fun button(v:View,label:String):Button?{if(v is Button&&v.text.toString()==label)return v;if(v is ViewGroup)for(i in 0 until v.childCount){val b=button(v.getChildAt(i),label);if(b!=null)return b};return null}
  try{
   val start=System.currentTimeMillis();for(i in 0..8)VehicleApi.request("/api/session/visits/mock","POST",JSONObject().put("source","simulation").put("latitude",35.8347).put("longitude",129.219).put("timestamp",start+i*15000),member)
   val library=VehicleApi.request("/api/session/library","GET",null,member);val pageId=library.getJSONArray("pages").getJSONObject(0).getString("id");val path="/api/session/pages/"+pageId
   val page=VehicleApi.request(path,"GET",null,member).put("title","경주의 밤").put("entryDate","2026-11-03").put("text","가족과 함께 본 첨성대")
   page.put("strokes",JSONArray("""[{"tool":"paint","color":"#293C70","width":30,"points":[[20,50],[200,50]]},{"tool":"erase","color":"#293C70","width":20,"points":[[100,50]]}]"""))
   page.put("stickers",JSONArray("""[{"id":"tower","stickerId":"gyeongju-cheomseongdae","x":250,"y":120,"width":100,"height":180,"strokes":[{"tool":"paint","color":"#D9AA38","width":30,"points":[[40,200],[550,200]]}]}]"""))
   val saved=VehicleApi.request(path,"PUT",page,member)
   VehicleApi.request("/api/session/diaries/"+library.getJSONArray("diaries").getJSONObject(0).getString("id")+"/pages","POST",JSONObject().put("title","다음 날").put("entryDate","2026-11-02").put("template","blank"),member)
   val source=File(context.cacheDir,"book-fixture.wav");val pcm=ByteArray(32000){i->if(i%2==0)12 else 0};WavFile(source).use{it.append(pcm,pcm.size)}
   try{VehicleApi.request(path+"/voice/vehicle","PUT",JSONObject().put("revision",0).put("mime","audio/wav").put("audio",android.util.Base64.encodeToString(source.readBytes(),android.util.Base64.NO_WRAP)).put("duration",1000).put("uploadId",java.util.UUID.randomUUID().toString()).put("cues",JSONArray("""[{"question":0,"at":0,"questionText":"경주에서 무엇을 봤어?","answerText":"합성 시험 답변","skipped":false}]""")),member)}finally{source.delete()}
   VehicleApi.saveDemo(context,member)
   val main=instrument.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK));activity=main
   val monitor=instrument.addMonitor(JourneyBookActivity::class.java.name,null,false)
   instrument.runOnMainSync{val open=button(main.window.decorView,"여행책 감상")!!;assertTrue(open.isShown);open.performClick()}
   val viewer=monitor.waitForActivityWithTimeout(10000);assertNotNull(viewer);instrument.removeMonitor(monitor);activity=viewer
   fun tagged(tag:String):View=viewer.window.decorView.findViewWithTag(tag)
   fun status():String{var s="";instrument.runOnMainSync{s=(tagged("book-status") as TextView).text.toString()};return s}
   fun waitFor(message:String,condition:()->Boolean){for(i in 0..300){if(condition())return;Thread.sleep(100)};fail(message+": "+status())}
   fun js(script:String):String{val latch=CountDownLatch(1);var result="";instrument.runOnMainSync{(tagged("book-drawing") as WebView).evaluateJavascript(script){result=it;latch.countDown()}};assertTrue(latch.await(5,TimeUnit.SECONDS));return result}
   waitFor("page ready"){status()=="질문 재합성 · 답변 원음"}
   waitFor("local renderer ready"){js("document.body.dataset.ready === 'true'")=="true"}
   assertEquals("[41,60,112,255]",js("JSON.stringify(Array.from(document.getElementById('ink').getContext('2d').getImageData(50,50,1,1).data))").let{JSONArray("["+it+"]").getString(0)})
   assertEquals("0",js("document.getElementById('ink').getContext('2d').getImageData(100,50,1,1).data[3]"))
   assertEquals("1",js("document.querySelectorAll('.sticker').length"))
   assertEquals("true",js("document.querySelector('.sticker img').complete"))
   assertEquals("\"undefined\"",js("typeof window.Android"))
   instrument.runOnMainSync{(tagged("book-play") as Button).performClick()}
   waitFor("audio starts"){status()=="재생 중"}
   instrument.runOnMainSync{(tagged("book-play") as Button).performClick()};assertEquals("일시정지",status())
   instrument.runOnMainSync{(tagged("book-play") as Button).performClick()};assertEquals("재생 중",status())
   instrument.waitForIdleSync();Thread.sleep(200);val screenshot=instrument.uiAutomation.takeScreenshot();File(context.cacheDir,"journey-book.png").outputStream().use{screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
   instrument.runOnMainSync{(tagged("book-picker") as Spinner).setSelection(1)}
   waitFor("other page without recording"){status()=="아직 대화가 없어요"}
   instrument.runOnMainSync{assertFalse((tagged("book-play") as Button).isEnabled)}
   assertTrue(context.cacheDir.listFiles()!!.none{it.name.startsWith("book-audio-")})
   instrument.runOnMainSync{(tagged("book-picker") as Spinner).setSelection(0)};waitFor("first page reload"){status()=="질문 재합성 · 답변 원음"}
   instrument.runOnMainSync{(tagged("book-play") as Button).performClick()};waitFor("conversation completes"){status()=="재생 완료"}
   assertEquals(saved.toString(),VehicleApi.request(path,"GET",null,member).toString())
   assertTrue(VehicleApi.request("/api/session/connection","GET",null,member).isNull("page"))
   VehicleApi.request("/api/sessions/"+session.getString("id"),"DELETE",null,owner)
   instrument.runOnMainSync{button(viewer.window.decorView,"새로고침")!!.performClick()};waitFor("expired family clears viewer"){status().startsWith("가족 연결 만료")}
   instrument.runOnMainSync{assertFalse((tagged("book-play") as Button).isEnabled)}
  }finally{
   if(activity!=null)instrument.runOnMainSync{activity!!.finish()}
   if(previousToken==null)context.getSharedPreferences("connection",0).edit().remove("demo-participant").commit() else VehicleApi.save(context,"demo-participant",previousToken)
   VehicleApi.useDemo(context,previousDemo);VehicleApi.request("/api/sessions/"+session.getString("id"),"DELETE",null,owner)
  }
 }
}
