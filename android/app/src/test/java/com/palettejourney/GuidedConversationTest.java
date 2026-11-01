package com.palettejourney;
import org.junit.Test;
import static org.junit.Assert.*;

public class GuidedConversationTest {
    @Test public void choosesPreparedQuestionWithoutClaimingAnExperience() {
        GuidedConversation c=new GuidedConversation();c.start();
        assertTrue(c.accept("첨성대에는 못 갔어"));
        assertEquals("그 장소에 대해 더 이야기해 줄래?",c.question());
        assertFalse(c.question().contains("가서"));
        assertEquals("첨성대에는 못 갔어",c.entries().get(0).answer);
        c.start();c.accept("아이스크림을 먹었어");assertEquals("음식에 관해 더 이야기해 줄래?",c.question());
    }
    @Test public void boundsTurnsAvoidsRepeatingTheSameTopicAndPreservesSkippedTurns() {
        GuidedConversation c=new GuidedConversation();c.start();c.accept("빵 먹었어");
        String food=c.question();c.accept("맛있어");assertNotEquals(food,c.question());
        c.skip();assertTrue(c.entries().get(2).skipped);c.accept("즐거웠어");
        assertFalse(c.active());assertEquals(4,c.entries().size());assertFalse(c.accept("더 말할래"));
        assertEquals(4,c.entries().size());
    }
    @Test public void refusesEmptyOrOversizedAnswersAndHonorsAnExplicitStop() {
        GuidedConversation c=new GuidedConversation();c.start();
        assertFalse(c.accept(" "));assertFalse(c.accept("가".repeat(2001)));assertEquals(0,c.entries().size());
        c.accept("그만 할래!");assertFalse(c.active());assertEquals(1,c.entries().size());
        c.start();c.accept("말하기 싫어");assertFalse(c.active());
        c.clear();assertEquals(0,c.entries().size());
    }
    @Test public void restartingDoesNotLeakPriorAnswersAndHistoryCannotBeMutatedByCaller() {
        GuidedConversation c=new GuidedConversation();c.start();c.accept("가족과 놀았어");
        try { c.entries().clear();fail("History must be read-only"); }catch(UnsupportedOperationException expected) { }
        c.start();assertEquals(0,c.entries().size());assertEquals(1,c.number());
        assertEquals("오늘 어떤 일이 있었어?",c.question());
    }
    @Test public void genericAnswersNeverRepeatAQuestionWithinTheFourTurnLimit() {
        GuidedConversation c=new GuidedConversation();c.start();
        java.util.Set<String> questions=new java.util.HashSet<>();
        while(c.active()) { assertTrue(questions.add(c.question()));c.accept("잘 모르겠어"); }
        assertEquals(4,questions.size());
    }
}
