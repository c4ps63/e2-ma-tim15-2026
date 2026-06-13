package com.example.slagalicavpl.game;

import com.example.slagalicavpl.model.AsocijacijePuzzle;
import com.example.slagalicavpl.multiplayer.AsocijacijeSync;

public class AsocijacijeEngine {

    public static final int ROUND_SECS = 120;

    public interface Listener {
        /** Round just started. localFirst = true when local player opens first. */
        void onRoundStarted(int round, boolean localFirst);
        /** A cell was revealed. */
        void onFieldOpened(int col, int cell, String content, boolean byLocal);
        /** Opponent is about to guess this text (show for ~1 s before result). */
        void onOpponentAttempt(String text);
        /** A column solution was guessed correctly. */
        void onColumnSolved(int col, int pts, boolean byLocal);
        /** Final solution guessed. Fragment must call continueAfterFinal() after pause. */
        void onFinalSolved(int pts, boolean byLocal);
        /** A guess was wrong — turn passes. */
        void onWrongGuess(boolean byLocal);
        /** Whose turn it is. */
        void onTurnChanged(boolean isLocalTurn);
        /** Score update. */
        void onScoreChanged(int localTotal, int oppTotal);
        /** Round ended without anyone guessing the final (timer). Fragment calls continueAfterRound(). */
        void onRoundEnded(int localRoundPts, int oppRoundPts);
        /** Game is fully over. */
        void onGameOver(int localTotal, int oppTotal);
    }

    private final AsocijacijeSync sync;
    private final Listener        listener;

    private AsocijacijePuzzle round1Puzzle;
    private AsocijacijePuzzle round2Puzzle;
    private AsocijacijePuzzle currentPuzzle;

    private int       currentRound  = 0;
    private boolean   localsTurn    = true;
    private boolean   paused        = false;   // true during post-final pause
    private Runnable  pendingNext   = null;

    // Per-round state
    private final boolean[]   colSolved   = new boolean[4];
    private final boolean[][] cellOpened  = new boolean[4][4];
    private final int[]       openedCount = new int[4];   // cells opened per column
    private boolean           finalSolved = false;
    private int               localRoundPts = 0;
    private int               oppRoundPts   = 0;

    // Across rounds
    private int localScore = 0;
    private int oppScore   = 0;

    // true = lokalni igrač igra prvi u rundi 1 (standardno); false = protivnik prvi
    private boolean localStartsRound1 = true;

    /** Postavi ko počinje rundu 1 (za online: p1=true, p2=false). */
    public void setLocalStartsRound1(boolean localFirst) {
        this.localStartsRound1 = localFirst;
    }

    private boolean localFirstForRound(int round) {
        // Runda 1: localStartsRound1; Runda 2: suprotno
        return round == 1 ? localStartsRound1 : !localStartsRound1;
    }

    public AsocijacijeEngine(AsocijacijePuzzle round1, AsocijacijePuzzle round2,
                              AsocijacijeSync sync, Listener listener) {
        this.round1Puzzle = round1;
        this.round2Puzzle = round2;
        this.sync         = sync;
        this.listener     = listener;
    }

    public void startGame() {
        localScore = 0;
        oppScore   = 0;
        beginRound(1, round1Puzzle);
    }

    // ── Called by fragment (local player actions) ─────────────────────────────

    public boolean openField(int col, int cell) {
        if (!localsTurn || paused) return false;
        if (cellOpened[col][cell]) return false;
        revealCell(col, cell, true);
        return true;
    }

    public boolean guessColumn(int col, String text) {
        if (!localsTurn || paused) return false;
        if (colSolved[col]) return false;
        if (text.trim().equalsIgnoreCase(currentPuzzle.colSolutions[col])) {
            int pts = colScore(col);
            localRoundPts += pts;
            colSolved[col] = true;
            listener.onColumnSolved(col, pts, true);
            listener.onScoreChanged(localScore + localRoundPts, oppScore + oppRoundPts);
            // local stays active — can guess again
            return true;
        } else {
            listener.onWrongGuess(true);
            switchToOpponent();
            return false;
        }
    }

    public boolean guessFinal(String text) {
        if (!localsTurn || paused || finalSolved) return false;
        if (text.trim().equalsIgnoreCase(currentPuzzle.finalSolution)) {
            int pts = finalScore();
            localRoundPts += pts;
            finalSolved = true;
            listener.onScoreChanged(localScore + localRoundPts, oppScore + oppRoundPts);
            pauseAndFire(pts, true, this::endRound);
            return true;
        } else {
            listener.onWrongGuess(true);
            switchToOpponent();
            return false;
        }
    }

    /** Local player passes their guess turn → opponent's turn. */
    public void passGuess() {
        if (!localsTurn || paused) return;
        switchToOpponent();
    }

