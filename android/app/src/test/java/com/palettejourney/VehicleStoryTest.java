package com.palettejourney;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

public class VehicleStoryTest {
    @Test public void combinesExactPcmAndKeepsSkippedQuestionOffsets()throws Exception{
        File root=Files.createTempDirectory("pj-story").toFile();File a=new File(root,"a.wav"),b=new File(root,"b.wav");
        byte[] first=new byte[32000],second=new byte[16000];Arrays.fill(first,(byte)13);Arrays.fill(second,(byte)47);
        try{
            try(WavFile out=new WavFile(a)){out.append(first,first.length);}try(WavFile out=new WavFile(b)){out.append(second,second.length);}
            VoiceArchive.Saved saved=new VoiceArchive.Saved(java.util.UUID.randomUUID().toString(),0,Arrays.asList(new VoiceArchive.Entry("질문1","답변1",false,a),new VoiceArchive.Entry("질문2","",true,null),new VoiceArchive.Entry("질문3","답변3",false,b)));
            VehicleStory result=VehicleStory.pack(saved);assertEquals(1500,result.duration);assertEquals(48044,result.wav.length);
            assertArrayEquals(first,Arrays.copyOfRange(result.wav,44,32044));assertArrayEquals(second,Arrays.copyOfRange(result.wav,32044,48044));
            assertEquals(1000,result.cues.get(1).at);assertEquals(1000,result.cues.get(2).at);assertTrue(result.cues.get(1).entry.skipped);
        }finally{a.delete();b.delete();root.delete();}
    }
    @Test public void fullFourAnswersFitInTwoMinutes()throws Exception{
        File root=Files.createTempDirectory("pj-story-full").toFile(),source=new File(root,"full.wav");
        try{try(WavFile out=new WavFile(source)){out.append(new byte[960000],960000);}VoiceArchive.Entry entry=new VoiceArchive.Entry("질문","답변",false,source);
            VehicleStory packed=VehicleStory.pack(new VoiceArchive.Saved(java.util.UUID.randomUUID().toString(),0,Arrays.asList(entry,entry,entry,entry)));assertEquals(120000,packed.duration);assertEquals(3840044,packed.wav.length);assertEquals(90000,packed.cues.get(3).at);
        }finally{source.delete();root.delete();}
    }
}
