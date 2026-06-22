package com.example.slagalicavpl.model;

import java.util.HashMap;
import java.util.Map;

public class Challenge {
    public String  challengeId;
    public String  creatorUid;
    public String  creatorName;
    public String  region;
    public int     stakeStars;   // max 10
    public int     stakeTokens;  // max 2
    /** "open" | "in_progress" | "finished" */
    public String  status;
    public long    createdAt;
    public Map<String, ChallengePlayer> players = new HashMap<>();

    public Challenge() {}

    public Challenge(String challengeId, String creatorUid, String creatorName,
                     String region, int stakeStars, int stakeTokens) {
        this.challengeId  = challengeId;
        this.creatorUid   = creatorUid;
        this.creatorName  = creatorName;
        this.region       = region;
        this.stakeStars   = stakeStars;
        this.stakeTokens  = stakeTokens;
        this.status       = "open";
        this.createdAt    = System.currentTimeMillis();
    }

    public static class ChallengePlayer {
        public String  name;
        public int     score;
        public boolean finished;
        public long    joinedAt;

        public ChallengePlayer() {}

        public ChallengePlayer(String name) {
            this.name     = name;
            this.score    = 0;
            this.finished = false;
            this.joinedAt = System.currentTimeMillis();
        }
    }

    public int playerCount()   { return players == null ? 0 : players.size(); }
    public boolean isFull()    { return playerCount() >= 4; }
    public boolean isOpen()    { return "open".equals(status); }
}
