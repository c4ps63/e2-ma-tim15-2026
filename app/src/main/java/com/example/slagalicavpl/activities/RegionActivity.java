package com.example.slagalicavpl.activities;

import android.graphics.Color;
import android.graphics.PointF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.model.SerbiaRegions;
import com.example.slagalicavpl.model.User;
import com.example.slagalicavpl.repository.UserRepository;
import com.example.slagalicavpl.service.AuthService;
import com.example.slagalicavpl.views.SerbiaMapView;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class RegionActivity extends AppCompatActivity {

    private SerbiaMapView mapView;
    private LinearLayout  llRegionList;
    private TextView      tvCycleLabel;

    private String currentUserRegion = null;
    private List<UserRepository.RegionEntry> ranking = new ArrayList<>();

    // Collected dots per region (normalized 0-1 points)
    private final Map<String, List<PointF>> allDots = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_region);

        mapView      = findViewById(R.id.serbiaMapView);
        llRegionList = findViewById(R.id.llRegionList);
        tvCycleLabel = findViewById(R.id.tvCycleLabel);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Tekući ciklus u naslovu (npr. "JUN 2026")
        String cycle = new SimpleDateFormat("MMM yyyy", new Locale("sr")).format(new Date()).toUpperCase();
        tvCycleLabel.setText(cycle);

        // Klik na region iz mape
        mapView.setRegionClickListener(this::showRegionDialog);

        // Učitaj profil trenutnog korisnika → znamo koji region da označimo
        FirebaseUser me = AuthService.getInstance().getCurrentUser();
        if (me != null) {
            UserRepository.getInstance().loadProfile(me.getUid(), new UserRepository.ProfileCallback() {
                @Override public void onLoaded(User u) {
                    currentUserRegion = u.region;
                    mapView.setMyRegion(currentUserRegion);
                    loadRanking();
                }
                @Override public void onError(String msg) { loadRanking(); }
            });
        } else {
            loadRanking();
        }
    }

    // ── Učitavanje rang liste ─────────────────────────────────────────────────

    private void loadRanking() {
        UserRepository.getInstance().loadRegionRanking(new UserRepository.RegionRankingCallback() {
            @Override
            public void onLoaded(List<UserRepository.RegionEntry> list) {
                ranking = list;
                checkAndSaveCycleResults(list);
                buildRegionList(list);
                loadAllDots(list);
            }
            @Override
            public void onError(String msg) {
                Toast.makeText(RegionActivity.this, "Greška pri učitavanju: " + msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Ako je ciklus prošao, sačuvaj top-3 regiona u app_state/last_cycle
    private void checkAndSaveCycleResults(List<UserRepository.RegionEntry> list) {
        UserRepository.getInstance().loadLastCycleResults((storedCycle, gold, silver, bronze) -> {
            String prevCycle = previousCycleId();
            if (prevCycle.equals(storedCycle)) return; // već sačuvano za prethodni ciklus

            // Prethodni ciklus nije snimljen — snimimo trenutni poredak kao "prethodni"
            // (u realnoj app-i ovo bi se triggerilovalo na kraju ciklusa; ovdje se radi best-effort)
            String g = list.size() > 0 ? list.get(0).regionId : null;
            String s = list.size() > 1 ? list.get(1).regionId : null;
            String b = list.size() > 2 ? list.get(2).regionId : null;
            UserRepository.getInstance().saveLastCycleResults(g, s, b, prevCycle);
        });
    }

    // ── Gradnja liste regiona ─────────────────────────────────────────────────

    private void buildRegionList(List<UserRepository.RegionEntry> list) {
        llRegionList.removeAllViews();

        // Ubaci u mapu za brže traženje
        Map<String, Integer> starsMap = new HashMap<>();
        for (UserRepository.RegionEntry e : list) starsMap.put(e.regionId, e.cycleStars);

        // Prikaži sve regione sortirane po cycleStars (list je već sortiran)
        // Dopuni regionima koji nisu u listi (0 zvjezdica)
        List<UserRepository.RegionEntry> display = new ArrayList<>(list);
        for (String id : SerbiaRegions.all().keySet()) {
            if (starsMap.get(id) == null) display.add(new UserRepository.RegionEntry(id, 0));
        }

        float dp = getResources().getDisplayMetrics().density;

        for (int i = 0; i < display.size(); i++) {
            UserRepository.RegionEntry entry = display.get(i);
            SerbiaRegions.Region region = SerbiaRegions.get(entry.regionId);
            if (region == null) continue;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            int padV = (int)(8 * dp), padH = (int)(6 * dp);
            row.setPadding(padH, padV, padH, padV);

            boolean isMine = entry.regionId.equals(currentUserRegion);
            if (isMine) {
                row.setBackgroundColor(Color.parseColor("#22FFD23F"));
            }

            // Rang
            TextView tvRank = new TextView(this);
            tvRank.setText(medalFor(i));
            tvRank.setTextSize(18);
            tvRank.setWidth((int)(36 * dp));
            row.addView(tvRank);

            // Ikonica regiona
            TextView tvIcon = new TextView(this);
            tvIcon.setText(region.icon);
            tvIcon.setTextSize(20);
            tvIcon.setPadding((int)(4*dp), 0, (int)(8*dp), 0);
            row.addView(tvIcon);

            // Ime regiona
            TextView tvName = new TextView(this);
            tvName.setText(region.displayName + (isMine ? "  ◀ MOJ" : ""));
            tvName.setTextColor(Color.parseColor("#102341"));
            tvName.setTypeface(null, android.graphics.Typeface.BOLD);
            tvName.setTextSize(13);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tvName.setLayoutParams(lp);
            row.addView(tvName);

            // Broj zvjezdica
            TextView tvStars = new TextView(this);
            tvStars.setText("⭐ " + entry.cycleStars);
            tvStars.setTextColor(Color.parseColor("#102341"));
            tvStars.setTypeface(null, android.graphics.Typeface.BOLD);
            tvStars.setTextSize(13);
            tvStars.setGravity(Gravity.END);
            row.addView(tvStars);

            final String rid = entry.regionId;
            row.setOnClickListener(v -> showRegionDialog(rid));
            row.setClickable(true);
            row.setFocusable(true);

            llRegionList.addView(row);

            // Tanki separator
            if (i < display.size() - 1) {
                View sep = new View(this);
                sep.setBackgroundColor(Color.parseColor("#22102341"));
                llRegionList.addView(sep, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
            }
        }
    }

    private String medalFor(int index) {
        switch (index) {
            case 0: return "🥇";
            case 1: return "🥈";
            case 2: return "🥉";
            default: return (index + 1) + ".";
        }
    }

    // ── Učitavanje tačaka korisnika za svaki region ──────────────────────────

    private void loadAllDots(List<UserRepository.RegionEntry> list) {
        // Skupljamo pozive za sve regione, crtamo kad stignu svi
        List<String> regionIds = new ArrayList<>(SerbiaRegions.all().keySet());
        AtomicInteger pending = new AtomicInteger(regionIds.size());

        for (String regionId : regionIds) {
            UserRepository.getInstance().loadUserDotsForRegion(regionId,
                    new UserRepository.RegionDotsCallback() {
                @Override
                public void onLoaded(List<String> uids) {
                    SerbiaRegions.Region r = SerbiaRegions.get(regionId);
                    List<PointF> dots = new ArrayList<>();
                    if (r != null) {
                        for (String uid : uids) dots.add(r.getDotForUser(uid));
                    }
                    allDots.put(regionId, dots);
                    if (pending.decrementAndGet() == 0) {
                        mapView.setUserDots(allDots);
                    }
                }
                @Override
                public void onError(String msg) {
                    allDots.put(regionId, new ArrayList<>());
                    if (pending.decrementAndGet() == 0) mapView.setUserDots(allDots);
                }
            });
        }
    }

    // ── Dialog statistike regiona ─────────────────────────────────────────────

    private void showRegionDialog(String regionId) {
        SerbiaRegions.Region r = SerbiaRegions.get(regionId);
        if (r == null) return;

        // Pronađi zvjezdice iz rang liste
        int stars = 0;
        for (UserRepository.RegionEntry e : ranking)
            if (e.regionId.equals(regionId)) { stars = e.cycleStars; break; }
        final int finalStars = stars;

        UserRepository.getInstance().loadRegionStats(regionId,
                new UserRepository.RegionStatsCallback() {
            @Override
            public void onLoaded(int registered, int active, int first, int second, int third) {
                String msg = r.icon + "  " + r.displayName + "\n\n"
                        + "⭐  Zvjezdice ovog ciklusa:  " + finalStars + "\n\n"
                        + "🥇  Prva mjesta:      " + first  + "\n"
                        + "🥈  Druga mjesta:    " + second + "\n"
                        + "🥉  Treća mjesta:    " + third  + "\n\n"
                        + "👤  Registrovanih:  " + registered + "\n"
                        + "🟢  Aktivnih (24h): " + active;

                new AlertDialog.Builder(RegionActivity.this)
                        .setTitle("Statistika regiona")
                        .setMessage(msg)
                        .setPositiveButton("Zatvori", null)
                        .show();
            }
            @Override
            public void onError(String msg) {
                Toast.makeText(RegionActivity.this, "Greška: " + msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── Pomoćne metode ────────────────────────────────────────────────────────

    private static String previousCycleId() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.MONTH, -1);
        return new SimpleDateFormat("yyyy-MM", Locale.US).format(cal.getTime());
    }
}
