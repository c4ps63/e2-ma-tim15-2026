package com.example.slagalicavpl.model;

public class GameInvite {
    public String inviteId;
    public String senderUid;
    public String senderName;
    public String receiverUid;
    /** "pending", "accepted", "declined" */
    public String status;
    public long   timestamp;

    public GameInvite() {}

    public GameInvite(String inviteId, String senderUid, String senderName, String receiverUid) {
        this.inviteId    = inviteId;
        this.senderUid   = senderUid;
        this.senderName  = senderName;
        this.receiverUid = receiverUid;
        this.status      = "pending";
        this.timestamp   = System.currentTimeMillis();
    }
}
