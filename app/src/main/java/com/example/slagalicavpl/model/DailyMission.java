package com.example.slagalicavpl.model;

public class DailyMission {
    public final String  id;
    public final String  title;
    public final boolean completed;

    public DailyMission(String id, String title, boolean completed) {
        this.id        = id;
        this.title     = title;
        this.completed = completed;
    }
}
