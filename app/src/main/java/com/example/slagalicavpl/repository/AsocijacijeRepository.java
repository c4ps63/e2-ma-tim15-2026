package com.example.slagalicavpl.repository;

import com.example.slagalicavpl.model.AsocijacijePuzzle;

public class AsocijacijeRepository {

    private static final AsocijacijePuzzle[] PUZZLES = {

        // ── Runda 1 · Tema: BOJE ────────────────────────────────────────────
        new AsocijacijePuzzle(
            new String[][] {
                {"RUŽA",   "KRV",     "VATRA",   "JABUKA"},   // A → CRVENA
                {"TRAVA",  "ŽABA",    "ŠUMA",    "SMARAGD"},  // B → ZELENA
                {"NEBO",   "MORE",    "LED",     "SAFIR"},    // C → PLAVA
                {"LIMUN",  "SUNCE",   "PŠENICA", "BANANA"},   // D → ŽUTA
            },
            new String[]{"CRVENA", "ZELENA", "PLAVA", "ŽUTA"},
            "BOJE"
        ),

        // ── Runda 2 · Tema: SPORT ───────────────────────────────────────────
        new AsocijacijePuzzle(
            new String[][] {
                {"GOL",    "DRES",    "PENALI",  "STADION"},  // A → FUDBAL
                {"KOŠ",    "PARKET",  "LOPTICA", "SKOK"},     // B → KOŠARKA
                {"REKET",  "MREŽA",   "SERVIS",  "GREN"},     // C → TENIS
                {"BAZEN",  "PRSNO",   "KROL",    "KAPU"},     // D → PLIVANJE
            },
            new String[]{"FUDBAL", "KOŠARKA", "TENIS", "PLIVANJE"},
            "SPORT"
        ),
    };

    public static AsocijacijePuzzle getRound1() { return PUZZLES[0]; }
    public static AsocijacijePuzzle getRound2()  { return PUZZLES[1]; }
}
