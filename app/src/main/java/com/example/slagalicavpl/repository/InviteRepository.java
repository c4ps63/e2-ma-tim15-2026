package com.example.slagalicavpl.repository;

import com.example.slagalicavpl.model.GameInvite;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Firebase Realtime DB putanja: invites/{receiverUid}/{inviteId}
 */
public class InviteRepository {

    public interface InviteListCallback {
        void onLoaded(List<GameInvite> invites);
    }

    public interface Callback {
        void onSuccess();
        void onError(String msg);
    }

    private static final String DB_URL =
            "https://slagalica-vrtlogalica-default-rtdb.europe-west1.firebasedatabase.app";

    private static InviteRepository instance;
    private final DatabaseReference invitesRef;

    private InviteRepository() {
        invitesRef = FirebaseDatabase.getInstance(DB_URL).getReference("invites");
    }

    public static InviteRepository getInstance() {
        if (instance == null) instance = new InviteRepository();
        return instance;
    }

    /** Šalje poziv primaocu. */
    public void sendInvite(GameInvite invite, Callback cb) {
        invitesRef.child(invite.receiverUid).child(invite.inviteId)
                .setValue(invite)
                .addOnSuccessListener(v -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /** Sluša za dolazne pozive u realnom vremenu. */
    public ValueEventListener listenForInvites(String myUid, InviteListCallback cb) {
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<GameInvite> list = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    GameInvite inv = child.getValue(GameInvite.class);
                    if (inv != null && "pending".equals(inv.status)) list.add(inv);
                }
                cb.onLoaded(list);
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        invitesRef.child(myUid).addValueEventListener(listener);
        return listener;
    }

    public void removeListener(String myUid, ValueEventListener listener) {
        invitesRef.child(myUid).removeEventListener(listener);
    }

    /** Ažurira status poziva ("accepted" ili "declined"). */
    public void respondToInvite(String receiverUid, String inviteId, String status, Callback cb) {
        invitesRef.child(receiverUid).child(inviteId).child("status")
                .setValue(status)
                .addOnSuccessListener(v -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /** Briše poziv (po prihvatanju ili odbijanju). */
    public void deleteInvite(String receiverUid, String inviteId) {
        invitesRef.child(receiverUid).child(inviteId).removeValue();
    }
}
