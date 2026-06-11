package com.example.slagalicavpl.game;

import com.example.slagalicavpl.model.ConnectPair;
import com.example.slagalicavpl.multiplayer.SpojniceSync;

import java.util.List;
import java.util.Random;

public class SpojniceEngine {

    public static final int PAIRS_PER_ROUND = 5;
    public static final int PTS_PER_PAIR    = 2;

    public enum Phase { R1_LOCAL, R1_OPP, R2_OPP, R2_LOCAL, DONE }

    public interface Listener {
        void onRoundStarted(int round, Phase phase, List<ConnectPair> pairs, int[] rightSlots);
        void onPhaseChanged(Phase phase);
        void onPairConnected(int leftRow, int rightRow, boolean byLocal);
        void onScoreChanged(int localScore, int opponentScore);
        void onGameOver(int localScore, int opponentScore);
    }

    private final List<ConnectPair> round1Pairs;
    private final List<ConnectPair> round2Pairs;
    private final SpojniceSync      sync;
    private final Listener          listener;
    private final Random            rng = new Random();

    private Phase            phase = Phase.R1_LOCAL;
    private int              localScore    = 0;
    private int              opponentScore = 0;
    private List<ConnectPair> currentPairs;
    private boolean[]        connected;
    private int[]            rightSlots;

    public SpojniceEngine(List<ConnectPair> round1Pairs, List<ConnectPair> round2Pairs,
                          SpojniceSync sync, Listener listener) {
        this.round1Pairs = round1Pairs;
        this.round2Pairs = round2Pairs;
        this.sync        = sync;
        this.listener    = listener;
    }

    public void startGame() {
        localScore    = 0;
        opponentScore = 0;
        startRound(1, round1Pairs, Phase.R1_LOCAL);
    }

    public boolean connectPair(int leftRow, int rightRow) {
        if (phase != Phase.R1_LOCAL && phase != Phase.R2_LOCAL) return false;
        return processConnection(leftRow, rightRow, true);
    }

    public void pass() {
        if (phase == Phase.R1_LOCAL || phase == Phase.R2_LOCAL) advancePhase();
    }

    public void onTimerExpired() {
        if (phase != Phase.DONE) advancePhase();
    }

    public Phase getPhase() { return phase; }

    private void startRound(int round, List<ConnectPair> pairs, Phase startPhase) {
        currentPairs = pairs;
        connected    = new boolean[PAIRS_PER_ROUND];
        rightSlots   = shuffledSlots();
        phase        = startPhase;
        listener.onRoundStarted(round, phase, currentPairs, rightSlots.clone());
        if (phase == Phase.R2_OPP) startOpponentPhase();
    }

    private void advancePhase() {
        sync.cancel();
        switch (phase) {
            case R1_LOCAL:
                phase = Phase.R1_OPP;
                listener.onPhaseChanged(phase);
                startOpponentPhase();
                break;
            case R1_OPP:
                startRound(2, round2Pairs, Phase.R2_OPP);
                break;
            case R2_OPP:
                phase = Phase.R2_LOCAL;
                listener.onPhaseChanged(phase);
                break;
            case R2_LOCAL:
                phase = Phase.DONE;
                listener.onGameOver(localScore, opponentScore);
                break;
        }
    }

    private void startOpponentPhase() {
        sync.startOpponentTurn(currentPairs, connected, rightSlots,
                new SpojniceSync.ConnectCallback() {
                    @Override
                    public void onOpponentConnect(int leftRow, int rightRow) {
                        if (phase != Phase.R1_OPP && phase != Phase.R2_OPP) return;
                        processConnection(leftRow, rightRow, false);
                    }
                    @Override
                    public void onOpponentDone() {
                        if (phase != Phase.R1_OPP && phase != Phase.R2_OPP) return;
                        advancePhase();
                    }
                });
    }

    private boolean processConnection(int leftRow, int rightRow, boolean byLocal) {
        if (connected[leftRow]) return false;
        if (rightSlots[rightRow] != leftRow) return false;

        if (leftRow != rightRow) {
            int tmp = rightSlots[leftRow];
            rightSlots[leftRow]  = rightSlots[rightRow];
            rightSlots[rightRow] = tmp;
        }
        connected[leftRow] = true;

        if (byLocal) localScore    += PTS_PER_PAIR;
        else         opponentScore += PTS_PER_PAIR;

        listener.onScoreChanged(localScore, opponentScore);
        listener.onPairConnected(leftRow, rightRow, byLocal);

        boolean allDone = true;
        for (boolean c : connected) if (!c) { allDone = false; break; }
        if (allDone) advancePhase();

        return true;
    }

    private int[] shuffledSlots() {
        int[] slots = new int[PAIRS_PER_ROUND];
        for (int i = 0; i < PAIRS_PER_ROUND; i++) slots[i] = i;
        for (int i = PAIRS_PER_ROUND - 1; i > 0; i--) {
            int j   = rng.nextInt(i + 1);
            int tmp = slots[i]; slots[i] = slots[j]; slots[j] = tmp;
        }
        return slots;
    }
}
