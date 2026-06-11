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
import com.example.slagalicavpl.game.KoZnaZnaEngine;
import com.example.slagalicavpl.model.Question;
import com.example.slagalicavpl.multiplayer.LocalKoZnaZnaSync;
import com.example.slagalicavpl.repository.QuestionRepository;

public class KoZnaZnaFragment extends Fragment implements KoZnaZnaEngine.Listener {

    private static final int QUESTION_SECS  = 5;
    private static final int RESULT_SHOW_MS = 1500;
    private static final int WARN_SECS      = 2;

    private TextView tvRound;
    private TextView tvQuestionLabel;
    private TextView tvQuestion;
    private TextView tvTimer;
    private TextView tvTimerHud;
    private TextView tvP1Score;
    private TextView tvP2Score;
    private Button   btnA, btnB, btnC, btnD;
    private Button   btnSurrender;

    private KoZnaZnaEngine   engine;
    private CountDownTimer   questionTimer;
    private final Handler    handler = new Handler(Looper.getMainLooper());
    private long             questionStartTime;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ko_zna_zna, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvRound         = view.findViewById(R.id.tvRound);
        tvQuestionLabel = view.findViewById(R.id.tvQuestionLabel);
        tvQuestion      = view.findViewById(R.id.tvQuestion);
        tvTimer         = view.findViewById(R.id.tvTimer);
        tvTimerHud      = view.findViewById(R.id.timer_value);
        tvP1Score       = view.findViewById(R.id.p1_score);
        tvP2Score       = view.findViewById(R.id.p2_score);
        btnA            = view.findViewById(R.id.btnAnswerA);
        btnB            = view.findViewById(R.id.btnAnswerB);
        btnC            = view.findViewById(R.id.btnAnswerC);
        btnD            = view.findViewById(R.id.btnAnswerD);
        btnSurrender    = view.findViewById(R.id.btnSurrender);

        TextView tvP1Name = view.findViewById(R.id.p1_name);
        TextView tvP2Name = view.findViewById(R.id.p2_name);
        if (tvP1Name != null) tvP1Name.setText("TI");
        if (tvP2Name != null) tvP2Name.setText("PROTIVNIK");

        btnA.setOnClickListener(v -> onAnswerTapped('A'));
        btnB.setOnClickListener(v -> onAnswerTapped('B'));
        btnC.setOnClickListener(v -> onAnswerTapped('C'));
        btnD.setOnClickListener(v -> onAnswerTapped('D'));

        btnSurrender.setOnClickListener(v -> {
            cancelTimer();
            if (getActivity() instanceof GameActivity)
                ((GameActivity) getActivity()).showSpojnice();
        });

        engine = new KoZnaZnaEngine(
                QuestionRepository.getInstance().getQuestionsForGame(),
                new LocalKoZnaZnaSync(),
                this);

        tvRound.setText("🏛️ EVROPA · KO ZNA ZNA");
        engine.startGame();
    }

    @Override
    public void onPause() {
        super.onPause();
        cancelTimer();
        handler.removeCallbacksAndMessages(null);
    }

    private void onAnswerTapped(char option) {
        if (engine.getPhase() != KoZnaZnaEngine.Phase.QUESTION) return;
        long elapsed = System.currentTimeMillis() - questionStartTime;
        setAnswersEnabled(false);
        engine.submitAnswer(option, elapsed);
    }

    @Override
    public void onQuestionReady(Question question, int questionNum) {
        questionStartTime = System.currentTimeMillis();

        tvQuestionLabel.setText("PITANJE " + questionNum + " / " + KoZnaZnaEngine.QUESTIONS_PER_GAME);
        tvQuestion.setText(question.text);
        btnA.setText(question.optionA);
        btnB.setText(question.optionB);
        btnC.setText(question.optionC);
        btnD.setText(question.optionD);

        resetButtonStyles();
        setAnswersEnabled(true);
        startQuestionTimer();
    }

    @Override
    public void onAnswerResult(char correctOption, char localAnswer, char opponentAnswer,
                               int localDelta, int opponentDelta) {
        cancelTimer();
        setAnswersEnabled(false);

        highlightCorrect(correctOption);
        if (localAnswer != 0 && localAnswer != correctOption) highlightWrong(localAnswer);

        String deltaText = (localDelta > 0 ? "+" : "") + localDelta;
        tvQuestionLabel.setText("TAČNO: " + correctOption + "   TI: " + deltaText);

        handler.postDelayed(() -> engine.nextQuestion(), RESULT_SHOW_MS);
    }

    @Override
    public void onScoreChanged(int localScore, int opponentScore) {
        if (tvP1Score != null) tvP1Score.setText(String.valueOf(localScore));
        if (tvP2Score != null) tvP2Score.setText(String.valueOf(opponentScore));
    }

    @Override
    public void onGameOver(int localScore, int opponentScore) {
        cancelTimer();
        setAnswersEnabled(false);
        tvQuestionLabel.setText("KRAJ IGRE");
        tvQuestion.setText("TI: " + localScore + " bod.   PROTIVNIK: " + opponentScore + " bod.");
        tvTimer.setText("✓");

        handler.postDelayed(() -> {
            if (getActivity() instanceof GameActivity)
                ((GameActivity) getActivity()).showSpojnice();
        }, 2500);
    }

    private void startQuestionTimer() {
        cancelTimer();
        updateTimer(QUESTION_SECS);

        questionTimer = new CountDownTimer(QUESTION_SECS * 1000L, 1000) {
            @Override public void onTick(long msLeft) {
                int s = (int) (msLeft / 1000);
                updateTimer(s);
            }
            @Override public void onFinish() {
                updateTimer(0);
                engine.onTimerExpired();
            }
        }.start();
    }

    private void cancelTimer() {
        if (questionTimer != null) { questionTimer.cancel(); questionTimer = null; }
    }

    private void updateTimer(int s) {
        tvTimer.setText(String.valueOf(s));
        tvTimer.setTextColor(s <= WARN_SECS ? Color.parseColor("#CC0000") : Color.parseColor("#A8312A"));
        if (tvTimerHud != null) {
            tvTimerHud.setText(String.valueOf(s));
            tvTimerHud.setTextColor(s <= WARN_SECS ? Color.RED : Color.parseColor("#102341"));
        }
    }

    private void setAnswersEnabled(boolean enabled) {
        btnA.setEnabled(enabled);
        btnB.setEnabled(enabled);
        btnC.setEnabled(enabled);
        btnD.setEnabled(enabled);
    }

    private void resetButtonStyles() {
        int defaultBg = R.drawable.btn_cartoon_cream;
        btnA.setBackgroundResource(defaultBg);
        btnB.setBackgroundResource(defaultBg);
        btnC.setBackgroundResource(defaultBg);
        btnD.setBackgroundResource(defaultBg);
        btnA.setAlpha(1f); btnB.setAlpha(1f);
        btnC.setAlpha(1f); btnD.setAlpha(1f);
    }

    private void highlightCorrect(char option) {
        buttonForOption(option).setBackgroundResource(R.drawable.btn_cartoon_green);
    }

    private void highlightWrong(char option) {
        buttonForOption(option).setBackgroundResource(R.drawable.btn_cartoon_red);
    }

    private Button buttonForOption(char option) {
        switch (option) {
            case 'A': return btnA;
            case 'B': return btnB;
            case 'C': return btnC;
            default:  return btnD;
        }
    }
}
