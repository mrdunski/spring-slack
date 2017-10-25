package com.leanforge.game.slack;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class SlackHealthIndicator implements HealthIndicator {

    @Autowired
    SlackService slackService;


    @Override
    public Health health() {
        if (slackService.isConnected()) {
            return Health.up().build();
        }
        return Health.down().build();
    }
}
