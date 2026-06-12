package com.example.slagalicavpl.game;

import com.example.slagalicavpl.multiplayer.SkockoSync;

import java.util.Random;

public class SkockoEngine {

    public static final int      CODE_LENGTH = 4;
    public static final int      MAX_ATTEMPTS = 6;
    public static final String[] SYMBOLS = {"🦉", "♣", "♠", "♥", "♦", "★"};

    // Phases: local plays round 1, opponent gets bonus if local failed;
    //         opponent plays round 2, local gets bonus if opponent failed.
    public enum Phase {
        R1_LOCAL,        // local player's 30s main turn
        R1_BONUS_OPP,    // opponent's 10s bonus after local failed round 1
        R2_OPP,          // opponent's 30s main turn
        R2_BONUS_LOCAL,  // local player's 10s bonus after opponent failed round 2
        DONE
    }

    public interface Listener {
        void onRoundStarted(int round, Phase phase);
        void onPhaseChanged(Phase phase);
        void onAttemptResult(int attemptIndex, int[] guess, int hits, int nears, boolean byLocal);
        /** Fired when someone solves — fragment shows solution + score, then calls continueAfterSolve(). */
        void onSolved(int[] secret, boolean byLocal, int pointsEarned);
        /** Fired when nobody solved the round — fragment shows solution, then calls continueAfterSolve(). */
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

    public SkockoEngine(SkockoSync sync, Listener listener) {
        this.sync     = sync;
        this.listener = listener;
    }

    public void startGame() {
        round1Secret  = randomCode();
        round2Secret  = randomCode();
        localScore    = 0;
        oppScore      = 0;
        beginPhase(Phase.R1_LOCAL, 1, round1Secret);
    }

    /** Called by fragment when local player submits a guess. Returns false if not local phase. */
    public boolean submitAttempt(int[] guess) {
        if (phase != Phase.R1_LOCAL && phase != Phase.R2_BONUS_LOCAL) return false;

        int hits  = countHits(currentSecret, guess);
        int nears = countNears(currentSecret, guess);

        listener.onAttemptResult(attemptIndex, guess, hits, nears, true);

        if (hits == CODE_LENGTH) {
            int pts = attemptScore(attemptIndex);
            localScore += pts;
            listener.onScoreChanged(localScore, oppScore);
            pausedForSolve = true;
            listener.onSolved(currentSecret.clone(), true, pts);
        } else {
            attemptIndex++;
            if (phase == Phase.R2_BONUS_LOCAL || attemptIndex >= MAX_ATTEMPTS) {
                advancePhase();
            }
        }
        return true;
    }

    /** Called by fragment after the solution-display pause (solve or failed). */
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

    /** Called by fragment when local player's timer expires without solving. */
    public void onTimerExpired() {
        if (phase == Phase.DONE || pausedForSolve) return;
        sync.cancel();
        advancePhase();
    }

    public Phase getPhase() { return phase; }
    public int[] getCurrentSecret() { return currentSecret; }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void beginPhase(Phase p, int round, int[] secret) {
        sync.cancel();
        phase                = p;
        currentSecret        = secret;
        attemptIndex         = 0;
        oppAttemptIndex      = 0;
        pausedForSolve       = false;
        pendingContinuation  = null;

        if (round > 0) listener.onRoundStarted(round, phase);
        else           listener.onPhaseChanged(phase);

        if (phase == Phase.R1_BONUS_OPP || phase == Phase.R2_OPP) {
            startOpponentTurn(phase == Phase.R1_BONUS_OPP ? 1 : MAX_ATTEMPTS);
        }
    }

    private void advanceAfterSolve() {
        switch (phase) {
            case R1_LOCAL:
            case R1_BONUS_OPP:   beginPhase(Phase.R2_OPP, 2, round2Secret); break;
            case R2_OPP:
            case R2_BONUS_LOCAL: finish(); break;
        }
    }

    private void advancePhase() {
        switch (phase) {
            case R1_LOCAL:
                // Local failed → opponent bonus (still round 1, no solution reveal yet)
                beginPhase(Phase.R1_BONUS_OPP, 0, round1Secret);
                break;

            case R1_BONUS_OPP:
                // Nobody solved round 1 → reveal solution, then start round 2
                pausedForSolve      = true;
                pendingContinuation = () -> beginPhase(Phase.R2_OPP, 2, round2Secret);
                listener.onRoundFailed(currentSecret.clone());
                break;

            case R2_OPP:
                // Opponent failed → local bonus (still round 2, no solution reveal yet)
                beginPhase(Phase.R2_BONUS_LOCAL, 0, round2Secret);
                break;

            case R2_BONUS_LOCAL:
                // Nobody solved round 2 → reveal solution, then finish
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
                        if (phase != Phase.R1_BONUS_OPP && phase != Phase.R2_OPP) return;
                        oppAttemptIndex = idx;
                        listener.onAttemptResult(idx, guess, hits, nears, false);
                    }

                    @Override
                    public void onOpponentDone(boolean solved) {
                        if (phase != Phase.R1_BONUS_OPP && phase != Phase.R2_OPP) return;
                        if (solved) {
                            int pts = (phase == Phase.R1_BONUS_OPP) ? 10
                                    : attemptScore(oppAttemptIndex);
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

    // ── static helpers used by LocalSkockoSync ────────────────────────────────

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
