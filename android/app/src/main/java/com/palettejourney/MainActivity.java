package com.palettejourney;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ScrollView;
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
        addText(root, "에뮬레이터 전용 · Pleos 온디바이스 TTS · AI 대화/마이크 녹음 아님", 18);
        addText(root, QUESTION, 26);
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
        playQuestion.setOnClickListener(v -> {
            if (!speechReady || speechBusy || speech == null || !emulatorTestAllowed()) return;
            speechBusy = true; updateSpeech("질문 재생 요청 중");
            speechTimeoutAfter(speechEpoch, "음성 재생 응답 지연 · 연결을 다시 확인해주세요");
            try { speech.setLocale(Locale.KOREA); speech.speak(QUESTION, true); }
            catch (RuntimeException e) { failSpeech("질문 재생 요청 실패: " + e.getClass().getSimpleName()); }
        });
        stopQuestion.setOnClickListener(v -> {
            if (speech == null) return;
            try { speech.stop(); cancelSpeechTimeout(); speechBusy = false; updateSpeech("음성 중지 요청 완료"); }
            catch (RuntimeException e) { failSpeech("중지 실패: " + e.getClass().getSimpleName()); }
        });
        retrySpeech.setOnClickListener(v -> initializeSpeech());
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
        playQuestion.setEnabled(emulatorTestAllowed() && speechReady && !speechBusy);
        stopQuestion.setEnabled(speechReady && speechBusy);
        retrySpeech.setEnabled(emulatorTestAllowed() && !speechBusy);
    }
    private void failSpeech(String message) {
        cancelSpeechTimeout();
        speechBusy = false; speechReady = false; updateSpeech(message);
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
                public void onDone() { speechEvent(epoch, () -> { cancelSpeechTimeout(); speechBusy = false; updateSpeech("질문 한 문장 재생 완료"); }); }
                public void onStop() { speechEvent(epoch, () -> { cancelSpeechTimeout(); speechBusy = false; updateSpeech("질문 음성 중지됨"); }); }
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
    @Override public void onStart() { super.onStart(); initializeSpeech(); }
    @Override public void onStop() { releaseSpeech(); updateSpeech("화면을 떠나 음성 연결 종료"); super.onStop(); }
    private void addText(LinearLayout root, String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(35, 44, 51));
        view.setPadding(0, 8, 0, 16);
        root.addView(view);
    }
}
