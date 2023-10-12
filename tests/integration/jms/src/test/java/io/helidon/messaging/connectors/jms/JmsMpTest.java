/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.messaging.connectors.jms;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.messaging.MessagingCdiExtension;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddBeans;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.AddConfigs;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.AddExtensions;
import io.helidon.microprofile.testing.junit5.DisableDiscovery;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.enterprise.inject.spi.CDI;
import jakarta.jms.JMSException;
import jakarta.jms.TextMessage;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@HelidonTest
@DisableDiscovery
@AddBeans({
        @AddBean(JmsConnector.class),
        @AddBean(AbstractSampleBean.Channel1.class),
        @AddBean(AbstractSampleBean.Channel4.class),
        @AddBean(AbstractSampleBean.Channel5.class),
        @AddBean(AbstractSampleBean.Channel6.class),
        @AddBean(AbstractSampleBean.Channel7.class),
        @AddBean(AbstractSampleBean.ChannelSelector.class),
        @AddBean(AbstractSampleBean.ChannelError.class),
        @AddBean(AbstractSampleBean.ChannelProcessor.class),
        @AddBean(AbstractSampleBean.ChannelBytes.class),
        @AddBean(AbstractSampleBean.ChannelProperties.class),
        @AddBean(AbstractSampleBean.ChannelCustomMapper.class),
        @AddBean(AbstractSampleBean.ChannelDerivedMessage.class),
})
@AddExtensions({
        @AddExtension(ConfigCdiExtension.class),
        @AddExtension(MessagingCdiExtension.class),
})
@AddConfigs({
        @AddConfig(key = "mp.messaging.connector.helidon-jms.jndi.env-properties.java.naming.provider.url",
                value = AbstractJmsTest.BROKER_URL),
        @AddConfig(key = "mp.messaging.connector.helidon-jms.jndi.env-properties.java.naming.factory.initial",
                value = "org.apache.activemq.jndi.ActiveMQInitialContextFactory"),

        @AddConfig(key = "mp.messaging.connector.helidon-jms.period-executions", value = "5"),

        @AddConfig(key = "mp.messaging.incoming.test-channel-1.connector", value = JmsConnector.CONNECTOR_NAME),
        @AddConfig(key = "mp.messaging.incoming.test-channel-1.type", value = "topic"),
        @AddConfig(key = "mp.messaging.incoming.test-channel-1.destination", value = JmsMpTest.TEST_TOPIC_1),

        @AddConfig(key = "mp.messaging.incoming.test-channel-2.connector", value = JmsConnector.CONNECTOR_NAME),
        @AddConfig(key = "mp.messaging.incoming.test-channel-2.type", value = "topic"),
        @AddConfig(key = "mp.messaging.incoming.test-channel-2.destination", value = JmsMpTest.TEST_TOPIC_2),

        @AddConfig(key = "mp.messaging.outgoing.test-channel-3.connector", value = JmsConnector.CONNECTOR_NAME),
        @AddConfig(key = "mp.messaging.outgoing.test-channel-3.type", value = "topic"),
        @AddConfig(key = "mp.messaging.outgoing.test-channel-3.destination", value = JmsMpTest.TEST_TOPIC_3),

        @AddConfig(key = "mp.messaging.incoming.test-channel-31.connector", value = JmsConnector.CONNECTOR_NAME),
        @AddConfig(key = "mp.messaging.incoming.test-channel-31.type", value = "topic"),
        @AddConfig(key = "mp.messaging.incoming.test-channel-31.destination", value = JmsMpTest.TEST_TOPIC_3),

        @AddConfig(key = "mp.messaging.incoming.test-channel-error.connector", value = JmsConnector.CONNECTOR_NAME),
        @AddConfig(key = "mp.messaging.incoming.test-channel-error.type", value = "topic"),
        @AddConfig(key = "mp.messaging.incoming.test-channel-error.destination", value = JmsMpTest.TEST_TOPIC_ERROR),

        @AddConfig(key = "mp.messaging.incoming.test-channel-4.connector", value = JmsConnector.CONNECTOR_NAME),
        @AddConfig(key = "mp.messaging.incoming.test-channel-4.type", value = "topic"),
        @AddConfig(key = "mp.messaging.incoming.test-channel-4.destination", value = JmsMpTest.TEST_TOPIC_4),

        @AddConfig(key = "mp.messaging.incoming.test-channel-5.connector", value = JmsConnector.CONNECTOR_NAME),
        @AddConfig(key = "mp.messaging.incoming.test-channel-5.type", value = "queue"),
        @AddConfig(key = "mp.messaging.incoming.test-channel-5.destination", value = JmsMpTest.TEST_QUEUE_5),
        @AddConfig(key = "mp.messaging.incoming.test-channel-5.nack-dlq", value = JmsMpTest.DLQ_QUEUE),

        @AddConfig(key = "mp.messaging.incoming.test-channel-6.connector", value = JmsConnector.CONNECTOR_NAME),
        @AddConfig(key = "mp.messaging.incoming.test-channel-6.type", value = "queue"),
        @AddConfig(key = "mp.messaging.incoming.test-channel-6.destination", value = JmsMpTest.TEST_QUEUE_6),
        @AddConfig(key = "mp.messaging.incoming.test-channel-6.nack-log-only", value = "true"),

        @AddConfig(key = "mp.messaging.incoming.test-channel-7.connector", value = JmsConnector.CONNECTOR_NAME),
        @AddConfig(key = "mp.messaging.incoming.test-channel-7.type", value = "queue"),
        @AddConfig(key = "mp.messaging.incoming.test-channel-7.destination", value = JmsMpTest.TEST_QUEUE_7),

        @AddConfig(key = "mp.messaging.incoming.test-channel-selector.connector", value = JmsConnector.CONNECTOR_NAME),
        @AddConfig(key = "mp.messaging.incoming.test-channel-selector.message-selector",
                value = "source IN ('helidon','voyager')"),
        @AddConfig(key = "mp.messaging.incoming.test-channel-selector.type", value = "topic"),
        @AddConfig(key = "mp.messaging.incoming.test-channel-selector.destination", value = JmsMpTest.TEST_TOPIC_SELECTOR),

        @AddConfig(key = "mp.messaging.incoming.test-channel-bytes-fromJms.connector", value = JmsConnector.CONNECTOR_NAME),
        @AddConfig(key = "mp.messaging.incoming.test-channel-bytes-fromJms.type", value = "queue"),
        @AddConfig(key = "mp.messaging.incoming.test-channel-bytes-fromJms.destination", value = JmsMpTest.TEST_TOPIC_BYTES),

        @AddConfig(key = "mp.messaging.outgoing.test-channel-bytes-toJms.connector", value = JmsConnector.CONNECTOR_NAME),
        @AddConfig(key = "mp.messaging.outgoing.test-channel-bytes-toJms.type", value = "queue"),
        @AddConfig(key = "mp.messaging.outgoing.test-channel-bytes-toJms.destination", value = JmsMpTest.TEST_TOPIC_BYTES),

        @AddConfig(key = "mp.messaging.incoming.test-channel-props-fromJms.connector", value = JmsConnector.CONNECTOR_NAME),
        @AddConfig(key = "mp.messaging.incoming.test-channel-props-fromJms.type", value = "queue"),
        @AddConfig(key = "mp.messaging.incoming.test-channel-props-fromJms.destination", value = JmsMpTest.TEST_TOPIC_PROPS),

        @AddConfig(key = "mp.messaging.outgoing.test-channel-props-toJms.connector", value = JmsConnector.CONNECTOR_NAME),
        @AddConfig(key = "mp.messaging.outgoing.test-channel-props-toJms.type", value = "queue"),
        @AddConfig(key = "mp.messaging.outgoing.test-channel-props-toJms.destination", value = JmsMpTest.TEST_TOPIC_PROPS),

        @AddConfig(key = "mp.messaging.incoming.test-channel-custom-mapper-fromJms.connector", value = JmsConnector.CONNECTOR_NAME),
        @AddConfig(key = "mp.messaging.incoming.test-channel-custom-mapper-fromJms.type", value = "queue"),
        @AddConfig(key = "mp.messaging.incoming.test-channel-custom-mapper-fromJms.destination", value = JmsMpTest.TEST_TOPIC_CUST_MAPPER),

        @AddConfig(key = "mp.messaging.outgoing.test-channel-custom-mapper-toJms.connector", value = JmsConnector.CONNECTOR_NAME),
        @AddConfig(key = "mp.messaging.outgoing.test-channel-custom-mapper-toJms.type", value = "queue"),
        @AddConfig(key = "mp.messaging.outgoing.test-channel-custom-mapper-toJms.destination", value = JmsMpTest.TEST_TOPIC_CUST_MAPPER),

        @AddConfig(key = "mp.messaging.incoming.test-channel-derived-msg-fromJms.connector", value = JmsConnector.CONNECTOR_NAME),
        @AddConfig(key = "mp.messaging.incoming.test-channel-derived-msg-fromJms.type", value = "queue"),
        @AddConfig(key = "mp.messaging.incoming.test-channel-derived-msg-fromJms.destination", value = JmsMpTest.TEST_TOPIC_DERIVED_1),

        @AddConfig(key = "mp.messaging.outgoing.test-channel-derived-msg-process-toJms.connector", value = JmsConnector.CONNECTOR_NAME),
        @AddConfig(key = "mp.messaging.outgoing.test-channel-derived-msg-process-toJms.type", value = "queue"),
        @AddConfig(key = "mp.messaging.outgoing.test-channel-derived-msg-process-toJms.destination", value = JmsMpTest.TEST_TOPIC_DERIVED_1),

        @AddConfig(key = "mp.messaging.incoming.test-channel-derived-msg-process-fromJms.connector", value = JmsConnector.CONNECTOR_NAME),
        @AddConfig(key = "mp.messaging.incoming.test-channel-derived-msg-process-fromJms.type", value = "queue"),
        @AddConfig(key = "mp.messaging.incoming.test-channel-derived-msg-process-fromJms.destination", value = JmsMpTest.TEST_TOPIC_DERIVED_2),

        @AddConfig(key = "mp.messaging.outgoing.test-channel-derived-msg-toJms.connector", value = JmsConnector.CONNECTOR_NAME),
        @AddConfig(key = "mp.messaging.outgoing.test-channel-derived-msg-toJms.type", value = "queue"),
        @AddConfig(key = "mp.messaging.outgoing.test-channel-derived-msg-toJms.destination", value = JmsMpTest.TEST_TOPIC_DERIVED_2),
})
class JmsMpTest extends AbstractMPTest {

