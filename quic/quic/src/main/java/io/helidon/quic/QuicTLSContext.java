/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

/**
 * Instances of this class act as a factory for creation
 * of {@link QuicTLSEngine QUIC TLS engine}.
 * <p>
 * When the JDK exposes public QUIC TLS support, this factory should delegate to
 * {@code SSLContext#createQUICEngine(peerHost, peerPort, QuicTLSCallbacks)}.
 */
@Api.Internal
public final class QuicTLSContext {
    private final QuicTlsEngineFactory engineFactory;

    /**
     * Constructs a QuicTLSContext for the given engine factory.
     *
     * @param engineFactory QUIC TLS engine factory
     */
    private QuicTLSContext(QuicTlsEngineFactory engineFactory) {
        this.engineFactory = Objects.requireNonNull(engineFactory, "engineFactory");
    }

    /**
     * Returns {@code true} if the given {@code tls} supports QUIC TLS, {@code false} otherwise.
     *
     * @return {@code true} if the given {@code tls} supports QUIC TLS, {@code false} otherwise.
     *
     * @param tls TLS configuration
     * @throws IllegalStateException if cryptographic provider setup or validation fails
     */
    public static boolean isQuicCompatible(Tls tls) {
        Objects.requireNonNull(tls, "tls");
        if (!tls.enabled()) {
            return false;
        }
        return QuicTlsCompatibility.isCompatible(
                QuicTlsConfigSnapshot.create(tls, QuicRuntimeConfig.create(QuicConfig.create())));
    }

    /**
     * Creates a QUIC TLS context for the given TLS configuration.
     *
     * @param tls TLS configuration to wrap
     * @return QUIC TLS context
     * @throws IllegalArgumentException if the TLS configuration is disabled or incompatible with QUIC TLS,
     *                                  including configurations with algorithm constraints or non-empty SNI matchers
     * @throws IllegalStateException if cryptographic provider setup or validation fails
     */
    public static QuicTLSContext create(Tls tls) {
        Objects.requireNonNull(tls, "tls");
        if (!tls.enabled()) {
            throw new IllegalArgumentException("Cannot construct a QUIC TLS context with disabled TLS");
        }
        return create(QuicTlsConfigSnapshot.create(tls, QuicRuntimeConfig.create(QuicConfig.create())));
    }

    /**
     * Creates a QUIC TLS context for the resolved TLS configuration.
     *
     * @param config resolved QUIC TLS configuration to wrap
     * @return QUIC TLS context
     * @throws IllegalStateException if cryptographic provider setup or validation fails
     */
    static QuicTLSContext create(QuicTlsConfigSnapshot config) {
        Objects.requireNonNull(config, "config");
        return new QuicTLSContext(QuicTlsProviders.provider().createEngineFactory(config));
    }

    /**
     * Creates a {@link QuicTLSEngine} using this context.
     * <p>
     * This method does not provide hints for session caching.
     *
     * @return the newly created QuicTLSEngine
     */
    public QuicTLSEngine createEngine() {
        return engineFactory.createEngine();
    }

    /**
     * Creates a {@link QuicTLSEngine} using this context using
     * advisory peer information.
     * <p>
     * The provided parameters will be used as hints for session caching.
     * The {@code peerHost} parameter will be used in the server_name extension,
     * unless overridden later.
     * <p>
     * Future JDK migration point: create the underlying engine with
     * {@code SSLContext#createQUICEngine(peerHost, peerPort, QuicTLSCallbacks)}
     * once that API is public.
     *
     * @param peerHost The peer hostname or IP address.
     * @param peerPort The peer port, can be -1 if the port is unknown
     * @return the newly created QuicTLSEngine
     */
    public QuicTLSEngine createEngine(String peerHost, int peerPort) {
        Objects.requireNonNull(peerHost, "peerHost");
        return engineFactory.createEngine(peerHost, peerPort);
    }

    QuicTLSContext withClientSessionCache(QuicTlsSessionCache sessionCache) {
        return new QuicTLSContext(engineFactory.withClientSessionCache(
                Objects.requireNonNull(sessionCache, "sessionCache")));
    }

    QuicTLSContext withServerSessionCache(QuicTlsServerSessionCache sessionCache) {
        return new QuicTLSContext(engineFactory.withServerSessionCache(
                Objects.requireNonNull(sessionCache, "sessionCache")));
    }

    QuicTLSContext withServerTlsSelector(QuicTlsServerSelector selector) {
        return new QuicTLSContext(engineFactory.withServerTlsSelector(
                Objects.requireNonNull(selector, "selector")));
    }

}
