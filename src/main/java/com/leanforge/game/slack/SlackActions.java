package com.leanforge.game.slack;

import com.ullink.slack.simpleslackapi.SlackAttachment;

import java.util.UUID;
import java.util.stream.Stream;

public class SlackActions {
    private final String title;
    private final String text;
    private final String fallback;
    private final String color;
    private final SlackAction[] actions;
    private final String callbackId = UUID.randomUUID().toString();

    public SlackActions(String title, String text, String fallback, String color, SlackAction... actions) {
        this.title = title;
        this.text = text;
        this.fallback = fallback;
        this.color = color;
        this.actions = actions;
    }

    public String getTitle() {
        return title;
    }

    public String getText() {
        return text;
    }

    public String getFallback() {
        return fallback;
    }

    public String getColor() {
        return color;
    }

    public SlackAction[] getActions() {
        return actions;
    }

    public String getCallbackId() {
        return callbackId;
    }

    SlackAttachment toAttachment() {
        SlackAttachment attachment = new SlackAttachment(title, fallback, text, "");
        Stream.of(actions)
                .map(SlackAction::libSlackAction)
                .forEach(attachment::addAction);
        attachment.setColor(color);
        attachment.setCallbackId(callbackId);
        return attachment;
    }
}
