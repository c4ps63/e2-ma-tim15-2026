package com.example.slagalicavpl.game;

import com.example.slagalicavpl.model.Question;
import com.example.slagalicavpl.multiplayer.KoZnaZnaSync;

import java.util.List;

public class KoZnaZnaEngine {

    public static final int QUESTIONS_PER_GAME = 5;
    public static final int PTS_CORRECT        = 10;
    public static final int PTS_WRONG          = -5;

    public enum Phase { IDLE, QUESTION, DONE }

    public interface Listener {
        void onQuestionReady(Question question, int questionNum);
        void onAnswerResult(char correctOption, char localAnswer, char opponentAnswer,
                            int localDelta, int opponentDelta);
        void onScoreChanged(int localScore, int opponentScore);
        void onGameOver(int localScore, int opponentScore);
    }

    private final List<Question>  questions;
    private final KoZnaZnaSync    sync;
    private final Listener        listener;

    private Phase phase        = Phase.IDLE;
    private int   currentIdx   = 0;
    private int   localScore   = 0;
    private int   opponentScore = 0;

    private char    localAnswer     = 0;
    private char    opponentAnswer  = 0;
    private long    localElapsedMs  = 0;
    private long    opponentElapsedMs = 0;
    private boolean timerExpired    = false;

    public KoZnaZnaEngine(List<Question> questions, KoZnaZnaSync sync, Listener listener) {
        this.questions = questions;
        this.sync      = sync;
        this.listener  = listener;
    }

    public void startGame() {
        localScore    = 0;
        opponentScore = 0;
        currentIdx    = 0;
        showCurrentQuestion();
    }

    public void submitAnswer(char option, long elapsedMs) {
        if (phase != Phase.QUESTION || localAnswer != 0) return;
        localAnswer    = option;
        localElapsedMs = elapsedMs;
        sync.sendAnswer(currentIdx, option, elapsedMs);
        tryEvaluate();
    }

    public void onTimerExpired() {
        if (phase != Phase.QUESTION) return;
        timerExpired = true;
        tryEvaluate();
    }

    public void nextQuestion() {
        if (phase != Phase.QUESTION && phase != Phase.IDLE) return;
        currentIdx++;
        if (currentIdx >= QUESTIONS_PER_GAME) {
            phase = Phase.DONE;
            listener.onGameOver(localScore, opponentScore);
        } else {
            showCurrentQuestion();
        }
    }

    public Phase getPhase() { return phase; }

    private void showCurrentQuestion() {
        phase           = Phase.QUESTION;
        localAnswer     = 0;
        opponentAnswer  = 0;
        localElapsedMs  = 0;
        opponentElapsedMs = 0;
        timerExpired    = false;

        Question q = questions.get(currentIdx);
        listener.onQuestionReady(q, currentIdx + 1);

        sync.listenForOpponentAnswer(currentIdx, q, (option, elapsed) -> {
            if (phase != Phase.QUESTION || opponentAnswer != 0) return;
            opponentAnswer    = option;
            opponentElapsedMs = elapsed;
            tryEvaluate();
        });
    }

    private void tryEvaluate() {
        if (phase != Phase.QUESTION) return;
        boolean localDone    = (localAnswer != 0);
        boolean opponentDone = (opponentAnswer != 0);
        if (!timerExpired && !(localDone && opponentDone)) return;

        phase = Phase.IDLE;
        sync.cancel();

        Question q = questions.get(currentIdx);
        boolean localCorrect    = (localAnswer != 0 && localAnswer == q.correct);
        boolean opponentCorrect = (opponentAnswer != 0 && opponentAnswer == q.correct);

        int localDelta    = 0;
        int opponentDelta = 0;

        if (localCorrect && opponentCorrect) {
            if (localElapsedMs <= opponentElapsedMs) localDelta = PTS_CORRECT;
            else                                     opponentDelta = PTS_CORRECT;
        } else if (localCorrect) {
            localDelta = PTS_CORRECT;
        } else if (opponentCorrect) {
            opponentDelta = PTS_CORRECT;
        }

        if (localAnswer != 0 && !localCorrect)    localDelta    += PTS_WRONG;
        if (opponentAnswer != 0 && !opponentCorrect) opponentDelta += PTS_WRONG;

        localScore    += localDelta;
        opponentScore += opponentDelta;

        listener.onScoreChanged(localScore, opponentScore);
        listener.onAnswerResult(q.correct, localAnswer, opponentAnswer, localDelta, opponentDelta);
    }
}
