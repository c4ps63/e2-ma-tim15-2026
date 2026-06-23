package com.example.slagalicavpl.model;

public class RankingEntry {
    public String uid;
    public String username;
    public String region;
    public long   stars;  // zvezde zaradjene u tekucem ciklusu
    public int    league; // liga u trenutku poslednjeg azuriranja

    public RankingEntry() {}
}
