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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class QuicTlsServerSessionCacheTest {
    @Test
    void boundsTicketsAndEvictsLeastRecentlyUsed() {
        AtomicLong clock = new AtomicLong(1_000);
        QuicTlsServerSessionCache cache = new QuicTlsServerSessionCache(2, Duration.ofMinutes(1), clock::get);
        assertThat(cache.cache(resumptionTicket(1, clock.get(), 60)), is(true));
        assertThat(cache.cache(resumptionTicket(2, clock.get(), 60)), is(true));
        cache.cached(new byte[] {1}).orElseThrow();

        assertThat(cache.cache(resumptionTicket(3, clock.get(), 60)), is(true));

        assertThat(cache.size(), is(2));
        assertThat(cache.cached(new byte[] {2}), is(Optional.empty()));
        assertThat(cache.cached(new byte[] {1}).isPresent(), is(true));
        assertThat(cache.cached(new byte[] {3}).isPresent(), is(true));
    }

    @Test
    void appliesConfiguredTimeoutAndTerminalClose() {
        AtomicLong clock = new AtomicLong(1_000);
        QuicTlsServerSessionCache cache = new QuicTlsServerSessionCache(2, Duration.ofSeconds(1), clock::get);
        assertThat(cache.cache(resumptionTicket(1, clock.get(), 60)), is(true));
        clock.addAndGet(1_000);
        assertThat(cache.cached(new byte[] {1}), is(Optional.empty()));

        assertThat(cache.cache(resumptionTicket(2, clock.get(), 60)), is(true));
        cache.close();

        assertThat(cache.size(), is(0));
        assertThat(cache.cache(resumptionTicket(3, clock.get(), 60)), is(false));
        assertThat(cache.cached(new byte[] {3}), is(Optional.empty()));
    }

    @Test
    void zeroCapacityDoesNotIssueTickets() {
        AtomicLong clock = new AtomicLong(1_000);
        QuicTlsServerSessionCache cache = new QuicTlsServerSessionCache(0, Duration.ZERO, clock::get);

        assertThat(cache.cache(resumptionTicket(1, clock.get(), 60)), is(false));
        assertThat(cache.size(), is(0));
    }

    private static QuicTlsResumptionTicket resumptionTicket(int id,
                                                             long issuedAtMillis,
                                                             long ticketLifetimeSeconds) {
        return new QuicTlsResumptionTicket(QuicVersion.QUIC_V1,
                                           QuicTls13CipherSuite.TLS_AES_128_GCM_SHA256,
                                           ticketLifetimeSeconds,
                                           7,
                                           new byte[] {1},
                                           new byte[] {(byte) id},
                                           new byte[] {3},
                                           "h3",
                                           "example.com",
                                           new byte[] {4},
                                           issuedAtMillis);
    }
}
