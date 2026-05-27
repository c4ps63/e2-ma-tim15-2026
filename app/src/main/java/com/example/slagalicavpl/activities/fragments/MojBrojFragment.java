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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * MOJ BROJ — game fragment
 *
 * HUD timer states:
 *   ⏱  — idle / done
 *   5→1 — spinning (auto-stop countdown), normal color
 *   60→1 — building, turns red at ≤ 10 s
 */
public class MojBrojFragment extends Fragment implements SensorEventListener {

    /* ── Views ──────────────────────────────────────────────────────────── */
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

    /* ── Token history (for ⌫ backspace) ────────────────────────────────── */
    private static class Token {
        final String disp, eval;
        final int tileIdx; // ≥0 = number tile; -1 = operator
        Token(String d, String e, int t) { disp = d; eval = e; tileIdx = t; }
    }
    private final List<Token> tokens = new ArrayList<>();

    private final StringBuilder exprEval = new StringBuilder();
    private final StringBuilder exprDisp = new StringBuilder();

    /* ── Game state ──────────────────────────────────────────────────────── */
    private enum Phase { SPINNING_TARGET, SPINNING_NUMBERS, BUILDING, DONE }
    private Phase phase = Phase.SPINNING_TARGET;

    private int targetNumber;
    private final int[]     tileValues = new int[6];
    private final boolean[] tileUsed   = new boolean[6];

    /* ── Timers ──────────────────────────────────────────────────────────── */
    private final Handler handler   = new Handler(Looper.getMainLooper());
    private CountDownTimer spinTimer;   // 5 s during spinning phases
    private CountDownTimer roundTimer;  // 60 s during building phase

    private static final int SPIN_MS      = 75;
    private static final int SPIN_SECS    = 5;
    private static final int ROUND_SECS   = 60;
    private static final int WARN_SECS    = 10;

    private static final String CLOCK_ICON = "⏱";

    /* ── Number pools ────────────────────────────────────────────────────── */
    private static final int[] MED_POOL   = {10, 15, 20};
    private static final int[] LARGE_POOL = {25, 50, 75, 100};

    /* ── Operators ───────────────────────────────────────────────────────── */
    private static final String[] OP_DISP = {"+", "−", "×", "÷", "(", ")"};
    private static final String[] OP_EVAL = {"+", "-", "*", "/", "(", ")"};

    /* ── Shake ───────────────────────────────────────────────────────────── */
    private SensorManager sensorManager;
    private Sensor        accelerometer;
    private long          lastShakeTime = 0;
    private static final float SHAKE_THRESHOLD_G = 2.5f;
    private static final int   SHAKE_COOLDOWN_MS = 600;

    private final Random rng = new Random();

    /* ══════════════════════════════════════════════════════════════════════
     *  Fragment lifecycle
     * ══════════════════════════════════════════════════════════════════════ */

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

        setHudClock(); // početno stanje
        enterPhase(Phase.SPINNING_TARGET);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (sensorManager != null && accelerometer != null)
            sensorManager.registerListener(this, accelerometer,
                    SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacksAndMessages(null);
        cancelAllTimers();
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }

    /* ══════════════════════════════════════════════════════════════════════
     *  Phase management
     * ══════════════════════════════════════════════════════════════════════ */

    private void enterPhase(Phase next) {
        phase = next;
        handler.removeCallbacksAndMessages(null);
        cancelAllTimers();

        switch (phase) {
            case SPINNING_TARGET:  setupSpinTarget();  break;
            case SPINNING_NUMBERS: setupSpinNumbers(); break;
            case BUILDING:         setupBuilding();     break;
            case DONE:
                setHudClock();
                break;
        }
    }

    /* ── SPINNING_TARGET ──────────────────────────────────────────────── */

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

