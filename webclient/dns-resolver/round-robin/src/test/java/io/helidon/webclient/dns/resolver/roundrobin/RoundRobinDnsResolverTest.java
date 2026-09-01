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

package io.helidon.webclient.dns.resolver.roundrobin;

import java.net.InetAddress;
import java.security.Permission;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.helidon.webclient.api.DnsAddressLookup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

class RoundRobinDnsResolverTest {
    private static final int TEST_TIMEOUT_SECONDS = 10;
    private static final int TASK_COUNT = 256;
    private static final String HOSTNAME = "round-robin.test";

    @Test
    void cyclesThroughResolvedAddresses() throws Exception {
        InetAddress[] expectedAddresses = expectedAddresses();
        RoundRobinDnsResolver resolver = RoundRobinDnsResolver.create();
        List<InetAddress> actual = new ArrayList<>(expectedAddresses.length * 2);

        for (int i = 0; i < expectedAddresses.length * 2; i++) {
            actual.add(resolver.resolveAddress(HOSTNAME, DnsAddressLookup.IPV4));
        }

        List<InetAddress> expected = new ArrayList<>(expectedAddresses.length * 2);
        expected.addAll(List.of(expectedAddresses));
        expected.addAll(List.of(expectedAddresses));
        assertThat(actual, is(expected));
    }

    @Test
    void virtualThreadWarmHitsShareRoundRobinCounter() throws Exception {
        InetAddress[] expectedAddresses = expectedAddresses();
        RoundRobinDnsResolver resolver = RoundRobinDnsResolver.create();
        assertThat(resolver.resolveAddress(HOSTNAME, DnsAddressLookup.IPV4), is(expectedAddresses[0]));

        List<InetAddress> selected = invokeOnVirtualThreads(
                TASK_COUNT,
                () -> resolver.resolveAddress(HOSTNAME, DnsAddressLookup.IPV4));

        assertEvenDistribution(selected, expectedAddresses);
        assertThat(resolver.resolveAddress(HOSTNAME, DnsAddressLookup.IPV4), is(expectedAddresses[1]));
    }

    @Test
    @SuppressWarnings("removal")
    @EnabledForJreRange(max = JRE.JAVA_23,
                        disabledReason = "Dynamic SecurityManager installation is unavailable starting with JDK 24")
    void concurrentFirstUseMaintainsOneRoundRobinSequence() throws Exception {
        InetAddress[] expectedAddresses = expectedAddresses();
        RoundRobinDnsResolver resolver = RoundRobinDnsResolver.create();
        CountDownLatch lookupsEntered = new CountDownLatch(2);
        CountDownLatch releaseLookups = new CountDownLatch(1);
        // Use the SecurityManager only as a test barrier. InetAddress calls checkConnect after the resolver
        // observes a cache miss and before DNS lookup. Blocking both calls there exercises concurrent
        // publication deterministically without adding a production test hook.
        SecurityManager previousSecurityManager = System.getSecurityManager();
        System.setSecurityManager(new LookupBarrierSecurityManager(lookupsEntered, releaseLookups));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<InetAddress> first = executor.submit(() -> resolver.resolveAddress(HOSTNAME, DnsAddressLookup.IPV4));
            Future<InetAddress> second = executor.submit(() -> resolver.resolveAddress(HOSTNAME, DnsAddressLookup.IPV4));
            try {
                assertThat("Timed out waiting for concurrent cache misses",
                           lookupsEntered.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS), is(true));
                releaseLookups.countDown();
                List<InetAddress> selected = List.of(first.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                                                     second.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));

                assertThat(selected, containsInAnyOrder(expectedAddresses[0], expectedAddresses[1]));
                assertThat(resolver.resolveAddress(HOSTNAME, DnsAddressLookup.IPV4), is(expectedAddresses[2]));
            } finally {
                releaseLookups.countDown();
                first.cancel(true);
                second.cancel(true);
            }
        } finally {
            System.setSecurityManager(previousSecurityManager);
        }
    }

    private static InetAddress[] expectedAddresses() throws Exception {
        InetAddress[] addresses = DnsAddressLookup.IPV4.filter(InetAddress.getAllByName(HOSTNAME));
        assertThat(addresses.length, is(4));
        return addresses;
    }

    private static void assertEvenDistribution(List<InetAddress> selected, InetAddress[] expectedAddresses) {
        Map<InetAddress, Integer> counts = new HashMap<>();
        selected.forEach(address -> counts.merge(address, 1, Integer::sum));
        Map<InetAddress, Integer> expectedCounts = new HashMap<>();
        for (InetAddress address : expectedAddresses) {
            expectedCounts.put(address, TASK_COUNT / expectedAddresses.length);
        }
        assertThat(counts, is(expectedCounts));
    }

    private static <T> List<T> invokeOnVirtualThreads(int taskCount, Supplier<T> operation) throws Exception {
        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>(taskCount);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                for (int i = 0; i < taskCount; i++) {
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        assertThat("Expected operation to run on a virtual thread",
                                   Thread.currentThread().isVirtual(), is(true));
                        start.await();
                        return operation.get();
                    }));
                }
                assertThat("Timed out waiting for virtual threads",
                           ready.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS), is(true));
                start.countDown();
                List<T> results = new ArrayList<>(taskCount);
                for (Future<T> future : futures) {
                    results.add(future.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                }
                return results;
            } finally {
                start.countDown();
                futures.forEach(future -> future.cancel(true));
            }
        }
    }

    @SuppressWarnings("removal")
    private static final class LookupBarrierSecurityManager extends SecurityManager {
        private final CountDownLatch lookupsEntered;
        private final CountDownLatch releaseLookups;

        private LookupBarrierSecurityManager(CountDownLatch lookupsEntered, CountDownLatch releaseLookups) {
            this.lookupsEntered = lookupsEntered;
            this.releaseLookups = releaseLookups;
        }

        @Override
        public void checkPermission(Permission ignored) {
        }

        @Override
        public void checkConnect(String host, int port) {
            if (HOSTNAME.equals(host) && port == -1) {
                lookupsEntered.countDown();
                try {
                    assertThat("Timed out waiting to release concurrent lookups",
                               releaseLookups.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS), is(true));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Interrupted while waiting to resolve addresses", e);
                }
            }
        }
    }
}
