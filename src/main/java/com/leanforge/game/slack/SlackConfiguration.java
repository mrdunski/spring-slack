package com.leanforge.game.slack;

import com.ullink.slack.simpleslackapi.SlackSession;
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SlackConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SlackSession slackSession(@Value("${slack.token}") String slackToken) {
        return SlackSessionFactory.createWebSocketSlackSession(slackToken);
    }
}
