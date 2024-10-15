/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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

import java.lang.System.Logger.Level;
import java.lang.reflect.Method;
import java.util.Arrays;
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
import java.util.function.Function;
import java.util.stream.Collectors;

import io.helidon.common.Builder;
import io.helidon.common.configurable.ScheduledThreadPoolSupplier;
import io.helidon.common.configurable.ThreadPoolSupplier;
import io.helidon.common.reactive.BufferedEmittingPublisher;
import io.helidon.common.reactive.Multi;
import io.helidon.config.ConfigValue;
import io.helidon.config.mp.MpConfig;
import io.helidon.messaging.MessagingException;
import io.helidon.messaging.NackHandler;
import io.helidon.messaging.Stoppable;
import io.helidon.messaging.connectors.jms.shim.JakartaJms;
import io.helidon.messaging.connectors.jms.shim.JakartaWrapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.BeforeDestroyed;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.inject.Inject;
import jakarta.jms.BytesMessage;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.eclipse.microprofile.reactive.messaging.spi.ConnectorAttribute;
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
@ConnectorAttribute(name = JmsConnector.USERNAME_ATTRIBUTE,
        description = "User name used to connect JMS session",
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "string")
@ConnectorAttribute(name = JmsConnector.PASSWORD_ATTRIBUTE,
        description = "Password to connect JMS session",
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "string")
@ConnectorAttribute(name = JmsConnector.TYPE_ATTRIBUTE,
        description = "Possible values are: queue, topic",
        defaultValue = "queue",
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "string")
@ConnectorAttribute(name = JmsConnector.DESTINATION_ATTRIBUTE,
        description = "Queue or topic name",
        mandatory = true,
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "string")
@ConnectorAttribute(name = JmsConnector.ACK_MODE_ATTRIBUTE,
        description = "Possible values are: "
              + "AUTO_ACKNOWLEDGE- session automatically acknowledges a client’s receipt of a message, "
              + "CLIENT_ACKNOWLEDGE - receipt of a message is acknowledged only when Message.ack() is called manually, "
              + "DUPS_OK_ACKNOWLEDGE - session lazily acknowledges the delivery of messages.",
        defaultValue = "AUTO_ACKNOWLEDGE",
        direction = ConnectorAttribute.Direction.INCOMING,
        type = "io.helidon.messaging.connectors.jms.AcknowledgeMode")
@ConnectorAttribute(name = JmsConnector.TRANSACTED_ATTRIBUTE,
        description = "Indicates whether the session will use a local transaction.",
        mandatory = false,
        defaultValue = "false",
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "boolean")
@ConnectorAttribute(name = JmsConnector.AWAIT_ACK_ATTRIBUTE,
        description = "Wait for the acknowledgement of previous message before pulling next one.",
        mandatory = false,
        defaultValue = "false",
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "boolean")
@ConnectorAttribute(name = JmsConnector.MESSAGE_SELECTOR_ATTRIBUTE,
        description = "JMS API message selector expression based on a subset of the SQL92. "
              + "Expression can only access headers and properties, not the payload.",
        mandatory = false,
        direction = ConnectorAttribute.Direction.INCOMING,
        type = "string")
@ConnectorAttribute(name = JmsConnector.CLIENT_ID_ATTRIBUTE,
        description = "Client identifier for JMS connection.",
        mandatory = false,
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "string")
@ConnectorAttribute(name = JmsConnector.DURABLE_ATTRIBUTE,
        description = "True for creating durable consumer (only for topic).",
        mandatory = false,
        defaultValue = "false",
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "boolean")
@ConnectorAttribute(name = JmsConnector.SUBSCRIBER_NAME_ATTRIBUTE,
        description = "Subscriber name for durable consumer used to identify subscription.",
        mandatory = false,
        direction = ConnectorAttribute.Direction.INCOMING,
        type = "string")
@ConnectorAttribute(name = JmsConnector.NON_LOCAL_ATTRIBUTE,
        description = "If true then any messages published to the topic using this session’s connection, "
              + "or any other connection with the same client identifier, "
              + "will not be added to the durable subscription.",
        mandatory = false,
        defaultValue = "false",
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "boolean")
@ConnectorAttribute(name = JmsConnector.NAMED_FACTORY_ATTRIBUTE,
        description = "Select in case factory is injected as a named bean or configured with name.",
        mandatory = false,
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "string")
@ConnectorAttribute(name = JmsConnector.POLL_TIMEOUT_ATTRIBUTE,
        description = "Timeout for polling for next message in every poll cycle in millis. Default value: 50",
        mandatory = false,
        defaultValue = "50",
        direction = ConnectorAttribute.Direction.INCOMING,
        type = "long")
