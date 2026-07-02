package com.example.slagalicavpl.repository;

import com.example.slagalicavpl.model.Question;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class QuestionRepository {

    private static QuestionRepository instance;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static final String COLLECTION = "koznazna_sets";

    public interface QuestionSetCallback {
        void onLoaded(String setId, List<Question> questions);
        default void onError(String msg) {}
    }

    private QuestionRepository() {}

    public static QuestionRepository getInstance() {
        if (instance == null) instance = new QuestionRepository();
        return instance;
    }

    /** Učitava nasumičan set pitanja iz Firestore-a. Ako je kolekcija prazna, sijeje seed podatke. */
    public void loadRandomSet(QuestionSetCallback cb) {
        db.collection(COLLECTION).get()
          .addOnSuccessListener(qs -> {
              if (qs.isEmpty()) {
                  seedAndLoad(cb);
                  return;
              }
              List<DocumentSnapshot> docs = qs.getDocuments();
              DocumentSnapshot chosen = docs.get(new Random().nextInt(docs.size()));
              cb.onLoaded(chosen.getId(), parseQuestions(chosen));
          })
          .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /** Učitava konkretan set po ID-u (P2 u multiplayeru). */
    public void loadSetById(String setId, QuestionSetCallback cb) {
        db.collection(COLLECTION).document(setId).get()
          .addOnSuccessListener(doc -> {
              if (!doc.exists()) { loadRandomSet(cb); return; }
              cb.onLoaded(setId, parseQuestions(doc));
          })
          .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /** Vraća nasumičan redosljed indeksa za zadani broj pitanja. */
    public static int[] shuffleIndices(int size) {
        List<Integer> pool = new ArrayList<>();
        for (int i = 0; i < size; i++) pool.add(i);
        Collections.shuffle(pool);
        int[] result = new int[size];
        for (int i = 0; i < size; i++) result[i] = pool.get(i);
        return result;
    }

    /** Prepoređuje pitanja prema datim indeksima (zajednički redosljed za oba igrača). */
    public static List<Question> reorderByIndices(List<Question> questions, int[] indices) {
        List<Question> result = new ArrayList<>();
        for (int idx : indices) {
            if (idx >= 0 && idx < questions.size()) result.add(questions.get(idx));
        }
        return result;
    }

    // ── Parsiranje Firestore dokumenta ────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<Question> parseQuestions(DocumentSnapshot doc) {
        List<Question> result = new ArrayList<>();
        List<Map<String, Object>> list =
                (List<Map<String, Object>>) doc.get("questions");
        if (list == null) return result;
        for (Map<String, Object> m : list) {
            try {
                String text    = (String) m.get("text");
                String a       = (String) m.get("a");
                String b       = (String) m.get("b");
                String c       = (String) m.get("c");
                String d       = (String) m.get("d");
                String correct = (String) m.get("correct");
                if (text == null || correct == null) continue;
                result.add(new Question(text, a, b, c, d, correct.charAt(0)));
            } catch (Exception ignored) {}
        }
        return result;
    }

    // ── Seed: upisuje početna pitanja ako je kolekcija prazna ─────────────────

    private void seedAndLoad(QuestionSetCallback cb) {
        Map<String, List<Map<String, Object>>> sets = buildSeedData();
        final String[] setIds = sets.keySet().toArray(new String[0]);
        final int[] written = {0};

        for (String setId : setIds) {
            Map<String, Object> data = new HashMap<>();
            data.put("questions", sets.get(setId));
            db.collection(COLLECTION).document(setId).set(data)
              .addOnCompleteListener(t -> {
                  written[0]++;
                  if (written[0] == setIds.length) {
                      String picked = setIds[new Random().nextInt(setIds.length)];
                      loadSetById(picked, cb);
                  }
              });
        }
    }

    private Map<String, List<Map<String, Object>>> buildSeedData() {
        Map<String, List<Map<String, Object>>> sets = new HashMap<>();

        // Set 01 — Geografija Evrope (A)
        List<Map<String, Object>> s1 = new ArrayList<>();
        s1.add(q("Koji grad je prestonica Francuske?",
                "Barselona", "Pariz", "Brisel", "Rim", "B"));
        s1.add(q("Koja je najduža reka u Evropi po obimu vode?",
                "Rajna", "Dunav", "Volga", "Temza", "C"));
        s1.add(q("Koji je najviši vrh Alpa?",
                "Matterhorn", "Grossglockner", "Jungfrau", "Mont Blanc", "D"));
        s1.add(q("U kojoj zemlji se nalazi Akropolj?",
                "Italija", "Turska", "Grčka", "Albanija", "C"));
        s1.add(q("Koja je najmanja država u Evropi?",
                "Monako", "San Marino", "Lihtenštajn", "Vatikan", "D"));
        sets.put("set_01", s1);

        // Set 02 — Geografija Evrope (B)
        List<Map<String, Object>> s2 = new ArrayList<>();
        s2.add(q("Kojom rekom je okružena Budimpešta?",
                "Sava", "Dunav", "Rajna", "Tiber", "B"));
        s2.add(q("Koji je glavni grad Španije?",
                "Barselona", "Sevilja", "Valensija", "Madrid", "D"));
        s2.add(q("Na kojoj reci leži London?",
                "Sena", "Rajna", "Temza", "Tiber", "C"));
        s2.add(q("Koji je glavni grad Grčke?",
                "Solun", "Atina", "Sparta", "Korint", "B"));
        s2.add(q("Koji je glavni grad Norveške?",
                "Stokholm", "Helsinki", "Oslo", "Kopenhagen", "C"));
        sets.put("set_02", s2);

        // Set 03 — Opšte znanje
        List<Map<String, Object>> s3 = new ArrayList<>();
        s3.add(q("Koji je zvanični jezik Brazila?",
                "Španski", "Engleski", "Francuski", "Portugalski", "D"));
        s3.add(q("Koliko država graniči sa Njem ačkom?",
                "7", "8", "9", "10", "C"));
        s3.add(q("U kojoj zemlji se nalazi Koloseum?",
                "Španija", "Grčka", "Italija", "Portugal", "C"));
        s3.add(q("Koji kontinent prostorno pretežno zauzima Rusija?",
                "Evropa", "Azija", "Arktik", "Podjednako", "B"));
        s3.add(q("Koja planeta je najbliža Suncu?",
                "Venera", "Zemlja", "Merkur", "Mars", "C"));
        sets.put("set_03", s3);

        return sets;
    }

    private Map<String, Object> q(String text, String a, String b,
                                   String c, String d, String correct) {
        Map<String, Object> m = new HashMap<>();
        m.put("text",    text);
        m.put("a",       a);
        m.put("b",       b);
        m.put("c",       c);
        m.put("d",       d);
        m.put("correct", correct);
        return m;
    }
}
