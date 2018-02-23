package com.leanforge.game.slack.listener;

import com.leanforge.game.slack.SlackMessage;
import com.leanforge.game.slack.SlackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class SlackMessageEventListenerSupport {

    private static final Logger logger = LoggerFactory.getLogger(SlackMessageEventListenerSupport.class);

    private final SlackService slackService;
    private final ApplicationContext applicationContext;


    @Autowired
    public SlackMessageEventListenerSupport(SlackService slackService, ApplicationContext applicationContext) {
        this.slackService = slackService;
        this.applicationContext = applicationContext;

        registerHandlers();
    }

    void registerHandlers() {
        Map<String, Object> beansWithAnnotation = applicationContext.getBeansWithAnnotation(SlackController.class);
        beansWithAnnotation.values().parallelStream()
                .forEach(this::addHandlers);
    }


    void addHandlers(Object bean) {
        Stream.of(bean.getClass().getMethods())
                .parallel()
                .filter(this::isMessageCallback)
                .forEach(it -> addMessageHandler(bean, it));

        Stream.of(bean.getClass().getMethods())
                .parallel()
                .filter(this::isReactionCallback)
                .forEach(it -> addReactionHandler(bean, it));

        Stream.of(bean.getClass().getMethods())
                .parallel()
                .filter(this::isThreadCallback)
                .forEach(it -> addThreadMessageHandler(bean, it));
    }

    private void addMessageHandler(Object bean, Method method) {
        SlackMessageListener annotation = method.getAnnotation(SlackMessageListener.class);
        Pattern pattern = Pattern.compile(annotation.value());
        logger.info("Adding message listener for message {}", annotation.value());
        SlackMethodInvoker invoker = createAnnotationBasedInvoker(method, bean);

        slackService.addMessageListener((msg, txt) -> {
            Matcher matcher = pattern.matcher(txt);
            if (!matcher.matches()) {
                return;
            }
            logger.debug("Handling message for pattern {} in channel {}", pattern, msg.getChannelId());
            try {
                if (annotation.sendTyping()) {
                    slackService.sendTyping(msg.getChannelId());
                }
                invoker.invoke(msg, msg.getSenderId(), txt, matcher);
            } catch (Exception e) {
                logger.error("Can't handle message", e);
                reportError(msg.getChannelId(), e);
            }
        });
    }

    private void addThreadMessageHandler(Object bean, Method method) {
        SlackThreadMessageListener annotation = method.getAnnotation(SlackThreadMessageListener.class);
        Pattern pattern = Pattern.compile(annotation.value());
        logger.info("Adding thread message listener for message {}", annotation.value());
        SlackMethodInvoker invoker = createAnnotationBasedInvoker(method, bean);

        slackService.addThreadListener((msg, threadId, txt) -> {
            Matcher matcher = pattern.matcher(txt);
            if (!matcher.matches()) {
                return;
            }
            logger.debug("Handling message for pattern {} in channel {}", pattern, msg.getChannelId());
            try {
                invoker.invoke(msg, msg.getSenderId(), txt, matcher, threadId);
            } catch (Exception e) {
                logger.error("Can't handle message", e);
                reportError(msg.getChannelId(), e);
            }
        });
    }

    private void addReactionHandler(Object bean, Method method) {
        SlackReactionListener annotation = method.getAnnotation(SlackReactionListener.class);
        SlackReactionListener.Action action = annotation.action();
        String reaction = annotation.value();
        logger.info("Adding reaction listener for :{}: {}", reaction, action);
        SlackMethodInvoker invoker = createAnnotationBasedInvoker(method, bean);

        SlackService.MessageReactionCallback callback = (message, userId, reactionCode) -> {
            if (!reactionCode.equals(reaction)) {
                return;
            }
            logger.debug("Handling reaction {} - {} in channel {}", reaction, action, message.getChannelId());
            try {
                invoker.invoke(message, userId, null, null);
            } catch (Exception e) {
                logger.error("Can't handle reaction", e);
                reportError(message.getChannelId(), e);
            }
        };

        switch (action) {
            case ADD:
                slackService.addReactionListener(callback);
                break;
            case REMOVE:
                slackService.addRemoveReactionListener(callback);
                break;
        }
    }

    private void reportError(String channel, Exception e) {
        String msg = findExceptionWithResponseStatus(new HashSet<>(), e)
                .map(it -> Optional.of(it.getClass().getAnnotation(ResponseStatus.class).reason()).filter(v -> !v.isEmpty()).orElse(it.getMessage()))
                .orElseGet(() -> stackedMessage(e));
        slackService.sendChannelMessage(channel, msg);
    }

    private String stackedMessage(Throwable e) {
        if (e instanceof InvocationTargetException) {
            return NestedExceptionUtils.buildMessage("Failed to handle message. Contact bot author(s).", e.getCause());
        }

        return NestedExceptionUtils.buildMessage("Failed to handle message. Contact bot author(s).", e);
    }

    private Optional<Exception> findExceptionWithResponseStatus(Set<Exception> checked, Exception e) {
        if (e.getClass().getAnnotation(ResponseStatus.class) != null) {
            return Optional.of(e);
        }

        Throwable cause = e.getCause();

        if (!(cause instanceof Exception)) {
            return Optional.empty();
        }

        if (!checked.add((Exception) cause)) {
            return Optional.empty();
        }

        return findExceptionWithResponseStatus(checked, (Exception) cause);
    }

    SlackMethodInvoker createAnnotationBasedInvoker(Method method, Object obj) {

        PrecompiledParams precompiledParams = new PrecompiledParams(method);

        return ((slackMessage, userId, messageContent, matcher, threadId) -> {

            Object[] params = precompiledParams.buildParams(new InvocationData(slackMessage, userId, messageContent, matcher, threadId));

            Object result = method.invoke(obj, params);

            if (result instanceof String) {
                if (threadId == null) {
                    slackService.sendChannelMessage(slackMessage.getChannelId(), (String) result);
                } else {
                    slackService.sendThreadMessage(slackMessage.getChannelId(), threadId, (String) result);
                }
            }

            if (result instanceof SlackReactionResponse) {
                slackService.addReactions(slackMessage, ((SlackReactionResponse) result).getReactionCodes());
            }

            if (result instanceof SlackMessageResponse) {
                slackService.sendChannelMessage(slackMessage.getChannelId(), ((SlackMessageResponse) result).getMessage());
            }

            if (result instanceof SlackThreadMessageResponse) {
                String threadToUse = threadId;
                if (threadToUse != null) {
                    threadToUse = slackMessage.getTimestamp();
                }
                slackService.sendThreadMessage(slackMessage.getChannelId(), threadToUse, ((SlackThreadMessageResponse) result).getMessage());
            }
        });
    }

    private boolean isMessageCallback(Method m) {
        return m.getAnnotation(SlackMessageListener.class) != null;
    }

    private boolean isReactionCallback(Method m) {
        return m.getAnnotation(SlackReactionListener.class) != null;
    }

    private boolean isThreadCallback(Method m) {
        return m.getAnnotation(SlackThreadMessageListener.class) != null;
    }

    @FunctionalInterface
    private interface SlackMethodInvoker {
        default void invoke(SlackMessage slackMessage, String userId) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
            this.invoke(slackMessage, userId, null, null, null);
        }
        default void invoke(SlackMessage slackMessage, String userId, String messageContent, Matcher matcher) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
            this.invoke(slackMessage, userId, messageContent, matcher, null);
        }
        void invoke(SlackMessage slackMessage, String userId, String messageContent, Matcher matcher, String threadId) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException;
    }

    private class PrecompiledParams {

        private final Function<InvocationData, Object>[] precompiledParams;

        public PrecompiledParams(Method method) {
            Annotation[][] annotations = method.getParameterAnnotations();
            int parameterCount = method.getParameterCount();
            Function<InvocationData, Object>[] params = new Function[parameterCount];
            for (int i = 0; i < annotations.length; i++) {
                for (int y = 0; y < annotations[i].length; y++) {
                    if (annotations[i][y] instanceof SlackUserId) {
                        params[i] = InvocationData::getUserId;
                    }
                    if (annotations[i][y] instanceof SlackMessageContent) {
                        params[i] = InvocationData::getMessageContent;
                    }

                    if (annotations[i][y] instanceof SlackChannelId) {
                        params[i] = it -> it.getSlackMessage().getChannelId();
                    }

                    if (annotations[i][y] instanceof SlackThreadId) {
                        params[i] = InvocationData::getThreadId;
                    }

                    if (annotations[i][y] instanceof SlackMessageRegexGroup) {
                        SlackMessageRegexGroup regexGroup = (SlackMessageRegexGroup) annotations[i][y];
                        params[i] = it -> {
                            if (it.getMatcher() == null) {
                                return null;
                            }

                            return it.getMatcher().group(regexGroup.value());
                        };

                    }
                }
            }
            for (int i = 0; i < parameterCount; i++) {
                if (SlackMessage.class.equals(method.getParameterTypes()[i])) {
                    params[i] = InvocationData::getSlackMessage;
                }
            }

            for (Object param : params) {
                if (param == null) {
                    throw new IllegalStateException("Bad definition of the listener: " + method.getDeclaringClass().getCanonicalName() + "#" + method.getName());
                }
            }

            precompiledParams = params;
        }

        public Object[] buildParams(InvocationData data) {
            return Stream.of(precompiledParams)
                    .map(it -> it.apply(data))
                    .toArray();
        }
    }

    private class InvocationData {
        private final SlackMessage slackMessage;
        private final String userId;
        private final String messageContent;
        private final Matcher matcher;
        private final String threadId;

        public InvocationData(SlackMessage slackMessage, String userId, String messageContent, Matcher matcher, String threadId) {
            this.slackMessage = slackMessage;
            this.userId = userId;
            this.messageContent = messageContent;
            this.matcher = matcher;
            this.threadId = threadId;
        }

        public SlackMessage getSlackMessage() {
            return slackMessage;
        }

        public String getUserId() {
            return userId;
        }

        public String getMessageContent() {
            return messageContent;
        }

        public Matcher getMatcher() {
            return matcher;
        }

        public String getThreadId() {
            return threadId;
        }
    }
}
