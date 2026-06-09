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
            engine.submitAnswer(input);
        });

        KorakPuzzle puzzle = KorakRepository.getInstance().getRandomPuzzle();
        engine = new KorakPoKorakEngine(puzzle, this);

        setHudClock();
        engine.startRound(1, 1);
    }

    @Override
    public void onPause() {
        super.onPause();
        onCancelTimers();
    }

    @Override
    public void onRoundStarted(int round, int activePlayer) {
        for (int i = 0; i < MAX_STEPS; i++) lockStep(i);
        etAnswer.setText("");
    }

    @Override
    public void onStepRevealed(int stepIndex, String clue, int maxPoints) {
        stepRows[stepIndex].setBackgroundResource(R.drawable.bg_step_open);
        tvClues[stepIndex].setText(clue);
        tvClues[stepIndex].setTextColor(Color.parseColor("#06112A"));
        tvPoints[stepIndex].setText(maxPoints + " pts");
        tvPoints[stepIndex].setTextColor(Color.parseColor("#06112A"));
    }

    @Override
    public void onStartStepTimer() {
        startCountdown(KorakPoKorakEngine.STEP_SECS, () -> engine.onStepTimerExpired());
    }

    @Override
    public void onStartStealTimer(int stealPlayer) {
        startCountdown(KorakPoKorakEngine.STEAL_SECS, () -> engine.onStealTimerExpired());
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
    public void onRoundTransition(int nextRound, int nextPlayer) {
        handler.postDelayed(() -> engine.startRound(nextRound, nextPlayer), 2500);
    }

    @Override
    public void onGameOver(int p1Score, int p2Score) {
        setHudClock();
        handler.postDelayed(() -> {
            if (getActivity() instanceof GameActivity) {
                ((GameActivity) getActivity()).showMojBroj();
            }
        }, 2000);
    }

    @Override
    public void onScoreChanged(int p1Score, int p2Score) {
        if (getView() == null) return;
        TextView s1 = getView().findViewById(R.id.p1_score);
        TextView s2 = getView().findViewById(R.id.p2_score);
        if (s1 != null) s1.setText(String.valueOf(p1Score));
        if (s2 != null) s2.setText(String.valueOf(p2Score));
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
