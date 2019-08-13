/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.metrics;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * Class HelidonConcurrentGaugeTest.
 */
public class HelidonConcurrentGaugeTest {

    private static Metadata meta;
    private HelidonConcurrentGauge gauge;

    @BeforeAll
    static void initClass() {
        meta = new HelidonMetadata("aConcurrentGauge",
                "aConcurrentGauge",
                "aConcurrentGauge",
                MetricType.CONCURRENT_GAUGE,
                MetricUnits.NONE);
    }

    @BeforeEach
    void resetCounter() {
        gauge = HelidonConcurrentGauge.create("base", meta);
    }

    @Test
    void testConcurrentGauge() {
        assertThat(gauge.getCount(), is(0L));
        assertThat(gauge.getMax(), is(0L));
        assertThat(gauge.getMin(), is(0L));

        IntStream.range(0, 10).forEach(i -> gauge.inc());
        assertThat(gauge.getCount(), is(10L));
        assertThat(gauge.getMax(), is(10L));
        assertThat(gauge.getMin(), is(0L));

        IntStream.range(0, 20).forEach(i -> gauge.dec());
        assertThat(gauge.getCount(), is(-10L));
        assertThat(gauge.getMax(), is(10L));
        assertThat(gauge.getMin(), is(-10L));

        IntStream.range(0, 10).forEach(i -> gauge.inc());
        assertThat(gauge.getCount(), is(0L));
        assertThat(gauge.getMax(), is(10L));
        assertThat(gauge.getMin(), is(-10L));
    }

    @Test
    void testConcurrentGaugeInc() {
        CompletableFuture<?>[] futures = new CompletableFuture<?>[10];
        IntStream.range(0, 10).forEach(i -> {
            futures[i] = new CompletableFuture<>();
            ForkJoinPool.commonPool().submit(() -> {
                gauge.inc();
                futures[i].complete(null);
            });
        });
        CompletableFuture.allOf(futures).thenRun(() ->
                assertThat(gauge.getCount(), is(10L))
        );
    }

    @Test
    void testConcurrentGaugeDec() {
        CompletableFuture<?>[] futures = new CompletableFuture<?>[10];
        IntStream.range(0, 10).forEach(i -> {
            futures[i] = new CompletableFuture<>();
            ForkJoinPool.commonPool().submit(() -> {
                gauge.dec();
                futures[i].complete(null);
            });
        });
        CompletableFuture.allOf(futures).thenRun(() ->
                assertThat(gauge.getCount(), is(-10L))
        );
    }
}
