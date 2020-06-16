
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

package io.helidon.messaging.se.example;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;

import io.helidon.config.Config;
import io.helidon.messaging.Channel;
import io.helidon.messaging.Messaging;
import io.helidon.messaging.connectors.kafka.KafkaConfigBuilder;
import io.helidon.messaging.connectors.kafka.KafkaConnector;

import org.apache.kafka.common.serialization.StringDeserializer;

public class WebSocketEndpoint extends Endpoint {

    private static final Logger LOGGER = Logger.getLogger(WebSocketEndpoint.class.getName());

    private final Map<String, Messaging> messagingRegister = new HashMap<>();
    private final Config config = Config.create();

    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig) {

        System.out.println("Session " + session.getId());

        String kafkaServer = config.get("app.kafka.bootstrap.servers").asString().get();
        String topic = config.get("app.kafka.topic").asString().get();

        // Prepare channel for connecting kafka connector with specific publisher configuration -> listener,
        // channel -> connector mapping is automatic when using KafkaConnector.configBuilder()
        Channel<String> fromKafka = Channel.<String>builder()
                .name("from-kafka")
                .publisherConfig(KafkaConnector.configBuilder()
                        .bootstrapServers(kafkaServer)
                        .groupId("example-group-" + session.getId())
                        .topic(topic)
                        .autoOffsetReset(KafkaConfigBuilder.AutoOffsetReset.LATEST)
                        .enableAutoCommit(true)
                        .keyDeserializer(StringDeserializer.class)
                        .valueDeserializer(StringDeserializer.class)
                        .build()
                )
                .build();

        // Prepare Kafka connector, can be used by any channel
        KafkaConnector kafkaConnector = KafkaConnector.create();

        Messaging messaging = Messaging.builder()
                .connector(kafkaConnector)
                .listener(fromKafka, payload -> {
                    System.out.println("Kafka says: " + payload);
                    // Send message received from Kafka over websocket
                    sendTextMessage(session, payload);
                })
                .build()
                .start();

        //Save the messaging instance for proper shutdown
        // when websocket connection is terminated
        messagingRegister.put(session.getId(), messaging);
    }

    @Override
    public void onClose(final Session session, final CloseReason closeReason) {
        super.onClose(session, closeReason);
        LOGGER.info("Closing session " + session.getId());
        // Properly stop messaging when websocket connection is terminated
        Optional.ofNullable(messagingRegister.remove(session.getId()))
                .ifPresent(Messaging::stop);
    }

    private void sendTextMessage(Session session, String msg) {
        try {
            session.getBasicRemote().sendText(msg);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Message sending failed", e);
        }
    }
}
