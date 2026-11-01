package com.palettejourney;

import org.junit.Test;
import static org.junit.Assert.*;

public class AnswerTurnTest {
    @Test public void listeningStartsOnlyAfterExplicitStartAndCompletedQuestion() {
        AnswerTurn turn=new AnswerTurn();
        assertFalse(turn.questionFinished());
        assertTrue(turn.begin()); assertFalse(turn.begin());
        assertFalse(turn.questionFinished()); assertFalse(turn.updated("질문 음성",true));
        assertTrue(turn.prepared()); assertTrue(turn.questionFinished());
        assertFalse(turn.questionFinished());
        assertTrue(turn.updated("  오늘 첨성대를 봤어  ",true));
        assertEquals("오늘 첨성대를 봤어",turn.text);
        assertEquals(AnswerTurn.State.RESULT,turn.state); assertFalse(turn.busy());
    }
    @Test public void cancelAndErrorsCannotOpenMicOrAcceptLateResults() {
        AnswerTurn turn=new AnswerTurn(); turn.begin(); turn.prepared(); turn.cancel();
        assertFalse(turn.questionFinished()); assertFalse(turn.updated("늦은 답변",true));
        assertTrue(turn.begin()); turn.fail(); assertFalse(turn.prepared());
        assertFalse(turn.questionFinished()); assertEquals("",turn.text);
    }
    @Test public void interimOrEmptyResultsAreNotPresentedAsCompletedAnswer() {
        AnswerTurn turn=new AnswerTurn(); turn.begin(); turn.prepared(); turn.questionFinished();
        turn.updated("중간 결과",false); assertTrue(turn.busy()); turn.ended();
        assertEquals(AnswerTurn.State.REVIEW,turn.state);
        assertFalse(turn.updated("늦은 확정",true));
        assertTrue(turn.confirm()); assertEquals(AnswerTurn.State.RESULT,turn.state);
        assertFalse(turn.confirm());
        turn.begin(); turn.prepared(); turn.questionFinished(); turn.updated("  ",true);
        assertEquals(AnswerTurn.State.ERROR,turn.state); assertFalse(turn.busy());
    }
    @Test public void retryClearsPreviousAnswerAndStartsFromPreparation() {
        AnswerTurn turn=new AnswerTurn(); turn.begin(); turn.prepared(); turn.questionFinished(); turn.updated("첫 답변",true);
        assertTrue(turn.begin()); assertEquals("",turn.text);
        assertEquals(AnswerTurn.State.PREPARING,turn.state); assertFalse(turn.questionFinished());
    }
    @Test public void emptyFinalFlagPreservesInterimWordsForReviewAndCancelClearsThem() {
        AnswerTurn turn=new AnswerTurn(); turn.begin(); turn.prepared(); turn.questionFinished();
        turn.updated("첨성대를 봤어요",false); turn.updated("",true);
        assertEquals(AnswerTurn.State.REVIEW,turn.state); assertEquals("첨성대를 봤어요",turn.text);
        turn.cancel(); assertEquals("",turn.text); assertFalse(turn.confirm());
    }
}
