package com.example.slagalicavpl.repository;

import com.example.slagalicavpl.model.MojBrojPuzzle;

import java.util.Random;

public class MojBrojRepository {

    private static MojBrojRepository instance;

    private static final int[] MED_POOL   = {10, 15, 20};
    private static final int[] LARGE_POOL = {25, 50, 75, 100};

    private final Random rng = new Random();

    private MojBrojRepository() {}

    public static MojBrojRepository getInstance() {
        if (instance == null) instance = new MojBrojRepository();
        return instance;
    }

    public MojBrojPuzzle generatePuzzle() {
        int target = rng.nextInt(999) + 1;
        int[] tiles = new int[6];
        for (int i = 0; i < 4; i++) tiles[i] = rng.nextInt(9) + 1;
        tiles[4] = MED_POOL[rng.nextInt(MED_POOL.length)];
        tiles[5] = LARGE_POOL[rng.nextInt(LARGE_POOL.length)];
        return new MojBrojPuzzle(target, tiles);
    }

    public int[] getMedPool()   { return MED_POOL.clone(); }
    public int[] getLargePool() { return LARGE_POOL.clone(); }
}
