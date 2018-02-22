package com.leanforge.game.slack.listener;

import java.util.Arrays;

public class SlackReactionResponse {
    private final String[] reactionCodes;


    public SlackReactionResponse(String... reactionCodes) {
        this.reactionCodes = Arrays.copyOf(reactionCodes, reactionCodes.length);
    }

    String[] getReactionCodes() {
        return reactionCodes;
    }
}
