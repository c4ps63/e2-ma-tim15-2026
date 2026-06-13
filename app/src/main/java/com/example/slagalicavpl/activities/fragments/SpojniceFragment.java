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

    private TextView tvStatus;
    private TextView tvTimerHud;
    private TextView tvP1Score;
    private TextView tvP2Score;
    private Button   btnConfirm;

    private SpojniceEngine       engine;
    private CountDownTimer        roundTimer;
    private final Handler         handler = new Handler(Looper.getMainLooper());
    private int                   selectedLeft = -1;
    private SpojniceEngine.Phase  currentPhase;

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

        engine = new SpojniceEngine(
                repo.getRound1Pairs(),
                repo.getRound2Pairs(),
                new LocalSpojniceSync(),
                this);

        engine.startGame();
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
        boolean ok = engine.connectPair(selectedLeft, row);
        if (!ok) {
            tvStatus.setText("POGREŠNO!");
            handler.postDelayed(this::updateStatusText, 800);
        }
        clearSelection();
    }

    @Override
    public void onRoundStarted(int round, SpojniceEngine.Phase phase,
                               List<ConnectPair> pairs, int[] rightSlots) {
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
        }
        selectedLeft = -1;
        btnConfirm.setEnabled(localActive);

        updateStatusText();
        startPhaseTimer();
    }

    @Override
    public void onPhaseChanged(SpojniceEngine.Phase phase) {
        currentPhase = phase;
        boolean localActive = isLocalActive();

        for (int i = 0; i < 5; i++) {
            if (!connectedDisplay[i]) {
                leftBtns[i].setEnabled(localActive);
                rightBtns[i].setEnabled(localActive);
            }
        }
        selectedLeft = -1;
        clearLeftHighlights();
        btnConfirm.setEnabled(localActive);

        updateStatusText();
        startPhaseTimer();
    }

    @Override
    public void onPairConnected(int leftRow, int rightRow, boolean byLocal) {
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
            case R1_LOCAL: label = "RUNDA 1 · TI IGRAŠ";           break;
            case R1_OPP:   label = "RUNDA 1 · PROTIVNIK KRADE";     break;
            case R2_OPP:   label = "RUNDA 2 · PROTIVNIK IGRA";      break;
            case R2_LOCAL: label = "RUNDA 2 · TI KRADEŠ";           break;
            default:       label = "KRAJ IGRE";                      break;
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
        return currentPhase == SpojniceEngine.Phase.R1_LOCAL
            || currentPhase == SpojniceEngine.Phase.R2_LOCAL;
    }
}
