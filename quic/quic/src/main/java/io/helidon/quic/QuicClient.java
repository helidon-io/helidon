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

import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.Api;

/**
 * Standalone QUIC client.
 *
 * <p>Connection operations block and are intended for virtual threads. The client owns its UDP transport, timers,
 * connection registry, and transport caches. Its configured executor is borrowed and is never closed by the client.
 */
@Api.Incubating
public interface QuicClient extends RuntimeType.Api<QuicClientConfig>, AutoCloseable {

    /**
     * Creates a client configuration builder.
     *
     * @return new builder
     */
    static QuicClientConfig.Builder builder() {
        return QuicClientConfig.builder();
    }

    /**
     * Creates a standalone client.
     *
     * @param config client configuration
     * @return new client
     */
    static QuicClient create(QuicClientConfig config) {
        return new QuicClientImpl(config);
    }

    /**
     * Creates a standalone client using a configuration consumer.
     *
     * @param consumer configuration consumer
     * @return new client
     */
    static QuicClient create(Consumer<QuicClientConfig.Builder> consumer) {
        return create(QuicClientConfig.builder()
                              .update(consumer)
                              .buildPrototype());
    }

    /**
     * Connects to a target and completes the QUIC handshake.
     *
     * @param target target transport and TLS identity
     * @return handshake-complete session
     */
    QuicSession connect(QuicClientTarget target);

    /**
     * Whether this client is closed.
     *
     * @return {@code true} when closed
     */
    boolean isClosed();

    /**
     * Closes all client connections and owned transport resources.
     *
     * <p>This operation is idempotent.
     */
    @Override
    void close();
}
