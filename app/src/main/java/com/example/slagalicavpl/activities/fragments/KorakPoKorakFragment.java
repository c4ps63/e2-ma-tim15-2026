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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.activities.GameActivity;

/**
 * KORAK PO KORAK — game logic
 *
 * Rules (spec §5):
 *  - 2 rounds, max 70 s each
 *  - Up to 7 clues, one revealed every 10 s
 *  - Scoring: step 1 → 20 pts, each next step −2 pts  (8 pts at step 7)
 *  - If active player fails all 7 clues → opponent gets 10 s steal for 5 pts
 *  - HUD timer: 10 s per step (red at ≤ 3 s), 5 s steal, ⏱ when idle
 *
 * Hardcoded puzzle (replace with DB later):
 *   Answer: MESEC
 *   Clues:  7 clues from hardest → easiest
 */
public class KorakPoKorakFragment extends Fragment {

    /* ══════════ Hardcoded game data ════════════════════════════════════ */

    private static final String   ANSWER      = "MESEC";
    private static final String[] CLUES       = {
            "UTIČE NA PLIME I OSEKE",   // step 1 — 20 pts
            "ORBITA OKO ZEMLJE",         // step 2 — 18 pts
            "NEIL ARMSTRONG",            // step 3 — 16 pts
            "SATELIT ZEMLJE",            // step 4 — 14 pts
            "SUNČEV SISTEM",             // step 5 — 12 pts
            "NOĆNO NEBO",                // step 6 — 10 pts
            "LUNA"                       // step 7 — 8 pts
    };

    /* ══════════ Constants ══════════════════════════════════════════════ */

    private static final int MAX_STEPS    = 7;
    private static final int BASE_PTS     = 20;   // points at step 1
    private static final int STEP_DROP    = 2;    // lost per step
    private static final int STEAL_PTS   = 5;    // opponent steal reward
    private static final int STEP_SECS   = 10;   // seconds per step
    private static final int STEAL_SECS  = 10;   // seconds for opponent steal
    private static final int WARN_SECS   = 3;    // turn red in HUD
    private static final int TOTAL_ROUNDS = 2;

    private static final String CLOCK_ICON = "⏱";
    private static final String LOCKED_TEXT = "—  ZAKLJUČAN  —";

    /* ══════════ Views ══════════════════════════════════════════════════ */

    private TextView     tvRound;
    private EditText     etAnswer;
    private Button       btnConfirm;
    private TextView     tvTimerHud;

    // step rows and their clue/points TextViews (index 0 = step 1)
    private final LinearLayout[] stepRows  = new LinearLayout[MAX_STEPS];
    private final TextView[]     tvClues   = new TextView[MAX_STEPS];
    private final TextView[]     tvPoints  = new TextView[MAX_STEPS];

    /* ══════════ Game state ═════════════════════════════════════════════ */

    private enum Phase { PLAYER_TURN, STEAL, BETWEEN_ROUNDS, DONE }
    private Phase phase;

    private int currentRound = 1;   // 1 or 2
    private int currentStep  = 1;   // 1..7, which step is currently active
    private int p1Points     = 0;
    private int p2Points     = 0;

    /** Player index of the currently active/answering player (1 or 2). */
    private int activePlayer = 1;

    /* ══════════ Timing ═════════════════════════════════════════════════ */

    private final Handler       handler    = new Handler(Looper.getMainLooper());
    private       CountDownTimer stepTimer;

    /* ══════════════════════════════════════════════════════════════════
     *  Fragment lifecycle
     * ══════════════════════════════════════════════════════════════════ */

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_korak_po_korak, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvRound    = view.findViewById(R.id.tvRound);
        etAnswer   = view.findViewById(R.id.etAnswer);
        btnConfirm = view.findViewById(R.id.btnConfirm);
        tvTimerHud = view.findViewById(R.id.timer_value);

