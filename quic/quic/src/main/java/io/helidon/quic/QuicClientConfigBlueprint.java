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
import java.util.Optional;
import java.util.concurrent.Executor;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;
import io.helidon.common.tls.Tls;

/**
 * Standalone QUIC client configuration.
 *
 * <p>The builder validates the option domains documented below when it builds the configuration or client.
 */
@Prototype.Blueprint(decorator = QuicClientConfigSupport.Decorator.class, createEmptyPublic = false)
@Api.Incubating
interface QuicClientConfigBlueprint extends Prototype.Factory<QuicClient> {

    /**
     * TLS configuration used by newly created connections.
     *
     * @return TLS configuration
     */
    Tls tls();

    /**
     * Borrowed executor used by the transport.
     *
     * <p>The client never closes this executor.
     *
     * @return transport executor
     */
    @Option.DefaultCode("QuicPublicApiSupport.defaultExecutor()")
    Executor executor();

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
     * Timeout for the first authenticated server response to a client Initial flight.
     *
     * <p>The value must be positive, fit in signed 64-bit nanoseconds, and not exceed {@link #handshakeTimeout()}.
     *
     * @return initial response timeout
     */
    @Option.Default("PT30S")
    Duration initialResponseTimeout();

    /**
     * Total timeout for a standalone client handshake.
     *
     * <p>The value must be positive and fit in signed 64-bit nanoseconds.
     *
     * @return handshake timeout
     */
    @Option.Default("PT30S")
    Duration handshakeTimeout();

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
     * Client identifier used in diagnostics.
     *
     * <p>When present, the identifier must not be blank.
     *
     * @return configured identifier
     */
    Optional<String> clientId();
}
