package com.example.slagalicavpl.model;

public class Tournament {
    public String status;       // "waiting", "semifinal", "final", "done"
    public int    playerCount;
    public TournamentPlayer p1, p2, p3, p4;
    public String semi1Winner;  // uid or "" if not set
    public String semi2Winner;
    public String finalWinner;

    public Tournament() {}

    /** Returns 1–4 for the given uid, or -1 if not found. */
    public int slotOf(String uid) {
        if (p1 != null && uid.equals(p1.uid)) return 1;
        if (p2 != null && uid.equals(p2.uid)) return 2;
        if (p3 != null && uid.equals(p3.uid)) return 3;
        if (p4 != null && uid.equals(p4.uid)) return 4;
        return -1;
    }

    public TournamentPlayer playerAt(int slot) {
        switch (slot) { case 1: return p1; case 2: return p2;
                        case 3: return p3; case 4: return p4; default: return null; }
    }

    /** Slots 1-2 → semi1, slots 3-4 → semi2. */
    public static String phaseForSlot(int slot) {
        return (slot <= 2) ? "semi1" : "semi2";
    }

    /** Slot 1 or 3 → "p1" in the room, slot 2 or 4 → "p2". */
    public static String roleForSlot(int slot) {
        return (slot == 1 || slot == 3) ? "p1" : "p2";
    }

    public static class TournamentPlayer {
        public String uid;
        public String username;
        public String avatarColor;
        public int    league;
        public TournamentPlayer() {}
    }
}
