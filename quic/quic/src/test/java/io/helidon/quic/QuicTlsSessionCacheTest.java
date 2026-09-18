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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

class QuicTlsSessionCacheTest {
    @Test
    void isolatesPeerIdentityPortAndApplicationProtocol() {
        AtomicLong clock = new AtomicLong(1_000);
        QuicTlsSessionCache cache = new QuicTlsSessionCache(8, Duration.ofMinutes(1), clock::get);
        QuicTlsResumptionTicket ticket = resumptionTicket(1, "h3", clock.get(), 60);

        cache.cache("EXAMPLE.com", 443, "www.example.com", ticket);

        assertThat(cache.cachedResumptionTicket("example.COM",
                                                443,
                                                "WWW.example.com",
                                                new String[] {"h3"})
                           .orElseThrow()
                           .ticket(),
                   equalTo(ticket.ticket()));
        assertThat(cache.cachedResumptionTicket("example.com", 443, new String[] {"h3"}), is(Optional.empty()));
        assertThat(cache.cachedResumptionTicket("example.com", 8443, new String[] {"h3"}), is(Optional.empty()));
        assertThat(cache.cachedResumptionTicket("other.example", 443, new String[] {"h3"}), is(Optional.empty()));
        assertThat(cache.cachedResumptionTicket("example.com", 443, new String[] {"hq"}), is(Optional.empty()));
    }

    @Test
    void followsOfferedApplicationProtocolOrder() {
        AtomicLong clock = new AtomicLong(1_000);
        QuicTlsSessionCache cache = new QuicTlsSessionCache(8, Duration.ofMinutes(1), clock::get);
        cache.cache("example.com", 443, resumptionTicket(1, "h3", clock.get(), 60));
        cache.cache("example.com", 443, resumptionTicket(2, "h3-29", clock.get(), 60));

        assertThat(cached(cache, "example.com", 443, "h3-29", "h3").ticket(), equalTo(new byte[] {2}));
    }

    @Test
    void preservesWhitespaceOnlyApplicationProtocol() {
        AtomicLong clock = new AtomicLong(1_000);
        QuicTlsSessionCache cache = new QuicTlsSessionCache(8, Duration.ofMinutes(1), clock::get);
        QuicTlsResumptionTicket ticket = resumptionTicket(1, " ", clock.get(), 60);

        cache.cache("example.com", 443, ticket);

        assertThat(cached(cache, "example.com", 443, " ").ticket(), equalTo(ticket.ticket()));
    }

    @Test
    void skipsIncompatibleTicketForEarlierApplicationProtocol() {
        AtomicLong clock = new AtomicLong(1_000);
        QuicTlsSessionCache cache = new QuicTlsSessionCache(8, Duration.ofMinutes(1), clock::get);
        cache.cache("example.com", 443, resumptionTicket(1, "h3-29", clock.get(), 60));
        cache.cache("example.com", 443, resumptionTicket(2, "h3", clock.get(), 60));

        QuicTlsResumptionTicket ticket = cache.cachedResumptionTicket("example.com",
                                                                      443,
                                                                      "example.com",
                                                                      new String[] {"h3-29", "h3"},
                                                                      candidate -> candidate.ticket()[0] == 2)
                .orElseThrow();

        assertThat(ticket.ticket(), equalTo(new byte[] {2}));
    }

    @Test
    void evictsLeastRecentlyUsedTicketAtCapacity() {
        AtomicLong clock = new AtomicLong(1_000);
        QuicTlsSessionCache cache = new QuicTlsSessionCache(2, Duration.ofMinutes(1), clock::get);
        cache.cache("one.example", 443, resumptionTicket(1, "h3", clock.get(), 60));
        cache.cache("two.example", 443, resumptionTicket(2, "h3", clock.get(), 60));
        cached(cache, "one.example", 443, "h3");

        cache.cache("three.example", 443, resumptionTicket(3, "h3", clock.get(), 60));

        assertThat(cache.size(), is(2));
        assertThat(cache.cachedResumptionTicket("two.example", 443, new String[] {"h3"}), is(Optional.empty()));
        assertThat(cached(cache, "one.example", 443, "h3").ticket(), equalTo(new byte[] {1}));
        assertThat(cached(cache, "three.example", 443, "h3").ticket(), equalTo(new byte[] {3}));
    }

