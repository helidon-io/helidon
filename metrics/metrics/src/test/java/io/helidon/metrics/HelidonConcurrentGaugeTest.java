/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

import java.util.Calendar;
import java.util.Date;
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
 * <p>
 * The test collects timestamps from important moments in the code and, in case of an
 * assertion violation, formats all the collected timestamps into the assertion error
 * message so that info is available easily in the test output.
 */
public class HelidonConcurrentGaugeTest {
    private static final long MIN_REQUIRED_SECONDS = Integer.getInteger("helidon.concurrentGauge.minRequiredSeconds", 10);
    private static final long SECONDS_THRESHOLD = 60 - MIN_REQUIRED_SECONDS;

    /*
     * For debugging only, to speed up testing of the test. Setting this to
     * false avoids the logic that normally waits for a new, "clean" minute to
     * begin the test run and then waiting for the next minute to start before
     * retrieving the min and max to let the gauge stabilize its view back of
     * the previous minute. Setting shouldWait to false will almost always cause
     * the final test of max and min to fail because the gauge will not have had
     * time to gather the info about the prev. minute.
     */
    private static final boolean SHOULD_WAIT = Boolean.valueOf(System.getProperty("helidon.concurrentGauge.shouldWait", "true"));

    private static Metadata meta;

    private Date preStart;
    private Date start;
    private Date afterIncrementsComplete;
    private Date afterDecrementsComplete;
    private Date afterQuiescing;

    @BeforeAll
    static void initClass() {
        meta = Metadata.builder()
                .withName("aConcurrentGauge")
                .withDisplayName("aConcurrentGauge")
                .withDescription("aConcurrentGauge")
                .withType(MetricType.CONCURRENT_GAUGE)
                .withUnit(MetricUnits.NONE)
                .build();
        System.out.println("Minimum required seconds within minute is " + MIN_REQUIRED_SECONDS
                + ", so SECONDS_THRESHOLD is " + SECONDS_THRESHOLD);
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
        preStart = new Date();
        ensureSecondsInMinute();

        start = new Date();
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

        CompletableFuture.allOf(futuresInc).join();
        afterIncrementsComplete = new Date();
        assertThat(formatErrorOutput("after increments"), gauge.getCount(), is(5L));

        // Decrement gauge 10 times
        CompletableFuture<?>[] futuresDec = new CompletableFuture<?>[10];
        IntStream.range(0, 10).forEach(i -> {
            futuresDec[i] = new CompletableFuture<>();
            ForkJoinPool.commonPool().submit(() -> {
                gauge.dec();
                futuresDec[i].complete(null);
            });
        });
        CompletableFuture.allOf(futuresDec).join();
        afterDecrementsComplete = new Date();
        assertThat(formatErrorOutput("after decrements"), gauge.getCount(), is(-5L));
        System.out.println("CompletableFutures all done at seconds within minute = " + currentTimeSeconds());
        waitUntilNextMinute();

        afterQuiescing = new Date();

        System.out.println("Verifying max and min from last minute ...");
        assertThat(formatErrorOutput("checking max"), gauge.getMax(), is(5L));
        assertThat(formatErrorOutput("checking min"), gauge.getMin(), is(-5L));
    }

    private static void ensureSecondsInMinute() throws InterruptedException {
        long currentSeconds = currentTimeSeconds();
        System.out.println("Seconds in minute are " + currentSeconds);
        if (currentSeconds > SECONDS_THRESHOLD) {
            System.out.println("which is beyond the threshold " + SECONDS_THRESHOLD + "; waiting until the next minute");
            waitUntilNextMinute();
        }
        System.out.println("Continuing");
    }

    private static void waitUntilNextMinute() throws InterruptedException {
        if (!SHOULD_WAIT) {
            System.out.println("Not waiting");
            return;
        }
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

    private String formatErrorOutput(String note) {
        return String.format("%s%n"
                            + "           prestart: %s%n"
                            + "              start: %s%n"
                            + "   after increments: %s%n"
                            + "   after decrements: %s%n"
                            + "    after quiescing: %s%n",
                note,
                formatDate(preStart),
                formatDate(start),
                formatDate(afterIncrementsComplete),
                formatDate(afterDecrementsComplete),
                formatDate(afterQuiescing));
    }

    private static String formatDate(Date d) {
        if (d == null) {
            return "not reached";
        }
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        return String.format("%1$tH:%1$tM:%1$tS.%1$tL", c);
    }
}
