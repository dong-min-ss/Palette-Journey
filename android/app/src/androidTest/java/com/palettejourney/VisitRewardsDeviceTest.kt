package com.palettejourney
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
/** Synthetic family only; real Android HTTP transport, no personal credentials. */
@RunWith(AndroidJUnit4::class)
class VisitRewardsDeviceTest {
 private fun get(path:String,t:String)=VehicleApi.request("/api/session/$path","GET",null,t)
 private fun route(t:String,start:Long){for(i in 0..8)VehicleApi.request("/api/session/visits/mock","POST",JSONObject().put("source","simulation").put("latitude",35.8347).put("longitude",129.219).put("timestamp",start+i*15000L),t)}
 private fun check(t:String){
  val p=get("palette",t);assertEquals(14,p.getJSONArray("colors").length())
  assertEquals(1,get("visits",t).getJSONArray("rewards").length())
  val stickers=get("catalog",t).getJSONArray("stickers");var count=0
  for(i in 0 until stickers.length()){val s=stickers.getJSONObject(i);if(s.getString("kind")=="regional"){count++;assertTrue(s.getBoolean("unlocked"));assertFalse(s.getBoolean("developmentUnlocked"));assertEquals("simulation",s.getString("rewardSource"))}}
  assertEquals(2,count)
 }
 @Test fun sharedRewardsAndReconnect(){
  val owner=VehicleApi.randomToken();VehicleApi.request("/api/account","PUT",JSONObject(),owner)
  var session=VehicleApi.request("/api/sessions","POST",JSONObject(),owner)
  try {
   val vehicle=VehicleApi.randomToken();VehicleApi.request("/api/sessions/join","POST",JSONObject().put("code",session.getString("code")),vehicle)
   val mobile=VehicleApi.randomToken();val invite=VehicleApi.request("/api/session/invitations","POST",JSONObject(),vehicle)
   VehicleApi.request("/api/sessions/join","POST",JSONObject().put("code",invite.getString("code")),mobile)
   assertTrue(get("visits",vehicle).getBoolean("simulationEnabled"));assertEquals(0,get("visits",mobile).getJSONArray("rewards").length())
   route(vehicle,0);check(vehicle);check(mobile)
   VehicleApi.request("/api/session/visits/mock","POST",JSONObject().put("source","simulation").put("latitude",37).put("longitude",129.219).put("timestamp",150000),vehicle)
   route(vehicle,180000);check(mobile)
   session=VehicleApi.request("/api/sessions","POST",JSONObject(),owner)
   val again=VehicleApi.randomToken();VehicleApi.request("/api/sessions/join","POST",JSONObject().put("code",session.getString("code")),again);check(again)
  }finally{VehicleApi.request("/api/sessions/"+session.getString("id"),"DELETE",null,owner)}
 }
}
