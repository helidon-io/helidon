/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
import io.helidon.messaging.connectors.jms.JmsConnector;
import io.helidon.messaging.connectors.jms.Type;

import org.apache.activemq.jndi.ActiveMQInitialContextFactory;

/**
 * WebSocket endpoint.
 */
public class WebSocketEndpoint extends Endpoint {

    private static final Logger LOGGER = Logger.getLogger(WebSocketEndpoint.class.getName());

    private final Map<String, Messaging> messagingRegister = new HashMap<>();
    private final Config config = Config.create();

    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig) {

        System.out.println("Session " + session.getId());

        String url = config.get("app.jms.url").asString().get();
        String destination = config.get("app.jms.destination").asString().get();

        // Prepare channel for connecting jms connector with specific publisher configuration -> listener,
        // channel -> connector mapping is automatic when using JmsConnector.configBuilder()
        Channel<String> fromJms = Channel.<String>builder()
                .name("from-jms")
                .publisherConfig(JmsConnector.configBuilder()
                        .jndiInitialFactory(ActiveMQInitialContextFactory.class.getName())
                        .jndiProviderUrl(url)
                        .type(Type.QUEUE)
                        .destination(destination)
                        .build()
                )
                .build();

        // Prepare Jms connector, can be used by any channel
        JmsConnector jmsConnector = JmsConnector.create();

        Messaging messaging = Messaging.builder()
                .connector(jmsConnector)
                .listener(fromJms, payload -> {
                    System.out.println("Jms says: " + payload);
                    // Send message received from Jms over websocket
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
