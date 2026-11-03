package com.palettejourney;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.widget.*;
import android.util.Base64;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.*;

/** Explicit upload to a frozen, verified family page; never records a microphone. */
public class UploadStoryActivity extends Activity {
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler();
    private TextView status,target;private Button upload;
    private String token,pageId,archiveId;private int revision;private JSONObject body;
    private boolean uploading,ready;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(32,24,32,24);
        TextView title=new TextView(this);title.setText("차량 이야기를 웹 그림일기에 올리기");title.setTextSize(28);root.addView(title);
        target=new TextView(this);target.setTextSize(22);root.addView(target);
        TextView notice=new TextView(this);notice.setText("선택한 답변 원음과 질문·확인한 답변 문장을 연결된 가족 서버에 저장해요. 질문 음성은 포함하지 않아요. 차량 원본은 그대로 남아요. 기존 페이지 녹음은 덮어쓰지 않아요.");root.addView(notice);
        status=new TextView(this);status.setTextSize(20);root.addView(status);
        upload=new Button(this);upload.setText("이 페이지에 녹음 올리기");upload.setEnabled(false);root.addView(upload);
        Button back=new Button(this);back.setText("돌아가기");root.addView(back);back.setOnClickListener(v->{if(!uploading)finish();else status.setText("전송 결과를 확인 중이에요. 잠시 기다려주세요.");});
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,()->{if(!uploading)finish();else status.setText("전송 결과를 확인 중이에요. 잠시 기다려주세요.");});
        ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);root.setBackgroundColor(DevUi.BG);DevUi.styleTree(root);
        if(!BuildConfig.DEBUG||!("ranchu".equals(Build.HARDWARE)||"goldfish".equals(Build.HARDWARE))){status.setText("차량 상태 미확인: 업로드 조작은 잠겨 있어요.");return;}
        archiveId=getIntent().getStringExtra("archiveId");status.setText("연결된 가족과 페이지를 확인하고 있어요.");
        executor.execute(()->{try{prepare();}catch(Exception error){show("준비하지 못했어요. 차량의 웹 연결 화면에서 가족과 함께 볼 페이지를 선택한 뒤 다시 열어주세요.");}});
        upload.setOnClickListener(v->{if(!ready||uploading)return;new AlertDialog.Builder(this).setTitle("확인한 페이지에 올릴까요?").setMessage(target.getText()+"\n\n답변 목소리와 질문·답변 문장을 가족 서버로 전송합니다. 다른 기기가 공유 페이지를 바꾸어도 지금 확인한 페이지에 저장합니다.").setNegativeButton("취소",null).setPositiveButton("이 페이지에 올리기",(dialog,which)->send()).show();});
    }
    private void show(String message){main.post(()->{if(!isDestroyed())status.setText(message);});}
    private void prepare()throws Exception{
        token=VehicleApi.load(this);if(token==null)throw new Exception();
        VoiceArchive.Saved saved=new VoiceArchive(new File(getFilesDir(),"voice-archives")).load(archiveId);
        VehicleStory packed=VehicleStory.pack(saved);
        JSONObject connection=VehicleApi.request("/api/session/connection","GET",null,token),page=connection.optJSONObject("page");if(page==null)throw new Exception();
        pageId=page.getString("id");JSONObject library=VehicleApi.request("/api/session/library","GET",null,token);
        String label=page.getString("title");JSONArray diaries=library.getJSONArray("diaries");
        for(int i=0;i<diaries.length();i++){JSONObject diary=diaries.getJSONObject(i);if(diary.getString("id").equals(page.getString("diaryId"))){label=diary.getString("title")+" / "+label;for(String kind:new String[]{"children","trips"}){JSONArray items=library.getJSONArray(kind);String key=kind.equals("children")?"childId":"tripId";for(int j=0;j<items.length();j++)if(items.getJSONObject(j).getString("id").equals(diary.getString(key)))label=items.getJSONObject(j).getString(kind.equals("children")?"name":"title")+" / "+label;}}}
        JSONObject metadata=VehicleApi.request("/api/session/pages/"+pageId+"/voice","GET",null,token);revision=metadata.getInt("revision");JSONObject existing=metadata.optJSONObject("recording");
        if(existing!=null){String message=archiveId.equals(existing.optString("uploadId"))?"이 이야기는 이 페이지에 이미 올라가 있어요. 휴대폰에서 확인해주세요.":"이 페이지에는 이미 녹음이 있어요. 다른 페이지를 선택하거나 웹에서 기존 녹음을 확인해주세요.";String frozenLabel=label;main.post(()->{if(!isDestroyed())target.setText(frozenLabel);});show(message);return;}
        JSONArray cues=new JSONArray();for(VehicleStory.Cue cue:packed.cues)cues.put(new JSONObject().put("question",cue.index).put("at",cue.at).put("questionText",cue.entry.question).put("answerText",cue.entry.answer).put("skipped",cue.entry.skipped));
        body=new JSONObject().put("revision",revision).put("mime","audio/wav").put("audio",Base64.encodeToString(packed.wav,Base64.NO_WRAP)).put("duration",packed.duration).put("cues",cues).put("uploadId",archiveId);
        String frozenLabel=label;main.post(()->{if(isDestroyed())return;target.setText("저장할 페이지: "+frozenLabel+"\n"+saved.entries.size()+"개 질문 · 답변 원음 "+(packed.duration/1000)+"초");ready=true;upload.setEnabled(true);status.setText("페이지를 확인하고 직접 올리기 버튼을 눌러주세요.");});
    }
    private void send(){if(!ready||uploading)return;uploading=true;upload.setEnabled(false);status.setText("녹음을 올리고 있어요. 원본은 차량에 남아 있어요.");executor.execute(()->{try{
        VehicleApi.request("/api/session/pages/"+pageId+"/voice/vehicle","PUT",body,token);
        main.post(()->{if(isDestroyed())return;uploading=false;ready=false;status.setText("이 그림일기에 대화를 저장했어요. 휴대폰에서 같은 그림을 열면 자동으로 준비돼요. 그림 아래 대화 녹음 재생을 눌러주세요. 준비되지 않으면 대화 내용·기록 관리 → 차량에서 올린 대화 확인을 눌러주세요.");});
    }catch(Exception error){main.post(()->{if(isDestroyed())return;uploading=false;boolean expired=error.getMessage()!=null&&error.getMessage().contains("401");boolean conflict=error.getMessage()!=null&&error.getMessage().contains("409");if(expired){ready=false;status.setText("가족 연결이 만료됐어요. 차량에서 새 연결 코드로 다시 연결한 뒤 이 화면을 다시 열어주세요.");}else if(conflict){ready=false;status.setText("다른 기기에서 페이지 녹음이 바뀌었어요. 덮어쓰지 않았습니다. 웹에서 확인한 뒤 다시 선택해주세요.");}else{upload.setEnabled(true);status.setText("전송 결과를 확인하지 못했어요. 원본은 보관 중이에요. 연결 후 같은 버튼으로 재시도해도 중복 저장하지 않아요.");}});}});}
    @Override public void onBackPressed(){if(!uploading)super.onBackPressed();else status.setText("전송 결과를 확인 중이에요. 잠시 기다려주세요.");}
    @Override protected void onDestroy(){super.onDestroy();executor.shutdown();}
}
