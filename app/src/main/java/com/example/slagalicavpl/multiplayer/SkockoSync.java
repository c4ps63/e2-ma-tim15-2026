package com.example.slagalicavpl.multiplayer;

public interface SkockoSync {

    interface AttemptCallback {
        void onOpponentAttempt(int attemptIndex, int[] guess, int hits, int nears);
        void onOpponentDone(boolean solved);
    }

    /** maxAttempts=6 for main phase, maxAttempts=1 for bonus phase. */
    void startOpponentTurn(int[] secret, int maxAttempts, AttemptCallback cb);

    void cancel();
}
