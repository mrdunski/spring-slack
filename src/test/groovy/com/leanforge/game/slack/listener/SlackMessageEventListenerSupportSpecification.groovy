package com.leanforge.game.slack.listener

import com.leanforge.game.slack.SlackMessage
import com.leanforge.game.slack.SlackService
import org.springframework.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Subject

import java.util.regex.Pattern

class SlackMessageEventListenerSupportSpecification extends Specification {


    SlackService slackService = Mock(SlackService)
    ApplicationContext applicationContext = Mock(ApplicationContext)

    def handler = new TestHandler()
    def badHandler = new BadHandler()

    @Subject
    SlackMessageEventListenerSupport slackMessageEventHandler


    def setup() {
        applicationContext.getBeansWithAnnotation(_) >> ['aBean':handler]
        slackMessageEventHandler = new SlackMessageEventListenerSupport(slackService, applicationContext)
    }

    def "should invoke method with all params"() {
        given:
        def handler = new TestHandler()
        def invoker = slackMessageEventHandler.createAnnotationBasedInvoker(
                TestHandler.getMethod("thisIsExampleHandler1", SlackMessage, String, String, String),
                handler
        )
        def message = new SlackMessage('a', 'b', 'c')
        def content = 'content'
        def userid = 'userid'
        def matcher = Pattern.compile(/d(.*)3/).matcher("dupa123")
        matcher.matches()


        when:
        invoker.invoke(message, userid, content, matcher)

        then:
        handler.userId == userid
        handler.slackMessage == message
        handler.messageContent == content
        handler.methodCalled
        handler.matchedText == 'upa12'
        1 * slackService.sendChannelMessage('b', 'Test Response')
    }

    def "should invoke method with some params"() {
        given:
        def handler = new TestHandler()
        def invoker = slackMessageEventHandler.createAnnotationBasedInvoker(
                TestHandler.getMethod("thisIsExampleHandler2", String, SlackMessage),
                handler
        )
        def message = new SlackMessage('a', 'b', 'c')
        def content = 'content'
        def userid = 'userid'

        when:
        invoker.invoke(message, userid, content, null)

        then:
        handler.userId == userid
        handler.slackMessage == message
        handler.messageContent == null
        handler.methodCalled
    }

    def "should invoke method with no params"() {
        given:
        def handler = new TestHandler()
        def invoker = slackMessageEventHandler.createAnnotationBasedInvoker(
                TestHandler.getMethod("thisIsExampleHandler3"),
                handler
        )
        def message = new SlackMessage('a', 'b', 'c')
        def content = 'content'
        def userid = 'userid'

        when:
        invoker.invoke(message, userid, content, null)

        then:
        handler.userId == null
        handler.slackMessage == null
        handler.messageContent == null
        handler.methodCalled
        1 * slackService.addReactions(message, 'onion')
    }


    def "should register all handlers"() {
        when:
        slackMessageEventHandler.registerHandlers()
        then:
        1 * slackService.addReactionListener(_)
        1 * slackService.addRemoveReactionListener(_)
        1 * slackService.addMessageListener(_)
    }

    def "should fail on bad handler"() {
        when:
        slackMessageEventHandler.addHandlers(badHandler)
        then:
        thrown(IllegalStateException)
    }


    @SlackController
    public class TestHandler {

        SlackMessage slackMessage
        String userId
        String messageContent
        boolean methodCalled = false
        String matchedText

        @SlackReactionListener("x")
        String thisIsExampleHandler1(SlackMessage slackMessage, @SlackUserId String userId, @SlackMessageContent String content, @SlackMessageRegexGroup(1) String group1 ) {
            this.slackMessage = slackMessage
            this.messageContent = content
            this.userId = userId
            this.matchedText = group1
            methodCalled = true
            return 'Test Response'
        }

        @SlackReactionListener(value = "x", action = SlackReactionListener.Action.REMOVE)
        void thisIsExampleHandler2(@SlackUserId String userId, SlackMessage slackMessage) {
            this.slackMessage = slackMessage
            this.userId = userId
            methodCalled = true
        }

        @SlackMessageListener("x")
        SlackReactionResponse thisIsExampleHandler3() {
            methodCalled = true
            new SlackReactionResponse("onion")
        }
    }

    @SlackController
    public class BadHandler {

        SlackMessage slackMessage
        String userId
        String messageContent
        boolean methodCalled = false
        String matchedText

        @SlackReactionListener("x")
        void thisIsExampleHandler1(SlackMessage slackMessage, @SlackUserId String userId, @SlackMessageContent String content, @SlackMessageRegexGroup(1) String group1, String badParam) {
            this.slackMessage = slackMessage
            this.messageContent = content
            this.userId = userId
            this.matchedText = group1
            methodCalled = true
        }
    }
}
