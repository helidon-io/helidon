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

import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.common.tls.Tls;
import io.helidon.common.tls.TlsConfig;

/**
 * Opaque owner of client-side QUIC TLS session tickets.
 *
 * <p>A client runtime creates and closes one cache by default. Higher-level connection caches may instead share this
 * resource across short-lived runtimes using the same {@link Tls} identity and generation, then close it with that TLS
 * generation. A configured cache size of zero imposes no size limit, matching
 * {@link javax.net.ssl.SSLSessionContext#setSessionCacheSize(int)}.
 */
@Api.Internal
public final class QuicClientTlsSessionCache implements AutoCloseable {
    private final Tls tls;
    private final long tlsGeneration;
    private final QuicTlsSessionCache delegate;

    private QuicClientTlsSessionCache(Tls tls, long tlsGeneration, QuicTlsSessionCache delegate) {
        this.tls = tls;
        this.tlsGeneration = tlsGeneration;
        this.delegate = delegate;
    }

    /**
     * Creates a cache using the session policy from the supplied TLS configuration.
     *
     * @param tls TLS configuration
     * @return a new client TLS session cache
     */
    public static QuicClientTlsSessionCache create(Tls tls) {
        return create(tls, Integer.MAX_VALUE);
    }

    /**
     * Creates a cache using the session policy from the supplied TLS configuration, capped by the supplied maximum.
     *
     * @param tls TLS configuration
     * @param maximumSize maximum number of retained tickets; zero disables this cache, regardless of the TLS cache size
     * @return a new client TLS session cache
     * @throws IllegalArgumentException if the maximum size is negative
     */
    public static QuicClientTlsSessionCache create(Tls tls, int maximumSize) {
        if (maximumSize < 0) {
            throw new IllegalArgumentException("Maximum TLS session cache size must not be negative: " + maximumSize);
        }
        Objects.requireNonNull(tls, "tls");
        while (true) {
            long generation = tls.generation();
            TlsConfig tlsConfig = tls.prototype();
            if (generation == tls.generation()) {
                int configuredCapacity = tlsConfig.sessionCacheSize();
                int capacity = configuredCapacity;
                if (maximumSize != Integer.MAX_VALUE) {
                    capacity = configuredCapacity == 0 ? maximumSize : Math.min(configuredCapacity, maximumSize);
                }
                QuicTlsSessionCache delegate = new QuicTlsSessionCache(capacity,
                                                                       tlsConfig.sessionTimeout(),
                                                                       maximumSize != 0);
                return new QuicClientTlsSessionCache(tls,
                                                     generation,
                                                     delegate);
            }
        }
    }

    @Override
    public void close() {
        delegate.close();
    }

    QuicTlsSessionCache delegate() {
        return delegate;
    }

    boolean validFor(Tls tls) {
        return this.tls == tls && tlsGeneration == tls.generation();
    }

    int size() {
        return delegate.size();
    }
}