    @Test
    void appliesEarlierConfiguredOrTicketExpiry() {
        AtomicLong clock = new AtomicLong(1_000);
        QuicTlsSessionCache configuredTimeout = new QuicTlsSessionCache(2, Duration.ofSeconds(2), clock::get);
        configuredTimeout.cache("example.com", 443, resumptionTicket(1, "h3", clock.get(), 60));
        clock.addAndGet(1_999);
        assertThat(cached(configuredTimeout, "example.com", 443, "h3").ticket(), equalTo(new byte[] {1}));
        clock.incrementAndGet();
        assertThat(configuredTimeout.cachedResumptionTicket("example.com", 443, new String[] {"h3"}),
                   is(Optional.empty()));

        QuicTlsSessionCache ticketTimeout = new QuicTlsSessionCache(2, Duration.ZERO, clock::get);
        ticketTimeout.cache("example.com", 443, resumptionTicket(2, "h3", clock.get(), 1));
        clock.addAndGet(1_000);
        assertThat(ticketTimeout.cachedResumptionTicket("example.com", 443, new String[] {"h3"}),
                   is(Optional.empty()));
    }

    @Test
    void clearIsReusableAndCloseIsTerminal() {
        AtomicLong clock = new AtomicLong(1_000);
        QuicTlsSessionCache cache = new QuicTlsSessionCache(2, Duration.ofMinutes(1), clock::get);
        cache.cache("example.com", 443, resumptionTicket(1, "h3", clock.get(), 60));
        cache.clear();
        assertThat(cache.size(), is(0));

        cache.cache("example.com", 443, resumptionTicket(2, "h3", clock.get(), 60));
        assertThat(cache.size(), is(1));
        cache.close();
        cache.cache("example.com", 443, resumptionTicket(3, "h3", clock.get(), 60));

        assertThat(cache.size(), is(0));
        assertThat(cache.cachedResumptionTicket("example.com", 443, new String[] {"h3"}), is(Optional.empty()));
    }

    @Test
    void zeroCapacityIsUnlimited() {
        AtomicLong clock = new AtomicLong(1_000);
        QuicTlsSessionCache cache = new QuicTlsSessionCache(0, Duration.ZERO, clock::get);

        for (int id = 1; id <= 4; id++) {
            cache.cache(id + ".example.com", 443, resumptionTicket(id, "h3", clock.get(), 60));
        }

        assertThat(cache.size(), is(4));
        for (int id = 1; id <= 4; id++) {
            assertThat(cached(cache, id + ".example.com", 443, "h3").ticket(), equalTo(new byte[] {(byte) id}));
        }
    }

    @Test
    void unlimitedCapacityAndZeroTimeoutStillHonorTicketExpiry() {
        AtomicLong clock = new AtomicLong(1_000);
        QuicTlsSessionCache cache = new QuicTlsSessionCache(0, Duration.ZERO, clock::get);
        cache.cache("example.com", 443, resumptionTicket(1, "h3", clock.get(), 60));

        clock.addAndGet(59_999);
        assertThat(cached(cache, "example.com", 443, "h3").ticket(), equalTo(new byte[] {1}));
        clock.incrementAndGet();

        assertThat(cache.cachedResumptionTicket("example.com", 443, new String[] {"h3"}), is(Optional.empty()));
        assertThat(cache.size(), is(0));
    }

    @Test
    void unlimitedCapacityHonorsConfiguredTimeout() {
        AtomicLong clock = new AtomicLong(1_000);
        QuicTlsSessionCache cache = new QuicTlsSessionCache(0, Duration.ofSeconds(2), clock::get);
        cache.cache("example.com", 443, resumptionTicket(1, "h3", clock.get(), 60));

        clock.addAndGet(1_999);
        assertThat(cached(cache, "example.com", 443, "h3").ticket(), equalTo(new byte[] {1}));
        clock.incrementAndGet();

        assertThat(cache.cachedResumptionTicket("example.com", 443, new String[] {"h3"}), is(Optional.empty()));
        assertThat(cache.size(), is(0));
    }

