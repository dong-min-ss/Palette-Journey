package com.palettejourney;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.Manifest;
import android.content.pm.PackageManager;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.CheckBox;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.Locale;
import ai.pleos.playground.tts.TextToSpeech;
import ai.pleos.playground.tts.constant.Mode;
import ai.pleos.playground.tts.constant.ErrorType;
import ai.pleos.playground.tts.listener.EventListener;
import ai.pleos.playground.tts.listener.OnInitializeListener;

/** Palette preview and emulator-only Pleos speech diagnostic. Vehicle/account state is not connected. */
public class MainActivity extends Activity {
    private static final String QUESTION = "오늘 어떤 일이 있었어?";
    private static final String TAG = "PaletteTts";
    private TextToSpeech speech;
    private EventListener speechListener;
    private TextView speechStatus;
    private Button playQuestion, stopQuestion, retrySpeech;
    private boolean speechReady, speechBusy;
    private int speechEpoch;
    private PleosAnswerController answer;
    private Button beginAnswer, cancelAnswer, confirmAnswer;
    private TextView answerStatus, answerText;
    private CheckBox answerConsent;
    private final GuidedConversation conversation = new GuidedConversation();
    private TextView currentQuestion, conversationHistory, conversationState;
    private Button skipConversation, endConversation;
    private final List<VoiceArchive.Entry> recordedEntries=new ArrayList<>();
    private Button saveRecording,openRecordings;
    private boolean recordingSaved,foreground,pendingRecordStart;
    private void clearRecordedDrafts() {
        for(VoiceArchive.Entry entry:recordedEntries)if(entry.audio!=null)entry.audio.delete();
        recordedEntries.clear();recordingSaved=false;
    }
    private void cleanOrphanDrafts() {
        File[] files=new File(getCacheDir(),"voice-drafts").listFiles();
        if(files!=null)for(File f:files)if(f.isFile()&&f.getName().startsWith("answer-")&&f.getName().endsWith(".wav"))f.delete();
    }

