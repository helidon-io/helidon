/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.microprofile.messaging.connector;

import io.helidon.common.reactive.Multi;
import io.helidon.microprofile.tests.junit5.AddConfig;
import org.eclipse.microprofile.config.Config;
import io.helidon.config.mp.MpConfig;
import io.helidon.microprofile.messaging.MessagingCdiExtension;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.eclipse.microprofile.reactive.messaging.spi.IncomingConnectorFactory;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("because @Inject and @HelidonTest(resetPerTest=true) throws exception")
@HelidonTest(resetPerTest = true)
@DisableDiscovery
@AddExtension(MessagingCdiExtension.class)
public class ConnectorConfigTest {

    @Inject
    @Channel("config-channel-in")
    private Multi<Config> configMulti;

    @Test
    @AddConfig(key = "mp.messaging.incoming.config-channel-in.connector", value = "config-connector")
    @AddConfig(key = "mp.messaging.incoming.config-channel-in.non-empty-value", value = "a_value")
    @AddConfig(key = "mp.messaging.incoming.config-channel-in.empty-value", value = "${EMPTY}")
    @AddBean(ConfigConnector.class)
    void emptyConfigValueTest() throws ExecutionException, InterruptedException {
        Config connectorConfig = configMulti.collectList().get().get(0);
        var helidonConfig = MpConfig.toHelidonConfig(connectorConfig);
        var kafkaesqueConfig = helidonConfig.detach().asMap().orElseGet(Map::of);
        assertThat(kafkaesqueConfig.get("non-empty-value"), Matchers.is("a_value"));
        assertTrue(kafkaesqueConfig.containsKey("empty-value"));
        assertThat(kafkaesqueConfig.get("empty-value"), Matchers.is(""));
    }

    @ApplicationScoped
    @Connector("config-connector")
    public static class ConfigConnector implements IncomingConnectorFactory {

        @Override
        public PublisherBuilder<? extends Message<?>> getPublisherBuilder(Config config) {
            return ReactiveStreams.of(config).map(Message::of);
        }
    }
}
