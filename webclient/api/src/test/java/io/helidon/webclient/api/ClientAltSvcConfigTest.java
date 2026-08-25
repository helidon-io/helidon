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

package io.helidon.webclient.api;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientAltSvcConfigTest {
    @Test
    void defaultsEnableAllAvailableProtocols() {
        ClientAltSvcConfig config = ClientAltSvcConfig.create();

        assertThat(config.enabled(), is(true));
        assertThat(config.protocols().isEmpty(), is(true));
    }

    @Test
    void acceptsOpaqueAndUnavailableProtocolIdentifiers() {
        ClientAltSvcConfig config = ClientAltSvcConfig.builder()
                .addProtocol("h2")
                .addProtocol("H2")
                .addProtocol("future-protocol")
                .addProtocol("h\u00ff")
                .build();

        assertThat(config.protocols(), containsInAnyOrder("h2", "H2", "future-protocol", "h\u00ff"));
    }

    @Test
    void rejectsInvalidProtocolIdentifiersWhenEnabled() {
        var empty = assertThrows(IllegalArgumentException.class,
                                 () -> ClientAltSvcConfig.builder().addProtocol("").build());
        var tooLong = assertThrows(IllegalArgumentException.class,
                                   () -> ClientAltSvcConfig.builder().addProtocol("h".repeat(256)).build());
        var outsideByteRange = assertThrows(IllegalArgumentException.class,
                                            () -> ClientAltSvcConfig.builder().addProtocol("h\u0100").build());

        assertThat(empty.getMessage(), containsString("cannot be empty"));
        assertThat(tooLong.getMessage(), containsString("255-byte ALPN limit"));
        assertThat(outsideByteRange.getMessage(), containsString("outside the byte range"));
    }

    @Test
    void disabledPolicyOverridesUnusedProtocolValidation() {
        ClientAltSvcConfig config = ClientAltSvcConfig.builder()
                .enabled(false)
                .addProtocol("")
                .addProtocol("h\u0100")
                .build();

        assertThat(config.enabled(), is(false));
        assertThat(config.protocols(), containsInAnyOrder("", "h\u0100"));
    }
}
