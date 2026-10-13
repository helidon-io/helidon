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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3ResponseBodyJmhBenchmarkTest {
    @Test
    void actualMaterializedAndStreamedGetVerifyEveryPayloadAndPostExpectSetup() throws Exception {
        for (int size : List.of(0, 1024, 131072)) {
            for (var mode : Http3ResponseBodyJmhBenchmark.RuntimeExecutorMode.values()) {
                var benchmark = benchmark(size, mode);
                try {
                    benchmark.setUp();
                    assertThat(benchmark.materializedGet(), is(checksum(size)));
                    assertThat(benchmark.streamedGet(), is(checksum(size)));
                } finally {
                    benchmark.tearDown();
                }
            }
        }
    }

    @Test
    void sixtyFourCallersConsumeIndependentResponsesOnOneScopedClient() throws Exception {
        for (int size : List.of(0, 1024, 131072)) {
            for (var mode : Http3ResponseBodyJmhBenchmark.RuntimeExecutorMode.values()) {
                var benchmark = benchmark(size, mode);
                var callers = Executors.newVirtualThreadPerTaskExecutor();
                CountDownLatch ready = new CountDownLatch(64);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Integer>> results = new ArrayList<>();
                try {
                    benchmark.setUp();
                    for (int index = 0; index < 64; index++) {
                        results.add(callers.submit(() -> {
                            ready.countDown();
                            if (!start.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Concurrent response fixture start timed out");
                            }
                            return benchmark.materializedGet() + benchmark.streamedGet();
                        }));
                    }
                    assertThat("All callers ready", ready.await(5, TimeUnit.SECONDS), is(true));
                    start.countDown();
                    for (Future<Integer> result : results) {
                        assertThat(result.get(45, TimeUnit.SECONDS), is(2 * checksum(size)));
                    }
                } finally {
                    start.countDown();
                    results.forEach(result -> result.cancel(true));
                    callers.shutdownNow();
                    try {
                        benchmark.tearDown();
                    } finally {
                        assertThat("All callers terminated", callers.awaitTermination(45, TimeUnit.SECONDS), is(true));
                    }
                }
            }
        }
    }

    @Test
    void unsupportedPayloadFailsBeforeStartingTheEnvironment() throws Exception {
        var benchmark = benchmark(17, Http3ResponseBodyJmhBenchmark.RuntimeExecutorMode.FJP2);
        try {
            assertThrows(IllegalArgumentException.class, benchmark::setUp);
        } finally {
            benchmark.tearDown();
        }
    }

    private static Http3ResponseBodyJmhBenchmark benchmark(
            int size, Http3ResponseBodyJmhBenchmark.RuntimeExecutorMode mode) {
        var benchmark = new Http3ResponseBodyJmhBenchmark();
        benchmark.payloadSize = size;
        benchmark.runtimeExecutorMode = mode;
        benchmark.sourceIdentity = "functional-smoke";
        return benchmark;
    }

    private static int checksum(int size) {
        int checksum = 0;
        for (int index = 0; index < size; index++) {
            checksum += (index * 31 + 7) & 0xFF;
        }
        return checksum;
    }
}
