package com.palettejourney

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.media.MediaPlayer
import android.util.Base64
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Synthetic PCM only: does not open microphone or read personal archives. */
@RunWith(AndroidJUnit4::class)
class VehicleVoiceDeviceTest {
 @Test fun originalAnswerUploadRetriesAndDownloadedWavDecodes() {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val root=File(context.cacheDir,"vehicle-voice-test-"+java.util.UUID.randomUUID())
  assertTrue(root.mkdir())
  val source=File(root,"source.wav")
  val owner=VehicleApi.randomToken()
  VehicleApi.request("/api/account","PUT",JSONObject(),owner)
  val session=VehicleApi.request("/api/sessions","POST",JSONObject(),owner)
  var archive:VoiceArchive?=null
  var archiveId:String?=null
  try {
   val samples=ByteArray(32000) { i -> if(i%2==0) 12 else 0 }
   WavFile(source).use {it.append(samples,samples.size)}
   archive=VoiceArchive(File(root,"archives"))
   val saved=archive.save(listOf(VoiceArchive.Entry("시험 질문1","합성 답변1",false,source),VoiceArchive.Entry("시험 질문2","합성 답변2",false,source)))
   archiveId=saved.id
   val packed=VehicleStory.pack(saved)
   val member=VehicleApi.randomToken()
   VehicleApi.request("/api/sessions/join","POST",JSONObject().put("code",session.getString("code")),member)
   val page=VehicleApi.request("/api/session/library","GET",null,member).getJSONArray("pages").getJSONObject(0).getString("id")
   val cues=JSONArray()
   for(cue in packed.cues)cues.put(JSONObject().put("question",cue.index).put("at",cue.at).put("questionText",cue.entry.question).put("answerText",cue.entry.answer).put("skipped",cue.entry.skipped))
   val body=JSONObject().put("revision",0).put("mime","audio/wav").put("audio",Base64.encodeToString(packed.wav,Base64.NO_WRAP)).put("duration",packed.duration).put("cues",cues).put("uploadId",saved.id)
   val endpoint="/api/session/pages/"+page+"/voice"
   assertEquals(1,VehicleApi.request(endpoint+"/vehicle","PUT",body,member).getInt("revision"))
   assertEquals(1,VehicleApi.request(endpoint+"/vehicle","PUT",body,member).getInt("revision"))
   val connection=URL("http://10.0.2.2:3000"+endpoint+"/audio").openConnection() as HttpURLConnection
   connection.setRequestProperty("Authorization","Bearer "+member)
   connection.connectTimeout=5000;connection.readTimeout=5000
   val downloaded=try {assertEquals(200,connection.responseCode);connection.inputStream.use {it.readBytes()}}finally{connection.disconnect()}
   assertArrayEquals(packed.wav,downloaded)
   assertArrayEquals(samples,source.readBytes().copyOfRange(44,32044))
   val file=File(root,"downloaded.wav");file.writeBytes(downloaded)
   val player=MediaPlayer()
   try{player.setDataSource(file.absolutePath);player.prepare();assertTrue(player.duration in 1950..2050)}finally{player.release();file.delete()}
   VehicleApi.request(endpoint,"DELETE",JSONObject().put("revision",1),member)
   assertTrue(VehicleApi.request(endpoint,"GET",null,member).isNull("recording"))
  } finally {
   try{VehicleApi.request("/api/sessions/"+session.getString("id"),"DELETE",null,owner)}finally{
    if(archiveId!=null)archive?.delete(archiveId)
    File(root,"archives").delete();source.delete();root.delete()
   }
  }
 }
}
