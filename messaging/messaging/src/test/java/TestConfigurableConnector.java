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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import io.helidon.config.mp.MpConfig;
import io.helidon.messaging.ConnectorConfigBuilder;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.eclipse.microprofile.reactive.messaging.spi.ConnectorFactory;
import org.eclipse.microprofile.reactive.messaging.spi.IncomingConnectorFactory;
import org.eclipse.microprofile.reactive.messaging.spi.OutgoingConnectorFactory;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;

@Connector(TestConfigurableConnector.CONNECTOR_NAME)
public class TestConfigurableConnector implements IncomingConnectorFactory, OutgoingConnectorFactory {

    public static final String CONNECTOR_NAME = "test-configurable-connector";

    private TestConfigurableConnector() {
    }

    public static TestConfigurableConnector create() {
        return new TestConfigurableConnector();
    }

    @Override
    public PublisherBuilder<? extends Message<?>> getPublisherBuilder(final Config config) {
        io.helidon.config.Config helidonConfig = MpConfig.toHelidonConfig(config);
        printConfig(helidonConfig);
        return ReactiveStreams.fromIterable(config.getPropertyNames())
                .map(n -> n + "=" + config.getValue(n, String.class))
                .map(Message::of);
    }

    @Override
    public SubscriberBuilder<? extends Message<?>, Void> getSubscriberBuilder(final Config config) {
        io.helidon.config.Config helidonConfig = MpConfig.toHelidonConfig(config);
        printConfig(helidonConfig);
        return ReactiveStreams.<Message<CompletableFuture<Map<String, String>>>>builder()
                .map(Message::getPayload)
                .forEach(f -> f.complete(helidonConfig
                        .traverse()
                        .map(c -> Map.entry(c.key().name(), c.asString().get()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
                );
    }

    private static void printConfig(io.helidon.config.Config c) {
        c.asMap().orElse(Map.of()).forEach((key, value) -> System.out.println(key + ": " + value));
    }

    public static ConfigBuilder configBuilder() {
        return new ConfigBuilder();
    }

    public static class ConfigBuilder extends ConnectorConfigBuilder {

        protected ConfigBuilder() {
            super();
            super.put(ConnectorFactory.CONNECTOR_ATTRIBUTE, CONNECTOR_NAME);
        }

        public ConfigBuilder url(String url) {
            super.put("url", url);
            return this;
        }

        public ConfigBuilder port(int port) {
            super.put("port", String.valueOf(port));
            return this;
        }
    }
}
