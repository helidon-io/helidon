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

package io.helidon.quic.api;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.helidon.common.tls.Tls;
import io.helidon.quic.QuicClient;
import io.helidon.quic.QuicClientTarget;
import io.helidon.quic.QuicServer;
import io.helidon.quic.QuicServerConfig;
import io.helidon.quic.QuicStreamTerminationException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicPublicApiTest {

    @Test
    void shouldCreateImmutableTargetAndDefaultTlsPort() {
        var protocols = new ArrayList<>(List.of("example"));
        QuicClientTarget target = validTarget()
                .applicationProtocols(protocols)
                .buildPrototype();

        protocols.add("changed");

        assertThat(target.tlsPeerPort(), is(443));
        assertThat(target.applicationProtocols(), contains("example"));
    }

    @Test
    void shouldRejectInvalidTargets() {
        assertInvalidTarget(it -> it.peerAddress(InetSocketAddress.createUnresolved("localhost", 443)),
                            "must be resolved");
        assertInvalidTarget(it -> it.peerAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0)),
                            "positive port");
        assertInvalidTarget(it -> it.tlsPeerName("  "), "must not be blank");
        assertInvalidTarget(it -> it.tlsPeerPort(65_536), "between 1 and 65535");
        assertInvalidTarget(it -> it.applicationProtocols(List.of()), "At least one");
        assertInvalidTarget(it -> it.applicationProtocols(List.of("")), "must not be empty");
        assertInvalidTarget(it -> it.applicationProtocols(List.of("example", "example")), "Duplicate");
        assertInvalidTarget(it -> it.applicationProtocols(List.of("\u20ac")), "outside the byte range");
        assertInvalidTarget(it -> it.applicationProtocols(List.of("a".repeat(256))), "255-byte");
    }

    @Test
    void shouldPreserveOpaqueApplicationProtocolIds() {
        String opaqueProtocol = "\u0000\u00ff";
        Tls tls = Tls.create(it -> { });

        QuicClientTarget target = validTarget()
                .applicationProtocols(List.of(opaqueProtocol))
                .buildPrototype();
        QuicServerConfig config = QuicServer.builder()
                .tls(tls)
                .applicationProtocols(List.of(opaqueProtocol))
                .buildPrototype();

        assertThat(target.applicationProtocols(), contains(opaqueProtocol));
        assertThat(config.applicationProtocols(), contains(opaqueProtocol));
    }

    @Test
    void shouldExposeStreamTerminationTypeFromExportedPackage() {
        Class<QuicStreamTerminationException> type = QuicStreamTerminationException.class;

        assertThat(type.getModule().isExported(type.getPackageName()), is(true));
        assertThat(QuicStreamTerminationException.Kind.RESET_BY_PEER.name(), is("RESET_BY_PEER"));
    }

    @Test
    void shouldValidateClientConfiguration() {
        Tls tls = Tls.create(it -> { });
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> QuicClient.builder()
                        .tls(tls)
                        .initialResponseTimeout(Duration.ofSeconds(5))
                        .handshakeTimeout(Duration.ofSeconds(4))
                        .buildPrototype());

        assertThat(failure.getMessage(), containsString("must not exceed"));
    }

    @Test
    void shouldValidateServerLimitsAndSnapshotProtocols() {
        Tls tls = Tls.create(it -> { });
        var protocols = new ArrayList<>(List.of("example"));
        QuicServerConfig config = QuicServer.builder()
                .tls(tls)
                .applicationProtocols(protocols)
                .maxPendingHandshakes(1)
                .maxConnections(1)
                .buildPrototype();

        protocols.add("changed");

        assertThat(config.applicationProtocols(), contains("example"));
        assertThat(config.maxPendingHandshakes(), is(1));
        assertThat(config.maxConnections(), is(1));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> QuicServer.builder()
                        .tls(tls)
                        .applicationProtocols(List.of("example"))
                        .maxConnections(0)
                        .buildPrototype());
        assertThat(failure.getMessage(), containsString("maxConnections"));
    }

    private static QuicClientTarget.Builder validTarget() {
        return QuicClientTarget.builder()
                .peerAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 443))
                .tlsPeerName("localhost")
                .applicationProtocols(List.of("example"));
    }

    private static void assertInvalidTarget(Consumer<QuicClientTarget.Builder> mutation, String expectedMessage) {
        QuicClientTarget.Builder builder = validTarget();
        mutation.accept(builder);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, builder::buildPrototype);
        assertThat(failure.getMessage(), containsString(expectedMessage));
    }
}
