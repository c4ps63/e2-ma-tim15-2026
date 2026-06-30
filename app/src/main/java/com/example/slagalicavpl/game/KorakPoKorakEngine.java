package com.example.slagalicavpl.game;

import com.example.slagalicavpl.model.KorakPuzzle;

public class KorakPoKorakEngine {

    public static final int MAX_STEPS    = 7;
    public static final int BASE_PTS     = 20;
    public static final int STEP_DROP    = 2;
    public static final int STEAL_PTS    = 5;
    public static final int TOTAL_ROUNDS = 2;
    public static final int STEP_SECS    = 10;
    public static final int STEAL_SECS   = 10;

    public enum Phase { PLAYER_TURN, STEAL, BETWEEN_ROUNDS, DONE }

    public interface Listener {
        void onRoundStarted(int round, int activePlayer);
        void onStepRevealed(int stepIndex, String clue, int maxPoints);
        void onStartStepTimer();
        void onStartStealTimer(int stealPlayer);
        void onCancelTimers();
        void onHeaderChanged(String text);
        void onInputCleared();
        void onInputEnabled(boolean enabled);
        void onAnswerResult(boolean correct, int pts);
        void onRoundTransition(int nextRound, int nextPlayer);
        void onGameOver(int p1Score, int p2Score);
        void onScoreChanged(int p1Score, int p2Score);
    }

    private KorakPuzzle       puzzle;
    private final Listener    listener;

    private Phase phase        = Phase.DONE;
    private int   currentRound = 1;
    private int   currentStep  = 1;
    private int   activePlayer = 1;
    private int   p1Points     = 0;
    private int   p2Points     = 0;

    public KorakPoKorakEngine(KorakPuzzle puzzle, Listener listener) {
        this.puzzle   = puzzle;
        this.listener = listener;
    }

    public void setPuzzle(KorakPuzzle puzzle) {
        this.puzzle = puzzle;
    }

    public void startRound(int round, int player) {
        currentRound  = round;
        activePlayer  = player;
        currentStep   = 1;
        phase         = Phase.PLAYER_TURN;

        listener.onRoundStarted(round, player);
        listener.onInputEnabled(true);
        revealCurrentStep();
        listener.onHeaderChanged(buildHeader());
        listener.onStartStepTimer();
    }

    public void onStepTimerExpired() {
        if (phase != Phase.PLAYER_TURN) return;

        if (currentStep < MAX_STEPS) {
            currentStep++;
            revealCurrentStep();
            listener.onHeaderChanged(buildHeader());
            listener.onStartStepTimer();
        } else {
            enterSteal();
        }
    }

    public void onStealTimerExpired() {
        endRound();
    }

    public boolean submitAnswer(String input) {
        if (input == null || input.trim().isEmpty()) return false;
        boolean correct = input.trim().equalsIgnoreCase(puzzle.answer);

        if (phase == Phase.PLAYER_TURN) {
            if (correct) {
                int pts = BASE_PTS - STEP_DROP * (currentStep - 1);
                awardPoints(activePlayer, pts);
                listener.onAnswerResult(true, pts);
                listener.onInputEnabled(false);
                listener.onCancelTimers();
                endRound();
            } else {
                listener.onAnswerResult(false, 0);
                listener.onInputCleared();
            }
        } else if (phase == Phase.STEAL) {
            int stealPlayer = (activePlayer == 1) ? 2 : 1;
            int pts = correct ? STEAL_PTS : 0;
            if (correct) awardPoints(stealPlayer, pts);
            listener.onAnswerResult(correct, pts);
            listener.onInputEnabled(false);
            listener.onCancelTimers();
            endRound();
        }
        return correct;
    }

    public Phase getPhase()        { return phase; }
    public int   getActivePlayer() { return activePlayer; }
    public int   getCurrentStep()  { return currentStep; }
    public int   getP1Points()     { return p1Points; }
    public int   getP2Points()     { return p2Points; }

    private void enterSteal() {
        phase = Phase.STEAL;
        int stealPlayer = (activePlayer == 1) ? 2 : 1;
        listener.onCancelTimers();
        listener.onInputCleared();
        listener.onInputEnabled(true);
        listener.onHeaderChanged("ŠANSA ZA KRAĐU · IGRAČ " + stealPlayer + " · " + STEAL_PTS + " BODOVA");
        listener.onStartStealTimer(stealPlayer);
    }

    private void endRound() {
        listener.onCancelTimers();
        listener.onInputEnabled(false);

        if (currentRound < TOTAL_ROUNDS) {
            phase = Phase.BETWEEN_ROUNDS;
            int nextPlayer = (activePlayer == 1) ? 2 : 1;
            listener.onHeaderChanged("RUNDA " + (currentRound + 1) + "/2  ·  IGRAČ " + nextPlayer + " NA POTEZU");
            listener.onRoundTransition(currentRound + 1, nextPlayer);
        } else {
            phase = Phase.DONE;
            listener.onHeaderChanged("KRAJ  ·  P1: " + p1Points + " pts  P2: " + p2Points + " pts");
            listener.onGameOver(p1Points, p2Points);
        }
    }

    private void revealCurrentStep() {
        int i   = currentStep - 1;
        int pts = BASE_PTS - STEP_DROP * i;
        listener.onStepRevealed(i, puzzle.clues[i], pts);
    }

    private void awardPoints(int player, int pts) {
        if (player == 1) p1Points += pts;
        else             p2Points += pts;
        listener.onScoreChanged(p1Points, p2Points);
    }

    private String buildHeader() {
        int maxPts = BASE_PTS - STEP_DROP * (currentStep - 1);
        return "RUNDA " + currentRound + "/2  ·  "
             + "KORAK " + currentStep + "/" + MAX_STEPS + "  ·  "
             + "IGRAČ " + activePlayer + "  ·  MAX " + maxPts + " BODOVA";
    }
}
