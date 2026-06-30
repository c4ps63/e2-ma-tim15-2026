package com.example.slagalicavpl.notification;

import androidx.annotation.NonNull;

import com.example.slagalicavpl.model.AppNotification;
import com.example.slagalicavpl.repository.NotificationRepository;
import com.example.slagalicavpl.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;
import java.util.UUID;

/**
 * Prima FCM push poruke i prikazuje ih kao sistemsku notifikaciju —
 * radi i kad aplikacija nije pokrenuta (proces ugašen), za razliku od
 * NotificationRepository.listenRemote koji zahteva živ Firebase listener.
 * Koristi isti notifications/{uid}/{notifId} id kao RTDB zapis, pa ako je
 * korisnik bio u aplikaciji i notifikacija je već prikazana preko
 * listenRemote-a, ovde se preskače da ne bi došlo do duplikata.
 */
public class AppFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        if (fbUser != null) {
            UserRepository.getInstance().saveFcmToken(fbUser.getUid(), token);
        }
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);

        Map<String, String> data = message.getData();
        String id      = data.containsKey("id") ? data.get("id") : UUID.randomUUID().toString();
        String channel = data.getOrDefault("channel", "OTHER");
        String title   = data.containsKey("title") ? data.get("title") : "Slagalica Vrtloga";
        String body    = data.containsKey("body")  ? data.get("body")  : "";

        NotificationRepository repo = NotificationRepository.getInstance(this);
        if (repo.containsId(id)) return;

        AppNotification n = AppNotification.create(
                AppNotification.Channel.valueOf(channel), title, body, data.getOrDefault("action", ""));
        n.id = id;
        repo.add(n);
    }
}
