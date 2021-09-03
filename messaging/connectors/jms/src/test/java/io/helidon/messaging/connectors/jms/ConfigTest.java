/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.messaging.connectors.jms;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.literal.NamedLiteral;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;

import io.helidon.config.Config;
import io.helidon.config.mp.MpConfigSources;
import io.helidon.messaging.MessagingException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsMapContaining.hasEntry;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class ConfigTest {

    private static JmsConnector conn;
    private static final HashMap<String, Object> results = new HashMap<>();

    @BeforeEach
    void before() throws JMSException {
        results.clear();
        ConnectionFactory connectionFactory = Mockito.mock(ConnectionFactory.class);
        Instance<ConnectionFactory> instance = Mockito.mock(Instance.class);
        Connection jmsConnection = Mockito.mock(Connection.class);
        Session session = Mockito.mock(Session.class);
        Queue queue = Mockito.mock(Queue.class);
        Topic topic = Mockito.mock(Topic.class);
        MessageConsumer consumer = Mockito.mock(MessageConsumer.class);
        MessageProducer producer = Mockito.mock(MessageProducer.class);
        javax.jms.Message msg = Mockito.mock(javax.jms.Message.class);
        Mockito.when(connectionFactory.createConnection()).thenReturn(jmsConnection);
        Mockito.when(instance.select(NamedLiteral.of("test-factory"))).thenReturn(instance);
        Mockito.when(instance.stream()).thenReturn(Stream.of(connectionFactory));
        Mockito.when(connectionFactory.createConnection(Mockito.anyString(), Mockito.anyString())).thenAnswer(i -> {
            results.put(JmsConnector.USERNAME_ATTRIBUTE, i.getArgument(0));
            results.put(JmsConnector.PASSWORD_ATTRIBUTE, i.getArgument(1));
            return jmsConnection;
        });
        Mockito.when(jmsConnection.createSession(Mockito.anyBoolean(), Mockito.anyInt())).thenAnswer(i -> {
            results.put(JmsConnector.TRANSACTED_ATTRIBUTE, i.getArgument(0));
            results.put(JmsConnector.ACK_MODE_ATTRIBUTE, i.getArgument(1));
            return session;
        });
        Mockito.when(session.createConsumer(Mockito.any(), Mockito.any())).thenAnswer(i -> {
            results.put(JmsConnector.MESSAGE_SELECTOR_ATTRIBUTE, i.getArgument(1));
            return consumer;
        });
        Mockito.when(session.createQueue(Mockito.any())).thenAnswer(i -> {
            results.put(JmsConnector.DESTINATION_ATTRIBUTE, i.getArgument(0));
            return queue;
        });
        Mockito.when(session.createTopic(Mockito.any())).thenAnswer(i -> {
            results.put(JmsConnector.DESTINATION_ATTRIBUTE, i.getArgument(0));
            return topic;
        });
        Mockito.when(session.createProducer(Mockito.any())).thenAnswer(i -> {
            return producer;
        });
        Mockito.when(consumer.receive(Mockito.anyLong())).thenAnswer(i -> {
            results.put(JmsConnector.POLL_TIMEOUT_ATTRIBUTE, i.getArgument(0));
            return msg;
        });
        Mockito.when(msg.getBody(Mockito.any())).thenReturn("test message");

        conn = new JmsConnector(Config.empty(), instance);
    }

    @AfterEach
    void after() {
        conn.stop();
    }

    @Test
    void defaultsPub() {
        await(conn.getPublisherBuilder(conf(Map.of(
                JmsConnector.CHANNEL_NAME_ATTRIBUTE, "test-1",
                JmsConnector.CONNECTOR_ATTRIBUTE, JmsConnector.CONNECTOR_NAME,
                JmsConnector.NAMED_FACTORY_ATTRIBUTE, "test-factory",
                JmsConnector.DESTINATION_ATTRIBUTE, "testQueue1",
                JmsConnector.USERNAME_ATTRIBUTE, "Jack",
                JmsConnector.PASSWORD_ATTRIBUTE, "O'Neil"
        ))).findFirst().run());

        assertThat(results, hasEntry(JmsConnector.USERNAME_ATTRIBUTE, "Jack"));
        assertThat(results, hasEntry(JmsConnector.PASSWORD_ATTRIBUTE, "O'Neil"));
        assertThat(results, hasEntry(JmsConnector.POLL_TIMEOUT_ATTRIBUTE, JmsConnector.POLL_TIMEOUT_DEFAULT));
        assertThat(results, hasEntry(JmsConnector.TRANSACTED_ATTRIBUTE, JmsConnector.TRANSACTED_DEFAULT));
        assertThat(results, hasEntry(JmsConnector.ACK_MODE_ATTRIBUTE, JmsConnector.ACK_MODE_DEFAULT.getAckMode()));
        assertThat(results, hasEntry(JmsConnector.MESSAGE_SELECTOR_ATTRIBUTE, null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void defaultsSub() {
        SubscriberBuilder<Message<String>, Void> subscriberBuilder =
                (SubscriberBuilder<Message<String>, Void>) conn.getSubscriberBuilder(conf(Map.of(
                        JmsConnector.CHANNEL_NAME_ATTRIBUTE, "test-1",
                        JmsConnector.CONNECTOR_ATTRIBUTE, JmsConnector.CONNECTOR_NAME,
                        JmsConnector.NAMED_FACTORY_ATTRIBUTE, "test-factory",
                        JmsConnector.DESTINATION_ATTRIBUTE, "testQueue1",
                        JmsConnector.USERNAME_ATTRIBUTE, "Jack",
                        JmsConnector.PASSWORD_ATTRIBUTE, "O'Neil"
                )));
        await(ReactiveStreams
                .of("test message")
                .map(Message::of)
                .to(subscriberBuilder).run());

        assertThat(results, hasEntry(JmsConnector.USERNAME_ATTRIBUTE, "Jack"));
        assertThat(results, hasEntry(JmsConnector.PASSWORD_ATTRIBUTE, "O'Neil"));
        assertThat(results, hasEntry(JmsConnector.TRANSACTED_ATTRIBUTE, JmsConnector.TRANSACTED_DEFAULT));
    }

    @Test
    void sessionConfigPub() {
        await(conn.getPublisherBuilder(conf(Map.of(
                JmsConnector.CHANNEL_NAME_ATTRIBUTE, "test-1",
                JmsConnector.CONNECTOR_ATTRIBUTE, JmsConnector.CONNECTOR_NAME,
                JmsConnector.NAMED_FACTORY_ATTRIBUTE, "test-factory",
                JmsConnector.DESTINATION_ATTRIBUTE, "testQueue1",
                JmsConnector.USERNAME_ATTRIBUTE, "Jack",
                JmsConnector.PASSWORD_ATTRIBUTE, "O'Neil",
                JmsConnector.POLL_TIMEOUT_ATTRIBUTE, "33",
                JmsConnector.TRANSACTED_ATTRIBUTE, "true",
                JmsConnector.ACK_MODE_ATTRIBUTE, AcknowledgeMode.CLIENT_ACKNOWLEDGE.toString(),
                JmsConnector.MESSAGE_SELECTOR_ATTRIBUTE, "NewsType = 'Sports' OR NewsType = 'Opinion'"
        ))).findFirst().run());

        assertThat(results, hasEntry(JmsConnector.USERNAME_ATTRIBUTE, "Jack"));
        assertThat(results, hasEntry(JmsConnector.PASSWORD_ATTRIBUTE, "O'Neil"));
        assertThat(results, hasEntry(JmsConnector.POLL_TIMEOUT_ATTRIBUTE, Long.valueOf(33)));
        assertThat(results, hasEntry(JmsConnector.TRANSACTED_ATTRIBUTE, Boolean.TRUE));
        assertThat(results, hasEntry(JmsConnector.ACK_MODE_ATTRIBUTE, AcknowledgeMode.CLIENT_ACKNOWLEDGE.getAckMode()));
        assertThat(results, hasEntry(JmsConnector.MESSAGE_SELECTOR_ATTRIBUTE, "NewsType = 'Sports' OR NewsType = 'Opinion'"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sessionConfigSub() {
        SubscriberBuilder<Message<String>, Void> subscriberBuilder =
                (SubscriberBuilder<Message<String>, Void>) conn.getSubscriberBuilder(conf(Map.of(
                        JmsConnector.CHANNEL_NAME_ATTRIBUTE, "test-1",
                        JmsConnector.CONNECTOR_ATTRIBUTE, JmsConnector.CONNECTOR_NAME,
                        JmsConnector.NAMED_FACTORY_ATTRIBUTE, "test-factory",
                        JmsConnector.DESTINATION_ATTRIBUTE, "testQueue1",
                        JmsConnector.USERNAME_ATTRIBUTE, "Jack",
                        JmsConnector.PASSWORD_ATTRIBUTE, "O'Neil"
                )));
        await(ReactiveStreams
                .of("test message")
                .map(Message::of)
                .to(subscriberBuilder).run());

        assertThat(results, hasEntry(JmsConnector.USERNAME_ATTRIBUTE, "Jack"));
        assertThat(results, hasEntry(JmsConnector.PASSWORD_ATTRIBUTE, "O'Neil"));
        assertThat(results, hasEntry(JmsConnector.TRANSACTED_ATTRIBUTE, JmsConnector.TRANSACTED_DEFAULT));
    }

    @Test
    void missingDestinationPub() {
        assertThrows(MessagingException.class, () -> await(conn.getPublisherBuilder(conf(Map.of(
                JmsConnector.CHANNEL_NAME_ATTRIBUTE, "test-1",
                JmsConnector.CONNECTOR_ATTRIBUTE, JmsConnector.CONNECTOR_NAME,
                JmsConnector.NAMED_FACTORY_ATTRIBUTE, "test-factory",
                JmsConnector.USERNAME_ATTRIBUTE, "Jack",
                JmsConnector.PASSWORD_ATTRIBUTE, "O'Neil",
                JmsConnector.POLL_TIMEOUT_ATTRIBUTE, "33",
                JmsConnector.TRANSACTED_ATTRIBUTE, "true",
                JmsConnector.ACK_MODE_ATTRIBUTE, AcknowledgeMode.CLIENT_ACKNOWLEDGE.toString(),
                JmsConnector.MESSAGE_SELECTOR_ATTRIBUTE, "NewsType = 'Sports' OR NewsType = 'Opinion'"
                ))).findFirst().run()),
                "Destination for channel test-1 not specified!");
    }

    @Test
    void missingDestinationSub() {
        assertThrows(MessagingException.class, () -> conn.getSubscriberBuilder(conf(Map.of(
                JmsConnector.CHANNEL_NAME_ATTRIBUTE, "test-1",
                JmsConnector.CONNECTOR_ATTRIBUTE, JmsConnector.CONNECTOR_NAME,
                JmsConnector.NAMED_FACTORY_ATTRIBUTE, "test-factory",
                JmsConnector.USERNAME_ATTRIBUTE, "Jack",
                JmsConnector.PASSWORD_ATTRIBUTE, "O'Neil"
                ))),
                "Destination for channel test-1 not specified!");
    }

    private org.eclipse.microprofile.config.Config conf(Map<String, String> m) {
        return ConfigProviderResolver.instance()
                .getBuilder()
                .withSources(MpConfigSources.create(m))
                .build();
    }

    private <T> T await(CompletionStage<T> stage) {
        try {
            return stage.toCompletableFuture().get(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            fail(e);
            return null;
        }
    }
}
