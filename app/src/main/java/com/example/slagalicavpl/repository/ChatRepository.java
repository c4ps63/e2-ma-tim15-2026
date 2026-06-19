package com.example.slagalicavpl.repository;

import com.example.slagalicavpl.model.ChatMessage;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.List;

public class ChatRepository {

    public interface MessagesCallback {
        void onMessages(List<ChatMessage> messages);
    }

    private static ChatRepository instance;
    private final FirebaseFirestore db;

    private ChatRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public static ChatRepository getInstance() {
        if (instance == null) instance = new ChatRepository();
        return instance;
    }

    /**
     * Šalje poruku u čet sobe regiona.
     * Region se koristi kao ID dokumenta u kolekciji "chat".
     */
    public void sendMessage(String region, ChatMessage msg,
                            UserRepository.Callback cb) {
        DocumentReference ref = db.collection("chat")
                .document(region)
                .collection("messages")
                .document();
        msg.id = ref.getId();
        ref.set(msg)
           .addOnSuccessListener(v -> cb.onSuccess())
           .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /**
     * Otvara real-time listener za poruke regiona.
     * Vrača ListenerRegistration koji treba da se ukloni pri zatvaranju ekrana.
     */
    public ListenerRegistration listenMessages(String region,
                                               MessagesCallback cb) {
        return db.collection("chat")
                .document(region)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, err) -> {
                    if (err != null || snap == null) return;
                    List<ChatMessage> list = snap.toObjects(ChatMessage.class);
                    cb.onMessages(list);
                });
    }
}
