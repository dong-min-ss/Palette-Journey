package com.palettejourney;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class VoiceArchiveTest {
    private File temp()throws IOException{return Files.createTempDirectory("palette-voice-test-").toFile();}
    private File wav(File dir,String name,int samples)throws IOException{
        File file=new File(dir,name);try(WavFile writer=new WavFile(file)){byte[] data=new byte[samples*2];for(int i=0;i<data.length;i++)data[i]=(byte)(i%32);writer.append(data,data.length);}return file;
    }
    private void cleanup(File dir)throws IOException{try(java.util.stream.Stream<Path> files=Files.walk(dir.toPath())){for(Path file:(Iterable<Path>)files.sorted(Comparator.reverseOrder())::iterator)Files.delete(file);}}
    @Test public void wavHasCorrectSizeAndDurationAndRejectsOddOrOversizedPcm()throws Exception{
        File dir=temp();try{
            File file=wav(dir,"answer.wav",16000);assertEquals(32044,file.length());assertEquals(1000,WavFile.durationMillis(file));
            try(WavFile writer=new WavFile(new File(dir,"limits.wav"))){
                try{writer.append(new byte[3],3);fail();}catch(IOException expected){}
                writer.append(new byte[WavFile.MAX_BYTES],WavFile.MAX_BYTES);
                try{writer.append(new byte[2],2);fail();}catch(IOException expected){}
            }
            assertEquals(30000,WavFile.durationMillis(new File(dir,"limits.wav")));
        }finally{cleanup(dir);}
    }
    @Test public void corruptTruncatedOrEmptyWavCannotBeSavedAsAnAnswer()throws Exception{
        File dir=temp();try{
            File file=wav(dir,"answer.wav",100);
            try(RandomAccessFile bytes=new RandomAccessFile(file,"rw")){bytes.setLength(file.length()-1);}
            try{WavFile.durationMillis(file);fail();}catch(IOException expected){}
            File empty=wav(dir,"empty.wav",0);try{WavFile.durationMillis(empty);fail();}catch(IOException expected){}
        }finally{cleanup(dir);}
    }
    @Test public void confirmedAudioAndQuestionSurviveReopeningAndDeleteIsScoped()throws Exception{
        File dir=temp();try{
            File root=new File(dir,"archives"),audio=wav(dir,"original.wav",1600);
            VoiceArchive store=new VoiceArchive(root);
            VoiceArchive.Saved saved=store.save(Arrays.asList(new VoiceArchive.Entry("오늘 뭐했어?","첨성대를 봤어",false,audio),new VoiceArchive.Entry("다른 이야기?","",true,null)));
            assertEquals(1,store.list().size());
            VoiceArchive.Saved reopened=new VoiceArchive(root).load(saved.id);
            assertEquals("첨성대를 봤어",reopened.entries.get(0).answer);assertTrue(reopened.entries.get(1).skipped);
            assertArrayEquals(Files.readAllBytes(audio.toPath()),Files.readAllBytes(reopened.entries.get(0).audio.toPath()));
            assertEquals(100,WavFile.durationMillis(reopened.entries.get(0).audio));
            assertTrue(store.delete(saved.id));assertEquals(0,store.list().size());assertTrue(audio.exists());
        }finally{cleanup(dir);}
    }
    @Test public void failedSaveNeverPublishesPartialArchiveAndRejectsPathTraversal()throws Exception{
        File dir=temp();try{
            VoiceArchive store=new VoiceArchive(new File(dir,"archives"));File invalid=new File(dir,"invalid.wav");Files.write(invalid.toPath(),new byte[60]);
            try{store.save(Arrays.asList(new VoiceArchive.Entry("질문","답변",false,invalid)));fail();}catch(IOException expected){}
            assertEquals(0,store.list().size());
            try{store.load("../../outside");fail();}catch(IOException expected){}
            try{store.delete("------------------------------------");fail();}catch(IOException expected){}
            try{store.save(Arrays.asList(new VoiceArchive.Entry("질문","",true,null)));fail();}catch(IOException expected){}
        }finally{cleanup(dir);}
    }
    @Test public void capacityLimitDoesNotDeleteExistingRecordings()throws Exception{
        File dir=temp();try{
            VoiceArchive store=new VoiceArchive(new File(dir,"archives"));File audio=wav(dir,"source.wav",10);
            List<VoiceArchive.Entry> entries=Arrays.asList(new VoiceArchive.Entry("질문","답변",false,audio));
            for(int i=0;i<20;i++)store.save(entries);
            try{store.save(entries);fail();}catch(IOException expected){}
            assertEquals(20,store.list().size());
        }finally{cleanup(dir);}
    }
}
