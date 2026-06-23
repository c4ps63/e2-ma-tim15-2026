package com.example.slagalicavpl.repository;

import androidx.annotation.NonNull;

import com.example.slagalicavpl.model.RankingEntry;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;

public class RankingRepository {

    public interface RankingCallback {
        void onLoaded(List<RankingEntry> sorted);
    }

    private static final String DB_URL = "https://slagalica-vrtlogalica-default-rtdb.europe-west1.firebasedatabase.app";
    private static final String[] SR_MONTHS = {
            "jan", "feb", "mar", "apr", "maj", "jun",
            "jul", "avg", "sep", "okt", "nov", "dec"
    };

    private static RankingRepository instance;
    private final DatabaseReference   entriesRef;

    private RankingRepository() {
        entriesRef = FirebaseDatabase.getInstance(DB_URL).getReference("rankingEntries");
    }

    public static RankingRepository getInstance() {
        if (instance == null) instance = new RankingRepository();
        return instance;
    }

    // ── Cycle keys ────────────────────────────────────────────────────────────

    public static String getCurrentWeekKey()   { return weekKey(0);  }
    public static String getPreviousWeekKey()  { return weekKey(-1); }
    public static String getCurrentMonthKey()  { return monthKey(0);  }
    public static String getPreviousMonthKey() { return monthKey(-1); }

    private static String weekKey(int offset) {
        LocalDate date = LocalDate.now().plusWeeks(offset);
        int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int year = date.get(IsoFields.WEEK_BASED_YEAR);
        return year + "-W" + String.format("%02d", week);
    }

    private static String monthKey(int offset) {
        LocalDate date = LocalDate.now().plusMonths(offset);
        return date.getYear() + "-" + String.format("%02d", date.getMonthValue());
    }

    /** "16. jun – 22. jun 2026." */
    public static String weekDateRange(String weekKey) {
        try {
            String[] p    = weekKey.split("-W");
            int year      = Integer.parseInt(p[0]);
            int week      = Integer.parseInt(p[1]);
            LocalDate monday = LocalDate.now()
                    .with(IsoFields.WEEK_BASED_YEAR,            year)
                    .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR,    week)
                    .with(DayOfWeek.MONDAY);
            LocalDate sunday   = monday.plusDays(6);
            String startMonth  = SR_MONTHS[monday.getMonthValue() - 1];
            String endMonth    = SR_MONTHS[sunday.getMonthValue()  - 1];
            int    endYear     = sunday.getYear();
            if (monday.getMonthValue() == sunday.getMonthValue())
                return monday.getDayOfMonth() + ". – " + sunday.getDayOfMonth()
                        + ". " + endMonth + " " + endYear + ".";
            return monday.getDayOfMonth() + ". " + startMonth
                    + " – " + sunday.getDayOfMonth() + ". " + endMonth + " " + endYear + ".";
        } catch (Exception e) {
            return weekKey;
        }
    }

    /** "1. – 30. jun 2026." */
    public static String monthDateRange(String monthKey) {
        try {
            String[] p = monthKey.split("-");
            int year   = Integer.parseInt(p[0]);
            int month  = Integer.parseInt(p[1]);
            LocalDate first   = LocalDate.of(year, month, 1);
            int       lastDay = first.lengthOfMonth();
            return "1. – " + lastDay + ". " + SR_MONTHS[month - 1] + " " + year + ".";
        } catch (Exception e) {
            return monthKey;
        }
    }

    // ── Token nagrade ─────────────────────────────────────────────────────────

    public static int weeklyTokenReward(int rank) {
        if (rank == 1)               return 5;
        if (rank == 2)               return 3;
        if (rank == 3)               return 2;
        if (rank >= 4 && rank <= 10) return 1;
        return 0;
    }

    public static int monthlyTokenReward(int rank) {
        if (rank == 1)               return 10;
        if (rank == 2)               return 6;
        if (rank == 3)               return 4;
        if (rank >= 4 && rank <= 10) return 2;
        return 0;
    }

    // ── Ažuriranje unosa ──────────────────────────────────────────────────────

    /**
     * Ažurira unos igrača u tekućem ciklusu.
     * starGain > 0 → pobeda (dodaje zvezde), starGain = 0 → registruje prisustvo bez promene zvezda.
     */
    public void updateEntry(String uid, String username, String region, int league, int starGain) {
        if (uid == null || starGain < 0) return;
        update(uid, username, region, league, starGain, "weekly",  getCurrentWeekKey());
        update(uid, username, region, league, starGain, "monthly", getCurrentMonthKey());
    }

    private void update(String uid, String username, String region, int league,
                        int gain, String type, String cycleKey) {
        DatabaseReference ref = entriesRef.child(type).child(cycleKey).child(uid);
        ref.runTransaction(new Transaction.Handler() {
            @NonNull @Override
            public Transaction.Result doTransaction(@NonNull MutableData data) {
                RankingEntry e = data.getValue(RankingEntry.class);
                if (e == null) e = new RankingEntry();
                e.uid      = uid;
                e.username = username != null ? username : "";
                e.region   = region   != null ? region   : "";
                e.league   = league   > 0     ? league   : 1;
                e.stars    = e.stars + gain;
                data.setValue(e);
                return Transaction.success(data);
            }
            @Override
            public void onComplete(DatabaseError err, boolean committed, DataSnapshot snap) {}
        });
    }

    // ── Učitavanje rang liste ─────────────────────────────────────────────────

    public void loadOnce(String type, String cycleKey, RankingCallback cb) {
        entriesRef.child(type).child(cycleKey)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        cb.onLoaded(toSorted(snapshot));
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        cb.onLoaded(new ArrayList<>());
                    }
                });
    }

    public ValueEventListener listenEntries(String type, String cycleKey, RankingCallback cb) {
        ValueEventListener listener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                cb.onLoaded(toSorted(snapshot));
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        entriesRef.child(type).child(cycleKey).addValueEventListener(listener);
        return listener;
    }

    public void removeListener(String type, String cycleKey, ValueEventListener listener) {
        if (listener != null)
            entriesRef.child(type).child(cycleKey).removeEventListener(listener);
    }

    private static List<RankingEntry> toSorted(DataSnapshot snapshot) {
        List<RankingEntry> list = new ArrayList<>();
        for (DataSnapshot child : snapshot.getChildren()) {
            RankingEntry e = child.getValue(RankingEntry.class);
            // Prikaži sve igrače koji su odigrali (čak i sa 0 zvezdica)
            if (e != null && e.uid != null) list.add(e);
        }
        list.sort((a, b) -> Long.compare(b.stars, a.stars));
        return list;
    }
}
