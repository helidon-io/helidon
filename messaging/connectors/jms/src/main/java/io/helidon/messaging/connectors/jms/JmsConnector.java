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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.BeforeDestroyed;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.literal.NamedLiteral;
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
import io.helidon.common.configurable.ThreadPoolSupplier;
import io.helidon.common.reactive.BufferedEmittingPublisher;
import io.helidon.common.reactive.Multi;
import io.helidon.config.ConfigValue;
import io.helidon.config.mp.MpConfig;
import io.helidon.messaging.MessagingException;
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
     * Microprofile messaging JMS connector name.
     */
    public static final String CONNECTOR_NAME = "helidon-jms";

    static final String ACK_MODE_ATTRIBUTE = "acknowledge-mode";
    static final String TRANSACTED_ATTRIBUTE = "transacted";
    static final String AWAIT_ACK_ATTRIBUTE = "await-ack";
    static final String MESSAGE_SELECTOR_ATTRIBUTE = "message-selector";
    static final String POLL_TIMEOUT_ATTRIBUTE = "poll-timeout";
    static final String PERIOD_EXECUTIONS_ATTRIBUTE = "period-executions";
    static final String TYPE_ATTRIBUTE = "type";
    static final String DESTINATION_ATTRIBUTE = "destination";
    static final String SESSION_GROUP_ID_ATTRIBUTE = "session-group-id";
    static final String JNDI_ATTRIBUTE = "jndi";
    static final String JNDI_PROPS_ATTRIBUTE = "env-properties";
    static final String JNDI_JMS_FACTORY_ATTRIBUTE = "jms-factory";
    static final String JNDI_DESTINATION_ATTRIBUTE = "destination";
    /**
     * Select in case factory is injected as a named bean or configured with name.
     */
    protected static final String NAMED_FACTORY_ATTRIBUTE = "named-factory";
    /**
     * User name used with ConnectionFactory.
     */
    protected static final String USERNAME_ATTRIBUTE = "username";
    /**
     * Password used with ConnectionFactory.
     */
    protected static final String PASSWORD_ATTRIBUTE = "password";

    static final AcknowledgeMode ACK_MODE_DEFAULT = AcknowledgeMode.AUTO_ACKNOWLEDGE;
    static final boolean TRANSACTED_DEFAULT = false;
    static final boolean AWAIT_ACK_DEFAULT = false;
    static final long POLL_TIMEOUT_DEFAULT = 50L;
    static final long PERIOD_EXECUTIONS_DEFAULT = 100L;
    static final String TYPE_PROP_DEFAULT = "queue";
    static final String JNDI_JMS_FACTORY_DEFAULT = "ConnectionFactory";

    private final Instance<ConnectionFactory> connectionFactories;

    private final ScheduledExecutorService scheduler;
    private final ExecutorService executor;
    private final Map<String, SessionMetadata> sessionRegister = new HashMap<>();

    /**
     * Create new JmsConnector.
     *
     * @param connectionFactories connection factory beans
     * @param config              root config for thread context
     */
    @Inject
    protected JmsConnector(io.helidon.config.Config config, Instance<ConnectionFactory> connectionFactories) {
        this.connectionFactories = connectionFactories;
        scheduler = ScheduledThreadPoolSupplier.builder()
                .threadNamePrefix("jms-poll-")
                .config(config)
                .build()
                .get();
        executor = ThreadPoolSupplier.builder()
                .threadNamePrefix("jms-")
                .config(config)
                .build()
                .get();
    }

    @Override
    public void stop() {
        scheduler.shutdown();
        executor.shutdown();
        try {
            scheduler.awaitTermination(100, TimeUnit.MILLISECONDS);
            executor.awaitTermination(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            LOGGER.log(Level.SEVERE, e, () -> "Error when awaiting scheduler termination.");
        }
        for (SessionMetadata e : sessionRegister.values()) {
            try {
                e.session().close();
                e.connection().close();
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

    /**
     * Create reactive messaging message from JMS message.
     *
     * @param message         JMS message
     * @param executor        executor used for async execution of ack
     * @param sessionMetadata JMS session metadata
     * @return reactive messaging message extended with custom JMS features
     */
    protected JmsMessage<?> createMessage(javax.jms.Message message, Executor executor, SessionMetadata sessionMetadata) {
        if (message instanceof TextMessage) {
            return new JmsTextMessage((TextMessage) message, executor, sessionMetadata);
        } else if (message instanceof BytesMessage) {
            return new JmsBytesMessage((BytesMessage) message, executor, sessionMetadata);
        } else {
            return new AbstractJmsMessage<javax.jms.Message>(executor, sessionMetadata) {

                @Override
                public javax.jms.Message getJmsMessage() {
                    return message;
                }

                @Override
                public javax.jms.Message getPayload() {
                    return message;
                }
            };
        }
    }

    /**
     * Find correct ConnectionFactory for channel.
     *
     * @param ctx Channel's context
     * @return appropriate connection factory
     */
    protected Optional<? extends ConnectionFactory> getFactory(ConnectionContext ctx) {
        if (ctx.isJndi()) {
            return ctx.lookupFactory();
        }
        ConfigValue<String> factoryName = ctx.config().get(NAMED_FACTORY_ATTRIBUTE).asString();
        if (factoryName.isPresent()) {
            return Optional.ofNullable(connectionFactories)
                    .flatMap(s -> s.select(NamedLiteral.of(factoryName.get())).stream().findFirst());
        }

        return Optional.ofNullable(connectionFactories)
                .flatMap(s -> s.stream().findFirst());
    }

    @Override
    public PublisherBuilder<? extends Message<?>> getPublisherBuilder(Config mpConfig) {
        io.helidon.config.Config config = MpConfig.toHelidonConfig(mpConfig);

        AcknowledgeMode ackMode = config.get(ACK_MODE_ATTRIBUTE)
                .asString()
                .map(AcknowledgeMode::parse)
                .orElse(ACK_MODE_DEFAULT);

        Boolean awaitAck = config.get(AWAIT_ACK_ATTRIBUTE)
                .asBoolean()
                .orElse(AWAIT_ACK_DEFAULT);

        ConnectionContext ctx = new ConnectionContext(config);
        ConnectionFactory factory = getFactory(ctx)
                .orElseThrow(() -> new MessagingException("No ConnectionFactory found."));

        try {
            SessionMetadata sessionEntry = prepareSession(config, factory);

            MessageConsumer consumer = sessionEntry
                    .session()
                    .createConsumer(
                            createDestination(sessionEntry.session(), ctx),
                            config.get(MESSAGE_SELECTOR_ATTRIBUTE).asString().orElse(null)
                    );

            BufferedEmittingPublisher<Message<?>> emitter = BufferedEmittingPublisher.create();

            Long pollTimeout = config.get(POLL_TIMEOUT_ATTRIBUTE)
                    .asLong()
                    .orElse(POLL_TIMEOUT_DEFAULT);

            AtomicReference<JmsMessage<?>> lastMessage = new AtomicReference<>();

            scheduler.scheduleAtFixedRate(
                    () -> produce(emitter, sessionEntry, consumer, ackMode, awaitAck, pollTimeout, lastMessage),
                    0,
                    config.get(PERIOD_EXECUTIONS_ATTRIBUTE)
                            .asLong()
                            .orElse(PERIOD_EXECUTIONS_DEFAULT),
                    TimeUnit.MILLISECONDS);
            sessionEntry.connection().start();
            return ReactiveStreams.fromPublisher(FlowAdapters.toPublisher(Multi.create(emitter)));
        } catch (JMSException e) {
            LOGGER.log(Level.SEVERE, e, () -> "Error during JMS publisher preparation");
            return ReactiveStreams.failed(e);
        }
    }

    @Override
    public SubscriberBuilder<? extends Message<?>, Void> getSubscriberBuilder(Config mpConfig) {
        io.helidon.config.Config config = MpConfig.toHelidonConfig(mpConfig);

        ConnectionContext ctx = new ConnectionContext(config);
        ConnectionFactory factory = getFactory(ctx)
                .orElseThrow(() -> new MessagingException("No ConnectionFactory found."));

        try {
            SessionMetadata sessionEntry = prepareSession(config, factory);
            Session session = sessionEntry.session();
            Destination destination = createDestination(session, ctx);
            MessageProducer producer = session.createProducer(destination);
            AtomicReference<MessageMappers.MessageMapper> mapper = new AtomicReference<>();
            return ReactiveStreams.<Message<?>>builder()
                    .flatMapCompletionStage(m -> consume(m, session, mapper, producer, config))
                    .onError(t -> LOGGER.log(Level.SEVERE, t, () -> "Error intercepted from channel "
                            + config.get(CHANNEL_NAME_ATTRIBUTE).asString().orElse("unknown")))
                    .ignore();
        } catch (JMSException e) {
            throw new MessagingException("Error when creating JMS producer.", e);
        }
    }

    private void produce(
            BufferedEmittingPublisher<Message<?>> emitter,
            SessionMetadata sessionEntry,
            MessageConsumer consumer,
            AcknowledgeMode ackMode,
            Boolean awaitAck,
            Long pollTimeout,
            AtomicReference<JmsMessage<?>> lastMessage) {

        if (!emitter.hasRequests()) {
            return;
        }
        // When await-ack is true, no message is received until previous one is acked
        if (ackMode != AcknowledgeMode.AUTO_ACKNOWLEDGE
                && awaitAck
                && lastMessage.get() != null
                && !lastMessage.get().isAck()) {
            return;
        }
        try {
            javax.jms.Message message = consumer.receive(pollTimeout);
            if (message == null) {
                return;
            }
            LOGGER.fine(() -> "Received message: " + message.toString());
            JmsMessage<?> preparedMessage = createMessage(message, executor, sessionEntry);
            lastMessage.set(preparedMessage);
            emitter.emit(preparedMessage);
        } catch (Throwable e) {
            emitter.fail(e);
        }
    }

    private CompletionStage<?> consume(
            Message<?> m,
            Session session,
            AtomicReference<MessageMappers.MessageMapper> mapper,
            MessageProducer producer,
            io.helidon.config.Config config) {

        //lookup mapper only the first time
        if (mapper.get() == null) {
            mapper.set(MessageMappers.getJmsMessageMapper(m));
        }

        return CompletableFuture
                .supplyAsync(() -> {
                    try {
                        javax.jms.Message jmsMessage;

                        if (m instanceof OutgoingJmsMessage) {
                            // custom mapping, properties etc.
                            jmsMessage = ((OutgoingJmsMessage<?>) m).toJmsMessage(session, mapper.get());
                        } else {
                            // default mappers
                            jmsMessage = mapper.get().apply(session, m);
                        }
                        // actual send
                        producer.send(jmsMessage);
                        return m.ack();
                    } catch (JMSException e) {
                        sendingErrorHandler(config).accept(m, e);
                    }
                    return CompletableFuture.completedFuture(null);
                }, executor)
                .thenApply(aVoid -> m);
    }

    /**
     * Customizable handler for errors during sending.
     *
     * @param config channel's config
     * @return consumer of errors
     */
    protected BiConsumer<Message<?>, JMSException> sendingErrorHandler(io.helidon.config.Config config) {
        return (m, e) -> {
            throw new MessagingException("Error during sending JMS message.", e);
        };
    }

    private SessionMetadata prepareSession(io.helidon.config.Config config,
                                           ConnectionFactory factory) throws JMSException {
        Optional<String> sessionGroupId = config.get(SESSION_GROUP_ID_ATTRIBUTE).asString().asOptional();
        if (sessionGroupId.isPresent() && sessionRegister.containsKey(sessionGroupId.get())) {
            return sessionRegister.get(sessionGroupId.get());
        } else {
            Optional<String> user = config.get(USERNAME_ATTRIBUTE).asString().asOptional();
            Optional<String> password = config.get(PASSWORD_ATTRIBUTE).asString().asOptional();

            Connection connection;
            if (user.isPresent() && password.isPresent()) {
                connection = factory.createConnection(user.get(), password.get());
            } else {
                connection = factory.createConnection();
            }

            boolean transacted = config.get(TRANSACTED_ATTRIBUTE)
                    .asBoolean()
                    .orElse(TRANSACTED_DEFAULT);

            int acknowledgeMode = config.get(ACK_MODE_ATTRIBUTE).asString()
                    .map(AcknowledgeMode::parse)
                    .orElse(ACK_MODE_DEFAULT)
                    .getAckMode();

            Session session = connection.createSession(transacted, acknowledgeMode);
            SessionMetadata sharedSessionEntry = new SessionMetadata(session, connection, factory);
            sessionRegister.put(sessionGroupId.orElseGet(() -> UUID.randomUUID().toString()), sharedSessionEntry);
            return sharedSessionEntry;
        }
    }

    Destination createDestination(Session session, ConnectionContext ctx) {
        io.helidon.config.Config config = ctx.config();

        if (ctx.isJndi()) {
            Optional<? extends Destination> jndiDestination = ctx.lookupDestination();
            // JNDI can be used for looking up ConnectorFactory only
            if (jndiDestination.isPresent()) {
                return jndiDestination.get();
            }
        }

        String type = config.get(TYPE_ATTRIBUTE)
                .asString()
                .map(String::toLowerCase)
                .orElse(TYPE_PROP_DEFAULT)
                .toLowerCase();

        String destination = config.get(DESTINATION_ATTRIBUTE)
                .asString()
                .orElseThrow(() -> new MessagingException("Destination for channel "
                        + config.get(CHANNEL_NAME_ATTRIBUTE).asString().get()
                        + " not specified!"));

        try {
            if ("queue".equals(type)) {
                return session.createQueue(destination);
            } else if ("topic".equals(type)) {
                return session.createTopic(destination);
            } else {
                throw new MessagingException("Unknown type");
            }
        } catch (JMSException jmsException) {
            throw new MessagingException("Error when creating destination.", jmsException);
        }

    }
}


