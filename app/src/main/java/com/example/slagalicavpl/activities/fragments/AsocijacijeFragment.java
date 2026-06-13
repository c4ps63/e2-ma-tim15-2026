package com.example.slagalicavpl.activities.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.activities.GameActivity;
import com.example.slagalicavpl.game.AsocijacijeEngine;
import com.example.slagalicavpl.multiplayer.LocalAsocijacijeSync;
import com.example.slagalicavpl.repository.AsocijacijeRepository;

public class AsocijacijeFragment extends Fragment implements AsocijacijeEngine.Listener {

    private static final int WARN_SECS = 20;

    // Cell buttons [col 0..3][row 0..3]
    private final Button[][] cells = new Button[4][4];
    // Column header buttons [col 0..3]
    private final Button[] colHeaders = new Button[4];

    private TextView tvRound;
    private TextView tvStatus;
    private TextView tvTimerHud;
    private TextView tvP1Score;
    private TextView tvP2Score;
    private EditText etGuess;
    private Button   btnSubmit;   // "KONAČNO" — guess final
    private Button   btnPass;     // "PRESKOČI" — pass turn

    private AsocijacijeEngine engine;
    private CountDownTimer    roundTimer;
    private final Handler     handler = new Handler(Looper.getMainLooper());

    private boolean localsTurn  = true;
    private boolean awaitGuess  = false;
    private int     selectedCol = -1;
    private int     currentRound = 1;

    // Firebase sync referenca za broadcast lokalnih akcija
    private com.example.slagalicavpl.multiplayer.FirebaseAsocijacijeSync firebaseAsocSync;

    // ── lifecycle ────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_asocijacije, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);

        // Bind cells [col][row]: A=0, B=1, C=2, D=3
        cells[0][0] = root.findViewById(R.id.tvCellA1);
        cells[0][1] = root.findViewById(R.id.tvCellA2);
        cells[0][2] = root.findViewById(R.id.tvCellA3);
        cells[0][3] = root.findViewById(R.id.tvCellA4);
        cells[1][0] = root.findViewById(R.id.tvCellB1);
        cells[1][1] = root.findViewById(R.id.tvCellB2);
        cells[1][2] = root.findViewById(R.id.tvCellB3);
        cells[1][3] = root.findViewById(R.id.tvCellB4);
        cells[2][0] = root.findViewById(R.id.tvCellC1);
        cells[2][1] = root.findViewById(R.id.tvCellC2);
        cells[2][2] = root.findViewById(R.id.tvCellC3);
        cells[2][3] = root.findViewById(R.id.tvCellC4);
        cells[3][0] = root.findViewById(R.id.tvCellD1);
        cells[3][1] = root.findViewById(R.id.tvCellD2);
        cells[3][2] = root.findViewById(R.id.tvCellD3);
        cells[3][3] = root.findViewById(R.id.tvCellD4);

        colHeaders[0] = root.findViewById(R.id.tvColHeaderA);
        colHeaders[1] = root.findViewById(R.id.tvColHeaderB);
        colHeaders[2] = root.findViewById(R.id.tvColHeaderC);
        colHeaders[3] = root.findViewById(R.id.tvColHeaderD);

        tvRound    = root.findViewById(R.id.tvRound);
        tvStatus   = root.findViewById(R.id.tvStatus);
        tvTimerHud = root.findViewById(R.id.timer_value);
        tvP1Score  = root.findViewById(R.id.p1_score);
        tvP2Score  = root.findViewById(R.id.p2_score);

        if (getActivity() instanceof GameActivity) {
            GameActivity ga = (GameActivity) getActivity();
            if (tvP1Score != null) tvP1Score.setText(String.valueOf(ga.getP1Total()));
            if (tvP2Score != null) tvP2Score.setText(String.valueOf(ga.getP2Total()));
        }

        etGuess    = root.findViewById(R.id.etGuess);
        btnSubmit  = root.findViewById(R.id.btnSubmit);
        btnPass    = root.findViewById(R.id.btnPass);

        if (root.findViewById(R.id.p1_name) != null)
            ((TextView) root.findViewById(R.id.p1_name)).setText("TI");
        if (root.findViewById(R.id.p2_name) != null)
            ((TextView) root.findViewById(R.id.p2_name)).setText("PROTIVNIK");

