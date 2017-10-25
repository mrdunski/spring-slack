package com.leanforge.game.slack.listener;

import java.lang.annotation.*;

/**
 * Marks user id param of slack listener.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SlackUserId {
}