@ConnectorAttribute(name = JmsConnector.PERIOD_EXECUTIONS_ATTRIBUTE,
        description = "Period for executing poll cycles in millis.",
        mandatory = false,
        defaultValue = "100",
        direction = ConnectorAttribute.Direction.INCOMING,
        type = "long")
@ConnectorAttribute(name = JmsConnector.SESSION_GROUP_ID_ATTRIBUTE,
        description = "When multiple channels share same session-group-id, "
              + "they share same JMS session and same JDBC connection as well.",
        mandatory = false,
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "string")
@ConnectorAttribute(name = JmsConnector.JNDI_ATTRIBUTE + "." + JmsConnector.JNDI_JMS_FACTORY_ATTRIBUTE,
        description = "JNDI name of JMS factory.",
        mandatory = false,
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "string")
@ConnectorAttribute(name = JmsConnector.JNDI_ATTRIBUTE + "." + JmsConnector.JNDI_PROPS_ATTRIBUTE,
        description = "Environment properties used for creating initial context java.naming.factory.initial, "
              + "java.naming.provider.url …",
        mandatory = false,
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "properties")
public class JmsConnector implements IncomingConnectorFactory, OutgoingConnectorFactory, Stoppable {

    private static final System.Logger LOGGER = System.getLogger(JmsConnector.class.getName());

    /**
     * Microprofile messaging JMS connector name.
     */
    public static final String CONNECTOR_NAME = "helidon-jms";

    /**
     * Select in case factory is injected as a named bean or configured with name.
     */
    public static final String NAMED_FACTORY_ATTRIBUTE = "named-factory";

    /**
     * Username used with ConnectionFactory.
     */
    public static final String USERNAME_ATTRIBUTE = "username";

    /**
     * Password used with ConnectionFactory.
     */
    public static final String PASSWORD_ATTRIBUTE = "password";

    /**
     * Client identifier for JMS connection.
     */
    public static final String CLIENT_ID_ATTRIBUTE = "client-id";

    /**
     * True for creating durable consumer (only for topic).
     */
    public static final String DURABLE_ATTRIBUTE = "durable";

    /**
     * Subscriber name for durable consumer used to identify subscription.
     */
    public static final String SUBSCRIBER_NAME_ATTRIBUTE = "subscriber-name";

    /**
     * If true then any messages published to the topic using this session's connection,
     * or any other connection with the same client identifier,
     * will not be added to the durable subscription.
     */
    public static final String NON_LOCAL_ATTRIBUTE = "non-local";

    /**
     * JMS acknowledge mode.
     * <p>
     * Possible values are:
     * </p>
     * <ul>
     * <li>AUTO_ACKNOWLEDGE - session automatically acknowledges a client’s receipt of a message,
     * <li>CLIENT_ACKNOWLEDGE - receipt of a message is acknowledged only when Message.ack() is called manually,
     * <li>DUPS_OK_ACKNOWLEDGE - session lazily acknowledges the delivery of messages.
     * </ul>
     */
    public static final String ACK_MODE_ATTRIBUTE = "acknowledge-mode";

    /**
     * Indicates whether the session will use a local transaction.
     */
    public static final String TRANSACTED_ATTRIBUTE = "transacted";

    /**
     * Wait for the acknowledgement of previous message before pulling next one.
     */
    public static final String AWAIT_ACK_ATTRIBUTE = "await-ack";

    /**
     * JMS API message selector expression based on a subset of the SQL92.
     * Expression can only access headers and properties, not the payload.
     */
    public static final String MESSAGE_SELECTOR_ATTRIBUTE = "message-selector";

    /**
     * Timeout for polling for next message in every poll cycle in millis.
     */
    public static final String POLL_TIMEOUT_ATTRIBUTE = "poll-timeout";

    /**
     * Period for executing poll cycles in millis.
     */
    public static final String PERIOD_EXECUTIONS_ATTRIBUTE = "period-executions";

