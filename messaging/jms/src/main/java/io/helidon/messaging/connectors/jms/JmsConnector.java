/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.BeforeDestroyed;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import io.helidon.common.configurable.ScheduledThreadPoolSupplier;
import io.helidon.common.reactive.BufferedEmittingPublisher;
import io.helidon.common.reactive.Multi;
import io.helidon.messaging.Stoppable;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.eclipse.microprofile.reactive.messaging.spi.IncomingConnectorFactory;
import org.eclipse.microprofile.reactive.messaging.spi.OutgoingConnectorFactory;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.reactivestreams.FlowAdapters;

/**
 * MicroProfile Reactive Messaging JMS connector.
 */
@ApplicationScoped
@Connector(JmsConnector.CONNECTOR_NAME)
public class JmsConnector implements IncomingConnectorFactory, OutgoingConnectorFactory, Stoppable {

    private static final Logger LOGGER = Logger.getLogger(JmsConnector.class.getName());
    /**
     * Microprofile messaging Kafka connector name.
     */
    static final String CONNECTOR_NAME = "helidon-jms";
    private final Instance<ConnectionFactory> connectionFactories;
    private final ScheduledExecutorService scheduler;
    private final Map<String, SessionMetadata> sessionRegister = new HashMap<>();

    /**
     * Create new JmsConnector.
     *
     * @param connectionFactories pre-prepared connection factories, jndi config takes precedence
     * @param config              channel related config
     */
    @Inject
    public JmsConnector(Instance<ConnectionFactory> connectionFactories, io.helidon.config.Config config) {
        this.connectionFactories = connectionFactories;
        scheduler = ScheduledThreadPoolSupplier.builder()
                .threadNamePrefix("jms-")
                .config(config)
                .build()
                .get();
    }

    @Override
    public void stop() {
        scheduler.shutdown();
        for (SessionMetadata e : sessionRegister.values()) {
            try {
                e.getSession().close();
                e.getConnection().close();
            } catch (JMSException jmsException) {
                LOGGER.log(Level.SEVERE, jmsException, () -> "Error when stopping JMS sessions.");
            }
        }
        LOGGER.info("JMS Connector gracefully stopped.");
    }

    /**
     * Called when container is terminated. If it is not running in a container {@link #stop()} must be explicitly invoked
     * to terminate the messaging and release JMS connections.
     *
     * @param event termination event
     */
    void terminate(@Observes @BeforeDestroyed(ApplicationScoped.class) Object event) {
        stop();
    }

    @Override
    public PublisherBuilder<? extends Message<?>> getPublisherBuilder(final Config config) {
        ConnectionFactoryLocator factoryLocator = ConnectionFactoryLocator.create(config, connectionFactories);

        try {
            SessionMetadata sessionEntry = prepareSession(config, factoryLocator);
            MessageConsumer consumer = sessionEntry
                    .getSession()
                    .createConsumer(factoryLocator.createDestination(sessionEntry.getSession()));
            BufferedEmittingPublisher<Message<?>> emittingPublisher = BufferedEmittingPublisher.create();
            scheduler.scheduleAtFixedRate(() -> {
                        try {
                            javax.jms.Message message = consumer.receive();
                            LOGGER.fine(() -> "Received message: " + message.toString());
                            emittingPublisher.emit(JmsMessage.of(message, sessionEntry));
                        } catch (Throwable e) {
                            emittingPublisher.fail(e);
                        }
                    }, 0,
                    config.getOptionalValue("period-executions", Long.class).orElse(200L),
                    TimeUnit.MILLISECONDS);
            sessionEntry.getConnection().start();
            return ReactiveStreams.fromPublisher(FlowAdapters.toPublisher(Multi.create(emittingPublisher)));
        } catch (JMSException e) {
            LOGGER.log(Level.SEVERE, e, () -> "Error during JMS publisher preparation");
            return ReactiveStreams.failed(e);
        }
    }

    @Override
    public SubscriberBuilder<? extends Message<?>, Void> getSubscriberBuilder(final Config config) {
        ConnectionFactoryLocator factoryLocator = ConnectionFactoryLocator.create(config, connectionFactories);
        try {
            SessionMetadata sessionEntry = prepareSession(config, factoryLocator);
            Session session = sessionEntry.getSession();
            Destination destination = factoryLocator.createDestination(session);
            MessageProducer producer = session.createProducer(destination);
            return ReactiveStreams.<Message<?>>builder()
                    .onError(t -> LOGGER.log(Level.SEVERE, t, () -> "Error intercepted from channel "
                            + config.getValue(CHANNEL_NAME_ATTRIBUTE, String.class)))
                    .forEach((Object m) -> {
                        try {
                            Object payload = ((Message<?>) m).getPayload();
                            if (payload instanceof String) {
                                TextMessage textMessage = session.createTextMessage((String) payload);
                                producer.send(textMessage);
                            } else if (payload instanceof byte[]) {
                                BytesMessage bytesMessage = session.createBytesMessage();
                                bytesMessage.writeBytes((byte[]) payload);
                                producer.send(bytesMessage);
                            }
                        } catch (JMSException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }

    private SessionMetadata prepareSession(final Config config,
                                           ConnectionFactoryLocator factoryLocator) throws JMSException {
        Optional<String> sessionGroupId = config.getOptionalValue("session-group-id", String.class);
        if (sessionGroupId.isPresent() && sessionRegister.containsKey(sessionGroupId.get())) {
            return sessionRegister.get(sessionGroupId.get());
        } else {
            ConnectionFactory connectionFactory = factoryLocator.connectionFactory();
            Optional<String> user = config.getOptionalValue("user", String.class);
            Optional<String> password = config.getOptionalValue("password", String.class);
            Connection connection;
            if (user.isPresent() && password.isPresent()) {
                connection = connectionFactory.createConnection(user.get(), password.get());
            } else {
                connection = connectionFactory.createConnection();
            }
            boolean transacted = config.getOptionalValue("transacted", Boolean.class)
                    .orElse(false);
            int acknowledgeMode = config.getOptionalValue("acknowledge-mode", String.class)
                    .map(AcknowledgeMode::parse)
                    .orElse(AcknowledgeMode.AUTO_ACKNOWLEDGE)
                    .getAckMode();
            Session session = connection.createSession(transacted, acknowledgeMode);
            SessionMetadata sharedSessionEntry = new SessionMetadata(session, connection, connectionFactory);
            sessionRegister.put(sessionGroupId.orElseGet(() -> UUID.randomUUID().toString()), sharedSessionEntry);
            return sharedSessionEntry;
        }
    }

}