        startSpinCountdown(() -> {
            if (phase == Phase.SPINNING_TARGET) lockTarget();
        });
    }

    private void lockTarget() {
        targetNumber = rng.nextInt(999) + 1;
        tvTarget.setText(String.valueOf(targetNumber));
        enterPhase(Phase.SPINNING_NUMBERS);
    }

    /* ── SPINNING_NUMBERS ─────────────────────────────────────────────── */

    private void setupSpinNumbers() {
        btnConfirm.setText("STOP");
        btnConfirm.setEnabled(true);
        tvShakeHint.setVisibility(View.VISIBLE);

        generateTileValues();

        startSpinAnimation(() -> {
            if (phase == Phase.SPINNING_NUMBERS) {
                for (int i = 0; i < 4; i++)
                    btnNumbers[i].setText(String.valueOf(rng.nextInt(9) + 1));
                btnNumbers[4].setText(
                        String.valueOf(MED_POOL[rng.nextInt(MED_POOL.length)]));
                btnNumbers[5].setText(
                        String.valueOf(LARGE_POOL[rng.nextInt(LARGE_POOL.length)]));
            }
        });

        startSpinCountdown(() -> {
            if (phase == Phase.SPINNING_NUMBERS) lockNumbers();
        });
    }

    private void generateTileValues() {
        for (int i = 0; i < 4; i++) tileValues[i] = rng.nextInt(9) + 1;
        tileValues[4] = MED_POOL[rng.nextInt(MED_POOL.length)];
        tileValues[5] = LARGE_POOL[rng.nextInt(LARGE_POOL.length)];
    }

    private void lockNumbers() {
        for (int i = 0; i < 6; i++) {
            tileUsed[i] = false;
            btnNumbers[i].setText(String.valueOf(tileValues[i]));
        }
        enterPhase(Phase.BUILDING);
    }

    /* ── BUILDING ─────────────────────────────────────────────────────── */

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

    /* ══════════════════════════════════════════════════════════════════════
     *  Timer helpers
     * ══════════════════════════════════════════════════════════════════════ */

    /** Spinning animation — calls tickFn every SPIN_MS via Handler. */
    private void startSpinAnimation(Runnable tickFn) {
        Runnable loop = new Runnable() {
            @Override public void run() {
                tickFn.run();
                handler.postDelayed(this, SPIN_MS);
            }
        };
        handler.post(loop);
    }

    /**
     * 5-second HUD countdown used during both spinning phases.
     * Calls onFinish when it reaches 0.
     */
    private void startSpinCountdown(Runnable onFinish) {
        setHudNumber(SPIN_SECS, false);

        spinTimer = new CountDownTimer(SPIN_SECS * 1000L, 1000) {
            @Override public void onTick(long msLeft) {
                int s = (int) (msLeft / 1000);
                setHudNumber(s, false);
            }
            @Override public void onFinish() {
                setHudNumber(0, false);
                onFinish.run();
            }
        }.start();
    }

    /** 60-second HUD countdown used during BUILDING. */
    private void startRoundCountdown() {
        setHudNumber(ROUND_SECS, false);

        roundTimer = new CountDownTimer(ROUND_SECS * 1000L, 1000) {
            @Override public void onTick(long msLeft) {
                int s = (int) (msLeft / 1000);
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

    /* ── HUD timer display helpers ─────────────────────────────────────── */

    /** Shows a number in the HUD timer, optionally in red. */
    private void setHudNumber(int value, boolean red) {
        if (tvTimerHud == null) return;
        tvTimerHud.setText(String.valueOf(value));
        tvTimerHud.setTextColor(red ? Color.RED : Color.parseColor("#102341"));
        tvTimerHud.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20);
    }

    /** Shows the clock icon (⏱) — used when there is no active countdown. */
    private void setHudClock() {
        if (tvTimerHud == null) return;
        tvTimerHud.setText(CLOCK_ICON);
        tvTimerHud.setTextColor(Color.parseColor("#102341"));
        tvTimerHud.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18);
    }

    /* ══════════════════════════════════════════════════════════════════════
     *  User input handlers
     * ══════════════════════════════════════════════════════════════════════ */

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
        appendToken(String.valueOf(tileValues[idx]),
                    String.valueOf(tileValues[idx]), idx);
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

        int result = 0;
        boolean valid = false;

        if (exprEval.length() > 0) {
            try {
                double val = new ExprParser(
                        exprEval.toString().replaceAll("\\s+", "")).expr();
                if (!Double.isNaN(val) && !Double.isInfinite(val) && val >= 0) {
                    result = (int) Math.round(val);
                    valid  = true;
                }
            } catch (Exception ignored) { }
        }

        tvPlayerResult.setText(valid ? String.valueOf(result) : "0");
        setTilesEnabled(false);
        setOpsEnabled(false);
        setEditEnabled(false);
        btnConfirm.setEnabled(false);

        enterPhase(Phase.DONE);

        // Moj broj je poslednja igra — završi partiju nakon kratke pauze
        handler.postDelayed(() -> {
            if (getActivity() != null) getActivity().finish();
        }, 3000);
    }

    /* ══════════════════════════════════════════════════════════════════════
     *  Shake sensor
     * ══════════════════════════════════════════════════════════════════════ */

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        if (phase != Phase.SPINNING_TARGET && phase != Phase.SPINNING_NUMBERS) return;

        float x = event.values[0], y = event.values[1], z = event.values[2];
        double net = Math.sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH;

        if (net > SHAKE_THRESHOLD_G) {
            long now = System.currentTimeMillis();
            if (now - lastShakeTime > SHAKE_COOLDOWN_MS) {
                lastShakeTime = now;
                onConfirmOrStop();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    /* ══════════════════════════════════════════════════════════════════════
     *  UI helpers
     * ══════════════════════════════════════════════════════════════════════ */

    private void setTilesEnabled(boolean en) {
        for (int i = 0; i < 6; i++) {
            if (!en || !tileUsed[i]) {
                btnNumbers[i].setEnabled(en);
                btnNumbers[i].setAlpha(en ? 1.0f : 0.55f);
            }
        }
    }

    private void setOpsEnabled(boolean en) {
        for (Button b : btnOps) b.setEnabled(en);
    }

    private void setEditEnabled(boolean en) {
        btnBackspace.setEnabled(en);
        btnReset.setEnabled(en);
    }

    private void setTilesText(String text) {
        for (Button b : btnNumbers) b.setText(text);
    }

    /* ══════════════════════════════════════════════════════════════════════
     *  Expression evaluator — recursive descent
     * ══════════════════════════════════════════════════════════════════════ */

    private static class ExprParser {
        private final String s;
        private int pos = 0;

        ExprParser(String input) { this.s = input; }

        double expr() {
            double v = term();
            while (pos < s.length() &&
                   (s.charAt(pos) == '+' || s.charAt(pos) == '-')) {
                char op = s.charAt(pos++);
                v = (op == '+') ? v + term() : v - term();
            }
            return v;
        }

        double term() {
            double v = factor();
            while (pos < s.length() &&
                   (s.charAt(pos) == '*' || s.charAt(pos) == '/')) {
                char op = s.charAt(pos++);
                double t = factor();
                if (op == '/' && t == 0) throw new ArithmeticException("div/0");
                v = (op == '*') ? v * t : v / t;
            }
            return v;
        }

        double factor() {
            if (pos < s.length() && s.charAt(pos) == '(') {
                pos++;
                double v = expr();
                if (pos < s.length() && s.charAt(pos) == ')') pos++;
                return v;
            }
            if (pos < s.length() && s.charAt(pos) == '-') {
                pos++;
                return -factor();
            }
            int start = pos;
            while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            if (pos == start)
                throw new NumberFormatException("Expected digit at pos " + pos);
            return Double.parseDouble(s.substring(start, pos));
        }
    }
}