    private void renderConversation() {
        if (conversationState == null) return;
        currentQuestion.setText(conversation.active() ? conversation.question() : QUESTION);
        conversationState.setText(conversation.active() ? "준비된 질문 시연 · " + conversation.number() + " / 4번째 질문" : conversation.entries().isEmpty() ? "준비된 질문 분기 · 실제 글레오 AI 대화 미연동" : recordingSaved ? "이 기기에 답변 녹음 저장 완료" : "대화 종료 · 저장 버튼으로 답변 원음을 보관하세요");
        StringBuilder history = new StringBuilder();
        int index=1;
        for (GuidedConversation.Entry entry : conversation.entries()) {
            history.append(index++).append(". 질문: ").append(entry.question).append("\n")
                .append(entry.skipped ? "건너뛰었어요" : "확인한 답변: " + entry.answer).append("\n\n");
        }
        conversationHistory.setText(history.length()==0 ? "확인한 질문과 답변이 여기에 쌓여요. 저장 전 녹음은 화면을 나가면 삭제돼요." : history.toString());
        beginAnswer.setText(conversation.active() ? "지금 질문 듣기 / 다시 답하기" : conversation.entries().isEmpty() ? "대화 시연 시작" : "새 대화 시연 시작");
        skipConversation.setEnabled(conversation.active());
        endConversation.setEnabled(conversation.active());
    }
    private void beginConversationTurn() {
        if (!answerConsent.isChecked() || !emulatorTestAllowed() || !speechReady || speechBusy || answer.busy()) return;
        if(!conversation.active())try {
            if(new VoiceArchive(new File(getFilesDir(),"voice-archives")).list().size()>=20) {
                answerStatus.setText("저장된 이야기가 20개예요. 저장한 이야기에서 불필요한 녹음을 삭제한 뒤 시작해주세요.");return;
            }
        }catch(Exception error){answerStatus.setText("녹음 보관 공간을 확인하지 못했어요.");return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            pendingRecordStart=true;requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},901);return;
        }
        if(!conversation.active()) {
            if(!recordingSaved&&!recordedEntries.isEmpty()) {
                new AlertDialog.Builder(this).setTitle("새 대화를 시작할까요?").setMessage("저장하지 않은 답변 녹음은 버려져요.").setNegativeButton("취소",null)
                    .setPositiveButton("버리고 시작",(dialog,which)->{answer.dispose();clearRecordedDrafts();conversation.start();beginConversationTurn();}).show();return;
            }
            answer.dispose();clearRecordedDrafts();conversation.start();
        }
        renderConversation();
        answer.begin(this::playSpeechQuestion);
    }
    private void refreshAnswerButtons() {
        if (beginAnswer == null || answer == null) return;
        beginAnswer.setEnabled(emulatorTestAllowed() && speechReady && !speechBusy && !answer.busy() && answerConsent.isChecked());
        cancelAnswer.setEnabled(answer.busy());
        confirmAnswer.setEnabled(conversation.active() && answer.canUseAnswer());
        if(saveRecording!=null)saveRecording.setEnabled(!conversation.active()&&!recordingSaved&&!answer.busy()&&!speechBusy&&recordedEntries.stream().anyMatch(entry->!entry.skipped));
        if(openRecordings!=null)openRecordings.setEnabled(!answer.busy()&&!speechBusy&&!conversation.active()&&(recordingSaved||recordedEntries.isEmpty()));
    }
    private void playSpeechQuestion() {
        if (!speechReady || speechBusy || speech == null || !emulatorTestAllowed()) {
            answer.fail("질문 음성 연결을 먼저 확인해주세요."); return;
        }
        speechBusy = true; updateSpeech("질문 재생 요청 중");
        speechTimeoutAfter(speechEpoch, "음성 재생 응답 지연 · 연결을 다시 확인해주세요");
        try { speech.setLocale(Locale.KOREA); speech.speak(conversation.active() ? conversation.question() : QUESTION, true); }
        catch (RuntimeException e) { failSpeech("질문 재생 요청 실패: " + e.getClass().getSimpleName()); }
    }
    private void cancelAnswerTurn() {
        answer.cancel();
        if (speech != null && speechBusy) {
            // New TTS epoch prevents a cancelled question's late onDone from starting a new microphone turn.
            initializeSpeech();
            return;
        }
        updateSpeech("질문·답변 시험 취소됨");
    }
    private final Handler speechHandler = new Handler(Looper.getMainLooper());
    private Runnable speechTimeout;

    private void cancelSpeechTimeout() {
        if (speechTimeout != null) speechHandler.removeCallbacks(speechTimeout);
        speechTimeout = null;
    }
    private void speechTimeoutAfter(int epoch, String message) {
        cancelSpeechTimeout();
        speechTimeout = () -> { if (epoch == speechEpoch) { releaseSpeech(); failSpeech(message); } };
        speechHandler.postDelayed(speechTimeout, 20000);
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cleanOrphanDrafts();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 36, 48, 36);
        root.setBackgroundColor(Color.rgb(247, 246, 242));
        addText(root, "Palette Journey", 36);
        addText(root, "여행의 색을 모으는 팔레트", 24);
        addText(root, "개발 미리보기 · 계정 및 차량 연결 전", 18);
        addText(root, "기본 11색", 24);
        LinearLayout palette = new LinearLayout(this);
        String[] colors = {"#F44336", "#FF9800", "#FFEB3B", "#4CAF50", "#2196F3", "#9C27B0", "#E91E63", "#795548", "#FFFFFF", "#000000", "#9E9E9E"};
        String[] names = {"빨강", "주황", "노랑", "초록", "파랑", "보라", "핑크", "브라운", "화이트", "블랙", "그레이"};
        for (int i = 0; i < colors.length; i++) {
            TextView swatch = new TextView(this);
            swatch.setText(names[i]);
            swatch.setContentDescription(names[i]);
            swatch.setTextColor(i == 9 ? Color.WHITE : Color.BLACK);
            swatch.setBackgroundColor(Color.parseColor(colors[i]));
            swatch.setGravity(android.view.Gravity.CENTER);
            palette.addView(swatch, new LinearLayout.LayoutParams(0, 100, 1));
        }
        root.addView(palette);
        addText(root, "첫 가입 혜택: 컬러티켓 1장 (실제 지급은 서버 연결 후)", 20);
        addText(root, "차량 상태 미확인 · 연결 및 창작 조작 잠김", 20);
        addText(root, "음성 질문 연결 시험", 26);
        addText(root, "에뮬레이터 전용 · Pleos 온디바이스 TTS · 질문 원음 파일 추출은 미연결", 18);
        currentQuestion = new TextView(this); currentQuestion.setTextSize(26); currentQuestion.setText(QUESTION); root.addView(currentQuestion);
        speechStatus = new TextView(this);
        speechStatus.setId(R.id.tts_status);
        root.addView(speechStatus);
        LinearLayout actions = new LinearLayout(this);
        playQuestion = new Button(this); playQuestion.setText("질문 한 문장 듣기");
        playQuestion.setId(R.id.tts_question);
        stopQuestion = new Button(this); stopQuestion.setText("음성 중지");
        retrySpeech = new Button(this); retrySpeech.setText("음성 연결 다시 확인");
        actions.addView(playQuestion); actions.addView(stopQuestion); actions.addView(retrySpeech);
        root.addView(actions);
        playQuestion.setOnClickListener(v -> { if (!answer.busy()) playSpeechQuestion(); });
        stopQuestion.setOnClickListener(v -> {
            if (answer.busy()) { cancelAnswerTurn(); return; }
            if (speech == null) return;
            try { speech.stop(); cancelSpeechTimeout(); speechBusy = false; updateSpeech("음성 중지 요청 완료"); }
            catch (RuntimeException e) { failSpeech("중지 실패: " + e.getClass().getSimpleName()); }
        });
        retrySpeech.setOnClickListener(v -> initializeSpeech());
        addText(root, "답변에 이어지는 여행 이야기", 26);
        addText(root, "준비된 질문 분기 시연 · 최대 네 질문. 답변 원음을 녹음하고 확인한 답변만 저장 버튼으로 이 기기에 보관해요. 질문 원음은 녹음하지 않고 다시 들을 때 TTS로 읽어요. 웹 동기화는 아직 없어요.", 18);
        answerConsent = new CheckBox(this);
        answerConsent.setText("보호자가 답변 녹음과 기기 내 보관 안내를 확인하고 함께 진행해요"); root.addView(answerConsent);
        answerStatus = new TextView(this); answerStatus.setTextSize(20); root.addView(answerStatus);
        answerText = new TextView(this); answerText.setTextSize(26); answerText.setTextIsSelectable(true); root.addView(answerText);
        LinearLayout answerActions = new LinearLayout(this);
        beginAnswer = new Button(this); beginAnswer.setText("질문 듣고 답하기 / 다시 말하기");
        cancelAnswer = new Button(this); cancelAnswer.setText("답변 인식 취소");
        confirmAnswer = new Button(this); confirmAnswer.setText("들은 내용이 맞아요 / 다음 질문");
        answerActions.addView(beginAnswer); answerActions.addView(cancelAnswer); answerActions.addView(confirmAnswer); root.addView(answerActions);
        answer = new PleosAnswerController(this, (status, text, busy) -> {
            answerStatus.setText(status); answerText.setText(text.isEmpty() ? "인식한 답변이 여기에 표시돼요" : text);
            updateSpeech(speechStatus.getText().toString());
        });
        conversationState = new TextView(this); conversationState.setTextSize(20); root.addView(conversationState);
        conversationHistory = new TextView(this); conversationHistory.setTextSize(18); conversationHistory.setTextIsSelectable(true); root.addView(conversationHistory);
        LinearLayout conversationActions = new LinearLayout(this);
        skipConversation = new Button(this); skipConversation.setText("질문 건너뛰기");
        endConversation = new Button(this); endConversation.setText("대화 종료");
        conversationActions.addView(skipConversation); conversationActions.addView(endConversation); root.addView(conversationActions);
        skipConversation.setOnClickListener(v -> {
            if (!conversation.active()) return;
            String skippedQuestion=conversation.question();
            cancelAnswerTurn();recordedEntries.add(new VoiceArchive.Entry(skippedQuestion,"",true,null));conversation.skip();renderConversation();refreshAnswerButtons();
            answerStatus.setText(conversation.active() ? "건너뛰었어요. 준비되면 지금 질문 듣기를 눌러주세요." : "네 질문 시연을 마쳤어요.");
        });
        endConversation.setOnClickListener(v -> {
            conversation.finish(); cancelAnswerTurn(); renderConversation();refreshAnswerButtons();
            answerStatus.setText("대화를 종료했어요. 마이크를 닫았어요.");
        });
        LinearLayout recordingActions=new LinearLayout(this);
        saveRecording=new Button(this);saveRecording.setText("확인한 답변 녹음 저장");recordingActions.addView(saveRecording);
        openRecordings=new Button(this);openRecordings.setText("저장한 이야기 듣기·삭제");recordingActions.addView(openRecordings);root.addView(recordingActions);
        saveRecording.setOnClickListener(v->{
            if(conversation.active()||recordingSaved||answer.busy()||speechBusy)return;
            try {
                new VoiceArchive(new File(getFilesDir(),"voice-archives")).save(recordedEntries);
                recordingSaved=true;renderConversation();
                for(VoiceArchive.Entry entry:recordedEntries)if(entry.audio!=null)entry.audio.delete();
                answerStatus.setText("이 기기에 답변 원음과 질문 문장을 저장했어요. 질문 음성은 재생할 때 다시 읽어요.");refreshAnswerButtons();
            }catch(Exception error){answerStatus.setText("녹음을 저장하지 못했어요. 저장 공간과 녹음 상태를 확인하고 다시 시도해주세요.");}
        });
        openRecordings.setOnClickListener(v->{if(openRecordings.isEnabled())startActivity(new Intent(this,RecordingsActivity.class));});
        renderConversation();
        answerStatus.setText("녹음 안내를 확인해주세요. 저장 전에는 화면을 나가면 녹음이 삭제돼요.");
        answerText.setText("인식한 답변이 여기에 표시돼요");
        beginAnswer.setOnClickListener(v -> beginConversationTurn());
        cancelAnswer.setOnClickListener(v -> cancelAnswerTurn());
        confirmAnswer.setOnClickListener(v -> {
            if (!conversation.active() || !answer.canUseAnswer()) return;
            answer.confirm();
            String recordedQuestion=conversation.question(),recordedText=answer.result();
            if(!conversation.accept(recordedText)){answerStatus.setText("답변을 다시 확인해주세요.");return;}
            File recordedAudio=answer.takeRecording();
            recordedEntries.add(new VoiceArchive.Entry(recordedQuestion,recordedText,false,recordedAudio));
            renderConversation();
            if (conversation.active()) beginConversationTurn();
            else { answer.dispose(); refreshAnswerButtons(); answerStatus.setText("대화 시연을 마쳤어요. 확인한 이야기를 아래에서 볼 수 있어요."); }
        });
        answerConsent.setOnCheckedChangeListener((button, checked) -> {
            if (!checked) { if (answer.busy()) cancelAnswerTurn(); conversation.finish(); renderConversation(); }
            refreshAnswerButtons();
        });
        ScrollView scroll = new ScrollView(this); scroll.addView(root); setContentView(scroll);
        updateSpeech("음성 서비스 연결 전");
    }

    // Until real parking state is wired, this diagnostic is locked on real vehicles/release builds.
    private boolean emulatorTestAllowed() {
        return BuildConfig.DEBUG && ("ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE));
    }
    private void updateSpeech(String message) {
        Log.i(TAG, message);
        speechStatus.setText(message);
        playQuestion.setEnabled(emulatorTestAllowed() && speechReady && !speechBusy && (answer == null || !answer.busy()));
        stopQuestion.setEnabled(speechReady && speechBusy);
        retrySpeech.setEnabled(emulatorTestAllowed() && !speechBusy && (answer == null || !answer.busy()));
        refreshAnswerButtons();
    }
    private void failSpeech(String message) {
        cancelSpeechTimeout();
        speechBusy = false; speechReady = false; updateSpeech(message);
        if (answer != null) answer.fail("질문 재생 실패 · 마이크를 시작하지 않았어요");
    }
    private void speechEvent(int epoch, Runnable action) {
        runOnUiThread(() -> { if (epoch == speechEpoch && speech != null) action.run(); });
    }
    private void initializeSpeech() {
        releaseSpeech();
        if (!emulatorTestAllowed()) { updateSpeech("실차/배포 빌드 음성 시험 잠김 · 정차 상태 연동 필요"); return; }
        final int epoch = speechEpoch;
        updateSpeech("Pleos TTS 3.3.1 연결 중 · ON_DEVICE");
        speechTimeoutAfter(epoch, "음성 연결 응답 지연 · 연결을 다시 확인해주세요");
        try {
            speech = new TextToSpeech(getApplicationContext(), Mode.ON_DEVICE);
            speechListener = new EventListener() {
                public void onReady() { speechEvent(epoch, () -> { speechReady = true; updateSpeech("질문 재생 준비 완료"); }); }
                public void onStart() { speechEvent(epoch, () -> { speechBusy = true; updateSpeech("질문 음성 재생 중"); }); }
                public void onDone() { speechEvent(epoch, () -> { cancelSpeechTimeout(); speechBusy = false; updateSpeech("질문 한 문장 재생 완료"); answer.questionFinished(); }); }
                public void onStop() { speechEvent(epoch, () -> { cancelSpeechTimeout(); speechBusy = false; updateSpeech("질문 음성 중지됨"); answer.fail("질문이 중지되어 답변 인식을 시작하지 않았어요."); }); }
                public void onError(ErrorType error) { speechEvent(epoch, () -> failSpeech("Pleos 음성 오류: " + error.name())); }
                public void onUpdatedRms(double rms) { }
            };
            speech.initialize(new OnInitializeListener() {
                public void onSuccess() { speechEvent(epoch, () -> {
                    cancelSpeechTimeout();
                    try { speech.addEventListener(speechListener); speech.setLocale(Locale.KOREA); speechReady = true; updateSpeech("Pleos 음성 연결 완료 · 질문을 들어보세요"); }
                    catch (RuntimeException e) { failSpeech("음성 설정 실패: " + e.getClass().getSimpleName()); }
                }); }
                public void onFailure(ErrorType error) { speechEvent(epoch, () -> failSpeech("Pleos 음성 초기화 실패: " + error.name())); }
            });
        } catch (RuntimeException e) { failSpeech("음성 서비스 연결 실패: " + e.getClass().getSimpleName()); }
    }
    private void releaseSpeech() {
        cancelSpeechTimeout();
        speechEpoch++; speechReady = false; speechBusy = false;
        TextToSpeech previous = speech; speech = null;
        if (previous != null) {
            try { previous.stop(); } catch (RuntimeException ignored) { }
            try { if (speechListener != null) previous.removeEventListener(speechListener); } catch (RuntimeException ignored) { }
            try { previous.release(); } catch (RuntimeException ignored) { }
        }
        speechListener = null;
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(code,permissions,grants);
        if(code!=901)return;boolean wanted=pendingRecordStart;pendingRecordStart=false;
        if(!wanted||!foreground||!answerConsent.isChecked())return;
        if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)beginConversationTurn();
        else answerStatus.setText("마이크 녹음 권한이 없어 대화를 시작하지 않았어요. 권한을 확인한 뒤 다시 시도해주세요.");
    }
    @Override public void onStart() { super.onStart();foreground=true; initializeSpeech(); }
    @Override public void onStop() { foreground=false;pendingRecordStart=false;if (answer != null) { conversation.clear(); answer.dispose();clearRecordedDrafts();answerConsent.setChecked(false); renderConversation(); answerStatus.setText("화면을 떠나 답변 인식을 종료했어요. 저장 전 녹음은 삭제했어요."); answerText.setText("인식한 답변이 여기에 표시돼요"); } releaseSpeech(); updateSpeech("화면을 떠나 음성 연결 종료"); super.onStop(); }
    private void addText(LinearLayout root, String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(35, 44, 51));
        view.setPadding(0, 8, 0, 16);
        root.addView(view);
    }
}
