package com.palettejourney;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Prepared question branching, NOT generative AI. Confirmed words only, no inferred experiences. */
final class GuidedConversation {
    static final int MAX_TURNS=4;
    enum Topic { OPEN, FOOD, PLACE, ACTIVITY, FEELING, MEMORY, SHARE }
    static final class Entry {
        final String question, answer;
        final boolean skipped;
        Entry(String question,String answer,boolean skipped) { this.question=question;this.answer=answer;this.skipped=skipped; }
    }
    private final List<Entry> entries=new ArrayList<>();
    private final Set<Topic> asked=new HashSet<>();
    private Topic current=Topic.OPEN;
    private boolean active;
    boolean active() { return active; }
    int number() { return Math.min(entries.size()+1,MAX_TURNS); }
    List<Entry> entries() { return Collections.unmodifiableList(new ArrayList<>(entries)); }
    void start() { entries.clear();asked.clear();current=Topic.OPEN;asked.add(current);active=true; }
    void clear() { entries.clear();asked.clear();active=false;current=Topic.OPEN; }
    void finish() { active=false; }
    String question() {
        switch(current) {
            case FOOD: return "음식에 관해 더 이야기해 줄래?";
            case PLACE: return "그 장소에 대해 더 이야기해 줄래?";
            case ACTIVITY: return "기억나는 모습을 조금 더 이야기해 줄래?";
            case FEELING: return "그 이야기를 하니 어떤 기분이 들어?";
            case MEMORY: return "또 기억나는 일이 있다면 이야기해 줄래?";
            case SHARE: return "이 이야기를 누구에게 들려주고 싶어?";
            default: return "오늘 어떤 일이 있었어?";
        }
    }
    boolean accept(String value) {
        if(!active||value==null||value.trim().isEmpty()||value.length()>2000)return false;
        String text=value.trim();entries.add(new Entry(question(),text,false));
        String compact=text.replaceAll("\\s+","").replaceAll("[.!?。！？]","");
        if(compact.equals("그만")||compact.equals("그만할래")||compact.equals("그만할래요")||compact.equals("그만하고싶어")||compact.equals("대화종료")||compact.equals("끝낼래")||compact.equals("말하기싫어")||compact.equals("안할래")) { finish();return true; }
        advance(text);return true;
    }
    void skip() { if(!active)return;entries.add(new Entry(question(),"",true));advance(""); }
    private void advance(String answer) {
        if(entries.size()>=MAX_TURNS) { finish();return; }
        // Neutral prompts do not claim the child ate something or visited a landmark.
        Topic suggested=contains(answer,"먹","맛","음식","빵","아이스크림")?Topic.FOOD:
            contains(answer,"첨성대","박물관","장소","금관","유적")?Topic.PLACE:
            contains(answer,"놀이","놀았","만들","그렸","걸었")?Topic.ACTIVITY:Topic.FEELING;
        if(asked.contains(suggested))suggested=!asked.contains(Topic.FEELING)?Topic.FEELING:!asked.contains(Topic.MEMORY)?Topic.MEMORY:Topic.SHARE;
        current=suggested;asked.add(current);
    }
    private static boolean contains(String text,String... words) { for(String word:words)if(text.contains(word))return true;return false; }
}
