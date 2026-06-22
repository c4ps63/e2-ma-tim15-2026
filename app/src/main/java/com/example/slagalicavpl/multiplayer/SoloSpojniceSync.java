package com.example.slagalicavpl.multiplayer;

import com.example.slagalicavpl.model.ConnectPair;
import java.util.List;

/** Used in challenge mode: opponent's turn ends immediately with zero connections. */
public class SoloSpojniceSync implements SpojniceSync {
    @Override
    public void startOpponentTurn(List<ConnectPair> pairs, boolean[] connected,
                                   int[] rightSlots, ConnectCallback cb) {
        cb.onOpponentDone();
    }

    @Override public void cancel() {}
}
