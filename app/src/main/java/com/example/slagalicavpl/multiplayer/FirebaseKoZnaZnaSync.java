package com.example.slagalicavpl.multiplayer;

import com.example.slagalicavpl.model.Question;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class FirebaseKoZnaZnaSync implements KoZnaZnaSync {

    private final DatabaseReference roomRef;
    private final String            myRole;   // "p1" or "p2"
    private final String            oppRole;

    private ValueEventListener activeListener;
    private DatabaseReference  activeRef;

    public FirebaseKoZnaZnaSync(DatabaseReference roomRef, String myRole) {
        this.roomRef = roomRef;
        this.myRole  = myRole;
        this.oppRole = myRole.equals("p1") ? "p2" : "p1";
    }

    @Override
    public void sendAnswer(int questionIndex, char option, long elapsedMs) {
        Map<String, Object> data = new HashMap<>();
        data.put("option",    String.valueOf(option));
        data.put("elapsedMs", elapsedMs);
        roomRef.child("koznaZna").child(String.valueOf(questionIndex))
               .child(myRole).setValue(data);
    }

    @Override
    public void listenForOpponentAnswer(int questionIndex, Question question,
                                        AnswerCallback callback) {
        activeRef = roomRef.child("koznaZna")
                           .child(String.valueOf(questionIndex))
                           .child(oppRole);

        activeListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snap) {
                if (!snap.exists()) return;
                String optStr   = snap.child("option").getValue(String.class);
                Long   elapsed  = snap.child("elapsedMs").getValue(Long.class);
                if (optStr == null || elapsed == null) return;
                activeRef.removeEventListener(this);
                activeListener = null;
                callback.onReceived(optStr.charAt(0), elapsed);
            }
            @Override
            public void onCancelled(DatabaseError e) {}
        };
        activeRef.addValueEventListener(activeListener);
    }

    @Override
    public void cancel() {
        if (activeListener != null && activeRef != null) {
            activeRef.removeEventListener(activeListener);
            activeListener = null;
            activeRef      = null;
        }
    }
}