        // Wire cell buttons
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                final int col = c, row = r;
                cells[c][r].setOnClickListener(v -> onCellTapped(col, row));
            }
        }

        // Column headers: tap to guess that column
        for (int c = 0; c < 4; c++) {
            final int col = c;
            colHeaders[c].setOnClickListener(v -> onColHeaderTapped(col));
        }

        // "KONAČNO" button
        btnSubmit.setOnClickListener(v -> onGuessFinal());

        // "PRESKOČI" button
        btnPass.setOnClickListener(v -> {
            if (firebaseAsocSync != null)
                firebaseAsocSync.broadcastLocalAction("done", -1, -1, null);
            engine.passGuess();
        });

        // Enter / IME Done → potvrdi odgovor
        etGuess.setOnEditorActionListener((v, actionId, event) -> {
            boolean isDone  = actionId == EditorInfo.IME_ACTION_DONE
                           || actionId == EditorInfo.IME_ACTION_GO
                           || actionId == EditorInfo.IME_ACTION_NEXT;
            boolean isEnter = event != null
                           && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                           && event.getAction() == KeyEvent.ACTION_DOWN;
            if (isDone || isEnter) {
                onConfirmByEnter();
                return true;
            }
            return false;
        });

        // Surrender
        root.findViewById(R.id.btnSurrender).setOnClickListener(v -> {
            cancelTimer();
            if (engine != null) engine.onTimerExpired();
        });

        boolean multiplayer = getActivity() instanceof GameActivity
                && ((GameActivity) getActivity()).isMultiplayer();
        String myRole = (getActivity() instanceof GameActivity)
                ? ((GameActivity) getActivity()).getMyRole() : "p1";

        com.example.slagalicavpl.multiplayer.AsocijacijeSync sync;
        if (multiplayer && getActivity() instanceof GameActivity) {
            com.google.firebase.database.DatabaseReference roomRef =
                    ((GameActivity) getActivity()).getRoomRef();
            firebaseAsocSync = new com.example.slagalicavpl.multiplayer.FirebaseAsocijacijeSync(
                    roomRef, myRole);
            sync = firebaseAsocSync;
        } else {
            sync = new LocalAsocijacijeSync();
        }

        engine = new AsocijacijeEngine(
                AsocijacijeRepository.getRound1(),
                AsocijacijeRepository.getRound2(),
                sync,
                this);

        // U online modu: P1 počinje rundu 1, P2 počinje rundu 2
        if (multiplayer) {
            engine.setLocalStartsRound1("p1".equals(myRole));
        }

        engine.startGame();
    }

    @Override
    public void onPause() {
        super.onPause();
        cancelTimer();
        handler.removeCallbacksAndMessages(null);
    }

    // ── AsocijacijeEngine.Listener ────────────────────────────────────────────

    @Override
    public void onRoundStarted(int round, boolean localFirst) {
        currentRound = round;
        localsTurn  = localFirst;
        awaitGuess  = false;
        selectedCol = -1;
        resetBoard();
        updateRoundBanner(round, localFirst);
        updateActionState();
        startTimer(AsocijacijeEngine.ROUND_SECS);
    }

    @Override
    public void onFieldOpened(int col, int cell, String content, boolean byLocal) {
        cells[col][cell].setText(content);
        cells[col][cell].setAlpha(1f);
        cells[col][cell].setEnabled(false);

        if (byLocal) {
            awaitGuess = true;
            updateActionState();
        }
    }

    @Override
    public void onOpponentAttempt(String text) {
        // Show what the opponent is about to guess in the input field
        if (etGuess != null) {
            etGuess.setText(text);
            etGuess.setEnabled(false);
        }
        setStatus("Protivnik pokušava: " + text + "...");
    }

    @Override
    public void onColumnSolved(int col, int pts, boolean byLocal) {
        colHeaders[col].setText("✓ " + engine.getCurrentPuzzle().colSolutions[col]);
        colHeaders[col].setBackgroundResource(R.drawable.btn_cartoon_green);
        colHeaders[col].setEnabled(false);

        // Reveal all hidden cells in this column
        for (int r = 0; r < 4; r++) {
            cells[col][r].setText(engine.getCurrentPuzzle().cells[col][r]);
            cells[col][r].setAlpha(1f);
            cells[col][r].setEnabled(false);
        }

        // Reset input field after opponent's guess is confirmed
        if (!byLocal && etGuess != null) {
            etGuess.setText("");
            etGuess.setEnabled(false);
        }

        setStatus((byLocal ? "TAČNO!" : "PROTIVNIK TAČNO!") + "  ·  +" + pts + " boda");
    }

    @Override
    public void onFinalSolved(int pts, boolean byLocal) {
        cancelTimer();
        disableAll();
        // Reveal all remaining hidden cells
        revealAllCells();
        String who = byLocal ? "TI SI POGODIO!" : "PROTIVNIK POGODIO!";
        setStatus(who + "  ·  +" + pts + " bodova  ·  KONAČNO: "
                + engine.getCurrentPuzzle().finalSolution);
        if (tvTimerHud != null) tvTimerHud.setText("✓");
        handler.postDelayed(() -> engine.continueAfterFinal(), 4000);
    }

    @Override
    public void onWrongGuess(boolean byLocal) {
        setStatus(byLocal ? "POGREŠNO! — PROTIVNIK NA REDU" : "PROTIVNIK PROMAŠIO — TI NA REDU");
    }

    @Override
    public void onTurnChanged(boolean isLocalTurn) {
        localsTurn  = isLocalTurn;
        awaitGuess  = false;
        selectedCol = -1;
        if (etGuess != null) { etGuess.setText(""); etGuess.setEnabled(isLocalTurn); }
        updateActionState();
    }

    @Override
    public void onScoreChanged(int localTotal, int oppTotal) {
        if (getActivity() instanceof GameActivity) {
            GameActivity ga = (GameActivity) getActivity();
            if (tvP1Score != null) tvP1Score.setText(String.valueOf(ga.getP1Total() + localTotal));
            if (tvP2Score != null) tvP2Score.setText(String.valueOf(ga.getP2Total() + oppTotal));
        } else {
            if (tvP1Score != null) tvP1Score.setText(String.valueOf(localTotal));
            if (tvP2Score != null) tvP2Score.setText(String.valueOf(oppTotal));
        }
    }

    @Override
    public void onRoundEnded(int localRoundPts, int oppRoundPts) {
        cancelTimer();
        disableAll();
        revealAllCells();
        if (tvTimerHud != null) tvTimerHud.setText("✓");
        setStatus("KRAJ RUNDE  ·  TI: +" + localRoundPts + "  PROTIVNIK: +" + oppRoundPts);
        handler.postDelayed(() -> engine.continueAfterRound(), 4000);
    }

    @Override
    public void onGameOver(int localTotal, int oppTotal) {
        cancelTimer();
        disableAll();
        setStatus("KRAJ  ·  TI: " + localTotal + "   PROTIVNIK: " + oppTotal);
        if (tvTimerHud != null) tvTimerHud.setText("✓");

        if (getActivity() instanceof GameActivity)
            ((GameActivity) getActivity()).addScores(localTotal, oppTotal);

        handler.postDelayed(() -> {
            if (getActivity() instanceof GameActivity)
                ((GameActivity) getActivity()).showSkocko();
        }, 2500);
    }

    // ── User interaction ──────────────────────────────────────────────────────

    private void onCellTapped(int col, int row) {
        if (!localsTurn || awaitGuess) return;
        if (engine.isCellOpened(col, row)) return;
        engine.openField(col, row);
        if (firebaseAsocSync != null)
            firebaseAsocSync.broadcastLocalAction("openField", col, row, null);
    }

    private void onColHeaderTapped(int col) {
        if (!localsTurn || !awaitGuess) return;
        if (engine.isColSolved(col)) return;

        String text = etGuess.getText().toString().trim();
        if (!text.isEmpty()) {
            selectedCol = col;
            if (firebaseAsocSync != null)
                firebaseAsocSync.broadcastLocalAction("guessColumn", col, -1, text);
            engine.guessColumn(col, text);
            etGuess.setText("");
            selectedCol = -1;
            highlightSelectedCol(-1);
        } else {
            // Select the column (highlight it) so Enter confirms for this column
            selectedCol = (selectedCol == col) ? -1 : col; // toggle
            highlightSelectedCol(selectedCol);
            if (selectedCol >= 0)
                setStatus("Kucaj odgovor za kolonu " + colLetter(col) + " pa Enter");
            else
                updateActionState();
        }
    }

    private void highlightSelectedCol(int col) {
        for (int c = 0; c < 4; c++) {
            if (engine.isColSolved(c)) continue;
            colHeaders[c].setBackgroundResource(
                c == col ? R.drawable.btn_cartoon_yellow : R.drawable.btn_cartoon_red);
        }
    }

    private void onGuessFinal() {
        if (!localsTurn || !awaitGuess) return;
        String text = etGuess.getText().toString().trim();
        if (text.isEmpty()) {
            setStatus("Unesi konačno rešenje!");
            return;
        }
        if (firebaseAsocSync != null)
            firebaseAsocSync.broadcastLocalAction("guessFinal", -1, -1, text);
        engine.guessFinal(text);
        etGuess.setText("");
    }

    /**
     * Called when Enter is pressed. If a column was last highlighted (selectedCol >= 0),
     * submits for that column; otherwise submits as the final guess.
     */
    private void onConfirmByEnter() {
        if (!localsTurn || !awaitGuess) return;
        String text = etGuess.getText().toString().trim();
        if (text.isEmpty()) {
            setStatus("Unesi odgovor pa pritisni Enter");
            return;
        }
        if (selectedCol >= 0 && !engine.isColSolved(selectedCol)) {
            int col = selectedCol;
            if (firebaseAsocSync != null)
                firebaseAsocSync.broadcastLocalAction("guessColumn", col, -1, text);
            engine.guessColumn(col, text);
            etGuess.setText("");
            selectedCol = -1;
            highlightSelectedCol(-1);
        } else {
            if (firebaseAsocSync != null)
                firebaseAsocSync.broadcastLocalAction("guessFinal", -1, -1, text);
            engine.guessFinal(text);
            etGuess.setText("");
        }
    }

    // ── Board helpers ─────────────────────────────────────────────────────────

    private void resetBoard() {
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                cells[c][r].setText("?");
                cells[c][r].setAlpha(0.55f);
                cells[c][r].setEnabled(true);
                cells[c][r].setBackgroundResource(R.drawable.btn_cartoon_yellow);
            }
            colHeaders[c].setText(colLetter(c));
            colHeaders[c].setBackgroundResource(R.drawable.btn_cartoon_red);
            colHeaders[c].setEnabled(true);
        }
        if (etGuess != null) { etGuess.setText(""); etGuess.setEnabled(true); }
        if (btnSubmit != null) btnSubmit.setEnabled(true);
        if (btnPass != null) btnPass.setEnabled(true);
    }

    private void revealAllCells() {
        for (int c = 0; c < 4; c++) {
            // Reveal column solution if not already solved
            if (!engine.isColSolved(c)) {
                colHeaders[c].setText(engine.getCurrentPuzzle().colSolutions[c]);
                colHeaders[c].setBackgroundResource(R.drawable.btn_cartoon_blue);
            }
            for (int r = 0; r < 4; r++) {
                if (!engine.isCellOpened(c, r)) {
                    cells[c][r].setText(engine.getCurrentPuzzle().cells[c][r]);
                    cells[c][r].setAlpha(0.7f);
                }
            }
        }
    }

    private void disableAll() {
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) cells[c][r].setEnabled(false);
            colHeaders[c].setEnabled(false);
        }
        if (etGuess != null) etGuess.setEnabled(false);
        if (btnSubmit != null) btnSubmit.setEnabled(false);
        if (btnPass != null) btnPass.setEnabled(false);
    }

    /** Enable/disable interactive elements depending on game state. */
    private void updateActionState() {
        boolean canOpen  = localsTurn && !awaitGuess;
        boolean canGuess = localsTurn && awaitGuess;

        // Hidden cells: only clickable when in "open mode"
        for (int c = 0; c < 4; c++)
            for (int r = 0; r < 4; r++)
                if (!engine.isCellOpened(c, r))
                    cells[c][r].setEnabled(canOpen);

        // Column headers: only as guess targets in guess mode
        for (int c = 0; c < 4; c++)
            if (!engine.isColSolved(c))
                colHeaders[c].setEnabled(canGuess);

        if (etGuess  != null) etGuess.setEnabled(canGuess);
        if (btnSubmit != null) btnSubmit.setEnabled(canGuess);
        if (btnPass   != null) btnPass.setEnabled(canGuess);

        // Status hint
        if (localsTurn) {
            if (!awaitGuess) setStatus("TI OTVORI POLJE");
            else             setStatus("POGODI KOLONU (tap A/B/C/D) ILI KONAČNO REŠENJE");
        } else {
            setStatus("PROTIVNIK NA REDU...");
        }
    }

    // ── Timer ─────────────────────────────────────────────────────────────────

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

    // ── Misc ──────────────────────────────────────────────────────────────────

    private void updateRoundBanner(int round, boolean localFirst) {
        if (tvRound == null) return;
        String starter = localFirst ? "TI POČINJEŠ" : "PROTIVNIK POČINJE";
        tvRound.setText("🦁 AFRIKA · ASOCIJACIJE  ·  RUNDA " + round + "/2  ·  " + starter);
    }

    private void setStatus(String text) {
        if (tvStatus != null) tvStatus.setText(text);
    }

    private static String colLetter(int col) {
        return new String[]{"A","B","C","D"}[col];
    }
}
