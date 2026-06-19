package com.example.slagalicavpl.multiplayer;

import android.os.Handler;
import android.os.Looper;

import com.example.slagalicavpl.model.AsocijacijePuzzle;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class LocalAsocijacijeSync implements AsocijacijeSync {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random  rng     = new Random();

    @Override
    public void startOpponentTurn(AsocijacijePuzzle puzzle, boolean[] colSolved,
                                   boolean[][] cellOpened, int round, Callback cb) {
        // 1. Pick a random unopened cell to open
        List<int[]> available = new ArrayList<>();
        for (int c = 0; c < 4; c++)
            for (int r = 0; r < 4; r++)
                if (!cellOpened[c][r]) available.add(new int[]{c, r});

        if (available.isEmpty()) {
            handler.postDelayed(cb::onOpponentDone, 600);
            return;
        }

        int[] pick = available.get(rng.nextInt(available.size()));
        int col  = pick[0];
        int cell = pick[1];

        // Step 1: open a field (takes 1.5–2.5 s)
        long t = 1500 + rng.nextInt(1000);
        handler.postDelayed(() -> {
            cb.onOpponentOpenField(col, cell);
            // Update local copy for decision-making
            cellOpened[col][cell] = true;

            // Step 2: decide whether to guess (after another 1.5–2 s)
            long t2 = 1500 + rng.nextInt(500);
            handler.postDelayed(() -> scheduleGuess(puzzle, colSolved, cellOpened, cb, 0), t2);
        }, t);
    }

    private void scheduleGuess(AsocijacijePuzzle puzzle, boolean[] colSolved,
                                boolean[][] cellOpened, Callback cb, int depth) {
        if (depth >= 3) { // limit chaining to avoid runaway
            handler.postDelayed(cb::onOpponentDone, 400);
            return;
        }

        // Count opened cells per unsolved column
        int bestCol = -1;
        int bestOpen = 0;
        for (int c = 0; c < 4; c++) {
            if (colSolved[c]) continue;
            int open = 0;
            for (int r = 0; r < 4; r++) if (cellOpened[c][r]) open++;
            if (open > bestOpen) { bestOpen = open; bestCol = c; }
        }

        // Count solved/opened columns for final guess decision
        int totalSolvedOrOpen = 0;
        for (int c = 0; c < 4; c++) {
            if (colSolved[c]) { totalSolvedOrOpen++; continue; }
            for (int r = 0; r < 4; r++) if (cellOpened[c][r]) { totalSolvedOrOpen++; break; }
        }

        // Decide: guess column (30% if 2+ open), guess final (15% if 3+ engaged), or done
        boolean tryFinal  = totalSolvedOrOpen >= 3 && rng.nextInt(100) < 15;
        boolean tryColumn = bestCol >= 0 && bestOpen >= 2 && rng.nextInt(100) < 30;

        if (tryFinal) {
            // Show "typing" for 1.2 s, then confirm
            handler.postDelayed(() -> {
                cb.onOpponentAttempt(puzzle.finalSolution);
                handler.postDelayed(cb::onOpponentGuessFinal, 1200);
            }, 800 + rng.nextInt(400));
        } else if (tryColumn) {
            final int guessCol = bestCol;
            // Show "typing" for 1.2 s, then confirm
            handler.postDelayed(() -> {
                cb.onOpponentAttempt(puzzle.colSolutions[guessCol]);
                handler.postDelayed(() -> {
                    cb.onOpponentGuessColumn(guessCol);
                    colSolved[guessCol] = true;
                    long delay = 1000 + rng.nextInt(500);
                    handler.postDelayed(
                        () -> scheduleGuess(puzzle, colSolved, cellOpened, cb, depth + 1),
                        delay);
                }, 1200);
            }, 800 + rng.nextInt(400));
        } else {
            handler.postDelayed(cb::onOpponentDone, 500);
        }
    }

    @Override
    public void cancel() {
        handler.removeCallbacksAndMessages(null);
    }
}
