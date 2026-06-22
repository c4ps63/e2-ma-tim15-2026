package com.example.slagalicavpl.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.slagalicavpl.model.AppNotification;
import com.example.slagalicavpl.notification.NotificationChannels;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class NotificationRepository {

    private static final String PREFS      = "app_notifications";
    private static final String KEY_IDS    = "notif_ids";
    private static final String PFX        = "notif_";
    private static final String KEY_VER    = "notif_data_version";
    /** Promenom ove vrednosti brišu se svi stari (mokovani) podaci pri sledećem pokretanju. */
    private static final String DATA_VER   = "v2_real";
    private static final String DB_URL   =
            "https://slagalica-vrtlogalica-default-rtdb.europe-west1.firebasedatabase.app";

    private static NotificationRepository instance;

    private final SharedPreferences prefs;
    private final Context           appCtx;
    private final AtomicInteger     pushCounter = new AtomicInteger(2000);

    private DatabaseReference    remoteRef;
    private ChildEventListener   remoteListener;

    private NotificationRepository(Context ctx) {
        appCtx = ctx.getApplicationContext();
        prefs  = appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        clearLegacyDataIfNeeded();
    }

    /** Briše stare mokovane podatke ako je verzija zastarela. */
    private void clearLegacyDataIfNeeded() {
        if (DATA_VER.equals(prefs.getString(KEY_VER, ""))) return;
        prefs.edit().clear().putString(KEY_VER, DATA_VER).apply();
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

    /** Vraća true ako notifikacija sa datim ID-om već postoji. */
    public boolean containsId(String id) {
        return getIds().contains(id);
    }

    /** Dodaje notifikaciju samo ako ne postoji (deduplication). */
    public void addIfAbsent(AppNotification n) {
        if (!containsId(n.id)) addSilent(n);
    }

    /**
     * Počinje Firebase RTDB listener za putanju notifications/{uid}.
     * Svaki novi child koji dođe sa servera (od drugog klijenta ili cloud funkcije)
     * automatski se dodaje u lokalnu historiju.
     * Bezbedno je pozvati više puta — registruje se samo jednom.
     */
    public void listenRemote(String uid) {
        if (remoteListener != null) return;
        remoteRef = FirebaseDatabase.getInstance(DB_URL)
                .getReference("notifications")
                .child(uid);
        remoteListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                AppNotification n = parseRemote(snapshot);
                if (n != null && !containsId(n.id)) add(n);
            }
            @Override public void onChildChanged(DataSnapshot s, String p) {}
            @Override public void onChildRemoved(DataSnapshot s) {}
            @Override public void onCancelled(DatabaseError e) {}
            @Override public void onChildMoved(DataSnapshot s, String p) {}
        };
        remoteRef.addChildEventListener(remoteListener);
    }

    /** Uklanja Firebase listener. Poziva se iz onDestroy aktivnosti. */
    public void stopRemoteListener() {
        if (remoteRef != null && remoteListener != null) {
            remoteRef.removeEventListener(remoteListener);
            remoteListener = null;
        }
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

    // ── Firebase remote parser ───────────────────────────────────────────────

    private AppNotification parseRemote(DataSnapshot snap) {
        try {
            String key     = snap.getKey();
            String channel = snap.child("channel").getValue(String.class);
            String title   = snap.child("title").getValue(String.class);
            String body    = snap.child("body").getValue(String.class);
            String action  = snap.child("action").getValue(String.class);
            Long   ts      = snap.child("timestamp").getValue(Long.class);
            if (key == null || title == null || channel == null) return null;
            AppNotification n = AppNotification.create(
                    AppNotification.Channel.valueOf(channel),
                    title, body != null ? body : "");
            n.id        = key;
            n.action    = action != null ? action : "";
            n.timestamp = ts != null ? ts : System.currentTimeMillis();
            return n;
        } catch (Exception ignored) {
            return null;
        }
    }

}
