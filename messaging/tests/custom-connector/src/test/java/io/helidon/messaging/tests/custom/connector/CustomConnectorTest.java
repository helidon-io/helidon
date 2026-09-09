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
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigNode;
import io.helidon.messaging.Message;
import io.helidon.messaging.MessagingChannel;
import io.helidon.messaging.MessagingConfig;
import io.helidon.messaging.MessagingGraph;
import io.helidon.messaging.MessagingRuntime;
import io.helidon.messaging.spi.MessagingConnector;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

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
        Config sentConfig = channelConfig("sent", "imperative-prefix");
        Config receivedConfig = channelConfig("received", "imperative-prefix");
        Message<String> message = Message.builder("hello")
                .header("trace", "imperative")
                .build();

        try (MessagingGraph.Builder builder = MessagingGraph.builder()) {
            MessagingChannel<String> sent = builder.channel("sent", String.class);
            MessagingChannel<String> received = builder.channel("received", String.class);
            builder.outgoingConnector(sent, connector.outgoing(sentConfig).orElseThrow())
                    .messageSource(received, connector.incomingStream(receivedConfig))
                    .messageSink(received, incoming -> {
                        probe.received(received.name(), incoming);
                        probe.settled();
                    });

            try (MessagingGraph graph = builder.build()) {
                graph.start();
                graph.emitter(sent).emit(message);

                assertThat("message was not received", probe.awaitSettled(WAIT), is(true));
            }
        }

        assertThat(probe.sent(), is(List.of("sent:imperative-prefix:hello:imperative")));
        assertThat(probe.received(), is(List.of("received:hello:imperative")));
    }

    @Test
    void roundTripsMessageThroughConfiguredConnector() throws InterruptedException {
        Config config = Config.builder(ConfigSources.create(Map.of(
                        "messaging.connector.test-custom.endpoint", "loopback",
                        "messaging.connector.test-custom.prefix", "connector-prefix",
                        "messaging.outgoing.sent.connector", CustomConnectorProvider.CONNECTOR_TYPE,
                        "messaging.outgoing.sent.prefix", "outgoing-prefix",
                        "messaging.incoming.received.connector", CustomConnectorProvider.CONNECTOR_TYPE,
                        "messaging.incoming.received.prefix", "incoming-prefix")))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
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
                    "OUTGOING:sent:test-custom:loopback:outgoing-prefix",
                    "INCOMING:received:test-custom:loopback:incoming-prefix"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void createsConnectorFromObjectConfiguration() {
        ConfigNode.ObjectNode connectorConfig = ConfigNode.ObjectNode.builder()
                .addObject("object-connector", ConfigNode.ObjectNode.builder()
                        .addValue("type", CustomConnectorProvider.CONNECTOR_TYPE)
                        .addValue("endpoint", "object-endpoint")
                        .addValue("prefix", "object-prefix")
                        .build())
                .build();

        assertConfiguredConnector(connectorConfig,
                                  "object-connector",
                                  "object-endpoint",
                                  "object-prefix");
    }

    @Test
    void createsConnectorFromListConfiguration() {
        ConfigNode.ListNode connectorConfig = ConfigNode.ListNode.builder()
                .addObject(ConfigNode.ObjectNode.builder()
                        .addValue("type", CustomConnectorProvider.CONNECTOR_TYPE)
                        .addValue("name", "list-connector")
                        .addValue("endpoint", "list-endpoint")
                        .addValue("prefix", "list-prefix")
                        .build())
                .build();

        assertConfiguredConnector(connectorConfig,
                                  "list-connector",
                                  "list-endpoint",
                                  "list-prefix");
    }

    @Test
    void preservesChannelConfigurationSubtrees() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "execution.queue-capacity", "0",
                "incoming.received.connector", "custom-connector",
                "incoming.received.prefix", "incoming-prefix",
                "incoming.received.custom.delivery.mode", "ordered",
                "outgoing.configured-channel.name", "sent",
                "outgoing.configured-channel.connector", "custom-connector",
                "outgoing.configured-channel.prefix", "outgoing-prefix",
                "outgoing.configured-channel.custom.retry.max-attempts", "3")));
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        try {
            MessagingConfig messaging = MessagingConfig.builder()
                    .serviceRegistry(manager.registry())
                    .config(config)
                    .build();

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

    private static Config channelConfig(String channel, String prefix) {
        return Config.just(ConfigSources.create(Map.of("channel-name", channel,
                                                       "prefix", prefix)));
    }

    private static void assertConfiguredConnector(ConfigNode connectorConfig,
                                                  String name,
                                                  String endpoint,
                                                  String prefix) {
        ServiceRegistryManager manager = ServiceRegistryManager.create();
        try {
            MessagingConfig config = MessagingConfig.builder()
                    .serviceRegistry(manager.registry())
                    .config(messagingConfig(connectorConfig))
                    .build();

            assertThat(config.connector().size(), is(1));
            MessagingConnector connector = config.connector().getFirst();
            assertThat(connector, instanceOf(CustomConnector.class));
            assertThat(connector.name(), is(name));
            assertThat(connector.type(), is(CustomConnectorProvider.CONNECTOR_TYPE));
            CustomConnector customConnector = (CustomConnector) connector;
            assertThat(customConnector.endpoint(), is(endpoint));
            assertThat(customConnector.prefix(), is(prefix));
            assertThat(config.incoming(), is(Map.of()));
            assertThat(config.outgoing(), is(Map.of()));
        } finally {
            manager.shutdown();
        }
    }

    private static Config messagingConfig(ConfigNode connectorConfig) {
        ConfigNode.ObjectNode empty = ConfigNode.ObjectNode.empty();
        ConfigNode.ObjectNode messaging = ConfigNode.ObjectNode.builder()
                .addObject("execution", empty)
                .addNode("connector", connectorConfig)
                .addObject("incoming", empty)
                .addObject("outgoing", empty)
                .build();
        ConfigNode.ObjectNode root = ConfigNode.ObjectNode.builder()
                .addObject("messaging", messaging)
                .build();
        return Config.just(ConfigSources.create(root)).get("messaging");
    }
}
