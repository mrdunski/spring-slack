package com.leanforge.game.slack.listener;

import java.lang.annotation.*;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SlackMessageRegexGroup {
    int value();
}
