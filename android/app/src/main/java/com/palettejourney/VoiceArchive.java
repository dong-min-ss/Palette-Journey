package com.palettejourney;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** App-private local archives. Question audio is reconstructed later, never claimed as captured. */
final class VoiceArchive {
    static final class Entry {
        final String question,answer;final boolean skipped;final File audio;
        Entry(String question,String answer,boolean skipped,File audio){this.question=question;this.answer=answer;this.skipped=skipped;this.audio=audio;}
    }
    static final class Saved {
        final String id;final long createdAt;final List<Entry> entries;
        Saved(String id,long createdAt,List<Entry> entries){this.id=id;this.createdAt=createdAt;this.entries=Collections.unmodifiableList(entries);}
    }
    private final File root;
    VoiceArchive(File root)throws IOException { this.root=root.getCanonicalFile();if(!root.isDirectory()&&!root.mkdirs())throw new IOException("ARCHIVE_DIRECTORY"); }
    private File directory(String id)throws IOException {
        if(id==null||!id.matches("[a-f0-9-]{36}"))throw new IOException("INVALID_ARCHIVE_ID");
        try { if(!UUID.fromString(id).toString().equals(id))throw new IOException("INVALID_ARCHIVE_ID"); }
        catch(IllegalArgumentException error){throw new IOException("INVALID_ARCHIVE_ID",error);}
        File dir=new File(root,id).getCanonicalFile();if(!root.equals(dir.getParentFile()))throw new IOException("INVALID_ARCHIVE_ID");return dir;
    }
    Saved save(List<Entry> entries)throws IOException {
        if(entries.isEmpty()||entries.size()>4||list().size()>=20)throw new IOException("ARCHIVE_LIMIT");
        for(Entry entry:entries)if(entry.question==null||entry.question.trim().isEmpty()||entry.question.length()>300||entry.answer==null||entry.answer.length()>2000||(!entry.skipped&&(entry.answer.isEmpty()||entry.audio==null)))throw new IOException("INVALID_ARCHIVE");
        if(entries.stream().noneMatch(e->!e.skipped))throw new IOException("NO_RECORDED_ANSWER");
        String id=UUID.randomUUID().toString();File pending=new File(root,"pending-"+id);if(!pending.mkdir())throw new IOException("ARCHIVE_DIRECTORY");
        Properties data=new Properties();data.setProperty("version","1");data.setProperty("createdAt",Long.toString(System.currentTimeMillis()));
        data.setProperty("count",Integer.toString(entries.size()));data.setProperty("questionAudio","tts-reconstructed");data.setProperty("answerAudio","microphone-original");
        try {
            for(int i=0;i<entries.size();i++) {
                Entry entry=entries.get(i);data.setProperty("q."+i,entry.question);data.setProperty("a."+i,entry.answer);data.setProperty("skip."+i,Boolean.toString(entry.skipped));
                if(!entry.skipped){ WavFile.durationMillis(entry.audio);Files.copy(entry.audio.toPath(),new File(pending,"answer-"+i+".wav").toPath()); }
            }
            try(Writer writer=new OutputStreamWriter(new FileOutputStream(new File(pending,"manifest.properties")),StandardCharsets.UTF_8)){data.store(writer,"Palette Journey private voice archive");}
            if(!pending.renameTo(directory(id)))throw new IOException("ARCHIVE_COMMIT");
            return load(id);
        }catch(IOException|RuntimeException error){removeKnownFiles(pending);throw error;}
    }
    Saved load(String id)throws IOException {
        File dir=directory(id);Properties data=new Properties();
        try(Reader reader=new InputStreamReader(new FileInputStream(new File(dir,"manifest.properties")),StandardCharsets.UTF_8)){data.load(reader);}
        catch(IllegalArgumentException error){throw new IOException("INVALID_ARCHIVE",error);}
        try {
            if(!"1".equals(data.getProperty("version"))||!"tts-reconstructed".equals(data.getProperty("questionAudio"))||!"microphone-original".equals(data.getProperty("answerAudio")))throw new IOException("INVALID_ARCHIVE");
            int count=Integer.parseInt(data.getProperty("count"));if(count<1||count>4)throw new IOException("INVALID_ARCHIVE");
            List<Entry> entries=new ArrayList<>();
            for(int i=0;i<count;i++) {
                String q=data.getProperty("q."+i),a=data.getProperty("a."+i),skip=data.getProperty("skip."+i);
                if(q==null||q.isEmpty()||q.length()>300||a==null||a.length()>2000||!("true".equals(skip)||"false".equals(skip)))throw new IOException("INVALID_ARCHIVE");
                File audio="true".equals(skip)?null:new File(dir,"answer-"+i+".wav");if(audio!=null)WavFile.durationMillis(audio);
                entries.add(new Entry(q,a,"true".equals(skip),audio));
            }
            return new Saved(id,Long.parseLong(data.getProperty("createdAt")),entries);
        }catch(NumberFormatException error){throw new IOException("INVALID_ARCHIVE",error);}
    }
    List<Saved> list()throws IOException {
        File[] dirs=root.listFiles();if(dirs==null)throw new IOException("ARCHIVE_DIRECTORY");List<Saved> result=new ArrayList<>();
        for(File dir:dirs)if(dir.isDirectory()&&dir.getName().matches("[a-f0-9-]{36}"))try{result.add(load(dir.getName()));}catch(IOException ignored){}
        result.sort((a,b)->Long.compare(b.createdAt,a.createdAt));return result;
    }
    boolean delete(String id)throws IOException { return removeKnownFiles(directory(id)); }
    private boolean removeKnownFiles(File dir) {
        boolean ok=true;for(int i=0;i<4;i++){File f=new File(dir,"answer-"+i+".wav");if(f.exists()&&!f.delete())ok=false;}
        File manifest=new File(dir,"manifest.properties");if(manifest.exists()&&!manifest.delete())ok=false;
        return dir.delete()&&ok;
    }
}
