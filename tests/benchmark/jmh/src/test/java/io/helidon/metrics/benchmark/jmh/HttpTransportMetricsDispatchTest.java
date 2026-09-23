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

package io.helidon.metrics.benchmark.jmh;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openjdk.jmh.infra.ThreadParams;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpTransportMetricsDispatchTest {
    @ParameterizedTest
    @CsvSource({
            "1, 32, false", "1, 128, false", "4, 32, false", "4, 128, false",
            "1, 32, true", "1, 128, true", "4, 32, true", "4, 128, true"
    })
    void benchmarkPathsExecuteEveryColdAction(int producers, int registrations, boolean completeWave) throws Exception {
        var benchmark = new HttpTransportMetricsDispatchJmhBenchmark();
        var state = new HttpTransportMetricsDispatchJmhBenchmark.RegistrationState();
        state.registrationsPerProducer = registrations;
        // Reuse the JMH state over multiple iterations to verify that every iteration stays cold.
        for (int iteration = 0; iteration < 3; iteration++) {
            state.prepare(producers);
            try {
                try (var executor = Executors.newFixedThreadPool(producers)) {
                    var ready = new CountDownLatch(producers);
                    var start = new CountDownLatch(1);
                    var results = new ArrayList<Future<Integer>>();
                    try {
                        for (int producer = 0; producer < producers; producer++) {
                            var thread = new ThreadParams(producer, producers, producer, producers, 0, 1, 0, 1, 0, 1);
                            results.add(executor.submit(() -> {
                                ready.countDown();
                                assertThat("Producers were not released", start.await(10, TimeUnit.SECONDS), is(true));
                                if (completeWave) {
                                    return benchmark.completeWave(state, thread);
                                }
                                benchmark.producerAdmission(state, thread);
                                return 0;
                            }));
                        }
                        assertThat("Producers were not ready", ready.await(10, TimeUnit.SECONDS), is(true));
                        start.countDown();
                        for (Future<Integer> result : results) {
                            assertThat("Unexpected completed action count", result.get(15, TimeUnit.SECONDS),
                                       is(completeWave ? producers * registrations : 0));
                        }
                    } finally {
                        start.countDown();
                    }
                }
            } finally {
                // Also verifies all dispatched actions for producerAdmission, outside the measured method.
                state.tearDown();
            }
        }
    }

    @Test
    void blockedProviderRetainsExactlyOneAdmissionBudget() throws Exception {
        try (var probe = new HttpTransportMetricsDispatchProbe(5, 256, true);
             var executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> probe.submit(0)).get(10, TimeUnit.SECONDS);
            probe.awaitProviderEntered();
            for (int producer = 1; producer < 5; producer++) {
                int index = producer;
                executor.submit(() -> probe.submit(index)).get(10, TimeUnit.SECONDS);
            }
            assertThat("All callbacks must return while the provider is blocked", probe.attemptedActions(), is(1280));
            probe.releaseProvider();
            assertThat("One busy wave must execute exactly its admission budget", probe.awaitDrained(), is(1024));
            assertThat(probe.executedActions(), is(1024));
        }
    }

    @Test
    void rejectsReusingResolvedRegistrations() throws Exception {
        try (var probe = new HttpTransportMetricsDispatchProbe(1, 32, false)) {
            probe.submit(0);
            assertThat(probe.awaitDrained(), is(32));
            assertThrows(IllegalStateException.class, () -> probe.submit(0));
        }
    }

    @Test
    void rejectsOversizedTimedWave() {
        var state = new HttpTransportMetricsDispatchJmhBenchmark.RegistrationState();
        state.registrationsPerProducer = 256;
        assertThrows(IllegalArgumentException.class, () -> state.prepare(5));
    }
}
