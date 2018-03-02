package com.leanforge.game.slack;

import com.ullink.slack.simpleslackapi.SlackAttachment;

import java.util.stream.Stream;

public class SlackActions {
    private final String title;
    private final String text;
    private final String fallback;
    private final String color;
    private final SlackAction[] actions;

    public SlackActions(String title, String text, String fallback, String color, SlackAction... actions) {
        this.title = title;
        this.text = text;
        this.fallback = fallback;
        this.color = color;
        this.actions = actions;
    }


    SlackAttachment toAttachment() {
        SlackAttachment attachment = new SlackAttachment(title, fallback, text, "x");
        Stream.of(actions)
                .map(SlackAction::libSlackAction)
                .forEach(attachment::addAction);
        attachment.setColor(color);
        return attachment;
    }
}
