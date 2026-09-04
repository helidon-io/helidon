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
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.Api;

/**
 * Standalone QUIC server.
 *
 * <p>Accept operations block and are intended for virtual threads. The server owns its UDP transport, timers, TLS state,
 * connection registry, and admission limits. Its configured executor is borrowed and is never closed by the server.
 */
@Api.Incubating
public interface QuicServer extends RuntimeType.Api<QuicServerConfig>, AutoCloseable {

    /**
     * Creates a server configuration builder.
     *
     * @return new builder
     */
    static QuicServerConfig.Builder builder() {
        return QuicServerConfig.builder();
    }

    /**
     * Creates a standalone server.
     *
     * @param config server configuration
     * @return new server
     */
    static QuicServer create(QuicServerConfig config) {
        return new QuicServerImpl(config);
    }

    /**
     * Creates a standalone server using a configuration consumer.
     *
     * @param consumer configuration consumer
     * @return new server
     */
    static QuicServer create(Consumer<QuicServerConfig.Builder> consumer) {
        return create(QuicServerConfig.builder()
                              .update(consumer)
                              .buildPrototype());
    }

    /**
     * Starts the server.
     *
     * @return this server
     */
    QuicServer start();

    /**
     * Whether this server is running.
     *
     * @return {@code true} after successful start and before stop
     */
    boolean isRunning();

    /**
     * Bound local address.
     *
     * @return bound address
     * @throws IllegalStateException if the server has not been started
     */
    InetSocketAddress localAddress();

    /**
     * Waits for and accepts the next handshake-complete session.
     *
     * @return accepted session
     * @throws IllegalStateException if the server is not running
     */
    QuicSession accept();

    /**
     * Stops the server using the configured shutdown timeout.
     *
     * @return this server
     */
    QuicServer stop();

    /**
     * Stops the server.
     *
     * <p>The timeout is validated before any server lifecycle state change. It must be non-null, strictly positive, and
     * fit in signed-long nanoseconds.
     *
     * @param timeout maximum graceful shutdown duration
     * @return this server
     * @throws NullPointerException if the timeout is {@code null}
     * @throws IllegalArgumentException if the timeout is zero, negative, or does not fit in signed-long nanoseconds
     * @throws QuicException if graceful cleanup does not complete within the timeout
     */
    QuicServer stop(Duration timeout);

    /**
     * Stops the server using the configured shutdown timeout.
     */
    @Override
    default void close() {
        stop();
    }
}
