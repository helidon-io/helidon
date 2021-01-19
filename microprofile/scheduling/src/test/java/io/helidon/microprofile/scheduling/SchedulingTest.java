/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.microprofile.scheduling;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddBeans;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.AddExtensions;
import io.helidon.microprofile.tests.junit5.Configuration;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;

import org.junit.jupiter.api.Test;

@HelidonTest
@DisableDiscovery
@AddBeans({
        @AddBean(ScheduledBean.class)
})
@AddExtensions({
        @AddExtension(SchedulingCdiExtension.class),
})
@Configuration(configSources = "test.properties")
public class SchedulingTest {

    static final long TWO_SEC_MILLIS = 2 * 1000L;

    final CountDownLatch fixedRateLatch = new CountDownLatch(5);
    final CountDownLatch fixedRateFromConfigLatch = new CountDownLatch(2);
    final CountDownLatch exprLatch = new CountDownLatch(1);
    final CompletableFuture<Integer> noConcurrentExecFuture = new CompletableFuture<>();
    final CompletableFuture<Integer> concurrentExecFuture = new CompletableFuture<>();
    final CompletableFuture<String> overriddenCronFuture = new CompletableFuture<>();
    final CompletableFuture<Boolean> overriddenConcurrentFuture = new CompletableFuture<>();
    final CompletableFuture<Long> overriddenInitDelayFuture = new CompletableFuture<>();
    final CompletableFuture<Long> overriddenDelayFuture = new CompletableFuture<>();
    final CompletableFuture<TimeUnit> overriddenTimeUnitFuture = new CompletableFuture<>();

    @Inject
    ScheduledBean scheduledBean;

    @FixedRate(value = 100, timeUnit = TimeUnit.MILLISECONDS)
    public void rate() {
        fixedRateLatch.countDown();
    }

    @FixedRate(999999)
    public void rateFromConfig() {
        fixedRateFromConfigLatch.countDown();
    }

    @Scheduled("${test-cron-expr}")
    void placeholder() {
        exprLatch.countDown();
    }

    AtomicInteger noConcurrentContenderCnt = new AtomicInteger(0);

    @Scheduled(value = "0/1 * * * * ? *", concurrentExecution = false)
    void noConcurrentExecutions() throws InterruptedException {
        noConcurrentContenderCnt.incrementAndGet();
        Thread.sleep(1800);
        noConcurrentExecFuture.complete(noConcurrentContenderCnt.get());
        noConcurrentContenderCnt.decrementAndGet();
    }

    AtomicInteger concurrentContenderCnt = new AtomicInteger(0);

    @Scheduled(value = "0/1 * * * * ? *")
    void concurrentExecutions() throws InterruptedException {
        concurrentContenderCnt.incrementAndGet();
        Thread.sleep(1800);
        concurrentExecFuture.complete(concurrentContenderCnt.get());
        concurrentContenderCnt.decrementAndGet();
    }

    @Scheduled(value = "0 0 * * * ? *", concurrentExecution = true)
    void overriddenValuesCron(CronInvocation inv) {
        overriddenCronFuture.complete(inv.cron());
        overriddenConcurrentFuture.complete(inv.concurrent());
    }

    @FixedRate(initialDelay = 500, value = 1000, timeUnit = TimeUnit.HOURS)
    void overriddenValuesFixed(FixedRateInvocation inv) {
        overriddenInitDelayFuture.complete(inv.initialDelay());
        overriddenDelayFuture.complete(inv.delay());
        overriddenTimeUnitFuture.complete(inv.timeUnit());
    }

    @Test
    void expressionPlaceHolder() throws InterruptedException {
        assertThat("Scheduled method expected to be invoked at least once",
                exprLatch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void executedEvery2Sec() throws InterruptedException {
        assertThat("Scheduled method expected to be invoked at least twice",
                scheduledBean.getCountDownLatch().await(5, TimeUnit.SECONDS));
        assertDuration(TWO_SEC_MILLIS, scheduledBean.getDuration(), 2000);
    }

    @Test
    void fixedRate() throws InterruptedException {
        assertThat("Scheduled method expected to be invoked at least 5 times",
                fixedRateLatch.await(3, TimeUnit.SECONDS));
    }

    @Test
    void fixedRateFromConfig() throws InterruptedException {
        assertThat("Scheduled method expected to be invoked at least twice",
                fixedRateLatch.await(3, TimeUnit.SECONDS));
    }

    @Test
    void forbiddenConcurrentExec() throws InterruptedException, TimeoutException, ExecutionException {
        assertThat("Scheduled method expected is NOT expected to be invoked concurrently with concurrentExecution = false",
                noConcurrentExecFuture.get(3, TimeUnit.SECONDS), equalTo(1));
    }

    @Test
    void concurrentExec() throws InterruptedException, TimeoutException, ExecutionException {
        assertThat("Scheduled method is expected to be invoked concurrently with concurrentExecution = true.",
                concurrentExecFuture.get(3, TimeUnit.SECONDS), greaterThan(1));
    }

    @Test
    void overriddenCron() throws InterruptedException, TimeoutException, ExecutionException {
        assertThat("Cron expression should have been overridden by config value",
                overriddenCronFuture.get(5, TimeUnit.SECONDS), equalTo("0/2 * * * * ? *"));
        assertThat("Concurrent flag should have been overridden by config value",
                overriddenConcurrentFuture.get(5, TimeUnit.SECONDS), equalTo(Boolean.FALSE));
    }

    @Test
    void overriddenFixedRate() throws InterruptedException, TimeoutException, ExecutionException {
        assertThat("Cron expression should have been overridden by config value",
                overriddenInitDelayFuture.get(5, TimeUnit.SECONDS), equalTo(1L));
        assertThat("Concurrent flag should have been overridden by config value",
                overriddenDelayFuture.get(5, TimeUnit.SECONDS), equalTo(2L));
        assertThat("Concurrent flag should have been overridden by config value",
                overriddenTimeUnitFuture.get(5, TimeUnit.SECONDS), equalTo(TimeUnit.SECONDS));
    }

    private void assertDuration(long expectedDuration, long duration, long allowedDiscrepancy) {
        String durationString = "Expected duration is 2 sec, but was " + ((float) duration / 1000) + "sec";
        assertThat(durationString, duration, greaterThan(expectedDuration - allowedDiscrepancy));
        assertThat(durationString, duration, lessThan(expectedDuration + allowedDiscrepancy));
    }
}
