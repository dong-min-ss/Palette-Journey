package com.palettejourney.companion;

import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.view.MotionEvent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CompanionDeviceTest {
    private CompanionActivity screen;
    private Object js(String source) throws Exception {
        CountDownLatch done=new CountDownLatch(1);String[] result={null};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->screen.web.evaluateJavascript(source,value->{result[0]=value;done.countDown();}));
        assertTrue("JavaScript response",done.await(10,TimeUnit.SECONDS));
        return new org.json.JSONTokener(result[0]).nextValue();
    }
    private void waitJs(String label,String expression) throws Exception {
        long deadline=SystemClock.uptimeMillis()+45000;
        while(SystemClock.uptimeMillis()<deadline){
            try { if(Boolean.TRUE.equals(js(expression)))return; }
            catch(AssertionError error){
                // A reload can discard an evaluation callback from the previous document.
                if(!"JavaScript response".equals(error.getMessage()))throw error;
            }
            Thread.sleep(100);
        }
        fail(label);
    }
    private void tap(String id) throws Exception {
        js("document.getElementById("+JSONObject.quote(id)+").scrollIntoView({block:'center'});true");Thread.sleep(250);
        JSONObject box=(JSONObject)js("(()=>{const r=document.getElementById("+JSONObject.quote(id)+").getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,w:innerWidth}})()");
        int[] position=new int[2],width=new int[1];var instrument=InstrumentationRegistry.getInstrumentation();
        instrument.runOnMainSync(()->{screen.web.getLocationOnScreen(position);width[0]=screen.web.getWidth();});
        float scale=width[0]/(float)box.getDouble("w"),x=position[0]+(float)box.getDouble("x")*scale,y=position[1]+(float)box.getDouble("y")*scale;
        long time=SystemClock.uptimeMillis();
        for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){
            MotionEvent event=MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,x,y,0);instrument.sendPointerSync(event);event.recycle();Thread.sleep(70);
        }
    }
    private void screenshot(String name) throws Exception {
        try(var out=new java.io.FileOutputStream(new java.io.File(screen.getExternalFilesDir(null),name))){
            InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);
        }
    }
    @Test public void samePhoneDrawingRecordingSaveReload() throws Exception {
        assertTrue(CompanionActivity.trusted(Uri.parse("http://localhost:3000/")));
        assertFalse(CompanionActivity.trusted(Uri.parse("http://localhost:3001/")));
        assertFalse(CompanionActivity.trusted(Uri.parse("http://example.com:3000/")));
        String code=InstrumentationRegistry.getArguments().getString("code");
        assertNotNull("Use the isolated phone-test-server and pass -e code",code);
        var instrument=InstrumentationRegistry.getInstrumentation();
        instrument.getUiAutomation().grantRuntimePermission(instrument.getTargetContext().getPackageName(),"android.permission.RECORD_AUDIO");
        screen=(CompanionActivity)instrument.startActivitySync(new Intent(instrument.getTargetContext(),CompanionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        try {
            waitJs("Web app loaded","!!document.getElementById('join-form')?.onsubmit");
            js("localStorage.clear();window.__reloading=true;true");instrument.runOnMainSync(()->screen.web.reload());
            waitJs("Join form ready","!window.__reloading && !!document.getElementById('join-form')?.onsubmit && !document.getElementById('connection').hidden");
            js("document.getElementById('code').value="+JSONObject.quote(code)+";document.getElementById('join-form').requestSubmit();true");
            waitJs("Family joined","document.getElementById('studio')?.hidden===false && !document.getElementById('voice-start').disabled");
            assertEquals(Boolean.TRUE,js("isSecureContext && !!navigator.mediaDevices?.getUserMedia"));
            js("const gum=navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);navigator.mediaDevices.getUserMedia=(o)=>gum(o).catch(e=>{window.__micError=e.name+': '+e.message;throw e;});true");
            instrument.runOnMainSync(()->((android.view.inputmethod.InputMethodManager)screen.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(screen.web.getWindowToken(),0));
            js("document.activeElement?.blur();document.getElementById('drawing').scrollIntoView({block:'center'});true");Thread.sleep(400);
            JSONObject box=(JSONObject)js("(()=>{const r=document.getElementById('drawing').getBoundingClientRect();return {x:r.x+r.width*.3,y:r.y+r.height*.5,w:innerWidth}})()");
            int[] position=new int[2];int[] width=new int[1];
            instrument.runOnMainSync(()->{screen.web.getLocationOnScreen(position);width[0]=screen.web.getWidth();});
            float scale=width[0]/(float)box.getDouble("w"),x=position[0]+(float)box.getDouble("x")*scale,y=position[1]+(float)box.getDouble("y")*scale;
            screenshot("phone-before-drawing.png");
            android.util.Log.i("PalettePhoneTest","drawing x="+x+" y="+y+" scale="+scale+" bounds="+box);
            long start=SystemClock.uptimeMillis();
            for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP}){
                MotionEvent event=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,x+(action==MotionEvent.ACTION_DOWN?0:80),y,0);
                instrument.sendPointerSync(event);event.recycle();Thread.sleep(60);
            }
            js("document.getElementById('voice-consent').checked=true;document.getElementById('voice-start').click();true");
            waitJs("WebView microphone recording","document.getElementById('voice-status').textContent.startsWith('녹음 중')");
            Thread.sleep(1800);
            js("document.getElementById('voice-pause').click();true");
            assertEquals(Boolean.TRUE,js("document.getElementById('voice-indicator').textContent==='녹음 일시정지'"));
            js("document.getElementById('voice-pause').click();true");Thread.sleep(600);
            js("document.getElementById('voice-stop').click();true");
            waitJs("Recording draft","!document.getElementById('voice-save').disabled");
            js("document.getElementById('voice-save').click();true");
            waitJs("Voice saved on drawing","document.getElementById('voice-status').textContent.includes('저장했어요')");
            // Read the same private page through the family's API after a real microphone upload.
            js("window.__verified=null;(async()=>{const h={Authorization:'Bearer '+localStorage.getItem('palette-journey-participant')};const lib=await(await fetch('/api/session/library',{headers:h})).json();const id=lib.pages[0].id;const p=await(await fetch('/api/session/pages/'+id,{headers:h})).json();const v=await(await fetch('/api/session/pages/'+id+'/voice',{headers:h})).json();window.__verified={strokes:p.strokes.length,revision:v.revision,mime:v.recording.mime};})().catch(e=>window.__verified={error:String(e)});true");
            waitJs("Drawing and voice persisted","window.__verified!==null");
            JSONObject saved=(JSONObject)js("window.__verified");assertTrue(saved.toString(),saved.getInt("strokes")>0);assertEquals(1,saved.getInt("revision"));
            js("window.__reloading=true;true");instrument.runOnMainSync(()->screen.web.reload());
            waitJs("Saved voice reload","!window.__reloading && document.getElementById('voice-play')?.disabled===false");
            tap("voice-play");
            waitJs("Recorded media decodes and plays","document.getElementById('voice-player').currentTime>0");
            screenshot("phone-studio.png");
            js("document.getElementById('voice-player').pause();true");
            String credential=(String)js("localStorage.getItem('palette-journey-participant')");
            String pageId=(String)js("Object.keys(localStorage).filter(k=>k.startsWith('pj-last-page-')).map(k=>localStorage.getItem(k))[0]");
            android.media.MediaPlayer nativePlayer=new android.media.MediaPlayer();
            try {
                nativePlayer.setDataSource(screen,Uri.parse(CompanionActivity.ORIGIN+"/api/session/pages/"+pageId+"/voice/audio"),java.util.Map.of("Authorization","Bearer "+credential));
                nativePlayer.prepare();nativePlayer.start();
                for(int i=0;i<50 && nativePlayer.getCurrentPosition()==0;i++)Thread.sleep(100);
                assertTrue("Vehicle-style native MediaPlayer decoding",nativePlayer.getCurrentPosition()>0);
            } finally { nativePlayer.release(); }
            js("document.querySelector('[data-panel=book]').click();true");
            assertEquals(Boolean.TRUE,js("(()=>{const a=document.querySelector('#panel-book .new-page').getBoundingClientRect();const b=document.querySelector('#panel-book .book-settings').getBoundingClientRect();return b.top-a.bottom>=20 && document.documentElement.scrollWidth<=innerWidth;})()"));
            js("document.getElementById('revisit-setting').scrollIntoView({block:'center'});true");Thread.sleep(350);
            screenshot("phone-book.png");
        } finally { if(screen!=null)instrument.runOnMainSync(()->screen.finish()); }
    }
}
