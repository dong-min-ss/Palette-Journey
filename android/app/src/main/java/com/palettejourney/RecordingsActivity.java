package com.palettejourney;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.media.MediaPlayer;
import android.widget.*;
import java.io.File;
import java.util.*;
import ai.pleos.playground.tts.TextToSpeech;
import ai.pleos.playground.tts.constant.Mode;
import ai.pleos.playground.tts.constant.ErrorType;
import ai.pleos.playground.tts.listener.EventListener;
import ai.pleos.playground.tts.listener.OnInitializeListener;

/** Reconstructed question TTS + original recorded answers; never opens a microphone. */
public class RecordingsActivity extends VehicleActivity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private VoiceArchive archive;
    private List<VoiceArchive.Saved> saved=Collections.emptyList();
    private TextToSpeech tts;
    private EventListener ttsListener;
    private MediaPlayer player;
    private Spinner selection;
    private TextView status,details;
    private Button play,stop,delete,upload;
    private boolean ready,playing;
    private int epoch,index;
    private VoiceArchive.Saved current;
    private Runnable timeout;
    private boolean allowed(){return BuildConfig.DEBUG&&("ranchu".equals(Build.HARDWARE)||"goldfish".equals(Build.HARDWARE));}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(40,32,40,32);
        TextView title=new TextView(this);title.setText("저장한 이야기");title.setTextSize(28);root.addView(title);
        TextView notice=new TextView(this);notice.setText("차량에 원본을 보관해요. 선택한 녹음은 확인 후 웹 그림일기에 올릴 수 있어요. 질문은 다시 들을 때 TTS로 읽고, 답변은 녹음 원음을 재생해요. 질문 원음까지 녹음한 파일은 아니에요.");notice.setTextSize(18);DevUi.fold(root,"녹음·재생 안내").addView(notice);
        selection=new Spinner(this);root.addView(selection);
        details=new TextView(this);details.setTextSize(20);DevUi.fold(root,"질문과 답변").addView(details);
        status=new TextView(this);status.setTextSize(20);root.addView(status);
        LinearLayout actions=new LinearLayout(this);
        play=new Button(this);play.setText("선택한 이야기 다시 듣기");actions.addView(play);
        stop=new Button(this);stop.setText("재생 중지");actions.addView(stop);
        delete=new Button(this);delete.setText("선택한 녹음 삭제");actions.addView(delete);root.addView(actions);
        upload=new Button(this);upload.setText("선택한 이야기를 웹 페이지에 올리기");root.addView(upload);upload.setOnClickListener(v->{VoiceArchive.Saved value=selected();if(value!=null&&allowed()&&!playing)startActivity(new android.content.Intent(this,UploadStoryActivity.class).putExtra("archiveId",value.id));});
        ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);root.setBackgroundColor(DevUi.BG);DevUi.styleTree(root);
        try{archive=new VoiceArchive(new File(getFilesDir(),"voice-archives"));reload();}catch(Exception error){status.setText("저장된 이야기를 읽지 못했어요.");}
        selection.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> parent){}
            public void onItemSelected(android.widget.AdapterView<?> parent,android.view.View view,int position,long id){stopPlayback();showSelection();}
        });
        play.setOnClickListener(v->{if(!allowed()||!ready||playing||selected()==null)return;current=selected();index=0;playing=true;buttons();initializePlaybackSpeech();});
        stop.setOnClickListener(v->{stopPlayback();status.setText("재생을 중지했어요.");});
        delete.setOnClickListener(v->{VoiceArchive.Saved value=selected();if(value==null||playing)return;
            new AlertDialog.Builder(this).setTitle("이 녹음을 삭제할까요?").setMessage("이 기기에 저장한 답변 원음과 질문·답변 문장을 삭제해요.")
                .setNegativeButton("취소",null).setPositiveButton("삭제",(dialog,which)->{try{if(!archive.delete(value.id))throw new java.io.IOException();reload();status.setText("녹음을 삭제했어요.");}catch(Exception error){status.setText("완전히 삭제하지 못했어요. 다시 확인해주세요.");}}).show();});
        buttons();
    }
    private VoiceArchive.Saved selected(){int p=selection.getSelectedItemPosition();return p>=0&&p<saved.size()?saved.get(p):null;}
    private void reload()throws java.io.IOException{
        saved=archive.list();List<String> labels=new ArrayList<>();for(int i=0;i<saved.size();i++)labels.add("음성 이야기 "+(i+1)+" · "+saved.get(i).entries.size()+"개 질문");
        selection.setAdapter(DevUi.spinner(this,labels.toArray(new String[0])));showSelection();buttons();
    }
    private void showSelection(){VoiceArchive.Saved value=selected();StringBuilder text=new StringBuilder();if(value!=null)for(VoiceArchive.Entry e:value.entries)text.append("질문: ").append(e.question).append("\n").append(e.skipped?"건너뛰었어요":"답변: "+e.answer).append("\n\n");details.setText(text.length()==0?"저장된 음성 이야기가 없어요.":text.toString());}
    private void buttons(){if(play==null)return;play.setEnabled(allowed()&&ready&&!playing&&selected()!=null);stop.setEnabled(playing);if(upload!=null)upload.setEnabled(allowed()&&!playing&&selected()!=null);delete.setEnabled(!playing&&selected()!=null);selection.setEnabled(!playing);}
    private void deadline(long millis){if(timeout!=null)main.removeCallbacks(timeout);timeout=()->{stopPlayback();status.setText("재생 응답이 지연돼 중지했어요.");};main.postDelayed(timeout,millis);}
    private void cancelDeadline(){if(timeout!=null)main.removeCallbacks(timeout);timeout=null;}
    private void playQuestion(){
        if(!playing||tts==null)return;
        if(index>=current.entries.size()){stopPlayback();status.setText("저장된 이야기를 모두 들었어요.");return;}
        status.setText((index+1)+"번째 질문 · 다시 읽는 TTS 음성이에요");deadline(20000);
        try{tts.setLocale(Locale.KOREA);tts.speak(current.entries.get(index).question,true);}catch(RuntimeException error){stopPlayback();status.setText("질문 음성을 재생하지 못했어요.");}
    }
    private void playAnswer(){
        cancelDeadline();if(!playing)return;VoiceArchive.Entry entry=current.entries.get(index);
        if(entry.skipped){index++;playQuestion();return;}
        final int generation=epoch;
        try{
            player=new MediaPlayer();player.setDataSource(entry.audio.getAbsolutePath());
            player.setOnPreparedListener(media->{if(generation==epoch&&playing){status.setText((index+1)+"번째 답변 · 녹음 원음 재생 중");media.start();deadline(35000);}});
            player.setOnCompletionListener(media->{if(generation!=epoch||!playing)return;cancelDeadline();media.release();player=null;index++;playQuestion();});
            player.setOnErrorListener((media,what,extra)->{if(generation==epoch){stopPlayback();status.setText("답변 녹음을 재생하지 못했어요.");}return true;});
            deadline(35000);player.prepareAsync();
        }catch(Exception error){stopPlayback();status.setText("답변 녹음 파일을 열지 못했어요.");}
    }
    private void stopPlayback(){epoch++;playing=false;cancelDeadline();if(tts!=null){try{tts.stop();if(ttsListener!=null)tts.removeEventListener(ttsListener);tts.release();}catch(RuntimeException ignored){}tts=null;}if(player!=null){player.release();player=null;}buttons();}
    private void event(int generation,Runnable action){main.post(()->{if(generation==epoch&&tts!=null)action.run();});}
    private void initializePlaybackSpeech(){if(!allowed()){status.setText("실차/배포 빌드 재생 시험은 잠겨 있어요.");return;}
        final int generation=++epoch;status.setText("질문 음성 연결 중");deadline(20000);
        try{tts=new TextToSpeech(getApplicationContext(),Mode.ON_DEVICE);ttsListener=new EventListener(){
            public void onReady(){}public void onStart(){}public void onStop(){}public void onUpdatedRms(double rms){}
            public void onDone(){event(generation,()->{if(playing)playAnswer();});}
            public void onError(ErrorType error){event(generation,()->{stopPlayback();status.setText("질문 음성 오류: "+error.name());});}
        };tts.initialize(new OnInitializeListener(){
            public void onSuccess(){event(generation,()->{try{cancelDeadline();tts.addEventListener(ttsListener);ready=true;buttons();playQuestion();}catch(RuntimeException error){stopPlayback();status.setText("질문 음성을 준비하지 못했어요.");}});}
            public void onFailure(ErrorType error){event(generation,()->{stopPlayback();status.setText("질문 음성 연결 실패: "+error.name());});}
        });}catch(RuntimeException error){stopPlayback();status.setText("질문 음성 서비스를 사용할 수 없어요.");}
    }
    @Override public void onStart(){super.onStart();ready=allowed();buttons();status.setText(ready?"다시 들을 이야기를 선택해주세요.":"실차/배포 빌드 재생 시험은 잠겨 있어요.");}
    @Override public void onStop(){stopPlayback();ready=false;super.onStop();}
}
