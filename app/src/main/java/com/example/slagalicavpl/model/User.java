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
    public int mojBrojCorrect;
    public int mojBrojTotal;

    public User() {}

    public User(String uid, String username, String email, String region) {
        this.uid      = uid;
        this.username = username;
        this.email    = email;
        this.region   = region;
        this.tokens   = 5; // svaki igrač dobija 5 tokena pri registraciji
    }
}
