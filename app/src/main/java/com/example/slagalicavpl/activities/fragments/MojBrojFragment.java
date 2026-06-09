package com.example.slagalicavpl.activities.fragments;

import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.game.MojBrojEngine;
import com.example.slagalicavpl.model.MojBrojPuzzle;
import com.example.slagalicavpl.repository.MojBrojRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MojBrojFragment extends Fragment implements SensorEventListener {

    private TextView tvTarget;
    private EditText tvExpression;
    private Button   btnConfirm;
    private Button   btnReset;
    private Button   btnBackspace;
    private TextView tvShakeHint;
    private TextView tvPlayerResult;
    private TextView tvOpponentResult;
    private TextView tvTimerHud;

    private final Button[] btnNumbers = new Button[6];
    private final Button[] btnOps     = new Button[6];

    private static class Token {
        final String disp, eval;
        final int tileIdx;
        Token(String d, String e, int t) { disp = d; eval = e; tileIdx = t; }
    }
    private final List<Token>   tokens   = new ArrayList<>();
    private final StringBuilder exprEval = new StringBuilder();
    private final StringBuilder exprDisp = new StringBuilder();

    private enum Phase { SPINNING_TARGET, SPINNING_NUMBERS, BUILDING, DONE }
    private Phase phase = Phase.SPINNING_TARGET;

    private MojBrojPuzzle puzzle;
    private final boolean[] tileUsed = new boolean[6];

    private final Handler        handler   = new Handler(Looper.getMainLooper());
    private       CountDownTimer spinTimer;
    private       CountDownTimer roundTimer;

    private static final int    SPIN_MS    = 75;
    private static final int    SPIN_SECS  = 5;
    private static final int    ROUND_SECS = 60;
    private static final int    WARN_SECS  = 10;
    private static final String CLOCK_ICON = "⏱";

    private final Random rng = new Random();

    private SensorManager sensorManager;
    private Sensor        accelerometer;
    private long          lastShakeTime = 0;
    private static final float SHAKE_THRESHOLD_G = 2.5f;
    private static final int   SHAKE_COOLDOWN_MS  = 600;

    private static final String[] OP_DISP = {"+", "−", "×", "÷", "(", ")"};
    private static final String[] OP_EVAL = {"+", "-", "*", "/", "(", ")"};

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_moj_broj, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvTarget         = view.findViewById(R.id.tvTarget);
        tvExpression     = view.findViewById(R.id.tvExpression);
        btnConfirm       = view.findViewById(R.id.btnConfirm);
        btnReset         = view.findViewById(R.id.btnReset);
        btnBackspace     = view.findViewById(R.id.btnBackspace);
        tvShakeHint      = view.findViewById(R.id.tvShakeHint);
        tvPlayerResult   = view.findViewById(R.id.tvPlayerResult);
        tvOpponentResult = view.findViewById(R.id.tvOpponentResult);
        tvTimerHud       = view.findViewById(R.id.timer_value);

        int[] numIds = { R.id.btnNum0, R.id.btnNum1, R.id.btnNum2,
                         R.id.btnNum3, R.id.btnNum4, R.id.btnNum5 };
        int[] opIds  = { R.id.btnOpAdd, R.id.btnOpSub, R.id.btnOpMul,
                         R.id.btnOpDiv, R.id.btnOpOpen, R.id.btnOpClose };
        for (int i = 0; i < 6; i++) {
            btnNumbers[i] = view.findViewById(numIds[i]);
            btnOps[i]     = view.findViewById(opIds[i]);
        }

        for (int i = 0; i < 6; i++) {
            final String d = OP_DISP[i], e = OP_EVAL[i];
            btnOps[i].setOnClickListener(v -> appendToken(d, e, -1));
        }
        for (int i = 0; i < 6; i++) {
            final int idx = i;
            btnNumbers[i].setOnClickListener(v -> onNumberClick(idx));
        }

        btnBackspace.setOnClickListener(v -> backspace());
        btnReset.setOnClickListener(v -> clearExpression());
        btnConfirm.setOnClickListener(v -> onConfirmOrStop());

        if (getContext() != null) {
            sensorManager = (SensorManager)
                    getContext().getSystemService(Context.SENSOR_SERVICE);
            if (sensorManager != null)
                accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        setHudClock();
        enterPhase(Phase.SPINNING_TARGET);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (sensorManager != null && accelerometer != null)
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacksAndMessages(null);
        cancelAllTimers();
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }

    private void enterPhase(Phase next) {
        phase = next;
        handler.removeCallbacksAndMessages(null);
        cancelAllTimers();
        switch (phase) {
            case SPINNING_TARGET:  setupSpinTarget();  break;
            case SPINNING_NUMBERS: setupSpinNumbers(); break;
            case BUILDING:         setupBuilding();    break;
            case DONE:             setHudClock();      break;
        }
    }

    private void setupSpinTarget() {
        btnConfirm.setText("STOP");
        btnConfirm.setEnabled(true);
        tvShakeHint.setVisibility(View.VISIBLE);
        tvTarget.setText("?");
        setTilesText("?");
        setTilesEnabled(false);
        setOpsEnabled(false);
        setEditEnabled(false);

        startSpinAnimation(() -> {
            if (phase == Phase.SPINNING_TARGET)
                tvTarget.setText(String.valueOf(rng.nextInt(999) + 1));
        });
        startSpinCountdown(() -> { if (phase == Phase.SPINNING_TARGET) lockTarget(); });
    }

    private void lockTarget() {
        puzzle = MojBrojRepository.getInstance().generatePuzzle();
        tvTarget.setText(String.valueOf(puzzle.target));
        enterPhase(Phase.SPINNING_NUMBERS);
    }

    private void setupSpinNumbers() {
        btnConfirm.setText("STOP");
        btnConfirm.setEnabled(true);
        tvShakeHint.setVisibility(View.VISIBLE);

        int[] medPool   = MojBrojRepository.getInstance().getMedPool();
        int[] largePool = MojBrojRepository.getInstance().getLargePool();

        startSpinAnimation(() -> {
            if (phase == Phase.SPINNING_NUMBERS) {
                for (int i = 0; i < 4; i++) btnNumbers[i].setText(String.valueOf(rng.nextInt(9) + 1));
                btnNumbers[4].setText(String.valueOf(medPool[rng.nextInt(medPool.length)]));
                btnNumbers[5].setText(String.valueOf(largePool[rng.nextInt(largePool.length)]));
            }
        });
        startSpinCountdown(() -> { if (phase == Phase.SPINNING_NUMBERS) lockNumbers(); });
    }

    private void lockNumbers() {
        for (int i = 0; i < 6; i++) {
            tileUsed[i] = false;
            btnNumbers[i].setText(String.valueOf(puzzle.tiles[i]));
        }
        enterPhase(Phase.BUILDING);
    }

    private void setupBuilding() {
        btnConfirm.setText(getString(R.string.btn_confirm));
        btnConfirm.setEnabled(true);
        tvShakeHint.setVisibility(View.GONE);
        setTilesEnabled(true);
        setOpsEnabled(true);
        setEditEnabled(true);
        clearExpression();
        startRoundCountdown();
    }

    private void startSpinAnimation(Runnable tickFn) {
        Runnable loop = new Runnable() {
            @Override public void run() {
                tickFn.run();
                handler.postDelayed(this, SPIN_MS);
            }
        };
        handler.post(loop);
    }

    private void startSpinCountdown(Runnable onFinish) {
        setHudNumber(SPIN_SECS, false);
        spinTimer = new CountDownTimer(SPIN_SECS * 1000L, 1000) {
            @Override public void onTick(long msLeft) { setHudNumber((int)(msLeft/1000), false); }
            @Override public void onFinish() { setHudNumber(0, false); onFinish.run(); }
        }.start();
    }

    private void startRoundCountdown() {
        setHudNumber(ROUND_SECS, false);
        roundTimer = new CountDownTimer(ROUND_SECS * 1000L, 1000) {
            @Override public void onTick(long msLeft) {
                int s = (int)(msLeft / 1000);
                setHudNumber(s, s <= WARN_SECS);
            }
            @Override public void onFinish() {
                setHudNumber(0, true);
                if (phase == Phase.BUILDING) submitAnswer();
            }
        }.start();
    }

    private void cancelAllTimers() {
        if (spinTimer  != null) { spinTimer.cancel();  spinTimer  = null; }
        if (roundTimer != null) { roundTimer.cancel(); roundTimer = null; }
    }

    private void onConfirmOrStop() {
        switch (phase) {
            case SPINNING_TARGET:  lockTarget();   break;
            case SPINNING_NUMBERS: lockNumbers();  break;
            case BUILDING:         submitAnswer(); break;
            default: break;
        }
    }

    private void onNumberClick(int idx) {
        if (phase != Phase.BUILDING || tileUsed[idx]) return;
        tileUsed[idx] = true;
        btnNumbers[idx].setEnabled(false);
        btnNumbers[idx].setAlpha(0.35f);
        appendToken(String.valueOf(puzzle.tiles[idx]), String.valueOf(puzzle.tiles[idx]), idx);
    }

    private void appendToken(String disp, String eval, int tileIdx) {
        if (phase != Phase.BUILDING) return;
        tokens.add(new Token(disp, eval, tileIdx));
        rebuildExpression();
    }

    private void backspace() {
        if (phase != Phase.BUILDING || tokens.isEmpty()) return;
        Token last = tokens.remove(tokens.size() - 1);
        if (last.tileIdx >= 0) {
            tileUsed[last.tileIdx] = false;
            btnNumbers[last.tileIdx].setEnabled(true);
            btnNumbers[last.tileIdx].setAlpha(1.0f);
        }
        rebuildExpression();
    }

    private void clearExpression() {
        tokens.clear();
        exprEval.setLength(0);
        exprDisp.setLength(0);
        tvExpression.setText("");
        if (phase == Phase.BUILDING) {
            for (int i = 0; i < 6; i++) {
                tileUsed[i] = false;
                btnNumbers[i].setEnabled(true);
                btnNumbers[i].setAlpha(1.0f);
            }
        }
    }

    private void rebuildExpression() {
        exprEval.setLength(0);
        exprDisp.setLength(0);
        for (Token t : tokens) {
            exprEval.append(t.eval);
            if (exprDisp.length() > 0) exprDisp.append(" ");
            exprDisp.append(t.disp);
        }
        tvExpression.setText(exprDisp.toString());
    }

    private void submitAnswer() {
        cancelAllTimers();
        setHudClock();

        int result = MojBrojEngine.evaluate(exprEval.toString());

        tvPlayerResult.setText(result > 0 ? String.valueOf(result) : "0");
        setTilesEnabled(false);
        setOpsEnabled(false);
        setEditEnabled(false);
        btnConfirm.setEnabled(false);

        enterPhase(Phase.DONE);

        handler.postDelayed(() -> {
            if (getActivity() != null) getActivity().finish();
        }, 3000);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        if (phase != Phase.SPINNING_TARGET && phase != Phase.SPINNING_NUMBERS) return;

        float x = event.values[0], y = event.values[1], z = event.values[2];
        double net = Math.sqrt(x*x + y*y + z*z) - SensorManager.GRAVITY_EARTH;

        if (net > SHAKE_THRESHOLD_G) {
            long now = System.currentTimeMillis();
            if (now - lastShakeTime > SHAKE_COOLDOWN_MS) {
                lastShakeTime = now;
                onConfirmOrStop();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

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

    private void setTilesEnabled(boolean en) {
        for (int i = 0; i < 6; i++) {
            if (!en || !tileUsed[i]) {
                btnNumbers[i].setEnabled(en);
                btnNumbers[i].setAlpha(en ? 1.0f : 0.55f);
            }
        }
    }

    private void setOpsEnabled(boolean en)  { for (Button b : btnOps) b.setEnabled(en); }
    private void setEditEnabled(boolean en) { btnBackspace.setEnabled(en); btnReset.setEnabled(en); }
    private void setTilesText(String text)  { for (Button b : btnNumbers) b.setText(text); }
}
