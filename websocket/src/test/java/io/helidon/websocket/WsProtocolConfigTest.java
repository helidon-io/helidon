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

package io.helidon.websocket;

import java.util.Map;

import io.helidon.common.Size;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WsProtocolConfigTest {
    @Test
    void acceptsInclusiveBufferedMessageSizeBounds() {
        WsProtocolConfig zero = assertDoesNotThrow(() -> WsProtocolConfig.builder()
                .maxBufferedMessageSize(Size.create(0, Size.Unit.BYTE))
                .build());
        WsProtocolConfig maximum = assertDoesNotThrow(() -> WsProtocolConfig.builder()
                .maxBufferedMessageSize(Size.create(Integer.MAX_VALUE, Size.Unit.BYTE))
                .build());

        assertThat(zero.maxBufferedMessageSize().toBytes(), is(0L));
        assertThat(maximum.maxBufferedMessageSize().toBytes(), is((long) Integer.MAX_VALUE));
    }

    @Test
    void rejectsInvalidBufferedMessageSizeFromBuilder() {
        assertThrows(IllegalArgumentException.class,
                     () -> WsProtocolConfig.builder()
                             .maxBufferedMessageSize(Size.create(-1, Size.Unit.BYTE))
                             .build());
        assertThrows(IllegalArgumentException.class,
                     () -> WsProtocolConfig.builder()
                             .maxBufferedMessageSize(Size.create((long) Integer.MAX_VALUE + 1, Size.Unit.BYTE))
                             .build());
        assertThrows(IllegalArgumentException.class,
                     () -> WsProtocolConfig.builder()
                             .maxBufferedMessageSize(Size.create(Long.MAX_VALUE, Size.Unit.KIB))
                             .build());
    }

    @Test
    void rejectsInvalidBufferedMessageSizeFromConfig() {
        assertThrows(IllegalArgumentException.class,
                     () -> WsProtocolConfig.create(config("-1 B")));
        assertThrows(IllegalArgumentException.class,
                     () -> WsProtocolConfig.create(config(((long) Integer.MAX_VALUE + 1) + " B")));
        assertThrows(IllegalArgumentException.class,
                     () -> WsProtocolConfig.create(config(Long.MAX_VALUE + " KiB")));
    }

    private static Config config(String maxBufferedMessageSize) {
        return Config.create(ConfigSources.create(Map.of("max-buffered-message-size", maxBufferedMessageSize)));
    }
}
