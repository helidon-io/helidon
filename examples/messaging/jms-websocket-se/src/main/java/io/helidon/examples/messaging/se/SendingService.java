
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

package io.helidon.examples.messaging.se;

import org.apache.activemq.jndi.ActiveMQInitialContextFactory;

import io.helidon.config.Config;
import io.helidon.messaging.Channel;
import io.helidon.messaging.Emitter;
import io.helidon.messaging.Messaging;
import io.helidon.messaging.connectors.jms.JmsConfigBuilder;
import io.helidon.messaging.connectors.jms.JmsConnector;
import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;

public class SendingService implements Service {

    private final Emitter<String> emitter;
    private final Messaging messaging;

    SendingService(Config config) {

        String url = config.get("app.jms.url").asString().get();
        String destination = config.get("app.jms.destination").asString().get();

        // Prepare channel for connecting processor -> jms connector with specific subscriber configuration,
        // channel -> connector mapping is automatic when using JmsConnector.configBuilder()
        Channel<String> toJms = Channel.<String>builder()
                .subscriberConfig(JmsConnector.configBuilder()
                        .jndiInitialFactory(ActiveMQInitialContextFactory.class.getName())
                        .jndiProviderUrl(url)
                        .type(JmsConfigBuilder.Type.QUEUE)
                        .destination(destination)
                        .build()
                ).build();

        // Prepare channel for connecting emitter -> processor
        Channel<String> toProcessor = Channel.create();

        // Prepare Jms connector, can be used by any channel
        JmsConnector jmsConnector = JmsConnector.create();

        // Prepare emitter for manual publishing to channel
        emitter = Emitter.create(toProcessor);

        messaging = Messaging.builder()
                .emitter(emitter)
                // Processor connect two channels together
                .processor(toProcessor, toJms, payload -> {
                    // Transforming to upper-case before sending to jms
                    return payload.toUpperCase();
                })
                .connector(jmsConnector)
                .build()
                .start();
    }

    /**
     * A service registers itself by updating the routing rules.
     *
     * @param rules the routing rules.
     */
    @Override
    public void update(Routing.Rules rules) {
        // Listen for GET /example/send/{msg}
        // to send it thru messaging to Jms
        rules.get("/send/{msg}", (req, res) -> {
            String msg = req.path().param("msg");
            System.out.println("Emitting: " + msg);
            emitter.send(msg);
            res.send();
        });
    }

    /**
     * Gracefully terminate messaging.
     */
    public void shutdown() {
        messaging.stop();
    }
}
