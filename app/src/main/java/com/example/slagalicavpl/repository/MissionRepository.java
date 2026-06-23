package com.example.slagalicavpl.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.slagalicavpl.model.AppNotification;
import com.example.slagalicavpl.model.DailyMission;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MissionRepository {

    public static final String MISSION_WIN_GAME       = "win_game";
    public static final String MISSION_SEND_CHAT      = "send_chat";
    public static final String MISSION_PLAY_FRIENDLY  = "play_friendly";
    public static final String MISSION_WIN_TOURNAMENT = "win_tournament";

    private static final List<String> ALL_IDS = Arrays.asList(
            MISSION_WIN_GAME, MISSION_SEND_CHAT,
            MISSION_PLAY_FRIENDLY, MISSION_WIN_TOURNAMENT);

    private static final String PREFS_NAME = "daily_missions";
    private static final String KEY_DATE   = "date";
    private static final String KEY_BONUS  = "bonus_claimed";
    private static final String PFX_DONE   = "done_";

    private static MissionRepository instance;
    private final SharedPreferences   prefs;

    private MissionRepository(Context ctx) {
        prefs = ctx.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        resetIfNewDay();
    }

    public static synchronized MissionRepository getInstance(Context ctx) {
        if (instance == null) instance = new MissionRepository(ctx);
        return instance;
    }

    // ── Stanje misija ─────────────────────────────────────────────────────────

    public boolean isComplete(String id) {
        return prefs.getBoolean(PFX_DONE + id, false);
    }

    public boolean isBonusClaimed() {
        return prefs.getBoolean(KEY_BONUS, false);
    }

    public int getCompletedCount() {
        int n = 0;
        for (String id : ALL_IDS) if (isComplete(id)) n++;
        return n;
    }

    public List<DailyMission> getAll() {
        List<DailyMission> list = new ArrayList<>();
        list.add(new DailyMission(MISSION_WIN_GAME,       "Pobedi partiju",               isComplete(MISSION_WIN_GAME)));
        list.add(new DailyMission(MISSION_SEND_CHAT,      "Pošalji poruku u čet",         isComplete(MISSION_SEND_CHAT)));
        list.add(new DailyMission(MISSION_PLAY_FRIENDLY,  "Odigraj prijateljsku partiju", isComplete(MISSION_PLAY_FRIENDLY)));
        list.add(new DailyMission(MISSION_WIN_TOURNAMENT, "Pobedi partiju u turniru",     isComplete(MISSION_WIN_TOURNAMENT)));
        return list;
    }

    // ── Ispunjavanje misije ───────────────────────────────────────────────────

    public interface MissionCallback {
        void onCompleted(boolean bonusEarned);
        void onAlreadyDone();
    }

    public void complete(String missionId, MissionCallback cb) {
        resetIfNewDay();
        if (isComplete(missionId)) { cb.onAlreadyDone(); return; }
        prefs.edit().putBoolean(PFX_DONE + missionId, true).apply();
        // Proveri da li je ovo četvrta ispunjena misija
        boolean bonusEarned = getCompletedCount() == 4 && !isBonusClaimed();
        if (bonusEarned) prefs.edit().putBoolean(KEY_BONUS, true).apply();
        cb.onCompleted(bonusEarned);
    }

    /**
     * Udobna metoda: ispuni misiju, dodeli 3 zvezde, pošalji REWARD notifikaciju.
     * Ako su sve 4 ispunjene, isplati bonus (+2 žetona, +3 zvezde).
     */
    public static void tryComplete(Context ctx, String uid, String missionId) {
        if (uid == null || uid.isEmpty()) return;
        MissionRepository repo = getInstance(ctx);
        repo.complete(missionId, new MissionCallback() {
            @Override
            public void onCompleted(boolean bonusEarned) {
                UserRepository.getInstance().addStars(uid, 3);
                NotificationRepository.getInstance(ctx).add(AppNotification.create(
                        AppNotification.Channel.REWARD,
                        "Misija ispunjena: " + missionTitle(missionId),
                        "+3 zvezde za ispunjenu dnevnu misiju.",
                        "reward"));
                if (bonusEarned) {
                    UserRepository.getInstance().addStars(uid, 3);
                    UserRepository.getInstance().addTokens(uid, 2);
                    NotificationRepository.getInstance(ctx).add(AppNotification.create(
                            AppNotification.Channel.REWARD,
                            "Sve dnevne misije ispunjene!",
                            "Bonus nagrada: +3 zvezde i +2 žetona.",
                            "reward"));
                }
            }
            @Override public void onAlreadyDone() {}
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void resetIfNewDay() {
        String today  = today();
        String stored = prefs.getString(KEY_DATE, "");
        if (!today.equals(stored)) {
            prefs.edit().clear().putString(KEY_DATE, today).apply();
        }
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    private static String missionTitle(String id) {
        switch (id) {
            case MISSION_WIN_GAME:       return "Pobedi partiju";
            case MISSION_SEND_CHAT:      return "Pošalji poruku u čet";
            case MISSION_PLAY_FRIENDLY:  return "Odigraj prijateljsku partiju";
            case MISSION_WIN_TOURNAMENT: return "Pobedi u turniru";
            default:                     return id;
        }
    }
}
