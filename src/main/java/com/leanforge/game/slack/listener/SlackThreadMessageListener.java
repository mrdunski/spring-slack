package com.leanforge.game.slack.listener;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SlackThreadMessageListener {
    /**
     * @return regular expression of handled message
     */
    String value();
}
