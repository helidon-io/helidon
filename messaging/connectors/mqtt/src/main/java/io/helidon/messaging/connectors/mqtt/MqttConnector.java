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

package io.helidon.messaging.connectors.mqtt;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.spi.DeploymentException;

import io.helidon.common.reactive.BufferedEmittingPublisher;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.eclipse.microprofile.reactive.messaging.spi.ConnectorFactory;
import org.eclipse.microprofile.reactive.messaging.spi.IncomingConnectorFactory;
import org.eclipse.microprofile.reactive.messaging.spi.OutgoingConnectorFactory;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.reactivestreams.FlowAdapters;

/**
 * MicroProfile Reactive Messaging MQTT connector.
 */
@ApplicationScoped
@Connector(MqttConnector.CONNECTOR_NAME)
public class MqttConnector implements IncomingConnectorFactory, OutgoingConnectorFactory {

    private static final Logger LOGGER = Logger.getLogger(MqttConnector.class.getName());

    /**
     * Microprofile messaging MQTT connector name.
     */
    public static final String CONNECTOR_NAME = "helidon-mqtt";

    @Override
    public PublisherBuilder<? extends Message<?>> getPublisherBuilder(final Config config) {
        String topic = config.getValue("topic", String.class);
        String server = config.getValue("server", String.class);
        String channel = config.getValue(ConnectorFactory.CHANNEL_NAME_ATTRIBUTE, String.class);
        String clientId = config.getOptionalValue("client-id", String.class)
                .orElseGet(() -> channel + "-" + UUID.randomUUID().toString());

        try {
            IMqttClient client = new MqttClient(server, clientId);
            BufferedEmittingPublisher<Message<MqttMessage>> emitter = BufferedEmittingPublisher.create();
            client.connect();
            client.subscribe(topic, (s, mqttMessage) -> emitter.emit(Message.of(mqttMessage)));
            return ReactiveStreams.fromPublisher(FlowAdapters.toPublisher(emitter));
        } catch (MqttException e) {
            throw new DeploymentException(e);
        }
    }

    @Override
    public SubscriberBuilder<? extends Message<?>, Void> getSubscriberBuilder(final Config config) {
        String topic = config.getValue("topic", String.class);
        String server = config.getValue("server", String.class);
        String channel = config.getValue(ConnectorFactory.CHANNEL_NAME_ATTRIBUTE, String.class);
        String clientId = config.getOptionalValue("client-id", String.class)
                .orElseGet(() -> channel + "-" + UUID.randomUUID().toString());

        try {
            IMqttClient client = new MqttClient(server, clientId);
            client.connect();
            return ReactiveStreams.<Message<MqttMessage>>builder()
                    .peek(m -> publish(client, topic, m.getPayload()))
                    .onError(t -> LOGGER.log(Level.SEVERE, "Error intercepted on channel " + channel, t))
                    .ignore();
        } catch (MqttException e) {
            throw new DeploymentException(e);
        }
    }

    void publish(IMqttClient client, String topic, MqttMessage msg) {
        try {
            client.publish(topic, msg);
        } catch (MqttException e) {
            throw new IllegalStateException(e);
        }
    }
}
