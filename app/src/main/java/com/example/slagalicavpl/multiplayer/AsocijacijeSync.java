package com.example.slagalicavpl.multiplayer;

import com.example.slagalicavpl.model.AsocijacijePuzzle;

public interface AsocijacijeSync {

    interface Callback {
        /** Opponent opens a cell. */
        void onOpponentOpenField(int col, int cell);
        /** Opponent is typing / about to submit this text — shown in UI before confirming. */
        void onOpponentAttempt(String text);
        /** Opponent correctly guesses a column solution. */
        void onOpponentGuessColumn(int col);
        /** Opponent correctly guesses the final solution — round ends. */
        void onOpponentGuessFinal();
        /** Opponent finishes their turn without solving the final. */
        void onOpponentDone();
    }

    void startOpponentTurn(AsocijacijePuzzle puzzle,
                           boolean[] colSolved,
                           boolean[][] cellOpened,
                           Callback cb);

    void cancel();
}
