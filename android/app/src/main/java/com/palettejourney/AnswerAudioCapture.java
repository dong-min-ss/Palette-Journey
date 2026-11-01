package com.palettejourney;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import java.io.File;
import java.util.Arrays;

/** One microphone owner: these exact PCM bytes are both written to WAV and fed to Pleos STT. */
final class AnswerAudioCapture {
    interface Listener { void pcm(byte[] bytes); void ended(boolean failed); }
    final File file;
    volatile boolean finished, failed;
    private volatile boolean running=true, discard;
    private AudioRecord input;
    AnswerAudioCapture(File file) { this.file=file; }
    void start(Listener listener) {
        new Thread(() -> {
            try(WavFile wav=new WavFile(file)) {
                int minimum=AudioRecord.getMinBufferSize(WavFile.SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
                if(minimum<1)throw new IllegalStateException("MIC_FORMAT_UNAVAILABLE");
                AudioRecord recorder=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,WavFile.SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(minimum,6400));
                synchronized(this) { input=recorder; }
                if(recorder.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("MIC_INIT_FAILED");
                if(!running)return;
                recorder.startRecording();
                if(recorder.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IllegalStateException("MIC_START_FAILED");
                byte[] chunk=new byte[3200];
                while(running&&wav.bytes()<WavFile.MAX_BYTES) {
                    int read=recorder.read(chunk,0,Math.min(chunk.length,WavFile.MAX_BYTES-wav.bytes()));
                    if(!running)break;
                    if(read<=0||(read&1)!=0)throw new IllegalStateException("MIC_READ_FAILED");
                    wav.append(chunk,read);listener.pcm(Arrays.copyOf(chunk,read));
                }
            }catch(Exception error){ if(running)failed=true; }
            finally {
                synchronized(this) { if(input!=null){try{input.stop();}catch(RuntimeException ignored){}input.release();input=null;} }
                running=false;finished=true;
                if(discard||failed)file.delete();
                listener.ended(failed);
            }
        },"palette-answer-audio").start();
    }
    void stop() { running=false;synchronized(this){if(input!=null)try{input.stop();}catch(RuntimeException ignored){}} }
    void discard() { discard=true;stop();if(finished)file.delete(); }
    boolean ready() { if(!finished||failed)return false;try{return WavFile.durationMillis(file)>0;}catch(Exception error){return false;} }
}
