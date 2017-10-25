package com.leanforge.game.slack.listener;

import java.lang.annotation.*;

/**
 * Marks message content param of slack listener.
 * It only makes sense in listeners for message events.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SlackMessageContent {
}