    /**
     * Possible values are: queue, topic.
     */
    public static final String TYPE_ATTRIBUTE = "type";

    /**
     * Queue or topic name.
     */
    public static final String DESTINATION_ATTRIBUTE = "destination";

    /**
     * When multiple channels share same session-group-id, they share same JMS session and same JDBC connection as well.
     */
    public static final String SESSION_GROUP_ID_ATTRIBUTE = "session-group-id";
    static final String JNDI_ATTRIBUTE = "jndi";
    static final String JNDI_PROPS_ATTRIBUTE = "env-properties";
    static final String JNDI_JMS_FACTORY_ATTRIBUTE = "jms-factory";
    static final String JNDI_DESTINATION_ATTRIBUTE = "destination";

    static final AcknowledgeMode ACK_MODE_DEFAULT = AcknowledgeMode.AUTO_ACKNOWLEDGE;
    static final boolean TRANSACTED_DEFAULT = false;
    static final boolean AWAIT_ACK_DEFAULT = false;
    static final long POLL_TIMEOUT_DEFAULT = 50L;
    static final long PERIOD_EXECUTIONS_DEFAULT = 100L;
    static final String TYPE_PROP_DEFAULT = "queue";
    static final String JNDI_JMS_FACTORY_DEFAULT = "ConnectionFactory";

    static final String SCHEDULER_THREAD_NAME_PREFIX = "jms-poll-";
    static final String EXECUTOR_THREAD_NAME_PREFIX = "jms-";

    private final Instance<ConnectionFactory> jakartaConnectionFactories;

    private final ScheduledExecutorService scheduler;
    private final ExecutorService executor;
    private final Map<String, SessionMetadata> sessionRegister = new HashMap<>();
    private final Map<String, ConnectionFactory> connectionFactoryMap;

    @Inject
    private Instance<javax.jms.ConnectionFactory> javaxConnectionFactories;

    /**
     * Provides a {@link JmsConnectorBuilder} for creating
     * a {@link io.helidon.messaging.connectors.jms.JmsConnector} instance.
     *
     * @return new Builder instance
     */
    public static JmsConnectorBuilder builder() {
        return new JmsConnectorBuilder();
    }

    /**
     * Creates a new instance of JmsConnector with empty configuration.
     *
     * @return the new instance
     */
    public static JmsConnector create() {
        return builder().config(io.helidon.config.Config.empty()).build();
    }

    /**
     * Custom config builder for JMS connector.
     *
     * @return new JMS specific config builder
     */
    public static JmsConfigBuilder configBuilder() {
        return new JmsConfigBuilder();
    }

    /**
     * Create new JmsConnector.
     *
     * @param jakartaConnectionFactories connection factory beans
     * @param config              root config for thread context
     */
    @Inject
    protected JmsConnector(io.helidon.config.Config config,
                           Instance<ConnectionFactory> jakartaConnectionFactories) {
        this.jakartaConnectionFactories = jakartaConnectionFactories;
        this.connectionFactoryMap = Map.of();
        scheduler = ScheduledThreadPoolSupplier.builder()
                .threadNamePrefix(SCHEDULER_THREAD_NAME_PREFIX)
                .config(config)
                .build()
                .get();
        executor = ThreadPoolSupplier.builder()
                .threadNamePrefix(EXECUTOR_THREAD_NAME_PREFIX)
                .config(config)
                .build()
                .get();
    }

    /**
     * Create new JmsConnector.
     *
     * @param connectionFactoryMap custom connection factories
     * @param scheduler            custom scheduler for polling
     * @param executor             custom executor for async tasks
     */
    protected JmsConnector(Map<String, ConnectionFactory> connectionFactoryMap,
                           ScheduledExecutorService scheduler,
                           ExecutorService executor) {
        this.jakartaConnectionFactories = null;
        this.javaxConnectionFactories = null;
        this.connectionFactoryMap = connectionFactoryMap;
        this.scheduler = scheduler;
        this.executor = executor;
    }

