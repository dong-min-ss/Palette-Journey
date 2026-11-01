package com.palettejourney;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.Locale;
import ai.pleos.playground.stt.SpeechToText;
import ai.pleos.playground.stt.constant.Mode;
import ai.pleos.playground.stt.constant.ErrorType;
import ai.pleos.playground.stt.listener.ResultListener;
import ai.pleos.playground.stt.listener.OnInitializeListener;

/** On-device STT only. No audio/text is saved or sent to the application server. */
final class PleosAnswerController {
    interface Observer { void changed(String status, String text, boolean busy); }
    private final Context context;
    private final Observer observer;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AnswerTurn turn = new AnswerTurn();
    private SpeechToText recognizer;
    private ResultListener listener;
    private int epoch;
    private Runnable timeout;

    PleosAnswerController(Context context, Observer observer) { this.context=context.getApplicationContext(); this.observer=observer; }
    boolean busy() { return turn.busy(); }
    boolean canConfirm() { return turn.state==AnswerTurn.State.REVIEW; }
    void confirm() { if (turn.confirm()) show("답변 확인 완료 · 이번 시험에서는 저장하지 않아요"); }
    private void show(String status) {
        // Log operational events, never the child's recognized words.
        Log.i("PaletteStt", status);
        observer.changed(status, turn.text, turn.busy());
    }
    private void event(int generation, Runnable action) {
        main.post(() -> { if (generation == epoch && recognizer != null) action.run(); });
    }
    private void deadline(int generation, long millis, String message) {
        if (timeout != null) main.removeCallbacks(timeout);
        timeout = () -> { if (generation == epoch && busy()) fail(message); };
        main.postDelayed(timeout,millis);
    }
    void begin(Runnable playQuestion) {
        if (busy()) return;
        closeResources(); turn.begin(); final int generation=epoch;
        show("음성 인식 연결 중 · 아직 마이크를 열지 않았어요");
        deadline(generation,20000,"음성 인식 연결이 지연돼요. 다시 시도해주세요.");
        try {
            recognizer=new SpeechToText(context,Mode.ON_DEVICE);
            listener=new ResultListener() {
                public void onReady() { event(generation,() -> { if (turn.state==AnswerTurn.State.LISTENING) show("마이크 준비 완료 · 지금 답해주세요"); }); }
                public void onStartedRecognition() { event(generation,() -> { if (turn.state==AnswerTurn.State.LISTENING) show("● 답변을 듣고 있어요 · 최대 30초"); }); }
                public void onUpdated(String text,boolean completed) { event(generation,() -> {
                    if (!turn.updated(text,completed)) return;
                    if (completed) { closeResources(); show(turn.state==AnswerTurn.State.RESULT?"답변 인식 완료 · 화면에서 확인해주세요":turn.state==AnswerTurn.State.REVIEW?"들은 내용이 맞으면 아래에서 확인해주세요.":"답변을 알아듣지 못했어요. 다시 말해주세요."); }
                    else show("듣는 중 · 중간 인식 결과예요");
                }); }
                public void onEndedRecognition() { event(generation,() -> {
                    if (turn.state!=AnswerTurn.State.LISTENING) return;
                    // Some runtimes end before delivering the final flag. Allow a short grace period;
                    // preserve interim words for explicit review rather than labelling them SDK-final.
                    main.postDelayed(() -> {
                        if (generation!=epoch || turn.state!=AnswerTurn.State.LISTENING) return;
                        turn.ended(); closeResources();
                        show(turn.state==AnswerTurn.State.REVIEW?"인식이 끝났어요. 들은 내용이 맞으면 아래에서 확인해주세요.":"답변을 듣지 못했어요. 마이크를 확인하고 다시 말해주세요.");
                    },1000);
                }); }
                public void onError(ErrorType error) { event(generation,() -> fail(errorMessage(error))); }
                public void onUpdatedEpdData(long on,long off) { }
                public void onUpdatedRms(double rms) { }
            };
            recognizer.initialize(new OnInitializeListener() {
                public void onSuccess() { event(generation,() -> {
                    try {
                        recognizer.addListener(listener); recognizer.setLocale(Locale.KOREA);
                        if (!turn.prepared()) return;
                        if (timeout!=null) main.removeCallbacks(timeout);
                        show("질문을 먼저 들어주세요. 끝나면 답변 인식을 시작해요.");
                        playQuestion.run();
                    } catch (RuntimeException e) { fail("음성 인식 설정 실패 · 다시 연결해주세요"); }
                }); }
                public void onFailure(ErrorType error) { event(generation,() -> fail(errorMessage(error))); }
            });
        } catch (RuntimeException e) { fail("음성 인식 서비스를 연결하지 못했어요. 다시 시도해주세요."); }
    }
    void questionFinished() {
        if (!turn.questionFinished() || recognizer==null) return;
        show("● 지금 답해주세요 · 마이크 시작 요청 중");
        deadline(epoch,30000,"답변 시간이 끝났어요. 다시 말하기로 시도해주세요.");
        try { recognizer.request(); }
        catch (RuntimeException e) { fail("마이크를 시작하지 못했어요. 기기 마이크 설정을 확인해주세요."); }
    }
    void fail(String message) { if (!busy()) return; turn.fail(); closeResources(); show(message); }
    void cancel() { turn.cancel(); closeResources(); show("답변 인식을 취소했어요 · 마이크 종료"); }
    void dispose() { turn.cancel(); turn.text=""; closeResources(); }
    private void closeResources() {
        epoch++;
        if (timeout!=null) main.removeCallbacks(timeout); timeout=null;
        SpeechToText previous=recognizer; recognizer=null;
        if (previous!=null) {
            try { if (listener!=null) previous.removeListener(listener); } catch (RuntimeException ignored) { }
            try { previous.stop(); } catch (RuntimeException ignored) { }
            try { previous.release(); } catch (RuntimeException ignored) { }
        }
        listener=null;
    }
    private static String errorMessage(ErrorType error) {
        switch (error) {
            case MIC_ERROR: return "마이크를 사용할 수 없어요. 에뮬레이터 입력과 마이크 설정을 확인해주세요.";
            case PERMISSION_DENIED: return "음성 인식 권한이 없어요. STT_SERVICE 허용 상태를 확인해주세요.";
            case TIMEOUT: return "답변을 듣지 못했어요. 다시 말해주세요.";
            case UNAVAILABLE_LANGUAGE: return "한국어 음성 인식을 사용할 수 없어요. 기기 언어 모델을 확인해주세요.";
            case ON_DEVICE_ENGINE_ERROR: return "기기 음성 인식 엔진을 사용할 수 없어요. 엔진 상태를 확인해주세요.";
            default: return "음성 인식 오류: "+error.name()+" · 다시 시도해주세요.";
        }
    }
}
