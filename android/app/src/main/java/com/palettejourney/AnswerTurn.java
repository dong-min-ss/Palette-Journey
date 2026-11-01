package com.palettejourney;

/** One explicitly started question/answer turn; never listens before question completion. */
final class AnswerTurn {
    enum State { IDLE, PREPARING, SPEAKING, LISTENING, REVIEW, RESULT, ERROR, CANCELLED }
    State state = State.IDLE;
    String text = "";
    boolean busy() { return state == State.PREPARING || state == State.SPEAKING || state == State.LISTENING; }
    boolean begin() { if (busy()) return false; text = ""; state = State.PREPARING; return true; }
    boolean prepared() { if (state != State.PREPARING) return false; state = State.SPEAKING; return true; }
    boolean questionFinished() { if (state != State.SPEAKING) return false; state = State.LISTENING; return true; }
    boolean updated(String value, boolean completed) {
        if (state != State.LISTENING) return false;
        String next = value == null ? "" : value.trim();
        if (!next.isEmpty()) text = next;
        if (completed) state = !next.isEmpty() ? State.RESULT : text.isEmpty() ? State.ERROR : State.REVIEW;
        return true;
    }
    void ended() { if (state == State.LISTENING) state = text.isEmpty() ? State.ERROR : State.REVIEW; }
    boolean confirm() { if (state != State.REVIEW) return false; state = State.RESULT; return true; }
    void fail() { if (busy()) state = State.ERROR; }
    void cancel() { state = State.CANCELLED; text = ""; }
}
