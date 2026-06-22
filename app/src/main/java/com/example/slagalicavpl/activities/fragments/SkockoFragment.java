package com.example.slagalicavpl.activities.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.activities.GameActivity;
import com.example.slagalicavpl.game.SkockoEngine;
import com.example.slagalicavpl.multiplayer.LocalSkockoSync;
import com.example.slagalicavpl.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SkockoFragment extends Fragment implements SkockoEngine.Listener {

    private static final int ROUND_SECS = 30;
    private static final int BONUS_SECS = 10;
    private static final int WARN_SECS  = 10;

    // Board rows [row 0..5][col 0..3]
    private final TextView[][] cells = new TextView[6][4];
    private final View[][]     pegs  = new View[6][4];

    // Stealing row (above solution): shows the single bonus attempt
    private final TextView[] oppCells = new TextView[4];
    private final View[]     oppPegs  = new View[4];

    // Solution row
    private final TextView[] solCells = new TextView[4];

    // Symbol picker (6 symbols)
    private final TextView[] picker = new TextView[6];

    private TextView tvRound;
    private TextView tvStatus;
    private TextView tvTimerHud;
    private TextView tvP1Score;
    private TextView tvP2Score;
    private View     btnConfirm;

    private SkockoEngine   engine;
    private CountDownTimer roundTimer;
    private final Handler  handler = new Handler(Looper.getMainLooper());

    // Attempt state
    private final int[] currentGuess = {-1, -1, -1, -1};
    private int         activeRow    = 0;
    private boolean     localActive  = false;
    private boolean     bonusPhase   = false;
    private SkockoEngine.Phase currentSkockoPhase = SkockoEngine.Phase.R1_LOCAL;

    // Firebase sync ref za pisanje pokušaja i inicijalizaciju tajni
    private com.example.slagalicavpl.multiplayer.FirebaseSkockoSync firebaseSkockoSync;
    private String  myRole           = "p1";
    private boolean localStartsFirst = true;
    private boolean localMainRoundSolved = false;

    // ── lifecycle ────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_skocko, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);

        int[][] cellIds = {
            {R.id.tvG1C1, R.id.tvG1C2, R.id.tvG1C3, R.id.tvG1C4},
            {R.id.tvG2C1, R.id.tvG2C2, R.id.tvG2C3, R.id.tvG2C4},
            {R.id.tvG3C1, R.id.tvG3C2, R.id.tvG3C3, R.id.tvG3C4},
            {R.id.tvG4C1, R.id.tvG4C2, R.id.tvG4C3, R.id.tvG4C4},
            {R.id.tvG5C1, R.id.tvG5C2, R.id.tvG5C3, R.id.tvG5C4},
            {R.id.tvG6C1, R.id.tvG6C2, R.id.tvG6C3, R.id.tvG6C4}
        };
        for (int r = 0; r < 6; r++)
            for (int c = 0; c < 4; c++)
                cells[r][c] = root.findViewById(cellIds[r][c]);

        int[][] pegIds = {
            {R.id.peg1_1, R.id.peg1_2, R.id.peg1_3, R.id.peg1_4},
            {R.id.peg2_1, R.id.peg2_2, R.id.peg2_3, R.id.peg2_4},
            {R.id.peg3_1, R.id.peg3_2, R.id.peg3_3, R.id.peg3_4},
            {R.id.peg4_1, R.id.peg4_2, R.id.peg4_3, R.id.peg4_4},
            {R.id.peg5_1, R.id.peg5_2, R.id.peg5_3, R.id.peg5_4},
            {R.id.peg6_1, R.id.peg6_2, R.id.peg6_3, R.id.peg6_4}
        };
        for (int r = 0; r < 6; r++)
            for (int p = 0; p < 4; p++)
                pegs[r][p] = root.findViewById(pegIds[r][p]);

        oppCells[0] = root.findViewById(R.id.tvOppC1);
        oppCells[1] = root.findViewById(R.id.tvOppC2);
        oppCells[2] = root.findViewById(R.id.tvOppC3);
        oppCells[3] = root.findViewById(R.id.tvOppC4);
        oppPegs[0]  = root.findViewById(R.id.pegOpp_1);
        oppPegs[1]  = root.findViewById(R.id.pegOpp_2);
        oppPegs[2]  = root.findViewById(R.id.pegOpp_3);
        oppPegs[3]  = root.findViewById(R.id.pegOpp_4);

        solCells[0] = root.findViewById(R.id.tvSolC1);
        solCells[1] = root.findViewById(R.id.tvSolC2);
        solCells[2] = root.findViewById(R.id.tvSolC3);
        solCells[3] = root.findViewById(R.id.tvSolC4);

        int[] pickerIds = {
            R.id.btnSym1, R.id.btnSym2, R.id.btnSym3,
            R.id.btnSym4, R.id.btnSym5, R.id.btnSym6
        };
        for (int i = 0; i < 6; i++) {
            picker[i] = root.findViewById(pickerIds[i]);
            final int sym = i;
            picker[i].setOnClickListener(v -> onSymbolPicked(sym));
        }

        tvRound    = root.findViewById(R.id.tvRound);
        tvStatus   = root.findViewById(R.id.tvStatus);
        tvTimerHud = root.findViewById(R.id.timer_value);
        tvP1Score  = root.findViewById(R.id.p1_score);
        tvP2Score  = root.findViewById(R.id.p2_score);
        btnConfirm = root.findViewById(R.id.btnConfirmAttempt);

        if (root.findViewById(R.id.p1_name) != null)
            ((TextView) root.findViewById(R.id.p1_name)).setText("TI");
        if (root.findViewById(R.id.p2_name) != null)
            ((TextView) root.findViewById(R.id.p2_name)).setText("PROTIVNIK");

        if (getActivity() instanceof GameActivity) {
            GameActivity ga = (GameActivity) getActivity();
            if (tvP1Score != null) tvP1Score.setText(String.valueOf(ga.getP1Total()));
            if (tvP2Score != null) tvP2Score.setText(String.valueOf(ga.getP2Total()));
            ga.applyAvatarsToHud(root);
        }

        btnConfirm.setOnClickListener(v -> onConfirmAttempt());
        root.findViewById(R.id.btnSurrender).setOnClickListener(v -> {
            cancelTimer();
            engine.onTimerExpired();
        });

        boolean multiplayer = getActivity() instanceof GameActivity
                && ((GameActivity) getActivity()).isMultiplayer();
        if (multiplayer && getActivity() instanceof GameActivity) {
            com.google.firebase.database.DatabaseReference roomRef =
                    ((GameActivity) getActivity()).getRoomRef();
            myRole           = ((GameActivity) getActivity()).getMyRole();
            localStartsFirst = "p1".equals(myRole);
            firebaseSkockoSync = new com.example.slagalicavpl.multiplayer.FirebaseSkockoSync(
                    roomRef, myRole);
            engine = new SkockoEngine(firebaseSkockoSync, this);
            engine.setLocalStartsFirst(localStartsFirst);

            if ("p1".equals(myRole)) {
                // P1 generiše tajne, piše na Firebase, pa starta
                engine.startGame(); // generiše round1Secret i round2Secret
                firebaseSkockoSync.writeSecrets(
                        engine.getRound1Secret(), engine.getRound2Secret());
            } else {
                // P2 čeka tajne od P1
                firebaseSkockoSync.readSecrets((s1, s2) -> {
                    if (getView() == null) return;
                    engine.startGame(s1, s2);
                });
            }
        } else {
            boolean challenge = getActivity() instanceof GameActivity
                    && ((GameActivity) getActivity()).isChallengeMode();
            com.example.slagalicavpl.multiplayer.SkockoSync offlineSync = challenge
                    ? new com.example.slagalicavpl.multiplayer.SoloSkockoSync()
                    : new LocalSkockoSync();
            engine = new SkockoEngine(offlineSync, this);
            engine.startGame();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        cancelTimer();
    }

    // ── SkockoEngine.Listener ─────────────────────────────────────────────────

    @Override
    public void onRoundStarted(int round, SkockoEngine.Phase phase) {
        currentSkockoPhase = phase;
        bonusPhase  = false;
        localActive = localStartsFirst
                ? (phase == SkockoEngine.Phase.R1_LOCAL)
                : (phase == SkockoEngine.Phase.R2_OPP);
        activeRow   = 0;
        resetBoard();           // clears all 6 rows + opp row + sol row
        resetGuessState();
        if (localActive) wireActiveRowListeners();
        setPickerEnabled(localActive);
        btnConfirm.setEnabled(false);
        updateRoundBanner(round, phase);
        updateStatus(phase);
        startTimer(timerSecsFor(phase));
    }

    @Override
    public void onPhaseChanged(SkockoEngine.Phase phase) {
        currentSkockoPhase = phase;
        bonusPhase  = true;
        localActive = localStartsFirst
                ? (phase == SkockoEngine.Phase.R2_BONUS_LOCAL)
                : (phase == SkockoEngine.Phase.R1_BONUS_OPP);

        // Write done=false for the main phase that just ended without solving.
        // Only the player who was active in that phase writes the signal.
        // P1 was active in R1_LOCAL → entering R1_BONUS_OPP signals "r1main_p1 = false"
        // P2 was active in R2_OPP  → entering R2_BONUS_LOCAL signals "r2main_p2 = false"
        if (firebaseSkockoSync != null) {
            if (phase == SkockoEngine.Phase.R1_BONUS_OPP && localStartsFirst) {
                firebaseSkockoSync.writePhaseDone("r1main_p1", false);
            } else if (phase == SkockoEngine.Phase.R2_BONUS_LOCAL && !localStartsFirst) {
                firebaseSkockoSync.writePhaseDone("r2main_p2", false);
            }
        }

        // Keep board rows intact — only clear the stealing row
        for (int c = 0; c < 4; c++) oppCells[c].setText("");
        setPegsArray(oppPegs, 0, 0);

        resetGuessState();
        setPickerEnabled(localActive);
        btnConfirm.setEnabled(false);
        updateStatus(phase);
        startTimer(timerSecsFor(phase));

        // Wire oppCells for tap-to-clear when local player steals
        if (localActive) {
            for (int c = 0; c < 4; c++) {
                final int col = c;
                oppCells[col].setOnClickListener(v -> clearBonusCellOnTap(col));
            }
        }
    }

    @Override
    public void onAttemptResult(int attemptIndex, int[] guess, int hits, int nears,
                                boolean byLocal) {
        // Piši lokalni pokušaj na Firebase da protivnik može da ga vidi
        if (byLocal && firebaseSkockoSync != null) {
            firebaseSkockoSync.writeAttempt(localPhaseKey(), attemptIndex, guess, hits, nears);
        }

        if (bonusPhase) {
            for (int c = 0; c < 4; c++)
                oppCells[c].setText(SkockoEngine.SYMBOLS[guess[c]]);
            setPegsArray(oppPegs, hits, nears);
            if (byLocal) {
                resetGuessState();
                setPickerEnabled(false);
            }
        } else {
            if (activeRow < 6) {
                fillRow(activeRow, guess);
                setPegs(activeRow, hits, nears);
                activeRow++;
                if (localActive) wireActiveRowListeners();
            }
            if (byLocal) {
                clearCurrentGuess();
                setPickerEnabled(true);
                btnConfirm.setEnabled(false);
            }
        }
    }

    @Override
    public void onRoundFailed(int[] secret) {
        cancelTimer();
        setPickerEnabled(false);
        btnConfirm.setEnabled(false);

        // Piši "done/false" samo ako je lokalni igrač bio aktivan u ovoj fazi
        if (localActive && firebaseSkockoSync != null) {
            firebaseSkockoSync.writePhaseDone(localPhaseKey(), false);
        }

        for (int c = 0; c < 4; c++)
            solCells[c].setText(SkockoEngine.SYMBOLS[secret[c]]);

        if (tvStatus != null)
            tvStatus.setText("NIKO NIJE POGODIO  ·  REŠENJE OTKRIVENO");

        handler.postDelayed(() -> engine.continueAfterSolve(), 5000);
    }

    @Override
    public void onSolved(int[] secret, boolean byLocal, int pointsEarned) {
        if (byLocal && !bonusPhase) localMainRoundSolved = true;
        cancelTimer();
        setPickerEnabled(false);
        btnConfirm.setEnabled(false);

        // Piši "done/true" za tekuću fazu na Firebase
        if (byLocal && firebaseSkockoSync != null) {
            firebaseSkockoSync.writePhaseDone(localPhaseKey(), true);
        }

        for (int c = 0; c < 4; c++)
            solCells[c].setText(SkockoEngine.SYMBOLS[secret[c]]);

        String who = byLocal ? "TAČNO!" : "PROTIVNIK POGODIO!";
        if (tvStatus != null)
            tvStatus.setText(who + "  ·  +" + pointsEarned + " BODOVA");

        handler.postDelayed(() -> engine.continueAfterSolve(), 5000);
    }

    @Override
    public void onScoreChanged(int localScore, int oppScore) {
        if (getActivity() instanceof GameActivity) {
            GameActivity ga = (GameActivity) getActivity();
            if (tvP1Score != null) tvP1Score.setText(String.valueOf(ga.getP1Total() + localScore));
            if (tvP2Score != null) tvP2Score.setText(String.valueOf(ga.getP2Total() + oppScore));
        } else {
            if (tvP1Score != null) tvP1Score.setText(String.valueOf(localScore));
            if (tvP2Score != null) tvP2Score.setText(String.valueOf(oppScore));
        }
    }

    @Override
    public void onGameOver(int localScore, int oppScore) {
        cancelTimer();
        localActive = false;
        setPickerEnabled(false);
        btnConfirm.setEnabled(false);

        int[] secret = engine.getCurrentSecret();
        if (secret != null)
            for (int c = 0; c < 4; c++)
                solCells[c].setText(SkockoEngine.SYMBOLS[secret[c]]);

        if (tvStatus != null)
            tvStatus.setText("KRAJ  ·  TI: " + localScore + "   PROTIVNIK: " + oppScore);
        if (tvTimerHud != null) tvTimerHud.setText("✓");

        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        boolean friendly = getActivity() instanceof GameActivity
                && ((GameActivity) getActivity()).isFriendlyGame();
        if (fbUser != null && !friendly)
            UserRepository.getInstance().incrementSkocko(fbUser.getUid(), localMainRoundSolved);

        if (getActivity() instanceof GameActivity)
            ((GameActivity) getActivity()).addScores(localScore, oppScore);

        handler.postDelayed(() -> {
            if (getActivity() instanceof GameActivity)
                ((GameActivity) getActivity()).showKorakPoKorak();
        }, 2500);
    }

    /** Returns the Firebase phase key for the current local-active phase. */
    private String localPhaseKey() {
        if (localStartsFirst) {
            return (currentSkockoPhase == SkockoEngine.Phase.R1_LOCAL) ? "r1main_p1" : "r2bonus_p1";
        } else {
            return (currentSkockoPhase == SkockoEngine.Phase.R1_BONUS_OPP) ? "r1bonus_p2" : "r2main_p2";
        }
    }

    // ── user interaction ──────────────────────────────────────────────────────

    private void onSymbolPicked(int symIndex) {
        if (!localActive) return;
        if (bonusPhase) {
            // In bonus phase fill the stealing row
            for (int c = 0; c < 4; c++) {
                if (currentGuess[c] < 0) {
                    currentGuess[c] = symIndex;
                    oppCells[c].setText(SkockoEngine.SYMBOLS[symIndex]);
                    break;
                }
            }
        } else {
            // In main phase fill the active board row
            for (int c = 0; c < 4; c++) {
                if (currentGuess[c] < 0) {
                    currentGuess[c] = symIndex;
                    cells[activeRow][c].setText(SkockoEngine.SYMBOLS[symIndex]);
                    cells[activeRow][c].setBackgroundResource(R.drawable.bg_skocko_cell_filled);
                    break;
                }
            }
        }
        boolean full = true;
        for (int g : currentGuess) if (g < 0) { full = false; break; }
        btnConfirm.setEnabled(full);
    }

    private void onConfirmAttempt() {
        if (!localActive) return;
        for (int g : currentGuess) if (g < 0) return;
        setPickerEnabled(false);
        btnConfirm.setEnabled(false);
        engine.submitAttempt(currentGuess.clone());
    }

    // ── board helpers ─────────────────────────────────────────────────────────

    private void resetBoard() {
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 4; c++) {
                cells[r][c].setText("");
                cells[r][c].setBackgroundResource(R.drawable.bg_skocko_cell_empty);
                cells[r][c].setOnClickListener(null);
            }
            setPegs(r, 0, 0);
        }
        for (int c = 0; c < 4; c++) {
            oppCells[c].setText("");
            oppCells[c].setOnClickListener(null);
            solCells[c].setText("?");
        }
        setPegsArray(oppPegs, 0, 0);
    }

    private void wireActiveRowListeners() {
        if (activeRow >= 6) return;
        for (int c = 0; c < 4; c++) {
            final int col = c;
            cells[activeRow][col].setOnClickListener(v -> clearCellOnTap(col));
        }
    }

    private void fillRow(int row, int[] guess) {
        for (int c = 0; c < 4; c++) {
            cells[row][c].setText(SkockoEngine.SYMBOLS[guess[c]]);
            cells[row][c].setBackgroundResource(R.drawable.bg_skocko_cell_filled);
            cells[row][c].setOnClickListener(null);
        }
    }

    private void setPegs(int row, int hits, int nears) {
        setPegsArray(pegs[row], hits, nears);
    }

    private void setPegsArray(View[] pegRow, int hits, int nears) {
        int[] kinds = new int[4];
        int idx = 0;
        for (int i = 0; i < hits;         i++) kinds[idx++] = 2;
        for (int i = 0; i < nears;        i++) kinds[idx++] = 1;
        for (int i = 0; i < 4-hits-nears; i++) kinds[idx++] = 0;
        for (int p = 0; p < 4; p++) {
            int bg = (kinds[p] == 2) ? R.drawable.bg_peg_hit
                   : (kinds[p] == 1) ? R.drawable.bg_peg_near
                   : R.drawable.bg_peg_miss;
            pegRow[p].setBackgroundResource(bg);
        }
    }

    private void resetGuessState() {
        for (int c = 0; c < 4; c++) currentGuess[c] = -1;
        btnConfirm.setEnabled(false);
    }

    private void clearCurrentGuess() {
        resetGuessState();
        if (localActive && activeRow < 6) {
            for (int c = 0; c < 4; c++) {
                cells[activeRow][c].setText("");
                cells[activeRow][c].setBackgroundResource(R.drawable.bg_skocko_cell_empty);
            }
            wireActiveRowListeners();
        }
    }

    private void clearCellOnTap(int col) {
        if (!localActive || bonusPhase || activeRow >= 6) return;
        currentGuess[col] = -1;
        cells[activeRow][col].setText("");
        cells[activeRow][col].setBackgroundResource(R.drawable.bg_skocko_cell_empty);
        btnConfirm.setEnabled(false);
    }

    private void clearBonusCellOnTap(int col) {
        if (!localActive || !bonusPhase) return;
        currentGuess[col] = -1;
        oppCells[col].setText("");
        btnConfirm.setEnabled(false);
    }

    // ── timer ─────────────────────────────────────────────────────────────────

    private void startTimer(int secs) {
        cancelTimer();
        updateTimer(secs);
        roundTimer = new CountDownTimer(secs * 1000L, 1000) {
            @Override public void onTick(long ms) { updateTimer((int)(ms / 1000)); }
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

    private int timerSecsFor(SkockoEngine.Phase phase) {
        return (phase == SkockoEngine.Phase.R1_BONUS_OPP
             || phase == SkockoEngine.Phase.R2_BONUS_LOCAL)
                ? BONUS_SECS : ROUND_SECS;
    }

    // ── status / banner ───────────────────────────────────────────────────────

    private void updateRoundBanner(int round, SkockoEngine.Phase phase) {
        if (tvRound == null) return;
        tvRound.setText("🦜 J. AMERIKA · SKOČKO  ·  RUNDA " + round + "/2");
    }

    private void updateStatus(SkockoEngine.Phase phase) {
        if (tvStatus == null) return;
        switch (phase) {
            case R1_LOCAL:       tvStatus.setText("RUNDA 1  ·  TI POGAĐAŠ");             break;
            case R1_BONUS_OPP:   tvStatus.setText("BONUS 10s  ·  PROTIVNIK KRADE");       break;
            case R2_OPP:         tvStatus.setText("RUNDA 2  ·  PROTIVNIK POGAĐA");        break;
            case R2_BONUS_LOCAL: tvStatus.setText("BONUS 10s  ·  TI KRADEŠ  (1 šansa)"); break;
            default:             tvStatus.setText("KRAJ");                                 break;
        }
    }

    private void setPickerEnabled(boolean enabled) {
        for (TextView t : picker) {
            t.setEnabled(enabled);
            t.setAlpha(enabled ? 1f : 0.4f);
        }
    }
}
