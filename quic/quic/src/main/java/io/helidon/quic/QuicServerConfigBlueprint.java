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

package io.helidon.quic;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;
import io.helidon.common.tls.Tls;

/**
 * Standalone QUIC server configuration.
 *
 * <p>The builder validates the option domains documented below when it builds the configuration or server.
 */
@Prototype.Blueprint(decorator = QuicServerConfigSupport.Decorator.class, createEmptyPublic = false)
@Api.Incubating
interface QuicServerConfigBlueprint extends Prototype.Factory<QuicServer> {

    /**
     * TLS configuration used by newly accepted connections.
     *
     * @return TLS configuration
     */
    Tls tls();

    /**
     * Borrowed executor used by the transport.
     *
     * <p>The server never closes this executor.
     *
     * @return transport executor
     */
    @Option.DefaultCode("QuicPublicApiSupport.defaultExecutor()")
    Executor executor();

    /**
     * Ordered application protocols offered through ALPN.
     *
     * <p>The list must be non-empty and contain unique, non-empty protocol names. Each Java character maps directly to
     * one opaque protocol byte using ISO-8859-1, so characters outside the byte range are rejected. Each name must fit
     * the ALPN one-byte length limit of {@code 255} bytes and the complete encoded list must not exceed
     * {@code 65535} bytes.
     *
     * @return immutable validated protocol list
     */
    @Option.Singular
    List<String> applicationProtocols();

    /**
     * QUIC transport configuration.
     *
     * @return transport configuration
     */
    @Option.DefaultMethod("create")
    QuicConfig quicConfig();

    /**
     * Local UDP bind address.
     *
     * <p>When present, the address must be resolved. When empty, an ephemeral wildcard address is used.
     *
     * @return local bind address
     */
    Optional<InetSocketAddress> bindAddress();

    /**
     * Whether address validation uses QUIC Retry before connection allocation.
     *
     * @return whether Retry is enabled
     */
    @Option.DefaultBoolean(false)
    boolean retryEnabled();

    /**
     * Maximum time for an admitted server handshake.
     *
     * <p>The value must be positive and fit in signed 64-bit nanoseconds.
     *
     * @return handshake timeout
     */
    @Option.Default("PT10S")
    Duration handshakeTimeout();

    /**
     * Maximum number of concurrent pending handshakes.
     *
     * <p>The value must be greater than zero.
     *
     * @return pending handshake limit
     */
    @Option.DefaultInt(256)
    int maxPendingHandshakes();

    /**
     * Maximum number of admitted pending and established connections.
     *
     * <p>The value must be greater than zero.
     *
     * @return connection limit
     */
    @Option.DefaultInt(1024)
    int maxConnections();

    /**
     * Default maximum wait for peer stream credit.
     *
     * <p>The value must be positive and fit in signed 64-bit nanoseconds.
     *
     * @return stream-open timeout
     */
    @Option.Default("PT5S")
    Duration streamOpenTimeout();

    /**
     * Maximum graceful server shutdown duration.
     *
     * <p>The value must be positive and fit in signed 64-bit nanoseconds.
     *
     * @return shutdown timeout
     */
    @Option.Default("PT10S")
    Duration shutdownTimeout();

    /**
     * Server identifier used in diagnostics.
     *
     * <p>When present, the identifier must not be blank.
     *
     * @return configured identifier
     */
    Optional<String> serverId();
}