        int[] rowIds   = { R.id.stepRow1, R.id.stepRow2, R.id.stepRow3,
                           R.id.stepRow4, R.id.stepRow5, R.id.stepRow6, R.id.stepRow7 };
        int[] clueIds  = { R.id.tvClue1,  R.id.tvClue2,  R.id.tvClue3,
                           R.id.tvClue4,  R.id.tvClue5,  R.id.tvClue6,  R.id.tvClue7 };
        int[] ptsIds   = { R.id.tvPoints1, R.id.tvPoints2, R.id.tvPoints3,
                           R.id.tvPoints4, R.id.tvPoints5, R.id.tvPoints6, R.id.tvPoints7 };

        for (int i = 0; i < MAX_STEPS; i++) {
            stepRows[i] = view.findViewById(rowIds[i]);
            tvClues[i]  = view.findViewById(clueIds[i]);
            tvPoints[i] = view.findViewById(ptsIds[i]);
        }

        btnConfirm.setOnClickListener(v -> onSubmitAnswer());

        setHudClock();
        startRound(1, 1);   // round 1, player 1 starts
    }

    @Override
    public void onPause() {
        super.onPause();
        cancelTimer();
    }

    /* ══════════════════════════════════════════════════════════════════
     *  Round / phase management
     * ══════════════════════════════════════════════════════════════════ */

    private void startRound(int round, int player) {
        currentRound  = round;
        activePlayer  = player;
        currentStep   = 1;
        cancelTimer();

        // Reset all steps to locked
        for (int i = 0; i < MAX_STEPS; i++) lockStep(i);

        etAnswer.setText("");
        etAnswer.setEnabled(true);
        btnConfirm.setEnabled(true);

        updateRoundHeader();
        enterPlayerTurn();
    }

    /** Active player is guessing — reveal step by step every 10 s. */
    private void enterPlayerTurn() {
        phase = Phase.PLAYER_TURN;
        revealStep(currentStep);                      // reveal step N immediately
        startStepTimer(STEP_SECS, this::onStepExpired);
    }

    /** Called when a 10-second step window expires without a correct guess. */
    private void onStepExpired() {
        if (phase != Phase.PLAYER_TURN) return;

        if (currentStep < MAX_STEPS) {
            currentStep++;
            updateRoundHeader();
            revealStep(currentStep);
            startStepTimer(STEP_SECS, this::onStepExpired);
        } else {
            // All 7 steps shown — opponent gets a steal chance
            enterSteal();
        }
    }

    /** Opponent gets 10 s to steal 5 pts. */
    private void enterSteal() {
        phase = Phase.STEAL;
        int opponent = (activePlayer == 1) ? 2 : 1;

        tvRound.setText("ŠANSA ZA KRAĐU · IGRAČ " + opponent + " · 5 BODOVA");
        etAnswer.setText("");
        etAnswer.setEnabled(true);
        btnConfirm.setEnabled(true);

        startStepTimer(STEAL_SECS, this::onStealExpired);
    }

    private void onStealExpired() {
        // Opponent didn't guess in time → end this round
        endRound(false, false);
    }

    /** Transition between rounds or finish the game. */
    private void endRound(boolean activeGuessed, boolean opponentStole) {
        cancelTimer();
        setHudClock();
        etAnswer.setEnabled(false);
        btnConfirm.setEnabled(false);

        if (currentRound < TOTAL_ROUNDS) {
            phase = Phase.BETWEEN_ROUNDS;
            // Brief pause then start round 2 with the other player
            int nextPlayer = (activePlayer == 1) ? 2 : 1;
            tvRound.setText("RUNDA " + (currentRound + 1) + "/2 · IGRAČ " + nextPlayer + " NA POTEZU");
            handler.postDelayed(() -> startRound(currentRound + 1, nextPlayer), 2500);
        } else {
            phase = Phase.DONE;
            tvRound.setText("KRAJ  ·  P1: " + p1Points + " pts  P2: " + p2Points + " pts");
            // Prelaz na sledeću igru — Moj broj
            handler.postDelayed(() -> {
                if (getActivity() instanceof GameActivity) {
                    ((GameActivity) getActivity()).showMojBroj();
                }
            }, 2000);
        }
    }

    /* ══════════════════════════════════════════════════════════════════
     *  Answer submission
     * ══════════════════════════════════════════════════════════════════ */

    private void onSubmitAnswer() {
        String input = etAnswer.getText().toString().trim().toUpperCase();
        if (input.isEmpty()) return;

        boolean correct = input.equals(ANSWER.toUpperCase());

        if (phase == Phase.PLAYER_TURN) {
            if (correct) {
                int pts = BASE_PTS - STEP_DROP * (currentStep - 1);
                awardPoints(activePlayer, pts);
                endRound(true, false);
            } else {
                // Wrong answer — just clear field, timer keeps running
                etAnswer.setText("");
            }

        } else if (phase == Phase.STEAL) {
            int opponent = (activePlayer == 1) ? 2 : 1;
            if (correct) {
                awardPoints(opponent, STEAL_PTS);
            }
            endRound(false, correct);
        }
    }

    /* ══════════════════════════════════════════════════════════════════
     *  Step UI
     * ══════════════════════════════════════════════════════════════════ */

    /** Reveals step at 1-based index: shows clue, open background. */
    private void revealStep(int stepNumber) {
        int i = stepNumber - 1; // 0-based index
        stepRows[i].setBackgroundResource(R.drawable.bg_step_open);
        tvClues[i].setText(CLUES[i]);
        tvClues[i].setTextColor(Color.parseColor("#06112A")); // ink_dark
        tvPoints[i].setTextColor(Color.parseColor("#06112A"));
    }

    /** Locks a step (locked / hidden appearance). */
    private void lockStep(int i) {
        stepRows[i].setBackgroundResource(R.drawable.bg_step_locked);
        tvClues[i].setText(LOCKED_TEXT);
        tvClues[i].setTextColor(0x80000000);
        tvPoints[i].setTextColor(0x80000000);
    }

    private void updateRoundHeader() {
        int maxPts = BASE_PTS - STEP_DROP * (currentStep - 1);
        tvRound.setText("RUNDA " + currentRound + "/2  ·  "
                + "KORAK " + currentStep + "/" + MAX_STEPS + "  ·  "
                + "IGRAČ " + activePlayer + "  ·  MAX " + maxPts + " BODOVA");
    }

    /* ══════════════════════════════════════════════════════════════════
     *  Scoring
     * ══════════════════════════════════════════════════════════════════ */

    private void awardPoints(int player, int pts) {
        if (player == 1) p1Points += pts;
        else             p2Points += pts;

        // Update HUD score TextViews
        if (getView() != null) {
            TextView tv = getView().findViewById(player == 1 ? R.id.p1_score : R.id.p2_score);
            if (tv != null) tv.setText(String.valueOf(player == 1 ? p1Points : p2Points));
        }
    }

    /* ══════════════════════════════════════════════════════════════════
     *  HUD timer
     * ══════════════════════════════════════════════════════════════════ */

    private void startStepTimer(int seconds, Runnable onFinish) {
        cancelTimer();
        setHudNumber(seconds, false);

        stepTimer = new CountDownTimer(seconds * 1000L, 1000) {
            @Override public void onTick(long msLeft) {
                int s = (int) (msLeft / 1000);
                setHudNumber(s, s <= WARN_SECS);
            }
            @Override public void onFinish() {
                setHudNumber(0, true);
                onFinish.run();
            }
        }.start();
    }

    private void cancelTimer() {
        if (stepTimer != null) { stepTimer.cancel(); stepTimer = null; }
        handler.removeCallbacksAndMessages(null);
    }

    private void setHudNumber(int value, boolean red) {
        if (tvTimerHud == null) return;
        tvTimerHud.setText(String.valueOf(value));
        tvTimerHud.setTextColor(red ? Color.RED : Color.parseColor("#102341"));
        tvTimerHud.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20);
    }

    private void setHudClock() {
        if (tvTimerHud == null) return;
        tvTimerHud.setText(CLOCK_ICON);
        tvTimerHud.setTextColor(Color.parseColor("#102341"));
        tvTimerHud.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18);
    }
}
