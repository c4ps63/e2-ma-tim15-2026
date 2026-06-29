package com.example.slagalicavpl.model;

public class User {
    public String uid;
    public String username;
    public String email;
    public String region;
    public String avatarColor;
    public int gamesPlayed;
    public int gamesWon;
    public int tokens;
    public int stars;
    public int kzzCorrect;
    public int kzzTotal;
    public int spojniceConnected;
    public int spojniceTotal;
    public int asocijacijeSolved;
    public int asocijacijeTotal;
    public int skockoCorrect;
    public int skockoTotal;
    public int korakCorrect;
    public int korakTotal;
    public int    mojBrojCorrect;
    public int    mojBrojTotal;
    public String lastTokenDate;           // "yyyy-MM-dd" datum poslednjeg dnevnog bonusa
    public String lastWeeklyRewardCycle;  // npr. "2026-W25" — poslednji nedeljni ciklus za koji je nagrada isplaćena
    public String lastMonthlyRewardCycle; // npr. "2026-06"  — poslednji mesečni ciklus za koji je nagrada isplaćena

    // Mesečni ciklus — format "yyyy-MM", npr. "2026-06"
    public String cycleId;
    public int    cycleStars;
    // Unix timestamp ms — za praćenje aktivnih igrača
    public long   lastSeen;

    public User() {}

    public User(String uid, String username, String email, String region) {
        this.uid      = uid;
        this.username = username;
        this.email    = email;
        this.region   = region;
        this.tokens   = 5;
    }
}
