package com.example.slagalicavpl.repository;

import com.example.slagalicavpl.model.ConnectPair;

import java.util.ArrayList;
import java.util.List;

public class ConnectRepository {

    private static ConnectRepository instance;

    private static final ConnectPair[] ROUND1 = {
        new ConnectPair("Tokio",   "Japan"),
        new ConnectPair("Peking",  "Kina"),
        new ConnectPair("Seul",    "J. Koreja"),
        new ConnectPair("Bangkok", "Tajland"),
        new ConnectPair("Hanoi",   "Vijetnam"),
    };

    private static final ConnectPair[] ROUND2 = {
        new ConnectPair("Jen",  "Japan"),
        new ConnectPair("Juan", "Kina"),
        new ConnectPair("Vón",  "J. Koreja"),
        new ConnectPair("Bat",  "Tajland"),
        new ConnectPair("Dong", "Vijetnam"),
    };

    private ConnectRepository() {}

    public static ConnectRepository getInstance() {
        if (instance == null) instance = new ConnectRepository();
        return instance;
    }

    public List<ConnectPair> getRound1Pairs() {
        List<ConnectPair> list = new ArrayList<>();
        for (ConnectPair p : ROUND1) list.add(p);
        return list;
    }

    public List<ConnectPair> getRound2Pairs() {
        List<ConnectPair> list = new ArrayList<>();
        for (ConnectPair p : ROUND2) list.add(p);
        return list;
    }
}
