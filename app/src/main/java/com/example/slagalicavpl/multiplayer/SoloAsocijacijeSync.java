package com.example.slagalicavpl.multiplayer;

import com.example.slagalicavpl.model.AsocijacijePuzzle;

/** Used in challenge mode: opponent's turn ends immediately without any actions. */
public class SoloAsocijacijeSync implements AsocijacijeSync {
    @Override
    public void startOpponentTurn(AsocijacijePuzzle puzzle, boolean[] colSolved,
                                   boolean[][] cellOpened, int round, Callback cb) {
        cb.onOpponentDone();
    }

    @Override public void cancel() {}
}
