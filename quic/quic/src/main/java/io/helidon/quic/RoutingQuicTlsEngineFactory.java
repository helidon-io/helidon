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
import java.util.Optional;

final class RoutingQuicTlsEngineFactory implements QuicTlsEngineFactory {
    private final QuicTlsConfigSnapshot config;
    private final QuicTlsSessionCache sessionCache;
    private final QuicTlsServerSessionCache serverSessionCache;
    private final QuicTlsServerSelector serverTlsSelector;

    RoutingQuicTlsEngineFactory(QuicTlsConfigSnapshot config) {
        this(config,
             new QuicTlsSessionCache(0, config.sessionTimeout()),
             new QuicTlsServerSessionCache(0, config.sessionTimeout()),
             null);
    }

    private RoutingQuicTlsEngineFactory(QuicTlsConfigSnapshot config,
                                        QuicTlsSessionCache sessionCache,
                                        QuicTlsServerSessionCache serverSessionCache,
                                        QuicTlsServerSelector serverTlsSelector) {
        this.config = Objects.requireNonNull(config, "config");
        this.sessionCache = Objects.requireNonNull(sessionCache, "sessionCache");
        this.serverSessionCache = Objects.requireNonNull(serverSessionCache, "serverSessionCache");
        this.serverTlsSelector = serverTlsSelector;
    }

    @Override
    public QuicTLSEngine createEngine() {
        return new RoutingQuicTLSEngine(config, sessionCache, serverSessionCache, serverTlsSelector);
    }

    @Override
    public QuicTLSEngine createEngine(String peerHost, int peerPort) {
        return new RoutingQuicTLSEngine(config,
                                        Objects.requireNonNull(peerHost, "peerHost"),
                                        peerPort,
                                        sessionCache,
                                        serverSessionCache,
                                        serverTlsSelector);
    }

    @Override
    public RoutingQuicTlsEngineFactory withClientSessionCache(QuicTlsSessionCache sessionCache) {
        return new RoutingQuicTlsEngineFactory(config,
                                               Objects.requireNonNull(sessionCache, "sessionCache"),
                                               serverSessionCache,
                                               serverTlsSelector);
    }

    @Override
    public RoutingQuicTlsEngineFactory withServerSessionCache(QuicTlsServerSessionCache sessionCache) {
        return new RoutingQuicTlsEngineFactory(config,
                                               this.sessionCache,
                                               Objects.requireNonNull(sessionCache, "sessionCache"),
                                               serverTlsSelector);
    }

    @Override
    public RoutingQuicTlsEngineFactory withServerTlsSelector(QuicTlsServerSelector selector) {
        return new RoutingQuicTlsEngineFactory(config,
                                               sessionCache,
                                               serverSessionCache,
                                               Objects.requireNonNull(selector, "selector"));
    }

    Optional<QuicTlsResumptionTicket> cachedResumptionTicket(String peerHost,
                                                             int peerPort,
                                                             String... applicationProtocols) {
        return sessionCache.cachedResumptionTicket(peerHost, peerPort, applicationProtocols);
    }
}
