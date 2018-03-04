package com.leanforge.game.slack;

import java.io.Serializable;
import java.time.Instant;

public class SlackMessage implements Serializable {

    private String timestamp;
    private String channelId;
    private String senderId;
    private Instant createdOn = Instant.now();

    @Deprecated
    public SlackMessage(String timestamp, String channelId, String senderId) {
        this.timestamp = timestamp;
        this.channelId = channelId;
        this.senderId = senderId;
    }

    public SlackMessage(String timestamp, String channelId) {
        this.timestamp = timestamp;
        this.channelId = channelId;
    }

    @Deprecated
    public String getSenderId() {
        return senderId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getChannelId() {
        return channelId;
    }

    public Instant getCreatedOn() {
        return createdOn;
    }
}