    static final String TEST_TOPIC_1 = "topic-1";
    static final String TEST_TOPIC_2 = "topic-2";
    static final String TEST_TOPIC_3 = "topic-3";
    static final String TEST_TOPIC_4 = "topic-4";
    static final String TEST_QUEUE_5 = "queue-5";
    static final String TEST_QUEUE_6 = "queue-6";
    static final String TEST_TOPIC_SELECTOR = "topic-selector";
    static final String TEST_QUEUE_7 = "queue-7";
    static final String TEST_TOPIC_BYTES = "topic-bytes";
    static final String TEST_TOPIC_PROPS = "topic-properties";
    static final String TEST_TOPIC_CUST_MAPPER = "topic-cust-mapper";
    static final String TEST_TOPIC_DERIVED_1 = "topic-derived-1";
    static final String TEST_TOPIC_DERIVED_2 = "topic-derived-2";
    static final String TEST_TOPIC_ERROR = "topic-error";
    static final String DLQ_QUEUE = "DLQ_TOPIC";

    @Test
    void messageSelector() {
        List<String> testData = List.of(
                "enterprise",
                "helidon",
                "defiant",
                "voyager",
                "reliant");
        //configured selector: source IN ('helidon','voyager')
        AbstractSampleBean bean = CDI.current().select(AbstractSampleBean.ChannelSelector.class).get();
        produceAndCheck(bean, testData, TEST_TOPIC_SELECTOR, List.of("helidon", "voyager"), this::setSourceProperty);
    }

