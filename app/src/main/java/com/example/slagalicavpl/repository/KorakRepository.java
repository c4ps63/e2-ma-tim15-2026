package com.example.slagalicavpl.repository;

import com.example.slagalicavpl.model.KorakPuzzle;

import java.util.Random;

public class KorakRepository {

    private static KorakRepository instance;

    private static final KorakPuzzle[] PUZZLES = {
        new KorakPuzzle("MESEC", new String[]{
            "UTIČE NA PLIME I OSEKE",
            "ORBITA OKO ZEMLJE",
            "NEIL ARMSTRONG",
            "SATELIT ZEMLJE",
            "SUNČEV SISTEM",
            "NOĆNO NEBO",
            "LUNA"
        }),
        new KorakPuzzle("PARIZ", new String[]{
            "MODNI TJEDAN",
            "EFELOV TORANJ",
            "REKA SENA",
            "LUVR",
            "BAGET I KROASAN",
            "GLAVNI GRAD FRANCUSKE",
            "GRAD SVJETLOSTI"
        }),
        new KorakPuzzle("AMAZON", new String[]{
            "NAJVEĆA SLIVNA OBLAST NA SVIJETU",
            "KIŠNA ŠUMA",
            "JUŽNA AMERIKA",
            "MANAUS",
            "PIRANHA",
            "NAJDUŽA REKA PO OBIMU VODE",
            "BRAZIL"
        })
    };

    private final Random rng = new Random();

    private KorakRepository() {}

    public static KorakRepository getInstance() {
        if (instance == null) instance = new KorakRepository();
        return instance;
    }

    public KorakPuzzle getRandomPuzzle() {
        return PUZZLES[rng.nextInt(PUZZLES.length)];
    }
}
