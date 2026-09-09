/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.messaging.tests.custom.connector;

import java.time.Duration;
import java.util.List;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.messaging.Message;
import io.helidon.messaging.MessagingChannel;
import io.helidon.messaging.MessagingConfig;
import io.helidon.messaging.MessagingGraph;
import io.helidon.messaging.MessagingRuntime;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;

@Timeout(10)
@SuppressWarnings({"helidon:api:internal", "helidon:api:preview"})
class CustomConnectorTest {
    private static final Duration WAIT = Duration.ofSeconds(5);

    @Test
    void roundTripsMessageThroughImperativeConnector() throws InterruptedException {
        CustomConnectorProbe probe = new CustomConnectorProbe();
        CustomConnector connector = CustomConnector.builder()
                .name("imperative")
                .endpoint("loopback")
                .prefix("imperative-prefix")
                .broker(new CustomConnectorBroker())
                .probe(probe)
                .build();
        CustomChannelConfig sentConfig = CustomChannelConfig.builder()
                .channelName("sent")
                .prefix("outgoing-prefix")
                .build();
        CustomChannelConfig receivedConfig = CustomChannelConfig.builder()
                .channelName("received")
                .build();
        Message<String> message = Message.builder("hello")
                .header("trace", "imperative")
                .build();

        MessagingGraph.Builder builder = MessagingGraph.builder();
        MessagingChannel<String> sent = builder.channel("sent", String.class);
        MessagingChannel<String> received = builder.channel("received", String.class);
        builder.outgoingChannel(sent, connector.outgoing(sentConfig))
                .incomingChannel(received, connector.incoming(receivedConfig))
                .messageSink(received, incoming -> probe.received(received.name(), incoming));

        try (MessagingGraph graph = builder.build()) {
            graph.start();
            graph.emitter(sent).emit(message);

            assertThat("message was not received", probe.awaitSettled(WAIT), is(true));
        }

        assertThat(probe.sent(), is(List.of("sent:outgoing-prefix:hello:imperative")));
        assertThat(probe.received(), is(List.of("received:hello:imperative")));
        assertThat(probe.configured(), containsInAnyOrder(
                "outgoing:sent:imperative:loopback:outgoing-prefix",
                "incoming:received:imperative:loopback:imperative-prefix"));
        assertThat(probe.lifecycle(), hasItems("started:sent", "started:received", "closed:sent", "closed:received"));
    }

    @Test
    void roundTripsMessageThroughConnectorObjectConfiguration() throws InterruptedException {
        Config config = Config.just("""
                messaging:
                  connector:
                    sender:
                      type: test-custom
                      endpoint: unused-endpoint
                      prefix: sender-prefix
                    receiver:
                      type: test-custom
                      endpoint: loopback
                      prefix: receiver-prefix
                  incoming:
                    received:
                      connector: receiver
                  outgoing:
                    configured-channel:
                      name: sent
                      connector: sender
                      endpoint: loopback
                      prefix: outgoing-prefix
                """, MediaTypes.APPLICATION_YAML);

        assertConfiguredRoundTrip(config);
    }

    @Test
    void roundTripsMessageThroughConnectorListConfiguration() throws InterruptedException {
        Config config = Config.just("""
                messaging:
                  connector:
                    - type: test-custom
                      name: sender
                      endpoint: unused-endpoint
                      prefix: sender-prefix
                    - type: test-custom
                      name: receiver
                      endpoint: loopback
                      prefix: receiver-prefix
                  incoming:
                    received:
                      connector: receiver
                  outgoing:
                    configured-channel:
                      name: sent
                      connector: sender
                      endpoint: loopback
                      prefix: outgoing-prefix
                """, MediaTypes.APPLICATION_YAML);

        assertConfiguredRoundTrip(config);
    }

    @Test
    void preservesChannelConfigurationSubtrees() {
        Config config = Config.just("""
                queue-capacity: 0
                incoming:
                  received:
                    connector: custom-connector
                    prefix: incoming-prefix
                    custom:
                      delivery:
                        mode: ordered
                outgoing:
                  configured-channel:
                    name: sent
                    connector: custom-connector
                    prefix: outgoing-prefix
                    custom:
                      retry:
                        max-attempts: 3
                """, MediaTypes.APPLICATION_YAML);
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        try {
            MessagingConfig messaging = MessagingConfig.builder()
                    .serviceRegistry(manager.registry())
                    .config(config)
                    .buildPrototype();

            assertThat(messaging.incoming().keySet(), containsInAnyOrder("received"));
            Config incoming = messaging.incoming().get("received");
            assertThat(incoming.get("connector").asString().get(), is("custom-connector"));
            assertThat(incoming.get("prefix").asString().get(), is("incoming-prefix"));
            assertThat(incoming.get("custom.delivery.mode").asString().get(), is("ordered"));

            assertThat(messaging.outgoing().keySet(), containsInAnyOrder("sent"));
            Config outgoing = messaging.outgoing().get("sent");
            assertThat(outgoing.get("name").asString().get(), is("sent"));
            assertThat(outgoing.get("connector").asString().get(), is("custom-connector"));
            assertThat(outgoing.get("prefix").asString().get(), is("outgoing-prefix"));
            assertThat(outgoing.get("custom.retry.max-attempts").asInt().get(), is(3));
        } finally {
            manager.shutdown();
        }
    }

    private static void assertConfiguredRoundTrip(Config config) throws InterruptedException {
        ServiceRegistryConfig registryConfig = ServiceRegistryConfig.builder()
                .putContractInstance(Config.class, config)
                .build();
        ServiceRegistryManager manager = ServiceRegistryManager.create(registryConfig);
        CustomConnectorProbe probe;
        try {
            ServiceRegistry registry = manager.registry();
            probe = registry.get(CustomConnectorProbe.class);
            MessagingRuntime runtime = registry.get(MessagingRuntime.class);
            Message<String> message = Message.builder("hello")
                    .header("trace", "configured")
                    .build();

            runtime.emit("sent", message);

            assertThat("message was not received", probe.awaitSettled(WAIT), is(true));
            assertThat(probe.sent(), is(List.of("sent:outgoing-prefix:hello:configured")));
            assertThat(probe.received(), is(List.of("received:hello:configured")));
            assertThat(probe.configured(), containsInAnyOrder(
                    "outgoing:sent:sender:loopback:outgoing-prefix",
                    "incoming:received:receiver:loopback:receiver-prefix"));
        } finally {
            manager.shutdown();
        }
        assertThat(probe.lifecycle(), hasItems("started:sent", "started:received", "closed:sent", "closed:received"));
    }
}
