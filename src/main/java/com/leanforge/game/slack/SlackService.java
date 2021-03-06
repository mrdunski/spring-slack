package com.leanforge.game.slack;

import com.ullink.slack.simpleslackapi.*;
import com.ullink.slack.simpleslackapi.replies.SlackMessageReply;
import com.ullink.slack.simpleslackapi.replies.SlackReply;
import com.ullink.slack.simpleslackapi.replies.SlackReplyImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Service
public class SlackService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    SlackSession slackSession;


    private final Set<ActionCallback> actionCallbacks = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Scheduled(fixedDelay = 30000)
    public synchronized void refreshUsers() {
        openSession();
        slackSession.refetchUsers();
    }

    public synchronized SlackMessage sendDirectMessage(String userId, String message, String... reactionCodes) {
        openSession();
        SlackUser userById = slackSession.findUserById(userId);
        SlackChannel channel = slackSession.openDirectMessageChannel(userById).getReply().getSlackChannel();
        SlackMessageHandle<SlackMessageReply> messageHandle = slackSession.sendMessage(channel, message);
        SlackMessage slackMessage = toChannelMessage(channel, messageHandle);
        addReactions(slackMessage, reactionCodes);
        return slackMessage;
    }

    public synchronized SlackMessage sendDirectMessage(String userId, String message, SlackActions slackActions) {
        openSession();
        SlackUser userById = slackSession.findUserById(userId);
        SlackChannel channel = slackSession.openDirectMessageChannel(userById).getReply().getSlackChannel();
        SlackPreparedMessage preparedMessage = new SlackPreparedMessage.Builder()
                .withMessage(message)
                .withAttachments(Collections.singletonList(slackActions.toAttachment()))
                .build();
        SlackMessageHandle<SlackMessageReply> messageHandle = slackSession.sendMessage(channel, preparedMessage);

        return toChannelMessage(channel, messageHandle);
    }

    public synchronized SlackMessage sendChannelMessage(String channelId, String message, SlackActions slackActions) {
        openSession();
        logger.debug("Sending message to: {}", channelId);
        SlackChannel channel = slackSession.findChannelById(channelId);
        SlackPreparedMessage preparedMessage = new SlackPreparedMessage.Builder()
                .withMessage(message)
                .withAttachments(Collections.singletonList(slackActions.toAttachment()))
                .build();
        SlackMessageHandle<SlackMessageReply> messageHandle = slackSession.sendMessage(channel, preparedMessage);

        return toChannelMessage(channel, messageHandle);
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

    public synchronized SlackMessage sendThreadMessage(String channelId, String threadId, String message, String... reactionCodes) {
        openSession();
        logger.debug("Sending message to: {}", channelId);
        SlackChannel channel = slackSession.findChannelById(channelId);
        SlackPreparedMessage preparedMessage = new SlackPreparedMessage.Builder()
                .withMessage(message)
                .withThreadTimestamp(threadId)
                .build();
        SlackMessageHandle<SlackMessageReply> messageHandle = slackSession.sendMessage(channel, preparedMessage);
        SlackMessage slackMessage = toChannelMessage(channel, messageHandle);
        addReactions(slackMessage, reactionCodes);

        return slackMessage;
    }

    public synchronized void sendTyping(String channelId) {
        openSession();
        SlackChannel channel = slackSession.findChannelById(channelId);
        slackSession.sendTyping(channel);
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

    public synchronized void addMessageListener(MessageCallback callback) {
        openSession();
        logger.debug("Adding direct message listener {}", callback);
        slackSession.addMessagePostedListener((event, session) -> {
            if (event.getSender().getId().equals(slackSession.sessionPersona().getId()) || event.getThreadTimestamp() != null) {
                return;
            }

            SlackMessage message = new SlackMessage(event.getTimestamp(), event.getChannel().getId(), event.getSender().getId());
            callback.handleMessage(message, event.getMessageContent());
        });
    }

    public synchronized void addActionListener(ActionCallback callback) {
        logger.debug("Adding action listener {}", callback);
        actionCallbacks.add(callback);
    }

    public synchronized ZoneId getUserTimezone(String userId) {
        openSession();
        SlackUser slackUser = slackSession.findUserById(userId);
        return ZoneId.of(slackUser.getTimeZone());
    }

    public synchronized void addThreadListener(ThreadMessageCallback callback) {
        slackSession.addMessagePostedListener((event, session) -> {
            if (event.getSender().getId().equals(slackSession.sessionPersona().getId()) || event.getThreadTimestamp() == null) {
                return;
            }
            callback.handleMessage(
                    new SlackMessage(event.getTimestamp(), event.getChannel().getId(), event.getSender().getId()),
                    event.getThreadTimestamp(),
                    event.getMessageContent());

        });
    }


    synchronized void fireActionCallbacks(String userId, SlackMessage parentMessage, String actionName, String actionValue, String callbackId) {
        logger.debug("Firing action event: {}.{}", actionName, actionValue, callbackId);
        actionCallbacks.forEach(actionCallback -> {
            try {
                actionCallback.handleMessage(parentMessage, userId, actionName, actionValue);
            } catch (Exception e) {
                logger.error("Can't handle action", e);
            }
        });
    }


    private void openSession() {
        if (slackSession.isConnected()) {
            return;
        }

        try {
            slackSession.disconnect();
            slackSession.connect();
        } catch (IOException e) {
            throw new IllegalStateException("Can't open slack session", e);
        }
    }

    private SlackMessage toChannelMessage(SlackChannel channel, SlackMessageHandle messageHandle) {
        SlackReply reply = messageHandle.getReply();
        if (reply instanceof SlackMessageReply) {
            SlackMessageReply messageReply = (SlackMessageReply) reply;
            String timestamp = messageReply.getTimestamp();
            String channelId = channel.getId();

            return new SlackMessage(timestamp, channelId, null);
        }

        if (reply instanceof SlackReplyImpl) {
            SlackReplyImpl slackReply = (SlackReplyImpl) reply;
            String msg = slackReply.getErrorMessage();

            throw new IllegalStateException("Slack error: " + msg);
        }

        throw new IllegalStateException("Unknown response type: "  + reply.toString());
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

    public Optional<String> getChannelId(String channelName) {
        openSession();
        return Optional.ofNullable(slackSession.findChannelByName(channelName)).map(SlackChannel::getId);
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

    @FunctionalInterface
    public interface ThreadMessageCallback {
        void handleMessage(SlackMessage slackMessage, String threadId, String callback);
    }

    @FunctionalInterface
    public interface ActionCallback {
        void handleMessage(SlackMessage slackMessage, String userId, String actionName, String actionValue);
    }
}
