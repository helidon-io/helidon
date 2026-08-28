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

package io.helidon.messaging.external;

import java.lang.reflect.Modifier;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.messaging.ConnectorConfig;
import io.helidon.messaging.ConnectorDirection;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectorConfigPublicApiTest {
    @Test
    void connectorApiIsPubliclyNameable() throws NoSuchMethodException {
        ConnectorConfig config = ConnectorConfig.builder()
                .direction(ConnectorDirection.INCOMING)
                .channelName("orders")
                .connector("test")
                .buildPrototype();

        assertThat(config.direction(), is(ConnectorDirection.INCOMING));
        assertThat(config.channelName(), is("orders"));
        assertThat(ConnectorConfig.class.getMethod("direction").getReturnType(), sameInstance(ConnectorDirection.class));
        assertThat(Modifier.isPublic(ConnectorDirection.class.getModifiers()), is(true));
        assertThat(ConnectorConfig.CHANNEL_NAME_ATTRIBUTE, is("channel-name"));
        assertThat(ConnectorConfig.CONNECTOR_ATTRIBUTE, is("connector"));
        assertThrows(NoSuchFieldException.class, () -> ConnectorConfig.class.getField("INCOMING_PREFIX"));
        assertThrows(NoSuchFieldException.class, () -> ConnectorConfig.class.getField("OUTGOING_PREFIX"));
        assertThrows(NoSuchFieldException.class, () -> ConnectorConfig.class.getField("CONNECTOR_PREFIX"));
    }

    @Test
    void loadsDerivedChannelNameConfigKey() {
        ConnectorConfig config = ConnectorConfig.create(Config.just(ConfigSources.create(Map.of(
                "direction", "OUTGOING",
                "channel-name", "orders",
                "connector", "test"))));

        assertThat(config.direction(), is(ConnectorDirection.OUTGOING));
        assertThat(config.channelName(), is("orders"));
        assertThat(config.connector(), is("test"));
    }
}
