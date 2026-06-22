package com.example.slagalicavpl.multiplayer;

import com.example.slagalicavpl.model.Question;

/** Used in challenge mode: no opponent, never fires the answer callback. */
public class SoloKoZnaZnaSync implements KoZnaZnaSync {
    @Override public void sendAnswer(int questionIndex, char option, long elapsedMs) {}
    @Override public void listenForOpponentAnswer(int questionIndex, Question question, AnswerCallback callback) {}
    @Override public void cancel() {}
}
