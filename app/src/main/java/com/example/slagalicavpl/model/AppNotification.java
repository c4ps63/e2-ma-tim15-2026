package com.example.slagalicavpl.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class AppNotification {

    public enum Channel {
        CHAT, RANKING, REWARD, OTHER;

        public String label() {
            switch (this) {
                case CHAT:    return "ČET";
                case RANKING: return "RANG";
                case REWARD:  return "NAGRADA";
                default:      return "OSTALO";
            }
        }
    }

    public String  id;
    public Channel channel;
    public String  title;
    public String  body;
    public long    timestamp;   // System.currentTimeMillis()
    public boolean read;
    /** Opcioni akcion ključ (npr. "chat", "ranking") za kasniju reakciju. */
    public String  action;

    public static AppNotification create(Channel channel, String title, String body) {
        AppNotification n = new AppNotification();
        n.id        = UUID.randomUUID().toString();
        n.channel   = channel;
        n.title     = title;
        n.body      = body;
        n.timestamp = System.currentTimeMillis();
        n.read      = false;
        n.action    = "";
        return n;
    }

    public static AppNotification create(Channel channel, String title, String body, String action) {
        AppNotification n = create(channel, title, body);
        n.action = action;
        return n;
    }

    public String toJson() throws JSONException {
        JSONObject j = new JSONObject();
        j.put("id",        id);
        j.put("channel",   channel.name());
        j.put("title",     title);
        j.put("body",      body);
        j.put("timestamp", timestamp);
        j.put("read",      read);
        j.put("action",    action != null ? action : "");
        return j.toString();
    }

    public static AppNotification fromJson(String json) throws JSONException {
        JSONObject j  = new JSONObject(json);
        AppNotification n = new AppNotification();
        n.id        = j.getString("id");
        n.channel   = Channel.valueOf(j.getString("channel"));
        n.title     = j.getString("title");
        n.body      = j.getString("body");
        n.timestamp = j.getLong("timestamp");
        n.read      = j.getBoolean("read");
        n.action    = j.optString("action", "");
        return n;
    }
}
