package com.example.slagalicavpl.game;

import com.example.slagalicavpl.multiplayer.SkockoSync;

import java.util.Random;

public class SkockoEngine {

    public static final int      CODE_LENGTH  = 4;
    public static final int      MAX_ATTEMPTS = 6;
    public static final String[] SYMBOLS = {"🦉", "♣", "♠", "♥", "♦", "★"};

    /**
     * Phase flow (same for both players):
     *   R1_LOCAL → R1_BONUS_OPP → R2_OPP → R2_BONUS_LOCAL → DONE
     *
     * For P1 (localStartsFirst=true):
     *   R1_LOCAL       = P1 active (main guessing)
     *   R1_BONUS_OPP   = P2 bonus steal (Firebase/AI)
     *   R2_OPP         = P2 active (Firebase/AI)
     *   R2_BONUS_LOCAL = P1 bonus steal (active)
     *
     * For P2 (localStartsFirst=false):
     *   R1_LOCAL       = P1 active (Firebase/AI)
     *   R1_BONUS_OPP   = P2 bonus steal (active)
     *   R2_OPP         = P2 active (main guessing)
     *   R2_BONUS_LOCAL = P1 bonus steal (Firebase/AI)
     */
    public enum Phase {
        R1_LOCAL, R1_BONUS_OPP, R2_OPP, R2_BONUS_LOCAL, DONE
    }

    public interface Listener {
        void onRoundStarted(int round, Phase phase);
        void onPhaseChanged(Phase phase);
        void onAttemptResult(int attemptIndex, int[] guess, int hits, int nears, boolean byLocal);
        void onSolved(int[] secret, boolean byLocal, int pointsEarned);
        void onRoundFailed(int[] secret);
        void onScoreChanged(int localScore, int oppScore);
        void onGameOver(int localScore, int oppScore);
    }

    private final SkockoSync sync;
    private final Listener   listener;
    private final Random     rng = new Random();

    private Phase    phase               = Phase.R1_LOCAL;
    private int      localScore          = 0;
    private int      oppScore            = 0;
    private int      attemptIndex        = 0;
    private int      oppAttemptIndex     = 0;
    private boolean  pausedForSolve      = false;
    private Runnable pendingContinuation = null;
    private int[]    round1Secret;
    private int[]    round2Secret;
    private int[]    currentSecret;

    // P1 sets true, P2 sets false
    private boolean localStartsFirst = true;

    // Challenge (solo) mode: no real opponent, no steal — the game ends
    // right after the local player finishes their one and only round.
    private boolean soloMode = false;

    public SkockoEngine(SkockoSync sync, Listener listener) {
        this.sync     = sync;
        this.listener = listener;
    }

    public void setLocalStartsFirst(boolean v) { localStartsFirst = v; }

    public void setSoloMode(boolean v) { soloMode = v; }

    public void startGame() {
        round1Secret = randomCode();
        round2Secret = randomCode();
        localScore   = 0;
        oppScore     = 0;
        beginPhase(Phase.R1_LOCAL, 1, round1Secret);
    }

    public void startGame(int[] secret1, int[] secret2) {
        round1Secret = secret1.clone();
        round2Secret = secret2.clone();
        localScore   = 0;
        oppScore     = 0;
        beginPhase(Phase.R1_LOCAL, 1, round1Secret);
    }

    public int[] getRound1Secret() { return round1Secret; }
    public int[] getRound2Secret() { return round2Secret; }

    /** Submit a guess from the local player. Returns false if not the local player's turn. */
    public boolean submitAttempt(int[] guess) {
        if (!isLocalActivePhase()) return false;

        int hits  = countHits(currentSecret, guess);
        int nears = countNears(currentSecret, guess);

        listener.onAttemptResult(attemptIndex, guess, hits, nears, true);

        if (hits == CODE_LENGTH) {
            int pts = isBonusPhase() ? 10 : attemptScore(attemptIndex);
            localScore += pts;
            listener.onScoreChanged(localScore, oppScore);
            pausedForSolve = true;
            listener.onSolved(currentSecret.clone(), true, pts);
        } else {
            attemptIndex++;
            if (isBonusPhase() || attemptIndex >= MAX_ATTEMPTS) {
                advancePhase();
            }
        }
        return true;
    }

    public void continueAfterSolve() {
        pausedForSolve = false;
        if (pendingContinuation != null) {
            Runnable next = pendingContinuation;
            pendingContinuation = null;
            next.run();
        } else {
            advanceAfterSolve();
        }
    }

    /** Called when timer expires — only advances if it is the local player's turn. */
    public void onTimerExpired() {
        if (phase == Phase.DONE || pausedForSolve) return;
        if (!isLocalActivePhase()) return; // passive phase — Firebase drives advancement
        sync.cancel();
        advancePhase();
    }

    public Phase  getPhase()         { return phase; }
    public int[]  getCurrentSecret() { return currentSecret; }

    // ── private helpers ───────────────────────────────────────────────────────

    /** True when the local player should be able to submit guesses. */
    private boolean isLocalActivePhase() {
        if (localStartsFirst)
            return phase == Phase.R1_LOCAL || phase == Phase.R2_BONUS_LOCAL;
        else
            return phase == Phase.R1_BONUS_OPP || phase == Phase.R2_OPP;
    }

