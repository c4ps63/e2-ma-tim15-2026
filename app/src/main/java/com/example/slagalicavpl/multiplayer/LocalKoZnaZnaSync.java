package com.example.slagalicavpl.multiplayer;

import android.os.Handler;
import android.os.Looper;

import com.example.slagalicavpl.model.Question;

import java.util.Random;

public class LocalKoZnaZnaSync implements KoZnaZnaSync {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random  rng     = new Random();

    @Override
    public void sendAnswer(int questionIndex, char option, long elapsedMs) {
        // local-only mode — nothing to send
    }

    @Override
    public void listenForOpponentAnswer(int questionIndex, Question question, AnswerCallback callback) {
        int roll = rng.nextInt(10);
        if (roll < 3) return; // 30% — opponent does not answer

        long delayMs = 800 + rng.nextInt(3700); // 0.8 – 4.5 s
        char answer  = (roll < 7) ? question.correct : randomWrong(question.correct); // 40% correct, 30% wrong

        handler.postDelayed(() -> callback.onReceived(answer, delayMs), delayMs);
    }

    @Override
    public void cancel() {
        handler.removeCallbacksAndMessages(null);
    }

    private char randomWrong(char correct) {
        char[] all = {'A', 'B', 'C', 'D'};
        char[] wrong = new char[3];
        int wi = 0;
        for (char c : all) if (c != correct) wrong[wi++] = c;
        return wrong[rng.nextInt(3)];
    }
}