    /** Called by fragment when 2-min timer fires. */
    public void onTimerExpired() {
        if (paused || currentRound == 0) return;
        sync.cancel();
        endRound();
    }

    /** Called by fragment after post-final pause. */
    public void continueAfterFinal() {
        paused = false;
        if (pendingNext != null) {
            Runnable r = pendingNext;
            pendingNext = null;
            r.run();
        }
    }

    /** Called by fragment after between-round pause. */
    public void continueAfterRound() {
        if (currentRound == 1) beginRound(2, round2Puzzle);
        else finishGame();
    }

    public boolean isLocalsTurn() { return localsTurn; }
    public AsocijacijePuzzle getCurrentPuzzle() { return currentPuzzle; }
    public boolean isCellOpened(int col, int cell) { return cellOpened[col][cell]; }
    public boolean isColSolved(int col) { return colSolved[col]; }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void beginRound(int round, AsocijacijePuzzle puzzle) {
        currentRound  = round;
        currentPuzzle = puzzle;
        paused        = false;
        pendingNext   = null;
        finalSolved   = false;
        localRoundPts = 0;
        oppRoundPts   = 0;

        for (int c = 0; c < 4; c++) {
            colSolved[c]   = false;
            openedCount[c] = 0;
            for (int r = 0; r < 4; r++) cellOpened[c][r] = false;
        }

        // Round 1: local starts; Round 2: opponent starts (može biti override-ovan)
        localsTurn = localFirstForRound(round);
        listener.onRoundStarted(round, localsTurn);

        if (!localsTurn) startOpponentTurn();
    }

    private void revealCell(int col, int cell, boolean byLocal) {
        cellOpened[col][cell] = true;
        openedCount[col]++;
        listener.onFieldOpened(col, cell, currentPuzzle.cells[col][cell], byLocal);
    }

    private void switchToOpponent() {
        if (paused) return;
        localsTurn = false;
        listener.onTurnChanged(false);
        startOpponentTurn();
    }

    private void switchToLocal() {
        if (paused) return;
        localsTurn = true;
        listener.onTurnChanged(true);
    }

    private void startOpponentTurn() {
        // Snapshot colSolved + cellOpened for sync (sync may mutate its own copy)
        boolean[]   solvedSnap = colSolved.clone();
        boolean[][] openSnap   = new boolean[4][4];
        for (int c = 0; c < 4; c++) openSnap[c] = cellOpened[c].clone();

        sync.startOpponentTurn(currentPuzzle, solvedSnap, openSnap,
                new AsocijacijeSync.Callback() {

                    @Override
                    public void onOpponentOpenField(int col, int cell) {
                        if (paused || localsTurn) return;
                        revealCell(col, cell, false);
                    }

                    @Override
                    public void onOpponentAttempt(String text) {
                        if (paused || localsTurn) return;
                        listener.onOpponentAttempt(text);
                    }

                    @Override
                    public void onOpponentGuessColumn(int col) {
                        if (paused || localsTurn || colSolved[col]) return;
                        int pts = colScore(col);
                        oppRoundPts += pts;
                        colSolved[col] = true;
                        listener.onColumnSolved(col, pts, false);
                        listener.onScoreChanged(localScore + localRoundPts,
                                                oppScore  + oppRoundPts);
                    }

                    @Override
                    public void onOpponentGuessFinal() {
                        if (paused || localsTurn || finalSolved) return;
                        int pts = finalScore();
                        oppRoundPts += pts;
                        finalSolved = true;
                        listener.onScoreChanged(localScore + localRoundPts,
                                                oppScore  + oppRoundPts);
                        pauseAndFire(pts, false, AsocijacijeEngine.this::endRound);
                    }

                    @Override
                    public void onOpponentDone() {
                        if (paused || localsTurn) return;
                        switchToLocal();
                    }
                });
    }

    private void pauseAndFire(int pts, boolean byLocal, Runnable continuation) {
        paused      = true;
        pendingNext = continuation;
        listener.onFinalSolved(pts, byLocal);
    }

    private void endRound() {
        sync.cancel();
        paused = false;
        int localPts = localRoundPts;
        int oppPts   = oppRoundPts;
        localScore += localPts;
        oppScore   += oppPts;
        listener.onRoundEnded(localPts, oppPts);
        // Fragment calls continueAfterRound() after pause
    }

    private void finishGame() {
        listener.onGameOver(localScore, oppScore);
    }

    // ── Scoring helpers ───────────────────────────────────────────────────────

    private int colScore(int col) {
        return 2 + (4 - openedCount[col]);
    }

    private int finalScore() {
        int pts = 7;
        for (int c = 0; c < 4; c++) {
            if (!colSolved[c]) pts += 2 + (4 - openedCount[c]);
        }
        return pts;
    }
}
