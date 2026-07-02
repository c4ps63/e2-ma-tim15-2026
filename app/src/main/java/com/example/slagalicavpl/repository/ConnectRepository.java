package com.example.slagalicavpl.repository;

import com.example.slagalicavpl.model.ConnectPair;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ConnectRepository {

    private static ConnectRepository instance;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static final String COLLECTION = "spojnice_sets";

    public interface ConnectSetCallback {
        void onLoaded(String setId, List<ConnectPair> round1, List<ConnectPair> round2);
        default void onError(String msg) {}
    }

    private ConnectRepository() {}

    public static ConnectRepository getInstance() {
        if (instance == null) instance = new ConnectRepository();
        return instance;
    }

    /** Učitava nasumičan set parova iz Firestore-a. Ako je kolekcija prazna, sijeje seed. */
    public void loadRandomSet(ConnectSetCallback cb) {
        db.collection(COLLECTION).get()
          .addOnSuccessListener(qs -> {
              if (qs.isEmpty()) {
                  seedAndLoad(cb);
                  return;
              }
              List<DocumentSnapshot> docs = qs.getDocuments();
              DocumentSnapshot chosen = docs.get(new Random().nextInt(docs.size()));
              cb.onLoaded(chosen.getId(),
                          parsePairs(chosen, "round1"),
                          parsePairs(chosen, "round2"));
          })
          .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /** Učitava konkretan set po ID-u (P2 u multiplayeru). */
    public void loadSetById(String setId, ConnectSetCallback cb) {
        db.collection(COLLECTION).document(setId).get()
          .addOnSuccessListener(doc -> {
              if (!doc.exists()) { loadRandomSet(cb); return; }
              cb.onLoaded(setId,
                          parsePairs(doc, "round1"),
                          parsePairs(doc, "round2"));
          })
          .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    // ── Parsiranje ────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<ConnectPair> parsePairs(DocumentSnapshot doc, String field) {
        List<ConnectPair> result = new ArrayList<>();
        List<Map<String, Object>> list = (List<Map<String, Object>>) doc.get(field);
        if (list == null) return result;
        for (Map<String, Object> m : list) {
            try {
                result.add(new ConnectPair((String) m.get("left"), (String) m.get("right")));
            } catch (Exception ignored) {}
        }
        return result;
    }

    // ── Seed ──────────────────────────────────────────────────────────────────

    private void seedAndLoad(ConnectSetCallback cb) {
        Map<String, Object[]> sets = buildSeedData();
        final String[] ids = sets.keySet().toArray(new String[0]);
        final int[] done = {0};

        for (String id : ids) {
            Object[] rds = sets.get(id);
            Map<String, Object> data = new HashMap<>();
            data.put("round1", rds[0]);
            data.put("round2", rds[1]);
            db.collection(COLLECTION).document(id).set(data)
              .addOnCompleteListener(t -> {
                  done[0]++;
                  if (done[0] == ids.length) {
                      String picked = ids[new Random().nextInt(ids.length)];
                      loadSetById(picked, cb);
                  }
              });
        }
    }

    private Map<String, Object[]> buildSeedData() {
        Map<String, Object[]> sets = new HashMap<>();

        // Set 01 — Azijski gradovi / valute
        sets.put("set_01", new Object[]{
            pairList(p("Tokio","Japan"), p("Peking","Kina"),
                     p("Seul","J. Koreja"), p("Bangkok","Tajland"), p("Hanoi","Vijetnam")),
            pairList(p("Jen","Japan"), p("Juan","Kina"),
                     p("Vón","J. Koreja"), p("Bat","Tajland"), p("Dong","Vijetnam"))
        });

        // Set 02 — Evropski gradovi / znamenitosti
        sets.put("set_02", new Object[]{
            pairList(p("Pariz","Francuska"), p("Berlin","Njemačka"),
                     p("Madrid","Španija"), p("Rim","Italija"), p("Atina","Grčka")),
            pairList(p("Ajfelov toranj","Pariz"), p("Koloseum","Rim"),
                     p("Akropolj","Atina"), p("Brandenb. vrata","Berlin"),
                     p("Sagrada Família","Barselona"))
        });

        // Set 03 — Životinje / stanište
        sets.put("set_03", new Object[]{
            pairList(p("Lav","Afrika"), p("Kengur","Australija"),
                     p("Panda","Azija"), p("Kondor","J. Amerika"), p("Polarni medvjed","Arktik")),
            pairList(p("Savana","Afrika"), p("Tajga","Azija"),
                     p("Tundra","Arktik"), p("Prašuma","J. Amerika"), p("Outback","Australija"))
        });

        return sets;
    }

    private Map<String, Object> p(String left, String right) {
        Map<String, Object> m = new HashMap<>();
        m.put("left",  left);
        m.put("right", right);
        return m;
    }

    private List<Map<String, Object>> pairList(Map<String, Object>... pairs) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> p : pairs) list.add(p);
        return list;
    }
}
