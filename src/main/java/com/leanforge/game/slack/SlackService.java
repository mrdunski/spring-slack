package com.leanforge.game.slack;

import com.ullink.slack.simpleslackapi.SlackChannel;
import com.ullink.slack.simpleslackapi.SlackMessageHandle;
import com.ullink.slack.simpleslackapi.SlackSession;
import com.ullink.slack.simpleslackapi.SlackUser;
import com.ullink.slack.simpleslackapi.replies.SlackMessageReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
public class SlackService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    SlackSession slackSession;

    @Scheduled(fixedDelay = 30000)
    public synchronized void refreshUsers() {
        openSession();
        slackSession.refetchUsers();
    }

    public synchronized SlackMessage sendChannelMessage(String channelId, String message, String... reactionCodes) {
        openSession();
        logger.debug("Sending message to: {}", channelId);
        SlackChannel channel = slackSession.findChannelById(channelId);
        SlackMessageHandle<SlackMessageReply> messageHandle = slackSession.sendMessage(channel, message);
        SlackMessage slackMessage = toChannelMessage(channel, messageHandle);
        addReactions(slackMessage, reactionCodes);

        return slackMessage;
    }

    public synchronized SlackMessage updateMessage(SlackMessage message, String text, String... reactionCodes) {
        openSession();
        logger.debug("Updating message: {} - {}", message.getChannelId(), message.getTimestamp());
        SlackChannel channel = slackSession.findChannelById(message.getChannelId());
        SlackMessageHandle<SlackMessageReply> messageHandle = slackSession.updateMessage(message.getTimestamp(), channel, text);
        SlackMessage slackMessage = toChannelMessage(channel, messageHandle);
        addReactions(slackMessage, reactionCodes);

        return slackMessage;
    }

    public synchronized void addReactions(SlackMessage slackMessage, String... reactionCodes) {
        openSession();

        SlackChannel channel = slackSession.findChannelById(slackMessage.getChannelId());

        for (String reactionCode : reactionCodes) {
            slackSession.addReactionToMessage(channel, slackMessage.getTimestamp(), reactionCode);
        }
    }

    public synchronized void addReactionListener(MessageReactionCallback callback) {
        openSession();
        logger.debug("Adding reaction listener {}", callback);
        slackSession.addReactionAddedListener((event, session) -> {
            if (event.getMessageID() == null || event.getChannel() == null || event.getUser().getId().equals(slackSession.sessionPersona().getId())) {
                return;
            }

            callback.handleReaction(new SlackMessage(event.getMessageID(), event.getChannel().getId(), null), event.getUser().getId(), event.getEmojiName());
        });
    }

    public synchronized void addRemoveReactionListener(MessageReactionCallback callback) {
        openSession();
        logger.debug("Adding reaction listener {}", callback);
        slackSession.addReactionRemovedListener((event, session) -> {
            if (event.getMessageID() == null || event.getChannel() == null || event.getUser().getId().equals(slackSession.sessionPersona().getId())) {
                return;
            }

            callback.handleReaction(new SlackMessage(event.getMessageID(), event.getChannel().getId(), null), event.getUser().getId(), event.getEmojiName());
        });
    }

    public synchronized void addChannelMessageListener(MessageCallback callback) {
        openSession();
        logger.debug("Adding direct message listener {}", callback);
        ConcurrentHashMap.KeySetView<SlackMessage, Boolean> handled = ConcurrentHashMap.newKeySet();
        slackSession.addMessagePostedListener((event, session) -> {
            if (event.getChannel().isDirect() || event.getSender().getId().equals(slackSession.sessionPersona().getId())) {
                return;
            }

            SlackMessage message = new SlackMessage(event.getTimestamp(), event.getChannel().getId(), event.getSender().getId());
            if (handled.add(message)) {
                callback.handleMessage(message, event.getMessageContent());
            }

            Instant limit = Instant.now().minus(15, ChronoUnit.MINUTES);
            handled.parallelStream()
                    .filter(it -> it.getCreatedOn().isBefore(limit))
                    .forEach(handled::remove);

        });
    }

    public synchronized ZoneId getUserTimezone(String userId) {
        openSession();
        SlackUser slackUser = slackSession.findUserById(userId);
        return ZoneId.of(slackUser.getTimeZone());
    }


    private void openSession() {
        if (slackSession.isConnected()) {
            return;
        }

        try {
            slackSession.connect();
        } catch (IOException e) {
            throw new IllegalStateException("Can't open slack session", e);
        }
    }

    private SlackMessage toChannelMessage(SlackChannel channel, SlackMessageHandle<SlackMessageReply> messageHandle) {
        String timestamp = messageHandle.getReply().getTimestamp();
        String channelId = channel.getId();

        return new SlackMessage(timestamp, channelId, null);
    }

    public String getRealNameByUsername(String username) {
        openSession();
        return slackSession.getUsers()
                .parallelStream()
                .filter(it -> it.getUserName().equals(username))
                .findAny()
                .map(SlackUser::getRealName)
                .orElse("Unknown");
    }

    public String getRealNameById(String userId) {
        openSession();
        return slackSession.getUsers()
                .parallelStream()
                .filter(it -> it.getId().equals(userId))
                .findAny()
                .map(SlackUser::getRealName)
                .orElse("Unknown");
    }

    public String getChannelName(String channelId) {
        openSession();
        return slackSession.findChannelById(channelId).getName();
    }

    public String getUsername(String userId) {
        return slackSession.findUserById(userId).getUserName();
    }

    public boolean isConnected() {
        return slackSession.isConnected();
    }

    @FunctionalInterface
    public interface MessageReactionCallback {
        void handleReaction(SlackMessage slackMessage, String userId, String reactionCode);
    }

    @FunctionalInterface
    public interface MessageCallback {
        void handleMessage(SlackMessage slackMessage, String content);
    }
}
