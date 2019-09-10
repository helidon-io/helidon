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
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * Class HelidonConcurrentGaugeTest.
 */
public class HelidonConcurrentGaugeTest {
    private static final long SECONDS_THRESHOLD = 50;

    private static Metadata meta;

    @BeforeAll
    static void initClass() {
        meta = new HelidonMetadata("aConcurrentGauge",
                "aConcurrentGauge",
                "aConcurrentGauge",
                MetricType.CONCURRENT_GAUGE,
                MetricUnits.NONE);
    }

    @Test
    void testInitialState() {
        HelidonConcurrentGauge gauge = HelidonConcurrentGauge.create("base", meta);
        assertThat(gauge.getCount(), is(0L));
        assertThat(gauge.getMax(), is(0L));
        assertThat(gauge.getMin(), is(0L));
    }

    @Test
    void testMaxAndMinConcurrent() throws InterruptedException {
        ensureSecondsInMinute();
        HelidonConcurrentGauge gauge = HelidonConcurrentGauge.create("base", meta);
        System.out.println("Calling inc() and dec() a few times concurrently ...");

        // Increment gauge 5 times
        CompletableFuture<?>[] futuresInc = new CompletableFuture<?>[5];
        IntStream.range(0, 5).forEach(i -> {
            futuresInc[i] = new CompletableFuture<>();
            ForkJoinPool.commonPool().submit(() -> {
                gauge.inc();
                futuresInc[i].complete(null);
            });
        });
        CompletableFuture.allOf(futuresInc).thenRun(() ->
                assertThat(gauge.getCount(), is(5L))
        );

        // Decrement gauge 10 times
        CompletableFuture<?>[] futuresDec = new CompletableFuture<?>[10];
        IntStream.range(0, 10).forEach(i -> {
            futuresDec[i] = new CompletableFuture<>();
            ForkJoinPool.commonPool().submit(() -> {
                gauge.dec();
                futuresDec[i].complete(null);
            });
        });
        CompletableFuture.allOf(futuresDec).thenRun(() ->
                assertThat(gauge.getCount(), is(-5L))
        );

        waitUntilNextMinute();
        System.out.println("Verifying max and min from last minute ...");
        assertThat(gauge.getMax(), is(5L));
        assertThat(gauge.getMin(), is(-5L));
    }

    private static void ensureSecondsInMinute() throws InterruptedException {
        long currentSeconds = currentTimeSeconds();
        System.out.println("Seconds in minute are " + currentSeconds);
        if (currentSeconds > SECONDS_THRESHOLD) {
            waitUntilNextMinute();
        }
    }

    private static void waitUntilNextMinute() throws InterruptedException {
        boolean displayMessage = true;
        long currentMinute = currentTimeMinute();
        while (currentMinute == currentTimeMinute()) {
            if (displayMessage) {
                System.out.println("Waiting for next minute to start ...");
                displayMessage = false;
            }
            Thread.sleep(10 * 1000);
        }
    }

    private static long currentTimeMinute() {
        return System.currentTimeMillis() / 1000 / 60;
    }

    private static long currentTimeSeconds() {
        return System.currentTimeMillis() / 1000 % 60;
    }
}
