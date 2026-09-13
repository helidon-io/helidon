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

package io.helidon.messaging;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.messaging.spi.MessagingIncomingConfig;
import io.helidon.messaging.spi.MessagingOutgoingConfig;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessagingChannelConfigTest {
    @Test
    void retainsConnectorOptionsAndDerivesChannelName() {
        Config source = Config.just("""
                orders:
                  connector: broker
                  channel-name: not-the-logical-name
                  topic: inventory
                  execution:
                    max-in-flight-messages: 8
                """, MediaTypes.APPLICATION_YAML).get("orders");

        MessagingIncomingConfig config = MessagingIncomingConfig.create(source);

        assertThat(config.connector(), is("broker"));
        assertThat(config.channelName(), is("orders"));
        assertThat(config.config().orElseThrow(), sameInstance(source));
        assertThat(config.config().orElseThrow().get("topic").asString().get(), is("inventory"));
        assertThat(config.execution().maxInFlightMessages().orElseThrow(), is(8));
        assertThat(config.execution().queueCapacity().isEmpty(), is(true));
    }

    @Test
    void derivesChannelNameFromMapEntryAlias() {
        Config source = Config.just("""
                configured-entry:
                  name: sent
                  connector: broker
                """, MediaTypes.APPLICATION_YAML).get("configured-entry");

        MessagingOutgoingConfig config = MessagingOutgoingConfig.create(source);

        assertThat(config.channelName(), is("sent"));
        assertThat(config.execution(), is(MessagingExecutionConfig.create()));
    }

    @Test
    void programmaticConfigurationNeedsNoRawConfig() {
        MessagingOutgoingConfig config = MessagingOutgoingConfig.builder()
                .connector("broker")
                .channelName("sent")
                .execution(execution -> execution.queueCapacity(3))
                .build();

        assertThat(config.connector(), is("broker"));
        assertThat(config.channelName(), is("sent"));
        assertThat(config.config().isEmpty(), is(true));
        assertThat(config.execution().queueCapacity().orElseThrow(), is(3));
        assertThat(config.execution().maxPendingMessages().isEmpty(), is(true));
    }

    @Test
    void requiresConnectorAndChannelName() {
        RuntimeException connectorFailure = assertThrows(RuntimeException.class,
                () -> MessagingIncomingConfig.builder().channelName("orders").build());
        RuntimeException nameFailure = assertThrows(RuntimeException.class,
                () -> MessagingIncomingConfig.builder().connector("broker").build());

        assertThat(connectorFailure.getMessage(), containsString("connector"));
        assertThat(nameFailure.getMessage(), containsString("channelName"));
    }
}
