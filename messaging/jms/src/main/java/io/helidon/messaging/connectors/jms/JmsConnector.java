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

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
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

    }

    @Override
    public PublisherBuilder<? extends Message<?>> getPublisherBuilder(final Config config) {
        ConnectionFactoryLocator factoryLocator = ConnectionFactoryLocator.create(config, connectionFactories);

        Connection connection;
        try {
            ConnectionFactory connectionFactory = factoryLocator.connectionFactory();
            connection = connectionFactory.createConnection();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            MessageConsumer consumer = session.createConsumer(factoryLocator.createDestination(session));
            BufferedEmittingPublisher<Message<?>> emittingPublisher = BufferedEmittingPublisher.create();
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    javax.jms.Message message = consumer.receive();
                    LOGGER.info("Received message: " + message.toString());
                    emittingPublisher.emit(JmsMessage.of(message));
                } catch (Throwable e) {
                    emittingPublisher.fail(e);
                }
            }, 0, 500, TimeUnit.MILLISECONDS);
            connection.start();
            return ReactiveStreams.fromPublisher(FlowAdapters.toPublisher(Multi.create(emittingPublisher).onCancel(() -> {
                System.out.println("Cancelled!!!");
            })));
        } catch (JMSException e) {
            e.printStackTrace();
            return ReactiveStreams.failed(e);
        }
    }

    @Override
    public SubscriberBuilder<? extends Message<?>, Void> getSubscriberBuilder(final Config config) {
        ConnectionFactoryLocator factoryLocator = ConnectionFactoryLocator.create(config, connectionFactories);
        try {
            ConnectionFactory connectionFactory = factoryLocator.connectionFactory();
            Connection connection = connectionFactory.createConnection();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Destination destination = factoryLocator.createDestination(session);
            MessageProducer producer = session.createProducer(destination);
            return ReactiveStreams.<Message<?>>builder()
                    .onTerminate(() -> {
                        System.out.println("TERMINATION");
                    })
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


}
