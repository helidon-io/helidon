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

package io.helidon.webclient.http3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.tls.Tls;
import io.helidon.common.tls.TlsMaterial;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3TlsCompatibilityTest {
    @Test
    void shouldRejectNonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new Http3TlsCompatibility(tls -> true, 0));
    }

    @Test
    void shouldCacheTlsIdentityAndGeneration() {
        AtomicInteger checks = new AtomicInteger();
        Http3TlsCompatibility compatibility = new Http3TlsCompatibility(tls -> {
            checks.incrementAndGet();
            return tls.generation() % 2 == 0;
        });
        Tls tls = Tls.builder().trustAll(true).build();

        assertThat(compatibility.compatible(tls), is(true));
        assertThat(compatibility.compatible(tls), is(true));
        assertThat(checks.get(), is(1));

        tls.reload(TlsMaterial.builder().trustAll(true).build());

        assertThat(compatibility.compatible(tls), is(false));
        assertThat(compatibility.compatible(tls), is(false));
        assertThat(checks.get(), is(2));
    }

    @Test
    void shouldDistinguishValueEqualTlsIdentities() {
        Tls first = Tls.builder().trustAll(true).build();
        Tls second = Tls.builder()
                .sslContext(first.sslContext())
                .sslParameters(first.sslParameters())
                .build();
        assertThat(second, is(first));
        assertThat(second, not(sameInstance(first)));
        AtomicInteger checks = new AtomicInteger();
        Http3TlsCompatibility compatibility = new Http3TlsCompatibility(tls -> {
            checks.incrementAndGet();
            return tls == first;
        });

        assertThat(compatibility.compatible(first), is(true));
        assertThat(compatibility.compatible(second), is(false));
        assertThat(compatibility.compatible(first), is(true));

        assertThat(checks.get(), is(2));
    }

    @Test
    void shouldBoundRetainedTlsContexts() {
        AtomicInteger checks = new AtomicInteger();
        Http3TlsCompatibility compatibility = new Http3TlsCompatibility(tls -> {
            checks.incrementAndGet();
            return true;
        }, 2);
        Tls first = Tls.builder().trustAll(true).build();
        Tls second = Tls.builder().trustAll(true).build();
        Tls third = Tls.builder().trustAll(true).build();

        assertThat(compatibility.compatible(first), is(true));
        assertThat(compatibility.compatible(second), is(true));
        assertThat(compatibility.compatible(third), is(true));

        assertThat(compatibility.compatible(second), is(true));
        assertThat(checks.get(), is(3));
        assertThat(compatibility.compatible(first), is(true));
        assertThat(checks.get(), is(4));
    }

    @Test
    void shouldServeConcurrentTlsOverridesFromTheBoundedCache() throws Exception {
        int contextCount = 8;
        AtomicInteger checks = new AtomicInteger();
        Http3TlsCompatibility compatibility = new Http3TlsCompatibility(tls -> {
            checks.incrementAndGet();
            return true;
        }, contextCount);
        Tls[] tlsContexts = new Tls[contextCount];
        for (int i = 0; i < tlsContexts.length; i++) {
            tlsContexts[i] = Tls.builder().trustAll(true).build();
            assertThat(compatibility.compatible(tlsContexts[i]), is(true));
        }

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(contextCount);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (Tls tls : tlsContexts) {
                results.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < 10_000; i++) {
                        if (!compatibility.compatible(tls)) {
                            return false;
                        }
                    }
                    return true;
                }));
            }
            start.countDown();
            for (Future<Boolean> result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS), is(true));
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(checks.get(), is(contextCount));
    }

    @Test
    void shouldRetainOneSlotForConcurrentColdMisses() throws Exception {
        int threadCount = 8;
        Phaser computing = new Phaser(threadCount);
        AtomicInteger checks = new AtomicInteger();
        Http3TlsCompatibility compatibility = new Http3TlsCompatibility(tls -> {
            checks.incrementAndGet();
            computing.arriveAndAwaitAdvance();
            return true;
        });
        Tls tls = Tls.builder().trustAll(true).build();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threadCount; i++) {
                results.add(executor.submit(() -> compatibility.compatible(tls)));
            }
            for (Future<Boolean> result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS), is(true));
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(checks.get(), is(threadCount));
        assertThat(compatibility.compatible(tls), is(true));
        assertThat(checks.get(), is(threadCount));
    }

    @Test
    void shouldDiscardACompatibilityResultRacedByReload() {
        Tls tls = Tls.builder().trustAll(true).build();
        AtomicBoolean reload = new AtomicBoolean(true);
        AtomicInteger checks = new AtomicInteger();
        Http3TlsCompatibility compatibility = new Http3TlsCompatibility(candidate -> {
            checks.incrementAndGet();
            if (reload.getAndSet(false)) {
                candidate.reload(TlsMaterial.builder().trustAll(true).build());
                return true;
            }
            return false;
        });

        assertThat(compatibility.compatible(tls), is(false));
        assertThat(checks.get(), is(2));
        assertThat(compatibility.compatible(tls), is(false));
        assertThat(checks.get(), is(2));

        compatibility.close();
        assertThat(compatibility.compatible(tls), is(false));
        assertThat(checks.get(), is(2));
    }

    @Test
    void shouldCloseWhileColdCompatibilityCheckIsInFlight() throws Exception {
        CountDownLatch computing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger checks = new AtomicInteger();
        Http3TlsCompatibility compatibility = new Http3TlsCompatibility(tls -> {
            checks.incrementAndGet();
            computing.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to finish TLS compatibility check");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting to finish TLS compatibility check", e);
            }
            return true;
        });
        Tls tls = Tls.builder().trustAll(true).build();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> result = executor.submit(() -> compatibility.compatible(tls));
            assertThat(computing.await(10, TimeUnit.SECONDS), is(true));

            compatibility.close();
            release.countDown();

            assertThat(result.get(10, TimeUnit.SECONDS), is(false));
            assertThat(compatibility.compatible(tls), is(false));
            assertThat(checks.get(), is(1));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
