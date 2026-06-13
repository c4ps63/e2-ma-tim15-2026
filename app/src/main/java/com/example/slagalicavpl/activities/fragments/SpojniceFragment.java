package com.example.slagalicavpl.activities.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.activities.GameActivity;
import com.example.slagalicavpl.game.SpojniceEngine;
import com.example.slagalicavpl.model.ConnectPair;
import com.example.slagalicavpl.multiplayer.LocalSpojniceSync;
import com.example.slagalicavpl.repository.ConnectRepository;

import java.util.List;

public class SpojniceFragment extends Fragment implements SpojniceEngine.Listener {

    private static final int ROUND_SECS = 30;
    private static final int WARN_SECS  = 10;

    private final Button[] leftBtns         = new Button[5];
    private final Button[] rightBtns        = new Button[5];
    private final View[]   connectors       = new View[5];
    private final boolean[] connectedDisplay = new boolean[5];
    private final boolean[] lockedWrong      = new boolean[5];

    private TextView tvStatus;
    private TextView tvTimerHud;
    private TextView tvP1Score;
    private TextView tvP2Score;
    private Button   btnConfirm;

    private SpojniceEngine                                          engine;
    private CountDownTimer                                          roundTimer;
    private final Handler                                           handler = new Handler(Looper.getMainLooper());
    private int                                                     selectedLeft = -1;
    private SpojniceEngine.Phase                                    currentPhase;
    private int                                                     currentRound = 1;
    private String                                                  myRole = "p1";
    private boolean                                                 localStartsFirst = true;

    private com.example.slagalicavpl.multiplayer.FirebaseSpojniceSync firebaseSpojSync;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_spojnice, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        leftBtns[0]  = view.findViewById(R.id.tvLeft1);
        leftBtns[1]  = view.findViewById(R.id.tvLeft2);
        leftBtns[2]  = view.findViewById(R.id.tvLeft3);
        leftBtns[3]  = view.findViewById(R.id.tvLeft4);
        leftBtns[4]  = view.findViewById(R.id.tvLeft5);
        rightBtns[0] = view.findViewById(R.id.tvRight1);
        rightBtns[1] = view.findViewById(R.id.tvRight2);
        rightBtns[2] = view.findViewById(R.id.tvRight3);
        rightBtns[3] = view.findViewById(R.id.tvRight4);
        rightBtns[4] = view.findViewById(R.id.tvRight5);
        connectors[0] = view.findViewById(R.id.connector1);
        connectors[1] = view.findViewById(R.id.connector2);
        connectors[2] = view.findViewById(R.id.connector3);
        connectors[3] = view.findViewById(R.id.connector4);
        connectors[4] = view.findViewById(R.id.connector5);

        tvStatus   = view.findViewById(R.id.tvStatus);
        tvTimerHud = view.findViewById(R.id.timer_value);
        tvP1Score  = view.findViewById(R.id.p1_score);
        tvP2Score  = view.findViewById(R.id.p2_score);
        btnConfirm = view.findViewById(R.id.btnConfirm);

        TextView tvP1Name = view.findViewById(R.id.p1_name);
        TextView tvP2Name = view.findViewById(R.id.p2_name);
        if (tvP1Name != null) tvP1Name.setText("TI");
        if (tvP2Name != null) tvP2Name.setText("PROTIVNIK");

        for (int i = 0; i < 5; i++) {
            final int row = i;
            leftBtns[i].setOnClickListener(v  -> onLeftTapped(row));
            rightBtns[i].setOnClickListener(v -> onRightTapped(row));
        }

        btnConfirm.setOnClickListener(v -> engine.pass());

        view.findViewById(R.id.btnSurrender).setOnClickListener(v -> {
            cancelTimer();
            if (getActivity() instanceof GameActivity)
                ((GameActivity) getActivity()).showAsocijacije();
        });

        ConnectRepository repo = ConnectRepository.getInstance();
        if (getActivity() instanceof GameActivity) {
            GameActivity ga = (GameActivity) getActivity();
            if (tvP1Score != null) tvP1Score.setText(String.valueOf(ga.getP1Total()));
            if (tvP2Score != null) tvP2Score.setText(String.valueOf(ga.getP2Total()));
        }

        boolean multiplayer = getActivity() instanceof GameActivity
                && ((GameActivity) getActivity()).isMultiplayer();