    @Test
    void unlimitedCacheClearIsReusableAndCloseIsTerminal() {
        AtomicLong clock = new AtomicLong(1_000);
        QuicTlsSessionCache cache = new QuicTlsSessionCache(0, Duration.ZERO, clock::get);
        cache.cache("example.com", 443, resumptionTicket(1, "h3", clock.get(), 60));
        cache.clear();
        assertThat(cache.size(), is(0));
        assertThat(cache.cachedResumptionTicket("example.com", 443, new String[] {"h3"}), is(Optional.empty()));

        cache.cache("example.com", 443, resumptionTicket(2, "h3", clock.get(), 60));
        assertThat(cached(cache, "example.com", 443, "h3").ticket(), equalTo(new byte[] {2}));
        cache.close();
        cache.clear();
        cache.cache("example.com", 443, resumptionTicket(3, "h3", clock.get(), 60));

        assertThat(cache.size(), is(0));
        assertThat(cache.cachedResumptionTicket("example.com", 443, new String[] {"h3"}), is(Optional.empty()));
    }

    @Test
    void disabledCacheRemainsDisabledAfterClearAndClose() {
        try (QuicTlsSessionCache cache = QuicTlsSessionCache.disabled()) {
            QuicTlsResumptionTicket ticket = resumptionTicket(1, "h3", System.currentTimeMillis(), 60);
            cache.cache("example.com", 443, ticket);
            assertThat(cache.size(), is(0));
            assertThat(cache.cachedResumptionTicket("example.com", 443, new String[] {"h3"}), is(Optional.empty()));

            cache.clear();
            cache.cache("example.com", 443, ticket);
            assertThat(cache.size(), is(0));

            cache.close();
            cache.clear();
            cache.cache("example.com", 443, ticket);
            assertThat(cache.size(), is(0));
            assertThat(cache.cachedResumptionTicket("example.com", 443, new String[] {"h3"}), is(Optional.empty()));
        }
    }

    @Test
    void concurrentAccessNeverExceedsCapacity() throws Exception {
        int capacity = 32;
        int taskCount = 8;
        AtomicLong clock = new AtomicLong(1_000);
        QuicTlsSessionCache cache = new QuicTlsSessionCache(capacity, Duration.ofMinutes(1), clock::get);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> tasks = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(taskCount)) {
            for (int task = 0; task < taskCount; task++) {
                int taskId = task;
                tasks.add(executor.submit(() -> {
                    start.await();
                    for (int ticket = 0; ticket < 100; ticket++) {
                        String host = taskId + "-" + ticket + ".example";
                        cache.cache(host, 443, resumptionTicket(ticket, "h3", clock.get(), 60));
                        cache.cachedResumptionTicket(host, 443, new String[] {"h3"});
                    }
                    return null;
                }));
            }
            start.countDown();
            for (var task : tasks) {
                task.get(5, TimeUnit.SECONDS);
            }
        }

        assertThat(cache.size() <= capacity, is(true));
    }

    private static QuicTlsResumptionTicket cached(QuicTlsSessionCache cache,
                                                   String peerHost,
                                                   int peerPort,
                                                   String... applicationProtocols) {
        return cache.cachedResumptionTicket(peerHost, peerPort, applicationProtocols).orElseThrow();
    }

    private static QuicTlsResumptionTicket resumptionTicket(int id,
                                                             String applicationProtocol,
                                                             long issuedAtMillis,
                                                             long ticketLifetimeSeconds) {
        return new QuicTlsResumptionTicket(QuicVersion.QUIC_V1,
                                           QuicTls13CipherSuite.TLS_AES_128_GCM_SHA256,
                                           ticketLifetimeSeconds,
                                           7,
                                           new byte[] {1},
                                           new byte[] {(byte) id},
                                           new byte[] {3},
                                           applicationProtocol,
                                           new byte[] {4},
                                           issuedAtMillis);
    }
}
