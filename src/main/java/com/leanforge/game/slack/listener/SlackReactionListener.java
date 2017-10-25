package com.leanforge.game.slack.listener;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SlackReactionListener {

    /**
     * @return reaction code
     */
    String value();
    Action action() default Action.ADD;

    enum Action {
        ADD, REMOVE
    }
}
