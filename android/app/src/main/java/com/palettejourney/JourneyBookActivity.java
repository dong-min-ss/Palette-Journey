package com.palettejourney;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.media.MediaPlayer;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.widget.*;
import org.json.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Read-only family book. The existing emulator gate and platform driving restrictions remain. */
public class JourneyBookActivity extends Activity {
    private final Handler main=new Handler();
    private final ExecutorService network=Executors.newSingleThreadExecutor();
    private final List<JSONObject> entries=new ArrayList<>();
    private Spinner picker;private TextView heading,caption,status,clock;private Button play,previous,next,refresh;private WebView drawing;private SeekBar seek;
    private String token,pageId;private int index=-1,generation=0,voiceRevision;private JSONObject recording;
    private boolean active,loading,preparing,binding,completed,scrubbing;private MediaPlayer player;private File audioFile;
    private boolean allowed(){return BuildConfig.DEBUG&&("ranchu".equals(android.os.Build.HARDWARE)||"goldfish".equals(android.os.Build.HARDWARE));}
    @Override public void onCreate(Bundle state){super.onCreate(state);
        File[] stale=getCacheDir().listFiles((dir,name)->name.startsWith("book-audio-")&&name.endsWith(".media"));if(stale!=null)for(File file:stale)file.delete();
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(DevUi.dp(this,24),DevUi.dp(this,12),DevUi.dp(this,24),DevUi.dp(this,12));root.setBackgroundColor(DevUi.BG);
        LinearLayout toolbar=new LinearLayout(this);TextView title=DevUi.text(this,"여행책 · 가족 비공개",28);toolbar.addView(title,new LinearLayout.LayoutParams(0,-2,1));refresh=new Button(this);refresh.setText("새로고침");toolbar.addView(refresh);Button back=new Button(this);back.setText("돌아가기");toolbar.addView(back);root.addView(toolbar);
        picker=new Spinner(this);picker.setTag("book-picker");root.addView(picker);heading=DevUi.text(this,"그림일기를 불러오는 중",26);heading.setTag("book-heading");root.addView(heading);
        drawing=new WebView(this);drawing.setTag("book-drawing");drawing.setBackgroundColor(DevUi.PANEL);drawing.getSettings().setJavaScriptEnabled(true);drawing.getSettings().setAllowFileAccess(false);drawing.getSettings().setAllowContentAccess(false);drawing.getSettings().setBlockNetworkLoads(true);drawing.getSettings().setDomStorageEnabled(false);drawing.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_NO_CACHE);drawing.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}});root.addView(drawing,new LinearLayout.LayoutParams(-1,0,1));
        caption=DevUi.text(this,"",20);caption.setMaxLines(2);root.addView(caption);
        seek=new SeekBar(this);seek.setTag("book-seek");root.addView(seek);LinearLayout controls=new LinearLayout(this);previous=new Button(this);previous.setText("이전");play=new Button(this);play.setText("대화 듣기");play.setTag("book-play");next=new Button(this);next.setText("다음");controls.addView(previous);controls.addView(play);controls.addView(next);root.addView(controls);
        LinearLayout footer=new LinearLayout(this);status=DevUi.text(this,"가족 연결 확인 중",20);status.setTag("book-status");footer.addView(status,new LinearLayout.LayoutParams(0,-2,1));clock=DevUi.text(this,"",20);footer.addView(clock);root.addView(footer);setContentView(root);DevUi.styleTree(root);
        back.setOnClickListener(v->finish());refresh.setOnClickListener(v->loadLibrary());previous.setOnClickListener(v->picker.setSelection(index-1));next.setOnClickListener(v->picker.setSelection(index+1));play.setOnClickListener(v->toggle());
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int position,long id){if(!binding&&position!=index)open(position);}});
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar bar){scrubbing=true;}public void onProgressChanged(SeekBar bar,int value,boolean user){}public void onStopTrackingTouch(SeekBar bar){scrubbing=false;if(player!=null&&!preparing){player.seekTo(bar.getProgress());completed=false;}}});buttons();
    }
    private void buttons(){boolean usable=active&&allowed()&&!loading;picker.setEnabled(usable&&!entries.isEmpty());refresh.setEnabled(active&&allowed()&&!loading);previous.setEnabled(usable&&index>0);next.setEnabled(usable&&index>=0&&index+1<entries.size());play.setEnabled(usable&&recording!=null&&!preparing);seek.setEnabled(player!=null&&!preparing);}
    private void clearAudio(){if(player!=null){player.release();player=null;}if(audioFile!=null){audioFile.delete();audioFile=null;}preparing=false;completed=false;clock.setText("");seek.setProgress(0);play.setText("대화 듣기");}
    private void blank(){drawing.loadData("","text/html","UTF-8");caption.setText("");recording=null;}
    private void fail(Exception error){status.setText("HTTP 401".equals(error.getMessage())?"가족 연결 만료 · 가족 연결에서 다시 연결해주세요.":"불러오지 못했어요 · 새로고침으로 다시 시도해주세요.");}
    private void loadLibrary(){if(!active||!allowed())return;int epoch=++generation;String remembered=pageId;clearAudio();blank();loading=true;index=-1;heading.setVisibility(View.VISIBLE);buttons();status.setText("그림일기 확인 중");
        network.execute(()->{try{String credential=VehicleApi.load(this);if(credential==null)throw new java.io.IOException("HTTP 401");JSONObject library=VehicleApi.request("/api/session/library","GET",null,credential),focus=VehicleApi.request("/api/session/connection","GET",null,credential);List<JSONObject> list=new ArrayList<>();JSONArray pages=library.getJSONArray("pages");for(int i=0;i<pages.length();i++)list.add(pages.getJSONObject(i));list.sort((a,b)->{int date=b.optString("entryDate").compareTo(a.optString("entryDate"));return date!=0?date:Integer.compare(a.optInt("position"),b.optInt("position"));});JSONObject shared=focus.optJSONObject("page");String wanted=remembered!=null?remembered:shared==null?null:shared.optString("id");
            main.post(()->{if(!active||generation!=epoch)return;token=credential;entries.clear();entries.addAll(list);loading=false;int selected=0;String[] labels=new String[list.size()];for(int i=0;i<list.size();i++){JSONObject p=list.get(i);labels[i]=p.optString("entryDate")+" · "+p.optString("title");if(p.optString("id").equals(wanted))selected=i;}binding=true;picker.setAdapter(DevUi.spinner(this,labels));picker.setSelection(selected,false);binding=false;if(list.isEmpty()){heading.setText("아직 그림일기가 없어요");status.setText("휴대폰에서 첫 그림을 남겨보세요.");buttons();}else open(selected);});
        }catch(Exception e){main.post(()->{if(!active||generation!=epoch)return;loading=false;entries.clear();binding=true;picker.setAdapter(DevUi.spinner(this,new String[0]));binding=false;heading.setText("여행책을 열 수 없어요");fail(e);buttons();});}});
    }
    private void open(int position){if(!active||position<0||position>=entries.size())return;int epoch=++generation;index=position;String id=entries.get(position).optString("id"),credential=token;pageId=id;clearAudio();blank();loading=true;heading.setText(entries.get(position).optString("entryDate")+" · "+entries.get(position).optString("title"));status.setText("그림 불러오는 중");buttons();
        network.execute(()->{try{JSONObject page=VehicleApi.request("/api/session/pages/"+id,"GET",null,credential),voice=VehicleApi.request("/api/session/pages/"+id+"/voice","GET",null,credential);String html=JourneyBookRenderer.html(this,page);
            main.post(()->{if(!active||generation!=epoch)return;heading.setVisibility(View.GONE);drawing.loadDataWithBaseURL("https://viewer.palette.invalid/",html,"text/html","UTF-8",null);caption.setText(page.optString("text"));caption.setVisibility(caption.getText().length()==0?View.GONE:View.VISIBLE);recording=voice.optJSONObject("recording");voiceRevision=voice.optInt("revision");loading=false;status.setText(recording==null?"아직 대화가 없어요":"vehicle-answer-original".equals(recording.optString("mode"))?"질문 재합성 · 답변 원음":"대화 듣기를 눌러 재생해요");buttons();});
        }catch(Exception e){main.post(()->{if(!active||generation!=epoch)return;loading=false;blank();fail(e);buttons();});}});
    }
    private void toggle(){if(!active||loading||preparing||recording==null)return;if(player!=null){if(player.isPlaying()){player.pause();play.setText("이어 듣기");status.setText("일시정지");}else{if(completed){player.seekTo(0);completed=false;}player.start();play.setText("일시정지");status.setText("재생 중");}return;}
        final int epoch=generation,revision=voiceRevision;String id=pageId,credential=token,route="vehicle-answer-original".equals(recording.optString("mode"))?"conversation":"audio";preparing=true;status.setText("대화 준비 중");buttons();
        network.execute(()->{File file=null;try{byte[] bytes=VehicleApi.audio("/api/session/pages/"+id+"/voice/"+route,credential,revision);JSONObject current=VehicleApi.request("/api/session/pages/"+id+"/voice","GET",null,credential);if(current.optJSONObject("recording")==null||current.getInt("revision")!=revision)throw new java.io.IOException("VOICE_CHANGED");file=File.createTempFile("book-audio-",".media",getCacheDir());try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){out.write(bytes);}File ready=file;
            main.post(()->{if(!active||generation!=epoch){ready.delete();return;}audioFile=ready;MediaPlayer media=new MediaPlayer();player=media;media.setOnPreparedListener(p->{if(!active||generation!=epoch||player!=p)return;preparing=false;seek.setMax(p.getDuration());clock.setText(time(0)+" / "+time(p.getDuration()));p.start();play.setText("일시정지");status.setText("재생 중");buttons();});media.setOnCompletionListener(p->{if(player!=p)return;completed=true;clock.setText(time(p.getDuration())+" / "+time(p.getDuration()));play.setText("다시 듣기");status.setText("재생 완료");});media.setOnErrorListener((p,what,extra)->{if(player==p){clearAudio();status.setText("재생 실패 · 다시 눌러주세요.");buttons();}return true;});try{media.setDataSource(ready.getAbsolutePath());media.prepareAsync();}catch(Exception e){clearAudio();fail(e);buttons();}});
        }catch(Exception e){if(file!=null)file.delete();main.post(()->{if(!active||generation!=epoch)return;preparing=false;status.setText("HTTP 401".equals(e.getMessage())?"가족 연결이 만료됐어요.":"VOICE_CHANGED".equals(e.getMessage())?"대화가 변경됐어요 · 새로고침해주세요.":"대화 준비 실패 · 다시 눌러주세요.");buttons();});}});
    }
    private String time(int ms){return (ms/60000)+":"+String.format(java.util.Locale.ROOT,"%02d",ms/1000%60);}
    private final Runnable tick=new Runnable(){public void run(){if(!active)return;if(player!=null&&!preparing){try{int position=player.getCurrentPosition();if(!scrubbing)seek.setProgress(position);clock.setText(time(position)+" / "+time(player.getDuration()));}catch(IllegalStateException ignored){}}main.postDelayed(this,300);}};
    @Override protected void onStart(){super.onStart();active=true;if(!allowed()){heading.setText("개발용 에뮬레이터에서 감상할 수 있어요.");status.setText("차량 상태 연동 전 · 실차 감상 잠김");buttons();return;}loadLibrary();main.post(tick);}
    @Override protected void onStop(){active=false;generation++;main.removeCallbacks(tick);clearAudio();blank();buttons();super.onStop();}
    @Override protected void onDestroy(){drawing.destroy();network.shutdownNow();super.onDestroy();}
}
