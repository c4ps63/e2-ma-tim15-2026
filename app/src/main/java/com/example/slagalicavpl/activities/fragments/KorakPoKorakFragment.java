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
import com.example.slagalicavpl.game.KorakPoKorakEngine;
import com.example.slagalicavpl.model.KorakPuzzle;
import com.example.slagalicavpl.multiplayer.FirebaseKorakSync;
import com.example.slagalicavpl.repository.KorakRepository;

public class KorakPoKorakFragment extends Fragment
        implements KorakPoKorakEngine.Listener {

    private TextView  tvRound;
    private EditText  etAnswer;
    private Button    btnConfirm;
    private TextView  tvTimerHud;

    private static final int MAX_STEPS = KorakPoKorakEngine.MAX_STEPS;
    private final LinearLayout[] stepRows = new LinearLayout[MAX_STEPS];
    private final TextView[]     tvClues  = new TextView[MAX_STEPS];
    private final TextView[]     tvPoints = new TextView[MAX_STEPS];

    private KorakPoKorakEngine engine;
    private KorakPuzzle        puzzle;

    // Multiplayer state
    private boolean            multiplayer  = false;
    private String             myRole       = "p1";
    private FirebaseKorakSync  korakSync;
    private int                currentRound = 1;
    private boolean            puzzleInitialized = false;

    private static final int WARN_SECS     = 3;
    private static final String CLOCK_ICON  = "⏱";
    private static final String LOCKED_TEXT = "—  ZAKLJUČAN  —";

    private final Handler       handler     = new Handler(Looper.getMainLooper());
    private       CountDownTimer activeTimer;

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

        int[] rowIds  = { R.id.stepRow1, R.id.stepRow2, R.id.stepRow3,
                          R.id.stepRow4, R.id.stepRow5, R.id.stepRow6, R.id.stepRow7 };
        int[] clueIds = { R.id.tvClue1,  R.id.tvClue2,  R.id.tvClue3,
                          R.id.tvClue4,  R.id.tvClue5,  R.id.tvClue6,  R.id.tvClue7 };
        int[] ptsIds  = { R.id.tvPoints1, R.id.tvPoints2, R.id.tvPoints3,
                          R.id.tvPoints4, R.id.tvPoints5, R.id.tvPoints6, R.id.tvPoints7 };
        for (int i = 0; i < MAX_STEPS; i++) {
            stepRows[i] = view.findViewById(rowIds[i]);
            tvClues[i]  = view.findViewById(clueIds[i]);
            tvPoints[i] = view.findViewById(ptsIds[i]);
        }

        btnConfirm.setOnClickListener(v -> {
            String input = etAnswer.getText().toString().trim();
            if (multiplayer && !isActivePlayer(currentRound)) {
                // Passive player submitting (steal phase)
                handlePassiveStealAnswer(input);
            } else {
                engine.submitAnswer(input);
            }
        });

        TextView hudP1 = view.findViewById(R.id.p1_score);
        TextView hudP2 = view.findViewById(R.id.p2_score);
        if (getActivity() instanceof GameActivity) {
            GameActivity ga = (GameActivity) getActivity();
            multiplayer = ga.isMultiplayer();
            myRole      = ga.getMyRole();
            if (multiplayer && ga.getRoomRef() != null) {
                korakSync = new FirebaseKorakSync(ga.getRoomRef());
            }
            if (hudP1 != null) hudP1.setText(String.valueOf(ga.getP1Total()));
            if (hudP2 != null) hudP2.setText(String.valueOf(ga.getP2Total()));
        }

        setHudClock();
        startRound1();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (korakSync != null) korakSync.cancelListener();
    }

    @Override
    public void onPause() {
        super.onPause();
        onCancelTimers();
    }

    // ── Round management ──────────────────────────────────────────────────────

    private void startRound1() {
        currentRound = 1;
        startRound(1);
    }

    private void startRound(int round) {
        currentRound = round;
        puzzleInitialized = false;

        if (!multiplayer || isActivePlayer(round)) {
            // I am the active player for this round
            int puzzleIdx = KorakRepository.getInstance().getRandomIndex();
            puzzle = KorakRepository.getInstance().getPuzzleByIndex(puzzleIdx);

            if (multiplayer && korakSync != null) {
                korakSync.writePuzzleIdx(round, puzzleIdx);
            }

            createEngine();
            engine.startRound(round, roleToPlayer(myRole, round));
        } else {
            // I am the passive player for this round — wait for puzzle from Firebase
            listenPassive(round);
        }
    }

    private void createEngine() {
        engine = new KorakPoKorakEngine(puzzle, this);
    }

    private boolean isActivePlayer(int round) {
        return ("p1".equals(myRole) && round == 1) || ("p2".equals(myRole) && round == 2);
    }

    private int roleToPlayer(String role, int round) {
        // In round 1 p1 is active (player 1), in round 2 p2 is active (player 2)
        if (isActivePlayer(round)) {
            return "p1".equals(role) ? 1 : 2;
        } else {
            return "p1".equals(role) ? 2 : 1;
        }
    }

    // ── Passive player listens to Firebase ───────────────────────────────────

    private int passiveP1pts = 0;
    private int passiveP2pts = 0;
    private boolean passiveStealEnabled = false;

    private void listenPassive(int round) {
        if (korakSync == null) return;

        // Show waiting header
        if (tvRound != null) tvRound.setText("ČEKAM PROTIVNIKA...");
        etAnswer.setEnabled(false);
        btnConfirm.setEnabled(false);

        korakSync.listenPassive(round, new FirebaseKorakSync.PassiveListener() {
            @Override
            public void onPuzzleSelected(int puzzleIdx) {
                if (puzzleInitialized) return;
                puzzleInitialized = true;
                puzzle = KorakRepository.getInstance().getPuzzleByIndex(puzzleIdx);

                // Reset UI for new round
                handler.post(() -> {
                    for (int i = 0; i < MAX_STEPS; i++) lockStep(i);
                    etAnswer.setText("");
                    etAnswer.setBackgroundResource(R.drawable.bg_expression_display);
                    if (tvRound != null) {
                        tvRound.setText("RUNDA " + round + "/2  ·  GLEDAM  ·  "
                                + (isActivePlayer(round) ? "JA" : "PROTIVNIK") + " NA POTEZU");
                    }
                });
            }

            @Override
            public void onStepAdvanced(int stepIndex) {
                if (puzzle == null) return;
                handler.post(() -> {
                    // Reveal all steps up to and including stepIndex
                    for (int i = 0; i <= stepIndex && i < MAX_STEPS; i++) {
                        int pts = KorakPoKorakEngine.BASE_PTS - KorakPoKorakEngine.STEP_DROP * i;
                        revealStep(i, puzzle.clues[i], pts);
                    }
                });
            }

            @Override
            public void onStealPhase() {
                handler.post(() -> {
                    passiveStealEnabled = true;
                    etAnswer.setEnabled(true);
                    btnConfirm.setEnabled(true);
                    etAnswer.setText("");
                    if (tvRound != null) {
                        tvRound.setText("ŠANSA ZA KRAĐU · "
                                + KorakPoKorakEngine.STEAL_PTS + " BODOVA");
                    }
                    startCountdown(KorakPoKorakEngine.STEAL_SECS, () -> {
                        if (passiveStealEnabled) submitPassiveSteal("");
                    });
                });
            }

            @Override
            public void onRoundEnd(int p1pts, int p2pts) {
                passiveP1pts = p1pts;
                passiveP2pts = p2pts;
                handler.post(() -> {
                    onCancelTimers();
                    passiveStealEnabled = false;
                    etAnswer.setEnabled(false);
                    btnConfirm.setEnabled(false);
                    updatePassiveHud(p1pts, p2pts);

                    if (round < KorakPoKorakEngine.TOTAL_ROUNDS) {
                        if (tvRound != null) tvRound.setText("RUNDA " + (round + 1) + "/2  ·  JA NA POTEZU");
                        korakSync.cancelListener();
                        handler.postDelayed(() -> startRound(round + 1), 2500);
                    }
                });
            }

            @Override
            public void onGameOver(int p1pts, int p2pts) {
                handler.post(() -> {
                    onCancelTimers();
                    passiveStealEnabled = false;
                    korakSync.cancelListener();
                    updatePassiveHud(p1pts, p2pts);
                    if (tvRound != null)
                        tvRound.setText("KRAJ  ·  P1: " + p1pts + " pts  P2: " + p2pts + " pts");

                    if (getActivity() instanceof GameActivity)
                        ((GameActivity) getActivity()).addScores(p1pts, p2pts);

                    handler.postDelayed(() -> {
                        if (getActivity() instanceof GameActivity)
                            ((GameActivity) getActivity()).showMojBroj();
                    }, 2000);
                });
            }
        });
    }

    private void handlePassiveStealAnswer(String input) {
        if (!passiveStealEnabled || puzzle == null) return;
        submitPassiveSteal(input);
    }

    private void submitPassiveSteal(String input) {
        passiveStealEnabled = false;
        onCancelTimers();
        boolean correct = input != null && input.trim().equalsIgnoreCase(puzzle.answer);
        etAnswer.setBackgroundResource(correct
                ? R.drawable.bg_expression_correct
                : R.drawable.bg_expression_wrong);
        etAnswer.setEnabled(false);
        btnConfirm.setEnabled(false);
        // Scores are authoritative on active player's side; passive player just shows feedback
    }

    private void updatePassiveHud(int p1pts, int p2pts) {
        if (getView() == null || !(getActivity() instanceof GameActivity)) return;
        GameActivity ga = (GameActivity) getActivity();
        TextView s1 = getView().findViewById(R.id.p1_score);
        TextView s2 = getView().findViewById(R.id.p2_score);
        if (s1 != null) s1.setText(String.valueOf(ga.getP1Total() + p1pts));
        if (s2 != null) s2.setText(String.valueOf(ga.getP2Total() + p2pts));
    }

    // ── KorakPoKorakEngine.Listener (active player) ───────────────────────────

    @Override
    public void onRoundStarted(int round, int activePlayer) {
        for (int i = 0; i < MAX_STEPS; i++) lockStep(i);
        etAnswer.setText("");
        etAnswer.setBackgroundResource(R.drawable.bg_expression_display);
    }

    @Override
    public void onStepRevealed(int stepIndex, String clue, int maxPoints) {
        revealStep(stepIndex, clue, maxPoints);
        if (multiplayer && korakSync != null) {
            korakSync.writeStep(stepIndex);
        }
    }

    @Override
    public void onStartStepTimer() {
        startCountdown(KorakPoKorakEngine.STEP_SECS, () -> engine.onStepTimerExpired());
    }

    @Override
    public void onStartStealTimer(int stealPlayer) {
        startCountdown(KorakPoKorakEngine.STEAL_SECS, () -> engine.onStealTimerExpired());
        if (multiplayer && korakSync != null) korakSync.writeStealPhase();
    }

    @Override
    public void onCancelTimers() {
        if (activeTimer != null) { activeTimer.cancel(); activeTimer = null; }
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onHeaderChanged(String text) {
        if (tvRound != null) tvRound.setText(text);
    }

    @Override
    public void onInputCleared() {
        etAnswer.setText("");
    }

    @Override
    public void onInputEnabled(boolean enabled) {
        etAnswer.setEnabled(enabled);
        btnConfirm.setEnabled(enabled);
    }

    @Override
    public void onAnswerResult(boolean correct, int pts) {
        etAnswer.setBackgroundResource(correct
                ? R.drawable.bg_expression_correct
                : R.drawable.bg_expression_wrong);
        if (!correct) {
            handler.postDelayed(() -> {
                if (getView() != null)
                    etAnswer.setBackgroundResource(R.drawable.bg_expression_display);
            }, 350);
        }
    }

    @Override
    public void onRoundTransition(int nextRound, int nextPlayer) {
        if (multiplayer && korakSync != null) {
            korakSync.writeRoundEnd(engine.getP1Points(), engine.getP2Points());
        }
        handler.postDelayed(() -> {
            if (multiplayer && korakSync != null) korakSync.cancelListener();
            startRound(nextRound);
        }, 2500);
    }

    @Override
    public void onGameOver(int p1Score, int p2Score) {
        if (multiplayer && korakSync != null) {
            korakSync.writeGameOver(p1Score, p2Score);
            korakSync.cancelListener();
        }
        setHudClock();
        if (getActivity() instanceof GameActivity)
            ((GameActivity) getActivity()).addScores(p1Score, p2Score);
        handler.postDelayed(() -> {
            if (getActivity() instanceof GameActivity)
                ((GameActivity) getActivity()).showMojBroj();
        }, 2000);
    }

    @Override
    public void onScoreChanged(int p1Score, int p2Score) {
        if (getView() == null) return;
        TextView s1 = getView().findViewById(R.id.p1_score);
        TextView s2 = getView().findViewById(R.id.p2_score);
        if (getActivity() instanceof GameActivity) {
            GameActivity ga = (GameActivity) getActivity();
            if (s1 != null) s1.setText(String.valueOf(ga.getP1Total() + p1Score));
            if (s2 != null) s2.setText(String.valueOf(ga.getP2Total() + p2Score));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void revealStep(int i, String clue, int maxPoints) {
        stepRows[i].setBackgroundResource(R.drawable.bg_step_open);
        tvClues[i].setText(clue);
        tvClues[i].setTextColor(Color.parseColor("#06112A"));
        tvPoints[i].setText(maxPoints + " pts");
        tvPoints[i].setTextColor(Color.parseColor("#06112A"));
    }

    private void startCountdown(int seconds, Runnable onFinish) {
        if (activeTimer != null) { activeTimer.cancel(); activeTimer = null; }
        setHudNumber(seconds, false);

        activeTimer = new CountDownTimer(seconds * 1000L, 1000) {
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

    private void lockStep(int i) {
        stepRows[i].setBackgroundResource(R.drawable.bg_step_locked);
        tvClues[i].setText(LOCKED_TEXT);
        tvClues[i].setTextColor(0x80000000);
        tvPoints[i].setTextColor(0x80000000);
    }
}
