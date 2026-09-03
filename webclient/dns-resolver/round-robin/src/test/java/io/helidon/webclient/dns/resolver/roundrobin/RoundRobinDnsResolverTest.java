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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.helidon.webclient.api.DnsAddressLookup;
import io.helidon.webclient.dns.resolver.roundrobin.ControlledInetAddressResolverProvider.LookupBarrier;

import org.junit.jupiter.api.Test;

import static io.helidon.webclient.dns.resolver.roundrobin.ControlledInetAddressResolverProvider.COLD_HOSTNAME;
import static io.helidon.webclient.dns.resolver.roundrobin.ControlledInetAddressResolverProvider.HOSTNAME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

class RoundRobinDnsResolverTest {
    private static final int TEST_TIMEOUT_SECONDS = 10;
    private static final int TASK_COUNT = 256;

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
    void concurrentFirstUseMaintainsOneRoundRobinSequence() throws Exception {
        InetAddress[] expectedAddresses = expectedAddresses();
        RoundRobinDnsResolver resolver = RoundRobinDnsResolver.create();
        LookupBarrier barrier = ControlledInetAddressResolverProvider.installLookupBarrier();
        CountDownLatch allowSecondResolverCall = new CountDownLatch(1);
        CountDownLatch secondResolverCallStarted = new CountDownLatch(1);
        AtomicReference<Thread> secondResolverThread = new AtomicReference<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<InetAddress> first =
                    executor.submit(() -> resolver.resolveAddress(COLD_HOSTNAME, DnsAddressLookup.IPV4));
            Future<InetAddress> second = executor.submit(() -> {
                allowSecondResolverCall.await();
                secondResolverThread.set(Thread.currentThread());
                secondResolverCallStarted.countDown();
                return resolver.resolveAddress(COLD_HOSTNAME, DnsAddressLookup.IPV4);
            });
            try {
                assertThat("Timed out waiting for the first cold DNS lookup",
                           barrier.awaitLookup(), is(true));
                allowSecondResolverCall.countDown();
                assertThat("Timed out waiting for the second resolver call",
                           secondResolverCallStarted.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS), is(true));
                // InetAddress serializes provider calls for the same hostname. Waiting for the second thread to park
                // behind the first lookup confirms that both calls observed the Helidon cache miss before publication.
                assertThat("The second resolver call did not wait behind the in-progress same-host DNS lookup",
                           awaitWaiting(secondResolverThread.get()), is(true));
                barrier.release();
                List<InetAddress> selected = List.of(first.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                                                     second.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));

                assertThat(selected, containsInAnyOrder(expectedAddresses[0], expectedAddresses[1]));
                assertThat(resolver.resolveAddress(COLD_HOSTNAME, DnsAddressLookup.IPV4), is(expectedAddresses[2]));
            } finally {
                allowSecondResolverCall.countDown();
                barrier.release();
                first.cancel(true);
                second.cancel(true);
            }
        } finally {
            ControlledInetAddressResolverProvider.clearLookupBarrier(barrier);
        }
    }

    private static boolean awaitWaiting(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TEST_TIMEOUT_SECONDS);
        do {
            Thread.State state = thread.getState();
            if (state == Thread.State.WAITING || state == Thread.State.BLOCKED) {
                return true;
            }
            if (state == Thread.State.TERMINATED) {
                return false;
            }
            Thread.onSpinWait();
        } while (System.nanoTime() < deadline);
        return false;
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
}
