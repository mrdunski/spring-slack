package com.leanforge.game.slack.listener;

public class SlackThreadMessageResponse {
    private final String message;


    public SlackThreadMessageResponse(String message) {
        if (message == null || message.isEmpty()) {
            throw new IllegalArgumentException("Message can't be empty");
        }
        this.message = message;
    }

    String getMessage() {
        return message;
    }
}
