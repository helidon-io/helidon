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

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

import io.helidon.common.tls.TlsConfig;

final class QuicTlsSessionCache implements AutoCloseable {
    private final QuicTlsTicketStore<SessionKey> tickets;

    QuicTlsSessionCache() {
        this(TlsConfig.DEFAULT_SESSION_CACHE_SIZE, Duration.parse(TlsConfig.DEFAULT_SESSION_TIMEOUT));
    }

    QuicTlsSessionCache(int capacity, Duration timeout) {
        this.tickets = new QuicTlsTicketStore<>(capacity, timeout);
    }

    QuicTlsSessionCache(int capacity, Duration timeout, LongSupplier currentTimeMillis) {
        this.tickets = new QuicTlsTicketStore<>(capacity, timeout, currentTimeMillis);
    }

    void cache(String peerHost, int peerPort, QuicTlsResumptionTicket ticket) {
        cache(peerHost, peerPort, peerHost, ticket);
    }

    void cache(String peerHost, int peerPort, String serverName, QuicTlsResumptionTicket ticket) {
        Objects.requireNonNull(ticket, "ticket");
        Optional<String> applicationProtocol = ticket.applicationProtocol();
        if (applicationProtocol.isEmpty() || applicationProtocol.orElseThrow().isEmpty()) {
            return;
        }
        SessionKey.create(peerHost, peerPort, serverName, applicationProtocol.orElseThrow())
                .ifPresent(key -> tickets.put(key, ticket));
    }

    Optional<QuicTlsResumptionTicket> cachedResumptionTicket(String peerHost,
                                                             int peerPort,
                                                             String[] applicationProtocols) {
        return cachedResumptionTicket(peerHost, peerPort, peerHost, applicationProtocols, _ -> true);
    }

    Optional<QuicTlsResumptionTicket> cachedResumptionTicket(String peerHost,
                                                             int peerPort,
                                                             String serverName,
                                                             String[] applicationProtocols) {
        return cachedResumptionTicket(peerHost, peerPort, serverName, applicationProtocols, _ -> true);
    }

    Optional<QuicTlsResumptionTicket> cachedResumptionTicket(String peerHost,
                                                             int peerPort,
                                                             String serverName,
                                                             String[] applicationProtocols,
                                                             Predicate<QuicTlsResumptionTicket> compatible) {
        Objects.requireNonNull(applicationProtocols, "applicationProtocols");
        Objects.requireNonNull(compatible, "compatible");
        for (String applicationProtocol : applicationProtocols) {
            Optional<SessionKey> key = SessionKey.create(peerHost, peerPort, serverName, applicationProtocol);
            if (key.isPresent()) {
                Optional<QuicTlsResumptionTicket> ticket = tickets.get(key.orElseThrow());
                if (ticket.isPresent() && compatible.test(ticket.orElseThrow())) {
                    return ticket;
                }
            }
        }
        return Optional.empty();
    }

    void clear() {
        tickets.clear();
    }

    int size() {
        return tickets.size();
    }

    @Override
    public void close() {
        tickets.close();
    }

    private record SessionKey(String peerHost, int peerPort, String serverName, String applicationProtocol) {
        private static Optional<SessionKey> create(String peerHost,
                                                   int peerPort,
                                                   String serverName,
                                                   String applicationProtocol) {
            if (peerHost == null || peerHost.isBlank() || peerPort < 0 || peerPort > 65_535
                    || serverName == null || serverName.isBlank()
                    || applicationProtocol == null || applicationProtocol.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new SessionKey(peerHost.toLowerCase(Locale.ROOT),
                                              peerPort,
                                              serverName.toLowerCase(Locale.ROOT),
                                              applicationProtocol));
        }
    }
}
