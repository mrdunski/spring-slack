package com.leanforge.game.slack.listener;

import java.lang.annotation.*;

/**
 * Marks channel id param of slack listener.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface SlackChannelId {
}