        if (multiplayer && getActivity() instanceof GameActivity) {
            GameActivity ga = (GameActivity) getActivity();
            myRole = ga.getMyRole();
            localStartsFirst = "p1".equals(myRole);

            com.google.firebase.database.DatabaseReference roomRef = ga.getRoomRef();
            firebaseSpojSync = new com.example.slagalicavpl.multiplayer.FirebaseSpojniceSync(
                    roomRef, myRole);

            engine = new SpojniceEngine(
                    repo.getRound1Pairs(),
                    repo.getRound2Pairs(),
                    firebaseSpojSync,
                    this);
            engine.setLocalStartsFirst(localStartsFirst);

            if ("p1".equals(myRole)) {
                // P1 generates slots for both rounds before starting the engine
                int[] slots1 = generateShuffledSlots();
                int[] slots2 = generateShuffledSlots();
                engine.setExternalSlots(slots1, slots2);
                firebaseSpojSync.writeAllSlots(slots1, slots2);
                engine.startGame(); // uses externalSlots1 for round 1
            } else {
                // P2 reads slots from Firebase, then starts
                firebaseSpojSync.readAllSlots((s1, s2) -> {
                    if (getView() == null) return;
                    engine.setExternalSlots(s1, s2);
                    engine.startGame();
                });
            }
        } else {
            engine = new SpojniceEngine(
                    repo.getRound1Pairs(),
                    repo.getRound2Pairs(),
                    new LocalSpojniceSync(),
                    this);
            engine.startGame();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        cancelTimer();
        handler.removeCallbacksAndMessages(null);
    }

    private void onLeftTapped(int row) {
        if (!isLocalActive()) return;
        if (connectedDisplay[row]) return;
        if (selectedLeft == row) { clearSelection(); return; }
        selectedLeft = row;
        refreshLeftStyles();
    }

    private void onRightTapped(int row) {
        if (!isLocalActive() || selectedLeft < 0) return;
        int left = selectedLeft;
        clearSelection();
        boolean ok = engine.connectPair(left, row);
        if (!ok) {
            // Lock the left button permanently — wrong guess costs you that item
            lockedWrong[left] = true;
            leftBtns[left].setEnabled(false);
            leftBtns[left].setBackgroundResource(R.drawable.btn_cartoon_red);
            leftBtns[left].setAlpha(0.5f);
            tvStatus.setText("POGREŠNO! — ZAKLJUČANO");
            handler.postDelayed(this::updateStatusText, 1500);
        }
    }

    @Override
    public void onPhaseChanged(SpojniceEngine.Phase phase) {
        // Write done signal for the phase we're LEAVING (the one that just ended)
        if (firebaseSpojSync != null) {
            writeDoneForEndedPhase(phase);
        }
        currentPhase = phase;
        boolean localActive = isLocalActive();

        for (int i = 0; i < 5; i++) {
            if (!connectedDisplay[i] && !lockedWrong[i]) {
                leftBtns[i].setEnabled(localActive);
                rightBtns[i].setEnabled(localActive);
            }
        }
        selectedLeft = -1;
        clearLeftHighlights();
        btnConfirm.setEnabled(localActive);

        // If this is a steal phase and all pairs are already connected, skip immediately
        boolean isStealPhase = (phase == SpojniceEngine.Phase.R1_OPP
                             || phase == SpojniceEngine.Phase.R2_LOCAL);
        if (isStealPhase && localActive) {
            boolean anyLeft = false;
            for (boolean c : connectedDisplay) if (!c) { anyLeft = true; break; }
            if (!anyLeft) {
                handler.post(() -> engine.pass());
                return;
            }
        }

        updateStatusText();
        startPhaseTimer();
    }

