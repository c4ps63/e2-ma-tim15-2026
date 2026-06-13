package com.example.slagalicavpl.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.slagalicavpl.model.AppNotification;
import com.example.slagalicavpl.notification.NotificationChannels;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class NotificationRepository {

    private static final String PREFS    = "app_notifications";
    private static final String KEY_IDS  = "notif_ids";
    private static final String PFX      = "notif_";

    private static NotificationRepository instance;

    private final SharedPreferences prefs;
    private final Context           appCtx;
    private final AtomicInteger     pushCounter = new AtomicInteger(2000);

    private NotificationRepository(Context ctx) {
        appCtx = ctx.getApplicationContext();
        prefs  = appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        seedIfEmpty();
    }

    public static synchronized NotificationRepository getInstance(Context ctx) {
        if (instance == null) instance = new NotificationRepository(ctx);
        return instance;
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /** Sve notifikacije sortirane od najnovije. */
    public List<AppNotification> getAll() {
        List<AppNotification> list = loadAll();
        Collections.sort(list, (a, b) -> Long.compare(b.timestamp, a.timestamp));
        return list;
    }

    /** Samo nepročitane, od najnovije. */
    public List<AppNotification> getUnread() {
        List<AppNotification> out = new ArrayList<>();
        for (AppNotification n : getAll()) if (!n.read) out.add(n);
        return out;
    }

    public int getUnreadCount() {
        return getUnread().size();
    }

    /** Označava jednu notifikaciju kao pročitanu. */
    public void markRead(String id) {
        prefs.edit().putBoolean(PFX + id + "_read", true).apply();
    }

    /** Označava sve kao pročitane. */
    public void markAllRead() {
        SharedPreferences.Editor ed = prefs.edit();
        for (String id : getIds()) ed.putBoolean(PFX + id + "_read", true);
        ed.apply();
    }

    /**
     * Dodaje novu notifikaciju u historiju I šalje push obaveštenje.
     * Poziva se npr. kad server pošalje notifikaciju igraču.
     */
    public void add(AppNotification n) {
        save(n);
        NotificationChannels.sendPush(appCtx, n, pushCounter.getAndIncrement());
    }

    /** Dodaje bez push-a (koristi se za seedovanje). */
    public void addSilent(AppNotification n) {
        save(n);
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    private List<AppNotification> loadAll() {
        List<AppNotification> list = new ArrayList<>();
        for (String id : getIds()) {
            String json = prefs.getString(PFX + id, null);
            if (json == null) continue;
            try {
                AppNotification n = AppNotification.fromJson(json);
                // read stanje je odvojeno radi lakog ažuriranja
                n.read = prefs.getBoolean(PFX + id + "_read", n.read);
                list.add(n);
            } catch (JSONException ignored) {}
        }
        return list;
    }

    private void save(AppNotification n) {
        Set<String> ids = new LinkedHashSet<>(getIds());
        ids.add(n.id);
        SharedPreferences.Editor ed = prefs.edit();
        try { ed.putString(PFX + n.id, n.toJson()); } catch (JSONException ignored) {}
        ed.putBoolean(PFX + n.id + "_read", n.read);
        ed.putStringSet(KEY_IDS, ids);
        ed.apply();
    }

    private Set<String> getIds() {
        return new LinkedHashSet<>(prefs.getStringSet(KEY_IDS, new HashSet<>()));
    }

    // ── Seed data (demo) ─────────────────────────────────────────────────────

    private void seedIfEmpty() {
        if (!getIds().isEmpty()) return;

        long now = System.currentTimeMillis();

        // ČET — nepročitana
        AppNotification n1 = AppNotification.create(
                AppNotification.Channel.CHAT,
                "MARKO: Hoćeš revanš?",
                "Nova poruka od Marko Petrović",
                "chat");
        n1.timestamp = now - 2 * 60_000L;

        // RANG — nepročitana
        AppNotification n2 = AppNotification.create(
                AppNotification.Channel.RANKING,
                "Ušao si u TOP 10!",
                "Trenutno si na 8. mestu nedeljne rang liste",
                "ranking");
        n2.timestamp = now - 60 * 60_000L;

        // NAGRADA — nepročitana
        AppNotification n3 = AppNotification.create(
                AppNotification.Channel.REWARD,
                "Primio si +5 žetona!",
                "Nedeljna nagrada za 1. mesto u regionu",
                "reward");
        n3.timestamp = now - 3 * 3600_000L;

        // OSTALO — pročitana
        AppNotification n4 = AppNotification.create(
                AppNotification.Channel.OTHER,
                "Dobrodošao u Ligu 3!",
                "Napredovao si u narednu ligu. Zarađuješ +3 žetona dnevno");
        n4.timestamp = now - 24 * 3600_000L;
        n4.read = true;

        // Poziv prijatelja — nepročitana
        AppNotification n5 = AppNotification.create(
                AppNotification.Channel.OTHER,
                "ANA te poziva na partiju!",
                "Ana Jovanović je poslala poziv za prijateljsku partiju",
                "friend_invite");
        n5.timestamp = now - 30 * 60_000L;

        addSilent(n1);
        addSilent(n2);
        addSilent(n3);
        addSilent(n4);
        addSilent(n5);
    }
}
