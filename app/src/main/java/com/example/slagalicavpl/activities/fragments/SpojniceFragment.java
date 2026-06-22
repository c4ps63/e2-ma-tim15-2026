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
import com.example.slagalicavpl.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

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

    private boolean localInputEnabled = false;
    private int localPairsConnected = 0;

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

        btnConfirm.setOnClickListener(v -> {
            if (localInputEnabled) engine.pass();
        });

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
            ga.applyAvatarsToHud(view);
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
                int[] slots1 = generateShuffledSlots();
                int[] slots2 = generateShuffledSlots();
                engine.setExternalSlots(slots1, slots2);
                firebaseSpojSync.writeAllSlots(slots1, slots2);
                engine.startGame();
            } else {
                firebaseSpojSync.readAllSlots((s1, s2) -> {
                    if (getView() == null) return;
                    engine.setExternalSlots(s1, s2);
                    engine.startGame();
                });
            }
        } else {
            boolean challenge = getActivity() instanceof GameActivity
                    && ((GameActivity) getActivity()).isChallengeMode();
            com.example.slagalicavpl.multiplayer.SpojniceSync offlineSync = challenge
                    ? new com.example.slagalicavpl.multiplayer.SoloSpojniceSync()
                    : new LocalSpojniceSync();
            engine = new SpojniceEngine(
                    repo.getRound1Pairs(),
                    repo.getRound2Pairs(),
                    offlineSync,
                    this);
            engine.startGame();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        cancelTimer();
    }

    // ── Graničnik: jedina tačka koja dozvoljava ili zabranjuje unos ──────────

    /**
     * Postavlja graničnik. Poziva se isključivo na kraju onPhaseChanged/onRoundStarted
     * i u onGameOver. Uvek se pre toga eksplicitno postavlja na false (lock).
     */
    private void applyInputLock(boolean active) {
        localInputEnabled = active;
        for (int i = 0; i < 5; i++) {
            if (!connectedDisplay[i] && !lockedWrong[i]) {
                leftBtns[i].setEnabled(active);
                rightBtns[i].setEnabled(active);
            }
        }
        btnConfirm.setEnabled(active);
    }

    // ── Klik handleri ─────────────────────────────────────────────────────────

    private void onLeftTapped(int row) {
        if (!localInputEnabled) return;          // graničnik
        if (connectedDisplay[row]) return;
        if (selectedLeft == row) { clearSelection(); return; }
        selectedLeft = row;
        refreshLeftStyles();
    }

    private void onRightTapped(int row) {
        if (!localInputEnabled || selectedLeft < 0) return;  // graničnik
        int left = selectedLeft;
        clearSelection();
        boolean ok = engine.connectPair(left, row);
        if (!ok) {
            lockedWrong[left] = true;
            leftBtns[left].setEnabled(false);
            leftBtns[left].setBackgroundResource(R.drawable.btn_cartoon_red);
            leftBtns[left].setAlpha(0.5f);
            tvStatus.setText("POGREŠNO! — ZAKLJUČANO");
            handler.postDelayed(this::updateStatusText, 1500);
        }
    }

    // ── Engine callback-ovi ───────────────────────────────────────────────────

    @Override
    public void onPhaseChanged(SpojniceEngine.Phase phase) {
        // Odmah zaključaj unos tokom tranzicije faze
        applyInputLock(false);

        if (firebaseSpojSync != null) {
            writeDoneForEndedPhase(phase);
        }
        currentPhase = phase;
        selectedLeft = -1;
        clearLeftHighlights();

        boolean localActive = isLocalActive();

        // Ako je faza krađe i sve su već spojene — preskočiti automatski
        boolean isStealPhase = (phase == SpojniceEngine.Phase.R1_OPP
                             || phase == SpojniceEngine.Phase.R2_LOCAL);
        if (isStealPhase && localActive) {
            boolean anyLeft = false;
            for (boolean c : connectedDisplay) if (!c) { anyLeft = true; break; }
            if (!anyLeft) {
                // Graničnik ostaje zaključan (false) — engine.pass() odmah prelazi dalje
                handler.post(() -> engine.pass());
                return;
            }
        }

        // Otključaj tek na kraju, kad je sve postavljeno
        applyInputLock(localActive);
        updateStatusText();
        startPhaseTimer();
    }

    /**
     * Aktivan igrač piše done signal kada napušta svoju fazu.
     *
     * R1_LOCAL → R1_OPP : P1 piše p1_r1
     * R1_OPP   → R2_OPP : P2 piše p2_steal  (via onRoundStarted za round 2)
     * R2_OPP   → R2_LOCAL: P2 piše p2_r2
     */
    private void writeDoneForEndedPhase(SpojniceEngine.Phase newPhase) {
        switch (newPhase) {
            case R1_OPP:
                if (localStartsFirst) firebaseSpojSync.writePhaseDone("p1_r1");
                break;
            case R2_OPP:
                if (!localStartsFirst) firebaseSpojSync.writePhaseDone("p2_steal");
                break;
            case R2_LOCAL:
                if (!localStartsFirst) firebaseSpojSync.writePhaseDone("p2_r2");
                break;
            default:
                break;
        }
    }

    @Override
    public void onRoundStarted(int round, SpojniceEngine.Phase phase,
                               List<ConnectPair> pairs, int[] rightSlots) {
        // Odmah zaključaj unos tokom tranzicije runde
        applyInputLock(false);

        if (round == 2 && firebaseSpojSync != null && !localStartsFirst) {
            firebaseSpojSync.writePhaseDone("p2_steal");
        }
        currentRound = round;
        currentPhase = phase;

        for (int i = 0; i < 5; i++) {
            leftBtns[i].setText(pairs.get(i).left);
            rightBtns[i].setText(pairs.get(rightSlots[i]).right);
            leftBtns[i].setBackgroundResource(R.drawable.btn_cartoon_salmon);
            rightBtns[i].setBackgroundResource(R.drawable.btn_cartoon_salmon);
            leftBtns[i].setAlpha(1f);
            rightBtns[i].setAlpha(1f);
            connectors[i].setVisibility(View.INVISIBLE);
            connectedDisplay[i] = false;
            lockedWrong[i] = false;
        }
        selectedLeft = -1;

        // Otključaj tek na kraju
        applyInputLock(isLocalActive());
        updateStatusText();
        startPhaseTimer();
    }

    @Override
    public void onPairConnected(int leftRow, int rightRow, boolean byLocal) {
        if (byLocal) localPairsConnected++;

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
        applyInputLock(false);  // graničnik — zaključaj sve na kraju igre

        if (firebaseSpojSync != null && localStartsFirst)
            firebaseSpojSync.writePhaseDone("p1_steal");

        tvStatus.setText("KRAJ · TI: " + localScore + "   PROTIVNIK: " + opponentScore);
        if (tvTimerHud != null) tvTimerHud.setText("✓");

        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        boolean friendly = getActivity() instanceof GameActivity
                && ((GameActivity) getActivity()).isFriendlyGame();
        if (fbUser != null && !friendly)
            UserRepository.getInstance().incrementSpojnice(fbUser.getUid(), localPairsConnected, 10);

        if (getActivity() instanceof GameActivity)
            ((GameActivity) getActivity()).addScores(localScore, opponentScore);

        handler.postDelayed(() -> {
            if (getActivity() instanceof GameActivity)
                ((GameActivity) getActivity()).showAsocijacije();
        }, 2500);
    }

    // ── Tajmer ────────────────────────────────────────────────────────────────

    private void startPhaseTimer() {
        cancelTimer();

        // Ako protivnik nije prisutan a on je na redu — odmah preskočiti
        boolean oppTurn = !localInputEnabled;
        if (oppTurn && isOpponentDisconnected()) {
            handler.post(() -> engine.onTimerExpired());
            return;
        }

        updateTimer(ROUND_SECS);
        roundTimer = new CountDownTimer(ROUND_SECS * 1000L, 1000) {
            @Override public void onTick(long msLeft) {
                updateTimer((int) (msLeft / 1000));
            }
            @Override public void onFinish() {
                updateTimer(0);
                if (localInputEnabled) engine.onTimerExpired();
                else if (isOpponentDisconnected()) engine.onTimerExpired();
            }
        }.start();
    }

    private boolean isOpponentDisconnected() {
        if (!(getActivity() instanceof GameActivity)) return false;
        return ((GameActivity) getActivity()).isOpponentDisconnected();
    }

    private void cancelTimer() {
        if (roundTimer != null) { roundTimer.cancel(); roundTimer = null; }
    }

    private void updateTimer(int s) {
        if (tvTimerHud == null) return;
        tvTimerHud.setText(String.valueOf(s));
        tvTimerHud.setTextColor(s <= WARN_SECS ? Color.RED : Color.parseColor("#102341"));
    }

    // ── Pomoćne metode ────────────────────────────────────────────────────────

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
