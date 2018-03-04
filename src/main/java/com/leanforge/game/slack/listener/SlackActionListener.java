package com.leanforge.game.slack.listener;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SlackActionListener {
    String actionName();
    String actionValue() default "*";
}
