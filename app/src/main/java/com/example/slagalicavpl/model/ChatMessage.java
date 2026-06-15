package com.example.slagalicavpl.model;

public class ChatMessage {
    public String id;
    public String senderId;
    public String senderName;
    public String text;
    public long   timestamp;

    public ChatMessage() {}

    public ChatMessage(String senderId, String senderName, String text) {
        this.senderId   = senderId;
        this.senderName = senderName;
        this.text       = text;
        this.timestamp  = System.currentTimeMillis();
    }
}