    private void setSourceProperty(TextMessage m) {
        try {
            m.setStringProperty("source", m.getText());
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void incomingOk() {
        List<String> testData = IntStream.range(0, 30).mapToObj(i -> "test" + i).collect(Collectors.toList());
        AbstractSampleBean bean = CDI.current().select(AbstractSampleBean.Channel1.class).get();
        produceAndCheck(bean, testData, TEST_TOPIC_1, testData);
    }

    @Test
    void processor() {
        // This test pushes in topic 2, it is processed and
        // pushed in topic 7, and finally check the results coming from topic 7.
        List<String> testData = IntStream.range(0, 30).mapToObj(Integer::toString).collect(Collectors.toList());
        List<String> expected = testData.stream().map(i -> "Processed" + i).collect(Collectors.toList());
        AbstractSampleBean bean = CDI.current().select(AbstractSampleBean.ChannelProcessor.class).get();
        produceAndCheck(bean, testData, TEST_TOPIC_2, expected);
    }

    @Test
    void error() {
        AbstractSampleBean bean = CDI.current().select(AbstractSampleBean.ChannelError.class).get();
        // This is correctly processed
        List<String> testData = Collections.singletonList("10");
        produce(TEST_TOPIC_ERROR, testData, textMessage -> {});
        // This will throw a run time error in TestBean#error
        testData = Collections.singletonList("error");
        produceAndCheck(bean, testData, TEST_TOPIC_ERROR, Collections.singletonList("10"));
        // After an error, it cannot receive new data
        testData = Collections.singletonList("20");
        produceAndCheck(bean, testData, TEST_TOPIC_ERROR, Collections.singletonList("10"));
    }

    @Test
    void withBackPressure() {
        List<String> testData = IntStream.range(0, 999).mapToObj(i -> "1").collect(Collectors.toList());
        List<String> expected = Arrays.asList("1", "1", "1");
        AbstractSampleBean bean = CDI.current().select(AbstractSampleBean.Channel4.class).get();
        produceAndCheck(bean, testData, TEST_TOPIC_4, expected);
    }

    @Test
    void withBackPressureAndNackKillChannel() {
        List<String> testData = Arrays.asList("2222", "2222");
        AbstractSampleBean bean = CDI.current().select(AbstractSampleBean.Channel7.class).get();
        produceAndCheck(bean, testData, TEST_QUEUE_7, testData);
        bean.restart();
        produceAndCheck(bean, List.of("not a number"), TEST_QUEUE_7, Collections.singletonList("error"));
    }

    @Test
    void noAckDQL() throws JMSException {
        List<String> expected = List.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
        List<String> testData = List.of("0", "1", "2", "3", "4", "5", "not a number!", "6", "7", "8", "9", "10");


        AbstractSampleBean.Channel5 channel5 = CDI.current().select(AbstractSampleBean.Channel5.class).get();
        produceAndCheck(channel5, testData, TEST_QUEUE_5, expected);

        List<TextMessage> dlq = consumeAllCurrent(DLQ_QUEUE)
                .map(TextMessage.class::cast)
                .toList();

        assertThat(dlq.stream()
                .map(tm -> {
                    try {
                        return tm.getText();
                    } catch (JMSException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList(), Matchers.contains("not a number!"));
        TextMessage textMessage = dlq.get(0);
        assertThat(textMessage.getStringProperty("dlq-error"), is("java.lang.NumberFormatException"));
        assertThat(textMessage.getStringProperty("dlq-error-msg"), is("For input string: \"not a number!\""));
        assertThat(textMessage.getStringProperty("dlq-orig-destination"), is(JmsMpTest.TEST_QUEUE_5));
    }

    @Test
    void noAckLogOnly() throws JMSException {
        Logger nackHandlerLogger = LogManager.getLogManager().getLogger(JmsNackHandler.class.getName());
        AssertingHandler assertingHandler = new AssertingHandler();
        nackHandlerLogger.addHandler(assertingHandler);
        try {
            List<String> expected = List.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
            List<String> testData = expected.stream()
                    .flatMap(s -> "5".equals(s) ? Stream.of(s, "not a number!") : Stream.of(s))
                    .toList();


            AbstractSampleBean.Channel6 channel6 = CDI.current().select(AbstractSampleBean.Channel6.class).get();
            produceAndCheck(channel6, testData, TEST_QUEUE_6, expected);
            assertingHandler.assertLogMessageLogged("NACKED Message ignored\nchannel: test-channel-6\n");
        } finally {
            nackHandlerLogger.removeHandler(assertingHandler);
        }
    }

    @Test
    void bytes() {
        AbstractSampleBean.ChannelBytes bean = CDI.current().select(AbstractSampleBean.ChannelBytes.class).get();
        bean.await(200);
        bean.assertResult();
    }

    @Test
    void jmsProperties() {
        AbstractSampleBean.ChannelProperties bean = CDI.current().select(AbstractSampleBean.ChannelProperties.class).get();
        bean.await(200);
        bean.assertResult();
    }

    @Test
    void customMapper() {
        AbstractSampleBean.ChannelCustomMapper bean = CDI.current().select(AbstractSampleBean.ChannelCustomMapper.class).get();
        bean.await(200);
        bean.assertResult();
    }

    @Test
    void derivedJmsMessage() throws InterruptedException, ExecutionException, TimeoutException {
        AbstractSampleBean.ChannelDerivedMessage bean = CDI.current().select(AbstractSampleBean.ChannelDerivedMessage.class).get();
        bean.await(50000);
        bean.assertResult();
    }
}
