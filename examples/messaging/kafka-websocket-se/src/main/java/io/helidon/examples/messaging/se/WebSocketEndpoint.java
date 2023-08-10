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

package io.helidon.examples.messaging.se;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.messaging.Channel;
import io.helidon.messaging.Messaging;
import io.helidon.messaging.connectors.kafka.KafkaConfigBuilder;
import io.helidon.messaging.connectors.kafka.KafkaConnector;
import io.helidon.websocket.WsListener;
import io.helidon.websocket.WsSession;

import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Web socket endpoint.
 */
public class WebSocketEndpoint implements WsListener {

    private static final Logger LOGGER = Logger.getLogger(WebSocketEndpoint.class.getName());

    private final Map<WsSession, Messaging> messagingRegister = new HashMap<>();
    private final Config config = Config.create();

    @Override
    public void onOpen(WsSession session) {
        System.out.println("Session " + session);

        String kafkaServer = config.get("app.kafka.bootstrap.servers").asString().get();
        String topic = config.get("app.kafka.topic").asString().get();

        // Prepare channel for connecting kafka connector with specific publisher configuration -> listener,
        // channel -> connector mapping is automatic when using KafkaConnector.configBuilder()
        Channel<String> fromKafka = Channel.<String>builder()
                .name("from-kafka")
                .publisherConfig(KafkaConnector.configBuilder()
                        .bootstrapServers(kafkaServer)
                        .groupId("example-group-" + session)
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
                    session.send(payload, false);
                })
                .build()
                .start();

        //Save the messaging instance for proper shutdown
        // when websocket connection is terminated
        messagingRegister.put(session, messaging);
    }

    @Override
    public void onClose(WsSession session, int status, String reason) {
        LOGGER.info("Closing session " + session);
        // Properly stop messaging when websocket connection is terminated
        Optional.ofNullable(messagingRegister.remove(session))
                .ifPresent(Messaging::stop);
    }
}
