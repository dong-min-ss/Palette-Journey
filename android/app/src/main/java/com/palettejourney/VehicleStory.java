package com.palettejourney;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Concatenates original PCM without gain changes or reconstructed question audio. */
final class VehicleStory {
    static final class Cue {
        final VoiceArchive.Entry entry;final int at,index;
        Cue(VoiceArchive.Entry entry,int at,int index){this.entry=entry;this.at=at;this.index=index;}
    }
    final byte[] wav;final int duration;final List<Cue> cues;
    private VehicleStory(byte[] wav,int duration,List<Cue> cues){this.wav=wav;this.duration=duration;this.cues=cues;}
    static VehicleStory pack(VoiceArchive.Saved saved)throws IOException {
        if(saved.entries.isEmpty()||saved.entries.size()>4)throw new IOException("INVALID_ARCHIVE");
        ByteArrayOutputStream pcm=new ByteArrayOutputStream();List<Cue> cues=new ArrayList<>();
        for(int i=0;i<saved.entries.size();i++){
            VoiceArchive.Entry entry=saved.entries.get(i);cues.add(new Cue(entry,(int)(pcm.size()*1000L/32000),i));
            if(!entry.skipped){
                WavFile.durationMillis(entry.audio);byte[] source=Files.readAllBytes(entry.audio.toPath());
                if(source.length<46||source.length>WavFile.MAX_BYTES+44||pcm.size()+source.length-44>3840000)throw new IOException("INVALID_WAV");
                pcm.write(source,44,source.length-44);
            }
        }
        if(pcm.size()<2)throw new IOException("NO_RECORDED_ANSWER");
        ByteBuffer result=ByteBuffer.allocate(44+pcm.size()).order(ByteOrder.LITTLE_ENDIAN);
        result.put(new byte[]{'R','I','F','F'}).putInt(36+pcm.size()).put(new byte[]{'W','A','V','E','f','m','t',' '}).putInt(16).putShort((short)1).putShort((short)1).putInt(16000).putInt(32000).putShort((short)2).putShort((short)16).put(new byte[]{'d','a','t','a'}).putInt(pcm.size()).put(pcm.toByteArray());
        return new VehicleStory(result.array(),Math.max(1,(int)(pcm.size()*1000L/32000)),cues);
    }
}
