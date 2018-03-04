package com.leanforge.game.slack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping(value = "/slack/action", consumes = {"application/x-www-form-urlencoded", "application/json"})
public class ActionController {

    private static final Logger logger = LoggerFactory.getLogger(ActionController.class);

    @Autowired
    SlackService slackService;


    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping
    public void handleAction(@RequestParam Map<String, String> data) throws IOException {
        logger.debug("Received button event: {}", data.get("payload"));
        JsonNode payload = objectMapper.readTree(data.get("payload"));
        String userId = Optional.of(payload)
                .map(it -> it.get("user"))
                .map(it -> it.get("id"))
                .map(JsonNode::textValue)
                .orElseThrow(IllegalArgumentException::new);
        String actionName = Optional.of(payload)
                .map(it -> it.get("actions"))
                .map(it -> it.get(0))
                .map(it -> it.get("name"))
                .map(JsonNode::textValue)
                .orElseThrow(IllegalArgumentException::new);
        String actionValue = Optional.of(payload)
                .map(it -> it.get("actions"))
                .map(it -> it.get(0))
                .map(it -> it.get("value"))
                .map(JsonNode::textValue)
                .orElseThrow(IllegalArgumentException::new);
        String channelId = Optional.of(payload)
                .map(it -> it.get("channel"))
                .map(it -> it.get("id"))
                .map(JsonNode::textValue)
                .orElseThrow(IllegalArgumentException::new);
        String messageId = Optional.of(payload)
                .map(it -> it.get("message_ts"))
                .map(JsonNode::textValue)
                .orElseThrow(IllegalArgumentException::new);
        String callbackId = Optional.of(payload)
                .map(it -> it.get("callback_id"))
                .map(JsonNode::textValue)
                .orElseThrow(IllegalArgumentException::new);

        slackService.fireActionCallbacks(userId, new SlackMessage(messageId, channelId), actionName, actionValue, callbackId);
    }
}