    @Override
    public void stop() {
        scheduler.shutdown();
        executor.shutdown();
        try {
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            LOGGER.log(Level.ERROR, () -> "Error when awaiting scheduler termination.", e);
            scheduler.shutdownNow();
            executor.shutdownNow();
        }
        for (SessionMetadata e : sessionRegister.values()) {
            try {
                e.session().close();
                e.connection().close();
            } catch (JMSException jmsException) {
                LOGGER.log(Level.ERROR, () -> "Error when stopping JMS sessions.", jmsException);
            }
        }
        LOGGER.log(Level.INFO, "JMS Connector gracefully stopped.");
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
     * @param nackHandler     Not acknowledged handler
     * @param message         JMS message
     * @param executor        executor used for async execution of ack
     * @param sessionMetadata JMS session metadata
     * @return reactive messaging message extended with custom JMS features
     */
    protected JmsMessage<?> createMessage(NackHandler nackHandler,
                                          jakarta.jms.Message message,
                                          Executor executor,
                                          SessionMetadata sessionMetadata) {
        if (message instanceof TextMessage textMessage) {
            return new JmsTextMessage(nackHandler, textMessage, executor, sessionMetadata);
        } else if (message instanceof BytesMessage bytesMessage) {
            return new JmsBytesMessage(nackHandler, bytesMessage, executor, sessionMetadata);
        } else {
            return new AbstractJmsMessage<jakarta.jms.Message>(nackHandler, executor, sessionMetadata) {

                @Override
                public jakarta.jms.Message getJmsMessage() {
                    return message;
                }

                @Override
                public jakarta.jms.Message getPayload() {
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
            // Check SE map and MP instance for named factories
            return Optional.ofNullable(connectionFactoryMap.get(factoryName.get()))
                    .or(() -> getConnectionFactoryBean(factoryName.get()));
        }

        // Check SE map and MP instance for any factories
        return connectionFactoryMap.values().stream().findFirst()
                .or(() -> getConnectionFactoryBean(factoryName.get()));
    }

    private <T> Optional<ConnectionFactory> getConnectionFactoryBean(String name){
        NamedLiteral literal = NamedLiteral.of(name);
        return jakartaConnectionFactories.select(literal)
                .stream()
                .findFirst()
                .or(() -> javaxConnectionFactories.select(literal).stream().map(JakartaJms::create).findFirst());
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

            Destination destination = createDestination(sessionEntry.session(), ctx);

            MessageConsumer consumer = createConsumer(config, destination, sessionEntry);


            BufferedEmittingPublisher<Message<?>> emitter = BufferedEmittingPublisher.create();
            JmsNackHandler nackHandler = JmsNackHandler.create(emitter, config, this);

            Long pollTimeout = config.get(POLL_TIMEOUT_ATTRIBUTE)
                    .asLong()
                    .orElse(POLL_TIMEOUT_DEFAULT);

            Long periodExecutions = config.get(PERIOD_EXECUTIONS_ATTRIBUTE)
                    .asLong()
                    .orElse(PERIOD_EXECUTIONS_DEFAULT);

            AtomicReference<JmsMessage<?>> lastMessage = new AtomicReference<>();

            scheduler.scheduleAtFixedRate(
                    () -> {
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
                        produce(emitter, sessionEntry, consumer, nackHandler, pollTimeout)
                                .ifPresent(lastMessage::set);
                    }, 0, periodExecutions, TimeUnit.MILLISECONDS);
            sessionEntry.connection().start();
            return ReactiveStreams.fromPublisher(FlowAdapters.toPublisher(Multi.create(emitter)));
        } catch (JMSException e) {
            LOGGER.log(Level.ERROR, () -> "Error during JMS publisher preparation", e);
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
            MessageProducer producer = createProducer(destination, ctx, sessionEntry);
            AtomicReference<MessageMapper> mapper = new AtomicReference<>();
            return ReactiveStreams.<Message<?>>builder()
                    .flatMapCompletionStage(m -> consume(m, session, mapper, producer, config))
                                  .onError(t -> LOGGER.log(Level.ERROR,
                                          () -> "Error intercepted from channel " + config.get(CHANNEL_NAME_ATTRIBUTE)
                                                                                           .asString()
                                                                                           .orElse("unknown"), t))
                    .ignore();
        } catch (JMSException e) {
            throw new MessagingException("Error when creating JMS producer.", e);
        }
    }

    private void configureProducer(MessageProducer producer, ConnectionContext ctx) {
        io.helidon.config.Config config = ctx.config().get("producer");
        if (!config.exists()) return;

        final Object instance;
        // Shim producer?
        if (producer instanceof JakartaWrapper<?>) {
            instance = ((JakartaWrapper<?>) producer).unwrap();
        } else {
            instance = producer;
        }

        Class<?> clazz = instance.getClass();
        Map<String, Method> setterMethods = Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> m.getParameterCount() == 1)
                .collect(Collectors.toMap(m -> ConfigHelper.stripSet(m.getName()), Function.identity()));
        config.detach()
                .traverse()
                .forEach(c -> {
                    String key = c.key().name();
                    String normalizedKey = ConfigHelper.kebabCase2CamelCase(key);
                    Method m = setterMethods.get(normalizedKey);
                    if (m == null) {
                        LOGGER.log(Level.WARNING,
                                "JMS producer property " + key + " can't be set for producer " + clazz.getName());
                        return;
                    }
                    try {
                        m.invoke(instance, c.as(m.getParameterTypes()[0]).get());
                    } catch (Throwable e) {
                        LOGGER.log(Level.WARNING,
                                "Error when setting JMS producer property " + key
                                        + " on " + clazz.getName()
                                        + "." + m.getName(),
                                e);
                    }
                });
    }

    private Optional<JmsMessage<?>> produce(
            BufferedEmittingPublisher<Message<?>> emitter,
            SessionMetadata sessionEntry,
            MessageConsumer consumer,
            JmsNackHandler nackHandler,
            Long pollTimeout) {
        try {
            jakarta.jms.Message message = consumer.receive(pollTimeout);
            if (message == null) {
                return Optional.empty();
            }
            LOGGER.log(Level.DEBUG, () -> "Received message: " + message);
            JmsMessage<?> preparedMessage = createMessage(nackHandler, message, executor, sessionEntry);
            emitter.emit(preparedMessage);
            return Optional.of(preparedMessage);
        } catch (Throwable e) {
            emitter.fail(e);
            return Optional.empty();
        }
    }

    CompletionStage<?> consume(
            Message<?> m,
            Session session,
            AtomicReference<MessageMapper> mapper,
            MessageProducer producer,
            io.helidon.config.Config config) {

        //lookup mapper only the first time
        if (mapper.get() == null) {
            mapper.set(MessageMappers.getJmsMessageMapper(m));
        }

        return CompletableFuture
                .supplyAsync(() -> consumeAsync(m, session, mapper, producer, config), executor)
                .thenApply(aVoid -> m);
    }

    protected CompletionStage<?> consumeAsync(Message<?> m,
                                              Session session,
                                              AtomicReference<MessageMapper> mapper,
                                              MessageProducer producer,
                                              io.helidon.config.Config config) {
        try {
            jakarta.jms.Message jmsMessage;

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
    }

    /**
     * Customizable handler for errors during sending.
     *
     * @param config channel's config
     * @return consumer of errors
     */
    protected BiConsumer<Message<?>, JMSException> sendingErrorHandler(io.helidon.config.Config config) {
        return (m, e) -> {
            m.nack(e);
            throw new MessagingException("Error during sending JMS message.", e);
        };
    }

    protected SessionMetadata prepareSession(io.helidon.config.Config config,
                                             ConnectionFactory factory) throws JMSException {
        Optional<String> sessionGroupId = config.get(SESSION_GROUP_ID_ATTRIBUTE).asString().asOptional();
        if (sessionGroupId.isPresent() && sessionRegister.containsKey(sessionGroupId.get())) {
            return sessionRegister.get(sessionGroupId.get());
        } else {
            Optional<String> user = config.get(USERNAME_ATTRIBUTE).asString().asOptional();
            Optional<String> password = config.get(PASSWORD_ATTRIBUTE).asString().asOptional();
            Optional<String> userId = config.get(CLIENT_ID_ATTRIBUTE).asString().asOptional();

            Connection connection;
            if (user.isPresent() && password.isPresent()) {
                connection = factory.createConnection(user.get(), password.get());
            } else {
                connection = factory.createConnection();
            }

            if (userId.isPresent()) {
                connection.setClientID(userId.get());
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

    protected Destination createDestination(Session session, ConnectionContext ctx) {
        io.helidon.config.Config config = ctx.config();

        if (ctx.isJndi()) {
            Optional<? extends Destination> jndiDestination = ctx.lookupDestination();
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

    protected MessageConsumer createConsumer(io.helidon.config.Config config,
                                             Destination destination,
                                             SessionMetadata sessionEntry) throws JMSException {
        String messageSelector = config.get(MESSAGE_SELECTOR_ATTRIBUTE).asString().orElse(null);
        String subscriberName = config.get(SUBSCRIBER_NAME_ATTRIBUTE).asString().orElse(null);

        if (config.get(DURABLE_ATTRIBUTE).asBoolean().orElse(false)) {
            if (!(destination instanceof Topic)) {
                throw new MessagingException("Can't create durable consumer. Only topic can be durable!");
            }
            return sessionEntry.session().createDurableSubscriber(
                    (Topic) destination,
                    subscriberName,
                    messageSelector,
                    config.get(NON_LOCAL_ATTRIBUTE).asBoolean().orElse(false));
        } else {
            return sessionEntry.session().createConsumer(destination, messageSelector);
        }
    }

    protected MessageProducer createProducer(Destination destination,
                                             ConnectionContext ctx,
                                             SessionMetadata sessionEntry) throws JMSException {
        MessageProducer producer = sessionEntry.session().createProducer(destination);
        configureProducer(producer, ctx);
        return producer;
    }

    /**
     * Builder for {@link io.helidon.messaging.connectors.jms.JmsConnector}.
     */
    public static class JmsConnectorBuilder implements Builder<JmsConnectorBuilder, JmsConnector> {

        private final Map<String, ConnectionFactory> connectionFactoryMap = new HashMap<>();
        private ScheduledExecutorService scheduler;
        private ExecutorService executor;
        private io.helidon.config.Config config;

        /**
         * Add custom {@link jakarta.jms.ConnectionFactory ConnectionFactory} referencable by supplied name with
         * {@link JmsConnector#NAMED_FACTORY_ATTRIBUTE}.
         *
         * @param name              referencable connection factory name
         * @param connectionFactory custom connection factory
         * @return this builder
         */
        public JmsConnectorBuilder connectionFactory(String name, ConnectionFactory connectionFactory) {
            connectionFactoryMap.put(name, connectionFactory);
            return this;
        }

        /**
         * Custom configuration for connector.
         *
         * @param config custom config
         * @return this builder
         */
        public JmsConnectorBuilder config(io.helidon.config.Config config) {
            this.config = config;
            return this;
        }

        /**
         * Custom executor for asynchronous operations like acknowledgement.
         *
         * @param executor custom executor service
         * @return this builder
         */
        public JmsConnectorBuilder executor(ExecutorService executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Custom executor for loop pulling messages from JMS.
         *
         * @param scheduler custom scheduled executor service
         * @return this builder
         */
        public JmsConnectorBuilder scheduler(ScheduledExecutorService scheduler) {
            this.scheduler = scheduler;
            return this;
        }

        /**
         * Custom executor supplier for asynchronous operations like acknowledgement.
         *
         * @param executorSupplier custom executor service
         * @return this builder
         */
        public JmsConnectorBuilder executor(ThreadPoolSupplier executorSupplier) {
            this.executor = executorSupplier.get();
            return this;
        }

        /**
         * Custom executor supplier for loop pulling messages from JMS.
         *
         * @param schedulerPoolSupplier custom scheduled executor service
         * @return this builder
         */
        public JmsConnectorBuilder scheduler(ScheduledThreadPoolSupplier schedulerPoolSupplier) {
            this.scheduler = schedulerPoolSupplier.get();
            return this;
        }

        @Override
        public JmsConnector build() {
            if (config == null) {
                config = io.helidon.config.Config.empty();
            }

            if (executor == null) {
                executor = ThreadPoolSupplier.builder()
                        .threadNamePrefix(JmsConnector.EXECUTOR_THREAD_NAME_PREFIX)
                        .config(config)
                        .build()
                        .get();
            }
            if (scheduler == null) {
                scheduler = ScheduledThreadPoolSupplier.builder()
                        .threadNamePrefix(JmsConnector.SCHEDULER_THREAD_NAME_PREFIX)
                        .config(config)
                        .build()
                        .get();
            }

            return new JmsConnector(connectionFactoryMap, scheduler, executor);
        }

    }
}


