package com.example.slagalicavpl.multiplayer;

import com.example.slagalicavpl.model.ConnectPair;

import java.util.List;

public interface SpojniceSync {

    interface ConnectCallback {
        void onOpponentConnect(int leftRow, int rightRow);
        void onOpponentDone();
    }

    void startOpponentTurn(List<ConnectPair> pairs, boolean[] connected,
                           int[] rightSlots, ConnectCallback cb);

    void cancel();
}
