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
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

import io.helidon.common.tls.TlsConfig;

final class QuicTlsServerSessionCache implements AutoCloseable {
    private final QuicTlsTicketStore<TicketKey> tickets;

    QuicTlsServerSessionCache() {
        this(TlsConfig.DEFAULT_SESSION_CACHE_SIZE, Duration.parse(TlsConfig.DEFAULT_SESSION_TIMEOUT));
    }

    QuicTlsServerSessionCache(int capacity, Duration timeout) {
        this.tickets = new QuicTlsTicketStore<>(capacity, timeout);
    }

    QuicTlsServerSessionCache(int capacity, Duration timeout, LongSupplier currentTimeMillis) {
        this.tickets = new QuicTlsTicketStore<>(capacity, timeout, currentTimeMillis);
    }

    boolean cache(QuicTlsResumptionTicket ticket) {
        Objects.requireNonNull(ticket, "ticket");
        if (!tickets.enabled()) {
            return false;
        }
        return tickets.put(new TicketKey(ticket.ticket()), ticket);
    }

    Optional<QuicTlsResumptionTicket> cached(byte[] ticketIdentity) {
        return tickets.get(new TicketKey(Objects.requireNonNull(ticketIdentity, "ticketIdentity")));
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

    private static final class TicketKey {
        private final byte[] value;
        private final int hashCode;

        private TicketKey(byte[] value) {
            this.value = Objects.requireNonNull(value, "value").clone();
            this.hashCode = Arrays.hashCode(this.value);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof TicketKey other)) {
                return false;
            }
            return Arrays.equals(value, other.value);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
