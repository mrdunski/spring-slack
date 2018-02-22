package com.leanforge.game.slack.listener;

public class SlackMessageResponse {
    private final String message;


    public SlackMessageResponse(String message) {
        if (message == null || message.isEmpty()) {
            throw new IllegalArgumentException("Message can't be empty");
        }
        this.message = message;
    }

    String getMessage() {
        return message;
    }
}
