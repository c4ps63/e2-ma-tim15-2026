package com.example.slagalicavpl.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.model.AppNotification;

public final class NotificationChannels {

    public static final String CH_CHAT    = "ch_chat";
    public static final String CH_RANKING = "ch_ranking";
    public static final String CH_REWARD  = "ch_reward";
    public static final String CH_OTHER   = "ch_other";

    private NotificationChannels() {}

    /** Kreira sva 4 kanala — poziva se jednom pri pokretanju aplikacije. */
    public static void createAll(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(channel(CH_CHAT,
                "Čet", "Poruke u četu", Color.parseColor("#7CC6FF")));
        nm.createNotificationChannel(channel(CH_RANKING,
                "Rangiranje", "Obaveštenja o rang listi", Color.parseColor("#FFD23F")));
        nm.createNotificationChannel(channel(CH_REWARD,
                "Nagrade", "Nagrade i žetoni", Color.parseColor("#21C87A")));
        nm.createNotificationChannel(channel(CH_OTHER,
                "Ostalo", "Ostala sistemska obaveštenja", Color.parseColor("#1F4FA8")));
    }

    /** Vraća ID kanala koji odgovara tipu notifikacije. */
    public static String idFor(AppNotification.Channel ch) {
        switch (ch) {
            case CHAT:    return CH_CHAT;
            case RANKING: return CH_RANKING;
            case REWARD:  return CH_REWARD;
            default:      return CH_OTHER;
        }
    }

    /** Šalje push notifikaciju na pravi kanal. Ignoriše SecurityException ako dozvola nije data. */
    public static void sendPush(Context ctx, AppNotification n, int notifId) {
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, idFor(n.channel))
                .setSmallIcon(R.drawable.ic_planet)
                .setContentTitle(n.title)
                .setContentText(n.body)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);
        try {
            NotificationManagerCompat.from(ctx).notify(notifId, b.build());
        } catch (SecurityException ignored) { /* dozvola nije odobrena */ }
    }

    // ── private ───────────────────────────────────────────────────────────────

    private static NotificationChannel channel(String id, String name, String desc, int light) {
        NotificationChannel ch = new NotificationChannel(
                id, name, NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription(desc);
        ch.enableLights(true);
        ch.setLightColor(light);
        return ch;
    }
}
