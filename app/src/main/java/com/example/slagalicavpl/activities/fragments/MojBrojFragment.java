package com.example.slagalicavpl.activities.fragments;

import android.content.Context;
import com.example.slagalicavpl.activities.GameActivity;
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
import com.example.slagalicavpl.multiplayer.FirebaseMojBrojSync;
import com.example.slagalicavpl.repository.MojBrojRepository;
import com.example.slagalicavpl.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

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

    private enum Phase { SPINNING_TARGET, WAITING_TARGET, SPINNING_NUMBERS, WAITING_NUMBERS, BUILDING, DONE }
    private Phase phase = Phase.SPINNING_TARGET;

    private int currentRound = 1;
    private int accP1 = 0;
    private int accP2 = 0;
    private int mojBrojExact = 0;
    private int mojBrojRounds = 0;

    private MojBrojPuzzle puzzle;
    private final boolean[] tileUsed = new boolean[6];

    // Multiplayer
    private boolean            multiplayer = false;
    private String             myRole      = "p1";
    private FirebaseMojBrojSync mojBrojSync;

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

        if (getActivity() instanceof GameActivity) {
            GameActivity ga = (GameActivity) getActivity();
            multiplayer = ga.isMultiplayer();
            myRole      = ga.getMyRole();
            if (multiplayer && ga.getRoomRef() != null) {
                mojBrojSync = new FirebaseMojBrojSync(ga.getRoomRef(), myRole);
            }

            TextView hudP1 = view.findViewById(R.id.p1_score);
            TextView hudP2 = view.findViewById(R.id.p2_score);
            if (hudP1 != null) hudP1.setText(String.valueOf(ga.getP1Total()));
            if (hudP2 != null) hudP2.setText(String.valueOf(ga.getP2Total()));
            ga.applyAvatarsToHud(view);
        }

        setHudClock();
        enterPhase(isRoundStarter() ? Phase.SPINNING_TARGET : Phase.WAITING_TARGET);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mojBrojSync != null) mojBrojSync.cancelListener();
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

    /** True if this player controls the STOP for this round (p1 in round1, p2 in round2). */
    private boolean isRoundStarter() {
        if (!multiplayer) return true;
        return ("p1".equals(myRole) && currentRound == 1)
            || ("p2".equals(myRole) && currentRound == 2);
    }

    private void enterPhase(Phase next) {
        phase = next;
        handler.removeCallbacksAndMessages(null);
        cancelAllTimers();
        switch (phase) {
            case SPINNING_TARGET:  setupSpinTarget();   break;
            case WAITING_TARGET:   setupWaitTarget();   break;
            case SPINNING_NUMBERS: setupSpinNumbers();  break;
            case WAITING_NUMBERS:  setupWaitNumbers();  break;
            case BUILDING:         setupBuilding();     break;
            case DONE:             setHudClock();       break;
        }
    }

    // ── Starter: spinning target ──────────────────────────────────────────────

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

        if (multiplayer && mojBrojSync != null) {
            mojBrojSync.writeRoundStart(currentRound);
            mojBrojSync.writeTarget(puzzle.target);
        }

        enterPhase(Phase.SPINNING_NUMBERS);
    }

    // ── Follower: wait for target from Firebase ───────────────────────────────

    private void setupWaitTarget() {
        btnConfirm.setEnabled(false);
        tvShakeHint.setVisibility(View.GONE);
        tvTarget.setText("...");
        setTilesText("?");
        setTilesEnabled(false);
        setOpsEnabled(false);
        setEditEnabled(false);

        if (mojBrojSync == null) return;
        mojBrojSync.listenAsFollower(currentRound, new FirebaseMojBrojSync.FollowerListener() {
            @Override
            public void onTargetLocked(int target) {
                handler.post(() -> {
                    puzzle = new MojBrojPuzzle(target, new int[6]);
                    tvTarget.setText(String.valueOf(target));
                    enterPhase(Phase.WAITING_NUMBERS);
                });
            }

            @Override
            public void onTilesLocked(int[] tiles) {
                handler.post(() -> {
                    if (puzzle != null) {
                        puzzle = new MojBrojPuzzle(puzzle.target, tiles);
                    }
                    for (int i = 0; i < 6; i++) {
                        tileUsed[i] = false;
                        btnNumbers[i].setText(String.valueOf(tiles[i]));
                    }
                    enterPhase(Phase.BUILDING);
                });
            }

            @Override
            public void onOpponentResult(int result) {
                handler.post(() -> showOpponentResult(result));
            }
        });
    }

    // ── Starter: spinning numbers ─────────────────────────────────────────────

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

        if (multiplayer && mojBrojSync != null) {
            mojBrojSync.writeTiles(puzzle.tiles);
        }

        enterPhase(Phase.BUILDING);
    }

    // ── Follower: wait for tiles ──────────────────────────────────────────────

    private void setupWaitNumbers() {
        btnConfirm.setEnabled(false);
        tvShakeHint.setVisibility(View.GONE);
        setTilesText("...");
        setTilesEnabled(false);
        setOpsEnabled(false);
        setEditEnabled(false);
        // The follower listener was already started in setupWaitTarget; it will call onTilesLocked
    }

    // ── Both players: building expression ────────────────────────────────────

    private void setupBuilding() {
        btnConfirm.setText(getString(R.string.btn_confirm));
        btnConfirm.setEnabled(true);
        tvShakeHint.setVisibility(View.GONE);
        setTilesEnabled(true);
        setOpsEnabled(true);
        setEditEnabled(true);
        clearExpression();
        startRoundCountdown();

        // Starter starts listening for opponent result now
        if (multiplayer && mojBrojSync != null && isRoundStarter()) {
            mojBrojSync.listenAsStarter(result -> handler.post(() -> showOpponentResult(result)));
        }
    }

    // ── Timer helpers ─────────────────────────────────────────────────────────

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
        View timerWrap = getView() != null ? getView().findViewById(R.id.timer_wrap) : null;
        if (timerWrap != null) timerWrap.setVisibility(View.VISIBLE);
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

    // ── User interaction ──────────────────────────────────────────────────────

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
        mojBrojRounds++;
        if (puzzle != null && result == puzzle.target) mojBrojExact++;
        tvPlayerResult.setText(result >= 0 ? String.valueOf(result) : "?");
        setTilesEnabled(false);
        setOpsEnabled(false);
        setEditEnabled(false);
        btnConfirm.setEnabled(false);

        if (puzzle != null && !multiplayer) {
            // Offline: compute scores immediately
            int[] scores = MojBrojEngine.computeScores(result, 0, puzzle.target, currentRound);
            accP1 += scores[0];
            accP2 += scores[1];
            updateHud();
            enterPhase(Phase.DONE);
            finishRound();
            return;
        }

        enterPhase(Phase.DONE);

        if (multiplayer && mojBrojSync != null) {
            mySubmittedResult = result;
            mySubmitted = true;
            mojBrojSync.writeResult(result);
            // Ako je protivnik već poslao rezultat pre nas, finalizeOnlineRound() je čekao —
            // pozovi ga odmah da ne ostanemo zaglavljeni
            if (oppResultReceived) {
                finalizeOnlineRound();
            } else {
                scheduleOnlineRoundTimeout();
            }
        }
    }

    private boolean mySubmitted       = false;
    private boolean oppResultReceived = false;
    private int     mySubmittedResult = -1;
    private int     oppResult         = -1;
    private boolean roundFinalized    = false;

    private void scheduleOnlineRoundTimeout() {
        // Wait up to 90s for opponent, then finalize with what we have
        handler.postDelayed(this::finalizeOnlineRound, 90_000);
    }

    private void showOpponentResult(int result) {
        oppResult = result;
        oppResultReceived = true;
        if (tvOpponentResult != null)
            tvOpponentResult.setText(result >= 0 ? String.valueOf(result) : "?");
        finalizeOnlineRound();
    }

    private void finalizeOnlineRound() {
        if (roundFinalized) return;
        if (!mySubmitted) return;   // wait until we've also submitted
        roundFinalized = true;
        handler.removeCallbacksAndMessages(null);

        int myRes = mySubmittedResult;
        int opp   = oppResultReceived ? oppResult : -1;

        // Determine p1/p2 results based on roles
        int p1res = "p1".equals(myRole) ? myRes : opp;
        int p2res = "p2".equals(myRole) ? myRes : opp;
        if (p1res < 0) p1res = 0;
        if (p2res < 0) p2res = 0;

        int[] scores = MojBrojEngine.computeScores(p1res, p2res, puzzle != null ? puzzle.target : 0, currentRound);
        accP1 += scores[0];
        accP2 += scores[1];
        updateHud();
        finishRound();
    }

    private void finishRound() {
        boolean challengeMode = !multiplayer && getActivity() instanceof GameActivity
                && ((GameActivity) getActivity()).isChallengeMode();
        if (currentRound == 1 && !challengeMode) {
            handler.postDelayed(this::startRound2, 2500);
        } else {
            if (getActivity() instanceof GameActivity) {
                GameActivity ga = (GameActivity) getActivity();
                ga.addScores(accP1, accP2);

                FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
                if (fbUser != null) {
                    String uid = fbUser.getUid();
                    if (!ga.isFriendlyGame()) {
                        UserRepository.getInstance().incrementMojBroj(uid, mojBrojExact, mojBrojRounds);
                    }
                    if (multiplayer && !ga.isFriendlyGame()) {
                        boolean won = ga.getMyScore() > (ga.getP1Total() + ga.getP2Total() - ga.getMyScore());
                        UserRepository.getInstance().incrementStats(uid, won, ga.getMyScore(),
                                (oldL, newL) -> ga.showLeagueChangeToast(oldL, newL));
                    }
                }

                handler.postDelayed(() -> {
                    if (getActivity() instanceof GameActivity)
                        ((GameActivity) getActivity()).finishGame();
                }, 2500);
            }
        }
    }

    private void startRound2() {
        if (getView() == null) return;
        currentRound = 2;
        roundFinalized    = false;
        mySubmitted       = false;
        mySubmittedResult = -1;
        oppResultReceived = false;
        oppResult         = -1;
        tvPlayerResult.setText(getString(R.string.result_pending));
        tvOpponentResult.setText(getString(R.string.result_pending));
        tvTarget.setText("RUNDA 2");
        clearExpression();
        if (mojBrojSync != null) mojBrojSync.cancelListener();
        handler.postDelayed(() -> enterPhase(isRoundStarter() ? Phase.SPINNING_TARGET : Phase.WAITING_TARGET), 800);
    }

    private void updateHud() {
        if (getView() == null || !(getActivity() instanceof GameActivity)) return;
        GameActivity ga = (GameActivity) getActivity();
        TextView s1 = getView().findViewById(R.id.p1_score);
        TextView s2 = getView().findViewById(R.id.p2_score);
        if (s1 != null) s1.setText(String.valueOf(ga.getP1Total() + accP1));
        if (s2 != null) s2.setText(String.valueOf(ga.getP2Total() + accP2));
    }

    // ── Shake sensor ──────────────────────────────────────────────────────────

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        if (phase != Phase.SPINNING_TARGET && phase != Phase.SPINNING_NUMBERS) return;
        if (!isRoundStarter()) return;

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

    // ── UI helpers ────────────────────────────────────────────────────────────

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
