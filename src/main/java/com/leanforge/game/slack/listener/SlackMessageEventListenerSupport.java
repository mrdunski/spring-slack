package com.leanforge.game.slack.listener;

import com.leanforge.game.slack.SlackMessage;
import com.leanforge.game.slack.SlackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
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


    private void addHandlers(Object bean) {
        Stream.of(bean.getClass().getMethods())
                .parallel()
                .filter(this::isMessageCallback)
                .forEach(it -> addMessageHandler(bean, it));

        Stream.of(bean.getClass().getMethods())
                .parallel()
                .filter(this::isReactionCallback)
                .forEach(it -> addReactionHandler(bean, it));
    }

    private void addMessageHandler(Object bean, Method method) {
        SlackMessageListener annotation = method.getAnnotation(SlackMessageListener.class);
        Pattern pattern = Pattern.compile(annotation.value());
        logger.info("Adding message listener for message {}", annotation.value());
        SlackMethodInvoker invoker = createAnnotationBasedInvoker(method, bean);

        slackService.addChannelMessageListener((msg, txt) -> {
            Matcher matcher = pattern.matcher(txt);
            if (!matcher.matches()) {
                return;
            }
            logger.debug("Handling message for pattern {} in channel {}", pattern, msg.getChannelId());
            try {
                invoker.invoke(msg, msg.getSenderId(), txt, matcher);
            } catch (IllegalAccessException | InvocationTargetException e) {
                logger.error("Can't handle message", e);
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
            } catch (IllegalAccessException | InvocationTargetException e) {
                logger.error("Can't handle reaction", e);
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

    SlackMethodInvoker createAnnotationBasedInvoker(Method method, Object obj) {
        Annotation[][] annotations = method.getParameterAnnotations();
        return ((slackMessage, userId, messageContent, matcher) -> {
            Object[] params = new Object[method.getParameterCount()];
            for (int i = 0; i < annotations.length; i++) {
                for (int y = 0; y < annotations[i].length; y++) {
                    if (annotations[i][y] instanceof SlackUserId) {
                        params[i] = userId;
                    }
                    if (annotations[i][y] instanceof SlackMessageContent) {
                        params[i] = messageContent;
                    }

                    if (annotations[i][y] instanceof SlackChannelId) {
                        params[i] = slackMessage.getChannelId();
                    }

                    if (annotations[i][y] instanceof SlackMessageRegexGroup) {
                        SlackMessageRegexGroup regexGroup = (SlackMessageRegexGroup) annotations[i][y];
                        if (matcher != null) {
                            params[i] = matcher.group(regexGroup.value());
                        }
                    }
                }
            }
            for (int i = 0; i < method.getParameterCount(); i++) {
                if (SlackMessage.class.equals(method.getParameterTypes()[i])) {
                    params[i] = slackMessage;
                }
            }
            method.invoke(obj, params);
        });
    }

    private boolean isMessageCallback(Method m) {
        return m.getAnnotation(SlackMessageListener.class) != null;
    }

    private boolean isReactionCallback(Method m) {
        return m.getAnnotation(SlackReactionListener.class) != null;
    }

    private interface SlackMethodInvoker {
        void invoke(SlackMessage slackMessage, String userId, String messageContent, Matcher matcher) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException;
    }
}