    /**
     * When phase transitions to `newPhase`, the previous phase just ended.
     * The player who was active in the previous phase writes the done signal.
     *
     * Phase transitions:
     *   R1_LOCAL → R1_OPP : P1 (localStartsFirst) was active → writes p1_r1
     *   R1_OPP   → R2_OPP : P2 (!localStartsFirst) was active → writes p2_steal
     *   R2_OPP   → R2_LOCAL: P2 (!localStartsFirst) was active → writes p2_r2
     *   R2_LOCAL → DONE   : handled in onGameOver
     */
    private void writeDoneForEndedPhase(SpojniceEngine.Phase newPhase) {
        switch (newPhase) {
            case R1_OPP:
                // P1 just finished R1_LOCAL
                if (localStartsFirst) firebaseSpojSync.writePhaseDone("p1_r1");
                break;
            case R2_OPP:
                // P2 just finished R1_OPP (steal) — only P2 writes this
                if (!localStartsFirst) firebaseSpojSync.writePhaseDone("p2_steal");
                break;
            case R2_LOCAL:
                // P2 just finished R2_OPP — only P2 writes
                if (!localStartsFirst) firebaseSpojSync.writePhaseDone("p2_r2");
                break;
            default:
                break;
        }
    }

    @Override
    public void onRoundStarted(int round, SpojniceEngine.Phase phase,
                               List<ConnectPair> pairs, int[] rightSlots) {
        // When P2 (localStartsFirst=false) transitions steal→round2, done signal is sent here
        // because the engine calls onRoundStarted (not onPhaseChanged) for round boundaries.
        if (round == 2 && firebaseSpojSync != null && !localStartsFirst) {
            firebaseSpojSync.writePhaseDone("p2_steal");
        }
        currentRound = round;
        currentPhase = phase;
        boolean localActive = isLocalActive();

        for (int i = 0; i < 5; i++) {
            leftBtns[i].setText(pairs.get(i).left);
            rightBtns[i].setText(pairs.get(rightSlots[i]).right);
            leftBtns[i].setBackgroundResource(R.drawable.btn_cartoon_salmon);
            rightBtns[i].setBackgroundResource(R.drawable.btn_cartoon_salmon);
            leftBtns[i].setAlpha(1f);
            rightBtns[i].setAlpha(1f);
            leftBtns[i].setEnabled(localActive);
            rightBtns[i].setEnabled(localActive);
            connectors[i].setVisibility(View.INVISIBLE);
            connectedDisplay[i] = false;
            lockedWrong[i] = false;
        }
        selectedLeft = -1;
        btnConfirm.setEnabled(localActive);

        updateStatusText();
        startPhaseTimer();
    }

    @Override
    public void onPairConnected(int leftRow, int rightRow, boolean byLocal) {
        // Write connection to Firebase when locally active
        if (byLocal && firebaseSpojSync != null) {
            String connKey = localConnectionKey();
            if (connKey != null) firebaseSpojSync.writeConnection(connKey, leftRow, rightRow);
        }

        if (leftRow != rightRow) {
            CharSequence tmp = rightBtns[leftRow].getText();
            rightBtns[leftRow].setText(rightBtns[rightRow].getText());
            rightBtns[rightRow].setText(tmp);
        }
        connectors[leftRow].setVisibility(View.VISIBLE);
        connectedDisplay[leftRow] = true;

        leftBtns[leftRow].setBackgroundResource(R.drawable.btn_cartoon_green);
        rightBtns[leftRow].setBackgroundResource(R.drawable.btn_cartoon_green);
        leftBtns[leftRow].setEnabled(false);
        rightBtns[leftRow].setEnabled(false);
        leftBtns[leftRow].setAlpha(1f);
        rightBtns[leftRow].setAlpha(1f);

        if (selectedLeft == leftRow) clearSelection();
        updateStatusText();
    }

    /** Returns the Firebase connection key for the current local active phase. */
    private String localConnectionKey() {
        if (localStartsFirst) {
            if (currentPhase == SpojniceEngine.Phase.R1_LOCAL) return "conn_p1_r1";
            if (currentPhase == SpojniceEngine.Phase.R2_LOCAL) return "conn_p1_steal";
        } else {
            if (currentPhase == SpojniceEngine.Phase.R1_OPP)  return "conn_p2_steal";
            if (currentPhase == SpojniceEngine.Phase.R2_OPP)  return "conn_p2_r2";
        }
        return null;
    }

    @Override
    public void onScoreChanged(int localScore, int opponentScore) {
        if (getActivity() instanceof GameActivity) {
            GameActivity ga = (GameActivity) getActivity();
            if (tvP1Score != null) tvP1Score.setText(String.valueOf(ga.getP1Total() + localScore));
            if (tvP2Score != null) tvP2Score.setText(String.valueOf(ga.getP2Total() + opponentScore));
        }
    }

