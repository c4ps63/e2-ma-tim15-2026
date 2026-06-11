package com.example.slagalicavpl.multiplayer;

import com.example.slagalicavpl.model.Question;

public interface KoZnaZnaSync {

    interface AnswerCallback {
        void onReceived(char option, long elapsedMs);
    }

    void sendAnswer(int questionIndex, char option, long elapsedMs);

    void listenForOpponentAnswer(int questionIndex, Question question, AnswerCallback callback);

    void cancel();
}
