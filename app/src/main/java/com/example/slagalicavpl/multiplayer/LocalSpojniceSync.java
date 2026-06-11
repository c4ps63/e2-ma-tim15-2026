package com.example.slagalicavpl.multiplayer;

import android.os.Handler;
import android.os.Looper;

import com.example.slagalicavpl.model.ConnectPair;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class LocalSpojniceSync implements SpojniceSync {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random  rng     = new Random();

    @Override
    public void startOpponentTurn(List<ConnectPair> pairs, boolean[] connected,
                                  int[] rightSlots, ConnectCallback cb) {
        List<Integer> targets = new ArrayList<>();
        for (int i = 0; i < pairs.size(); i++) {
            if (!connected[i]) targets.add(i);
        }

        long delay = 0;
        for (int leftRow : targets) {
            if (rng.nextInt(10) >= 8) continue; // 20% — opponent skips this pair

            delay += 600 + rng.nextInt(1200);
            final long d   = delay;
            final int  row = leftRow;
            handler.postDelayed(() -> {
                if (connected[row]) return;
                int rightRow = -1;
                for (int j = 0; j < rightSlots.length; j++) {
                    if (rightSlots[j] == row) { rightRow = j; break; }
                }
                if (rightRow >= 0) cb.onOpponentConnect(row, rightRow);
            }, d);
        }

        delay += 500;
        handler.postDelayed(cb::onOpponentDone, delay);
    }

    @Override
    public void cancel() {
        handler.removeCallbacksAndMessages(null);
    }
}
