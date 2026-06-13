package com.example.slagalicavpl.multiplayer;

import android.os.Handler;
import android.os.Looper;

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
    private final Handler      handler = new Handler(Looper.getMainLooper());

    public FirebaseKoZnaZnaSync(DatabaseReference roomRef, String myRole) {
        this.roomRef = roomRef;
        this.myRole  = myRole;
        this.oppRole = myRole.equals("p1") ? "p2" : "p1";
    }

    public interface QuestionOrderCallback { void onOrder(int[] indices); }

    /** P1 writes the shared question indices so both players see the same questions. */
    public void writeQuestionOrder(int[] indices) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < indices.length; i++) m.put(String.valueOf(i), indices[i]);
        roomRef.child("koznaZna").child("questionOrder").setValue(m);
    }

    /** P2 reads the question order written by P1. Retries every 300ms until available. */
    public void readQuestionOrder(QuestionOrderCallback cb) {
        roomRef.child("koznaZna").child("questionOrder")
               .addListenerForSingleValueEvent(new ValueEventListener() {
                   @Override public void onDataChange(DataSnapshot snap) {
                       if (!snap.exists()) {
                           handler.postDelayed(() -> readQuestionOrder(cb), 300);
                           return;
                       }
                       int[] indices = new int[5];
                       for (int i = 0; i < 5; i++) {
                           Object v = snap.child(String.valueOf(i)).getValue();
                           indices[i] = v instanceof Number ? ((Number) v).intValue() : 0;
                       }
                       handler.post(() -> cb.onOrder(indices));
                   }
                   @Override public void onCancelled(DatabaseError e) {}
               });
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