    @Override
    public void onGameOver(int localScore, int opponentScore) {
        cancelTimer();
        setAllButtonsEnabled(false);

        // P1 writes done signal for its last active phase (R2_LOCAL)
        if (firebaseSpojSync != null && localStartsFirst)
            firebaseSpojSync.writePhaseDone("p1_steal");

        tvStatus.setText("KRAJ · TI: " + localScore + "   PROTIVNIK: " + opponentScore);
        if (tvTimerHud != null) tvTimerHud.setText("✓");

        if (getActivity() instanceof GameActivity)
            ((GameActivity) getActivity()).addScores(localScore, opponentScore);

        handler.postDelayed(() -> {
            if (getActivity() instanceof GameActivity)
                ((GameActivity) getActivity()).showAsocijacije();
        }, 2500);
    }

    private void startPhaseTimer() {
        cancelTimer();
        updateTimer(ROUND_SECS);

        roundTimer = new CountDownTimer(ROUND_SECS * 1000L, 1000) {
            @Override public void onTick(long msLeft) {
                updateTimer((int) (msLeft / 1000));
            }
            @Override public void onFinish() {
                updateTimer(0);
                engine.onTimerExpired();
            }
        }.start();
    }

    private void cancelTimer() {
        if (roundTimer != null) { roundTimer.cancel(); roundTimer = null; }
    }

    private void updateTimer(int s) {
        if (tvTimerHud == null) return;
        tvTimerHud.setText(String.valueOf(s));
        tvTimerHud.setTextColor(s <= WARN_SECS ? Color.RED : Color.parseColor("#102341"));
    }

    private void updateStatusText() {
        if (tvStatus == null) return;
        int cnt = 0;
        for (boolean c : connectedDisplay) if (c) cnt++;
        String label;
        switch (currentPhase) {
            case R1_LOCAL:
                label = localStartsFirst ? "RUNDA 1 · TI IGRAŠ" : "RUNDA 1 · PROTIVNIK IGRA";
                break;
            case R1_OPP:
                label = localStartsFirst ? "RUNDA 1 · PROTIVNIK KRADE" : "RUNDA 1 · TI KRADEŠ";
                break;
            case R2_OPP:
                label = localStartsFirst ? "RUNDA 2 · PROTIVNIK IGRA" : "RUNDA 2 · TI IGRAŠ";
                break;
            case R2_LOCAL:
                label = localStartsFirst ? "RUNDA 2 · TI KRADEŠ" : "RUNDA 2 · PROTIVNIK KRADE";
                break;
            default:
                label = "KRAJ IGRE";
                break;
        }
        tvStatus.setText(label + " · " + cnt + "/5");
    }

    private void clearSelection() {
        selectedLeft = -1;
        refreshLeftStyles();
    }

    private void clearLeftHighlights() {
        for (int i = 0; i < 5; i++) {
            if (!connectedDisplay[i]) {
                leftBtns[i].setBackgroundResource(R.drawable.btn_cartoon_salmon);
                leftBtns[i].setAlpha(1f);
            }
        }
    }

    private void refreshLeftStyles() {
        for (int i = 0; i < 5; i++) {
            if (!connectedDisplay[i]) {
                leftBtns[i].setBackgroundResource(
                        i == selectedLeft ? R.drawable.btn_cartoon_yellow
                                          : R.drawable.btn_cartoon_salmon);
                leftBtns[i].setAlpha(1f);
            }
        }
    }

    private void setAllButtonsEnabled(boolean enabled) {
        for (int i = 0; i < 5; i++) {
            leftBtns[i].setEnabled(enabled);
            rightBtns[i].setEnabled(enabled);
        }
        btnConfirm.setEnabled(enabled);
    }

    private boolean isLocalActive() {
        if (localStartsFirst)
            return currentPhase == SpojniceEngine.Phase.R1_LOCAL
                || currentPhase == SpojniceEngine.Phase.R2_LOCAL;
        else
            return currentPhase == SpojniceEngine.Phase.R1_OPP
                || currentPhase == SpojniceEngine.Phase.R2_OPP;
    }

    private int[] generateShuffledSlots() {
        int[] s = {0, 1, 2, 3, 4};
        java.util.Random rng = new java.util.Random();
        for (int i = 4; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = s[i]; s[i] = s[j]; s[j] = t;
        }
        return s;
    }
}