    /** True when the current local-active phase is a bonus (single-attempt) phase. */
    private boolean isBonusPhase() {
        if (localStartsFirst) return phase == Phase.R2_BONUS_LOCAL;
        else                  return phase == Phase.R1_BONUS_OPP;
    }

    private void beginPhase(Phase p, int round, int[] secret) {
        sync.cancel();
        phase               = p;
        currentSecret       = secret;
        attemptIndex        = 0;
        oppAttemptIndex     = 0;
        pausedForSolve      = false;
        pendingContinuation = null;

        if (round > 0) listener.onRoundStarted(round, phase);
        else           listener.onPhaseChanged(phase);

        // Start opponent (Firebase/AI) for phases where the opponent is active
        boolean oppActive = localStartsFirst
                ? (phase == Phase.R1_BONUS_OPP || phase == Phase.R2_OPP)
                : (phase == Phase.R1_LOCAL      || phase == Phase.R2_BONUS_LOCAL);

        if (oppActive) {
            int maxAttempts = localStartsFirst
                    ? (phase == Phase.R1_BONUS_OPP ? 1 : MAX_ATTEMPTS)
                    : (phase == Phase.R1_LOCAL      ? MAX_ATTEMPTS : 1);
            startOpponentTurn(maxAttempts);
        }
    }

    private void advanceAfterSolve() {
        switch (phase) {
            case R1_LOCAL:
                if (soloMode) { finish(); } else { beginPhase(Phase.R1_BONUS_OPP, 0, round1Secret); }
                break;
            case R1_BONUS_OPP: beginPhase(Phase.R2_OPP, 2, round2Secret); break;
            case R2_OPP:
            case R2_BONUS_LOCAL: finish(); break;
        }
    }

    private void advancePhase() {
        switch (phase) {
            case R1_LOCAL:
                if (soloMode) { finish(); } else { beginPhase(Phase.R1_BONUS_OPP, 0, round1Secret); }
                break;
            case R1_BONUS_OPP:
                pausedForSolve      = true;
                pendingContinuation = () -> beginPhase(Phase.R2_OPP, 2, round2Secret);
                listener.onRoundFailed(currentSecret.clone());
                break;
            case R2_OPP:
                beginPhase(Phase.R2_BONUS_LOCAL, 0, round2Secret);
                break;
            case R2_BONUS_LOCAL:
                pausedForSolve      = true;
                pendingContinuation = this::finish;
                listener.onRoundFailed(currentSecret.clone());
                break;
            default:
                finish();
        }
    }

    private void startOpponentTurn(int maxAttempts) {
        oppAttemptIndex = 0;
        sync.startOpponentTurn(currentSecret, maxAttempts,
                new SkockoSync.AttemptCallback() {
                    @Override
                    public void onOpponentAttempt(int idx, int[] guess, int hits, int nears) {
                        boolean oppPhase = localStartsFirst
                                ? (phase == Phase.R1_BONUS_OPP || phase == Phase.R2_OPP)
                                : (phase == Phase.R1_LOCAL      || phase == Phase.R2_BONUS_LOCAL);
                        if (!oppPhase) return;
                        oppAttemptIndex = idx;
                        listener.onAttemptResult(idx, guess, hits, nears, false);
                    }

                    @Override
                    public void onOpponentDone(boolean solved) {
                        boolean oppPhase = localStartsFirst
                                ? (phase == Phase.R1_BONUS_OPP || phase == Phase.R2_OPP)
                                : (phase == Phase.R1_LOCAL      || phase == Phase.R2_BONUS_LOCAL);
                        if (!oppPhase) return;
                        if (solved) {
                            // Bonus phases give 10 pts; main phases use attempt-based score
                            boolean oppBonus = localStartsFirst
                                    ? (phase == Phase.R1_BONUS_OPP)
                                    : (phase == Phase.R2_BONUS_LOCAL);
                            int pts = oppBonus ? 10 : attemptScore(oppAttemptIndex);
                            oppScore += pts;
                            listener.onScoreChanged(localScore, oppScore);
                            pausedForSolve = true;
                            listener.onSolved(currentSecret.clone(), false, pts);
                        } else {
                            advancePhase();
                        }
                    }
                });
    }

    private void finish() {
        sync.cancel();
        phase = Phase.DONE;
        listener.onGameOver(localScore, oppScore);
    }

    private int[] randomCode() {
        int[] code = new int[CODE_LENGTH];
        for (int i = 0; i < CODE_LENGTH; i++) code[i] = rng.nextInt(SYMBOLS.length);
        return code;
    }

    private static int attemptScore(int idx) {
        if (idx <= 1) return 20;
        if (idx <= 3) return 15;
        return 10;
    }

    public static int countHits(int[] secret, int[] guess) {
        int h = 0;
        for (int i = 0; i < CODE_LENGTH; i++) if (secret[i] == guess[i]) h++;
        return h;
    }

    public static int countNears(int[] secret, int[] guess) {
        int[] sRem = new int[SYMBOLS.length];
        int[] gRem = new int[SYMBOLS.length];
        for (int i = 0; i < CODE_LENGTH; i++) {
            if (secret[i] != guess[i]) {
                sRem[secret[i]]++;
                gRem[guess[i]]++;
            }
        }
        int n = 0;
        for (int i = 0; i < SYMBOLS.length; i++) n += Math.min(sRem[i], gRem[i]);
        return n;
    }
}
