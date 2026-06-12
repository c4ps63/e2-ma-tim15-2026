package com.example.slagalicavpl.multiplayer;

import android.os.Handler;
import android.os.Looper;

import com.example.slagalicavpl.game.SkockoEngine;

import java.util.Random;

public class LocalSkockoSync implements SkockoSync {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random  rng     = new Random();

    @Override
    public void startOpponentTurn(int[] secret, int maxAttempts, AttemptCallback cb) {
        scheduleAttempts(secret, maxAttempts, 0, cb);
    }

    private void scheduleAttempts(int[] secret, int maxAttempts, int attemptIndex,
                                   AttemptCallback cb) {
        if (attemptIndex >= maxAttempts) {
            handler.postDelayed(() -> cb.onOpponentDone(false), 400);
            return;
        }

        // Opponent thinks slowly: 2–4 s per guess.
        // Main phase: ~8% chance of solving per attempt (very likely to fail all 6).
        // Bonus (maxAttempts==1): ~20% chance of solving.
        long delay = 2000 + rng.nextInt(2000);
        handler.postDelayed(() -> {
            int[] guess;
            boolean tryToSolve = (maxAttempts == 1)
                    ? rng.nextInt(100) < 20
                    : (rng.nextInt(100) < 8 && attemptIndex >= 2);

            if (tryToSolve) {
                guess = secret.clone();
            } else {
                guess = randomGuess();
            }

            int hits  = SkockoEngine.countHits(secret, guess);
            int nears = SkockoEngine.countNears(secret, guess);

            cb.onOpponentAttempt(attemptIndex, guess, hits, nears);

            if (hits == SkockoEngine.CODE_LENGTH) {
                handler.postDelayed(() -> cb.onOpponentDone(true), 500);
            } else {
                scheduleAttempts(secret, maxAttempts, attemptIndex + 1, cb);
            }
        }, delay);
    }

    private int[] randomGuess() {
        int[] g = new int[SkockoEngine.CODE_LENGTH];
        for (int i = 0; i < g.length; i++) g[i] = rng.nextInt(SkockoEngine.SYMBOLS.length);
        return g;
    }

    @Override
    public void cancel() {
        handler.removeCallbacksAndMessages(null);
    }
}
