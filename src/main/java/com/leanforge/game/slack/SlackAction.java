package com.leanforge.game.slack;

public class SlackAction {
    private final String type;
    private final String name;
    private final String text;
    private final String value;

    public SlackAction(String type, String name, String text, String value) {
        this.type = type;
        this.name = name;
        this.text = text;
        this.value = value;
    }

    public static SlackAction button(String name, String text, String value) {
        return new SlackAction(com.ullink.slack.simpleslackapi.SlackAction.TYPE_BUTTON, name, text, value);
    }

    com.ullink.slack.simpleslackapi.SlackAction libSlackAction() {
        return new com.ullink.slack.simpleslackapi.SlackAction(name, text, type, value);
    }
}
