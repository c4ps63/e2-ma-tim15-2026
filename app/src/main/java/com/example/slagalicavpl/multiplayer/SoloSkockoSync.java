package com.example.slagalicavpl.multiplayer;

/** Used in challenge mode: opponent's turn ends immediately as unsolved. */
public class SoloSkockoSync implements SkockoSync {
    @Override
    public void startOpponentTurn(int[] secret, int maxAttempts, AttemptCallback cb) {
        cb.onOpponentDone(false);
    }

    @Override public void cancel() {}
}
