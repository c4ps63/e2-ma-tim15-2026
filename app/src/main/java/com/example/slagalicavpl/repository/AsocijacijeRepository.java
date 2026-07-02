package com.example.slagalicavpl.repository;

import com.example.slagalicavpl.model.AsocijacijePuzzle;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class AsocijacijeRepository {

    private static AsocijacijeRepository instance;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static final String COLLECTION = "asocijacije_puzzles";

    public interface AsocijacijeSetCallback {
        void onLoaded(String setId, AsocijacijePuzzle round1, AsocijacijePuzzle round2);
        default void onError(String msg) {}
    }

    private AsocijacijeRepository() {}

    public static AsocijacijeRepository getInstance() {
        if (instance == null) instance = new AsocijacijeRepository();
        return instance;
    }

    /** Učitava dve nasumične (različite) asocijacije iz Firestore-a za rundu 1 i 2.
     *  Ako je kolekcija prazna, sijeje seed podatke. */
    public void loadRandomSet(AsocijacijeSetCallback cb) {
        db.collection(COLLECTION).get()
          .addOnSuccessListener(qs -> {
              if (qs.isEmpty()) {
                  seedAndLoad(cb);
                  return;
              }
              List<DocumentSnapshot> docs = qs.getDocuments();
              pickRandomPair(docs, cb);
          })
          .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /** Učitava konkretan par asocijacija po ID-u "docId1_docId2" (P2 u multiplayeru). */
    public void loadSetById(String setId, AsocijacijeSetCallback cb) {
        String[] ids = setId.split("_", 2);
        if (ids.length != 2) { loadRandomSet(cb); return; }

        db.collection(COLLECTION).document(ids[0]).get()
          .addOnSuccessListener(doc1 -> db.collection(COLLECTION).document(ids[1]).get()
              .addOnSuccessListener(doc2 -> {
                  if (!doc1.exists() || !doc2.exists()) { loadRandomSet(cb); return; }
                  cb.onLoaded(setId, parsePuzzle(doc1), parsePuzzle(doc2));
              })
              .addOnFailureListener(e -> cb.onError(e.getMessage())))
          .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    private void pickRandomPair(List<DocumentSnapshot> docs, AsocijacijeSetCallback cb) {
        List<DocumentSnapshot> pool = new ArrayList<>(docs);
        java.util.Collections.shuffle(pool);
        DocumentSnapshot d1 = pool.get(0);
        DocumentSnapshot d2 = pool.size() > 1 ? pool.get(1) : pool.get(0);
        String setId = d1.getId() + "_" + d2.getId();
        cb.onLoaded(setId, parsePuzzle(d1), parsePuzzle(d2));
    }

    // ── Parsiranje Firestore dokumenta ────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private AsocijacijePuzzle parsePuzzle(DocumentSnapshot doc) {
        String finalSolution = doc.getString("finalSolution");
        List<Map<String, Object>> columns = (List<Map<String, Object>>) doc.get("columns");

        String[][] cells = new String[4][4];
        String[] colSolutions = new String[4];
        if (columns != null) {
            for (int c = 0; c < columns.size() && c < 4; c++) {
                Map<String, Object> col = columns.get(c);
                colSolutions[c] = (String) col.get("solution");
                List<String> words = (List<String>) col.get("words");
                if (words != null) {
                    for (int r = 0; r < words.size() && r < 4; r++) cells[c][r] = words.get(r);
                }
            }
        }
        return new AsocijacijePuzzle(cells, colSolutions, finalSolution);
    }

    // ── Seed: upisuje početne asocijacije ako je kolekcija prazna ─────────────

    private void seedAndLoad(AsocijacijeSetCallback cb) {
        Map<String, Object> puzzles = buildSeedData();
        final String[] ids = puzzles.keySet().toArray(new String[0]);
        final int[] written = {0};

        for (String id : ids) {
            db.collection(COLLECTION).document(id).set((Map<String, Object>) puzzles.get(id))
              .addOnCompleteListener(t -> {
                  written[0]++;
                  if (written[0] == ids.length) loadRandomSet(cb);
              });
        }
    }

    private Map<String, Object> buildSeedData() {
        Map<String, Object> puzzles = new HashMap<>();

        // Tema: BOJE (nepromenjeno)
        puzzles.put("boje", puzzle("BOJE",
            column("CRVENA", "RUŽA",  "KRV",    "VATRA",   "JABUKA"),
            column("ZELENA", "TRAVA", "ŽABA",   "ŠUMA",    "SMARAGD"),
            column("PLAVA",  "NEBO",  "MORE",   "LED",     "SAFIR"),
            column("ŽUTA",   "LIMUN", "SUNCE",  "PŠENICA", "BANANA")
        ));

        // Tema: SPORT (nepromenjeno)
        puzzles.put("sport", puzzle("SPORT",
            column("FUDBAL",   "GOL",   "DRES",   "PENALI",  "STADION"),
            column("KOŠARKA",  "KOŠ",   "PARKET", "LOPTICA", "SKOK"),
            column("TENIS",    "REKET", "MREŽA",  "SERVIS",  "GREN"),
            column("PLIVANJE", "BAZEN", "PRSNO",  "KROL",    "KAPU")
        ));

        // Tema: ŽIVOTINJE (nova kombinacija)
        puzzles.put("zivotinje", puzzle("ŽIVOTINJE",
            column("LAV",    "GRIVA",   "SAVANA", "RIKA",    "KRALJ"),
            column("SLON",   "SURLA",   "KLJOVE", "UŠI",     "AFRIKA"),
            column("TIGAR",  "PRUGE",   "MAČKA",  "DŽUNGLA", "LOVAC"),
            column("MEDVED", "BERLOGA", "MED",    "KRZNO",   "ZIMA")
        ));

        return puzzles;
    }

    private Map<String, Object> puzzle(String finalSolution, Map<String, Object>... columns) {
        Map<String, Object> m = new HashMap<>();
        m.put("finalSolution", finalSolution);
        m.put("columns", Arrays.asList(columns));
        return m;
    }

    private Map<String, Object> column(String solution, String... words) {
        Map<String, Object> m = new HashMap<>();
        m.put("solution", solution);
        m.put("words", Arrays.asList(words));
        return m;
    }
}
