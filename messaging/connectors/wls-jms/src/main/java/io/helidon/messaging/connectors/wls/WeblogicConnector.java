/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.messaging.connectors.wls;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.config.Config;
import io.helidon.messaging.connectors.jms.ConnectionContext;
import io.helidon.messaging.connectors.jms.JmsConnector;
import io.helidon.messaging.connectors.jms.MessageMapper;
import io.helidon.messaging.connectors.jms.SessionMetadata;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.eclipse.microprofile.reactive.messaging.spi.ConnectorAttribute;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;

import static io.helidon.messaging.connectors.wls.ThinClientClassLoader.executeInIsolation;

/**
 * MicroProfile Reactive Messaging Weblogic JMS connector.
 */
@ApplicationScoped
@Connector(WeblogicConnector.CONNECTOR_NAME)
@ConnectorAttribute(name = WeblogicConnector.WLS_URL,
                    description = "Weblogic server URL",
                    direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
                    mandatory = true,
                    type = "string")
@ConnectorAttribute(name = WeblogicConnector.THIN_CLIENT_PATH,
                    description = "Filepath to the Weblogic thin T3 client jar(wlthint3client.jar), "
                            + "can be usually found within Weblogic installation 'server/lib/wlthint3client.jar'",
                    direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
                    mandatory = true,
                    type = "string")
@ConnectorAttribute(name = WeblogicConnector.JMS_FACTORY_ATTRIBUTE,
                    description = "Weblogic JMS factory name",
                    direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
                    type = "string")
@ConnectorAttribute(name = WeblogicConnector.WLS_INIT_CONTEXT_PRINCIPAL,
                    description = "Weblogic initial context principal(user)",
                    direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
                    type = "string")
@ConnectorAttribute(name = WeblogicConnector.WLS_INIT_CONTEXT_CREDENTIAL,
                    description = "Weblogic initial context credential(password)",
                    direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
                    type = "string")
@ConnectorAttribute(name = "producer.unit-of-order",
                    description = "All messages from same unit of order will be processed sequentially in the order they were "
                            + "created.",
                    direction = ConnectorAttribute.Direction.OUTGOING,
                    type = "string")
@ConnectorAttribute(name = "producer.compression-threshold",
                    description = "Max bytes number of serialized message body so any message that exceeds this limit "
                            + "will trigger message compression.",
                    direction = ConnectorAttribute.Direction.OUTGOING,
                    type = "int")
@ConnectorAttribute(name = "producer.redelivery-limit",
                    description = "Number of times message is redelivered after recover or rollback.",
                    direction = ConnectorAttribute.Direction.OUTGOING,
                    type = "int")
@ConnectorAttribute(name = "producer.send-timeout",
                    description = "Maximum time the producer will wait for space when sending a message.",
                    direction = ConnectorAttribute.Direction.OUTGOING,
                    type = "long")
@ConnectorAttribute(name = "producer.time-to-deliver",
                    description = "Delay before sent message is made visible on its target destination.",
                    direction = ConnectorAttribute.Direction.OUTGOING,
                    type = "long")
public class WeblogicConnector extends JmsConnector {
    private static final System.Logger LOGGER = System.getLogger(WeblogicConnector.class.getName());
    static final String JMS_FACTORY_ATTRIBUTE = "jms-factory";
    static final String THIN_CLIENT_PATH = "thin-jar";
    static final String WLS_URL = "url";
    static final String WLS_INIT_CONTEXT_PRINCIPAL = "principal";
    static final String WLS_INIT_CONTEXT_CREDENTIAL = "credentials";
    /**
     * Microprofile messaging Weblogic JMS connector name.
     */
    public static final String CONNECTOR_NAME = "helidon-weblogic-jms";

    @Inject
    protected WeblogicConnector(Config config,
                                Instance<ConnectionFactory> connectionFactories) {
        super(config, connectionFactories);
        config.get("mp.messaging.connector.helidon-weblogic-jms.thin-jar")
                .asString()
                .ifPresent(ThinClientClassLoader::setThinJarLocation);
    }

    protected WeblogicConnector(Map<String, ConnectionFactory> connectionFactoryMap,
                                ScheduledExecutorService scheduler,
                                String thinJarLocation,
                                ExecutorService executor) {
        super(connectionFactoryMap, scheduler, executor);
        ThinClientClassLoader.setThinJarLocation(thinJarLocation);
    }

    @Override
    public PublisherBuilder<? extends Message<?>> getPublisherBuilder(org.eclipse.microprofile.config.Config mpConfig) {
        return super.getPublisherBuilder(WlsConnectorConfigAliases.map(mpConfig));
    }

    @Override
    public SubscriberBuilder<? extends Message<?>, Void> getSubscriberBuilder(org.eclipse.microprofile.config.Config mpConfig) {
        return super.getSubscriberBuilder(WlsConnectorConfigAliases.map(mpConfig));
    }

    @Override
    protected MessageConsumer createConsumer(Config config,
                                             Destination destination,
                                             SessionMetadata sessionEntry) throws JMSException {
        return executeInIsolation(() -> super.createConsumer(config, destination, sessionEntry));
    }

    @Override
    protected Optional<? extends ConnectionFactory> getFactory(ConnectionContext ctx) {
        return executeInIsolation(() -> super.getFactory(ctx));
    }

    @Override
    protected Destination createDestination(Session session, ConnectionContext ctx) {
        return executeInIsolation(() -> super.createDestination(session, ctx));
    }

    @Override
    protected SessionMetadata prepareSession(Config config, ConnectionFactory factory) throws JMSException {
        return executeInIsolation(() -> super.prepareSession(config, factory));
    }

    @Override
    protected CompletionStage<?> consumeAsync(Message<?> m,
                                              Session session,
                                              AtomicReference<MessageMapper> mapper,
                                              MessageProducer producer,
                                              Config config) {
        return executeInIsolation(() -> super.consumeAsync(m, session, mapper, producer, config));
    }
}
