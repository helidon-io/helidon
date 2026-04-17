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

package io.helidon.common.concurrency.limits;

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.Services;

import static io.helidon.metrics.api.Meter.Scope.VENDOR;

class SemaphoreMetrics {
    private final boolean enableMetrics;
    private final Semaphore semaphore;
    private final String name;
    private final AtomicInteger rejectedRequests;
    private final AtomicInteger concurrentRequests;

    // set from a thread that may not be the same that uses them, must be volatile
    private volatile Timer rttTimer;
    private volatile Timer queueWaitTimer;

    /**
     * @param enableMetrics
     * @param semaphore          nullable
     * @param name
     * @param concurrentRequests
     * @param rejectedRequests
     */
    SemaphoreMetrics(boolean enableMetrics,
                     Semaphore semaphore,
                     String name,
                     AtomicInteger concurrentRequests,
                     AtomicInteger rejectedRequests) {
        this.enableMetrics = enableMetrics;
        this.semaphore = semaphore;
        this.name = name;
        this.rejectedRequests = rejectedRequests;
        this.concurrentRequests = concurrentRequests;
    }

    void init(String socketName) {
        if (!enableMetrics) {
            return;
        }

        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        MeterRegistry meterRegistry = metricsFactory.globalRegistry();

        List<Tag> tags;
        if (socketName.equals(Service.Named.DEFAULT_NAME)) {
            tags = List.of();
        } else {
            tags = List.of(Tag.create("socketName", socketName));
        }

        register(metricsFactory, meterRegistry, tags);
    }

    void register(MetricsFactory metricsFactory, MeterRegistry meterRegistry, List<Tag> tags) {
        if (semaphore != null) {
            Gauge.Builder<Integer> queueLengthBuilder = metricsFactory.gaugeBuilder(
                    name + "_queue_length", semaphore::getQueueLength).scope(VENDOR);
            queueLengthBuilder.tags(tags);
            meterRegistry.getOrCreate(queueLengthBuilder);
        }

        Gauge.Builder<Integer> concurrentRequestsBuilder = metricsFactory.gaugeBuilder(
                name + "_concurrent_requests", concurrentRequests::get).scope(VENDOR);
        concurrentRequestsBuilder.tags(tags);
        meterRegistry.getOrCreate(concurrentRequestsBuilder);

        Gauge.Builder<Integer> rejectedRequestsBuilder = metricsFactory.gaugeBuilder(
                name + "_rejected_requests", rejectedRequests::get).scope(VENDOR);
        rejectedRequestsBuilder.tags(tags);
        meterRegistry.getOrCreate(rejectedRequestsBuilder);

        Timer.Builder rttTimerBuilder = metricsFactory.timerBuilder(name + "_rtt")
                .scope(VENDOR)
                .baseUnit(Timer.BaseUnits.MILLISECONDS);
        rttTimerBuilder.tags(tags);
        rttTimer = meterRegistry.getOrCreate(rttTimerBuilder);

        Timer.Builder waitTimerBuilder = metricsFactory.timerBuilder(name + "_queue_wait_time")
                .scope(VENDOR)
                .baseUnit(Timer.BaseUnits.MILLISECONDS);
        waitTimerBuilder.tags(tags);
        queueWaitTimer = meterRegistry.getOrCreate(waitTimerBuilder);
    }

    /**
     * Updates the round-trip time (RTT) metric with the elapsed time between the specified start and end times.
     * <p>
     * The RTT is calculated as the difference between the end time and the start time. If the timer is not null,
     * the RTT is recorded using the {@link io.helidon.metrics.api.Timer#record(long, java.util.concurrent.TimeUnit)} method with
     * the {@link java.util.concurrent.TimeUnit#NANOSECONDS} unit.
     *
     * @param startTime the start time of the operation in nanoseconds
     * @param endTime   the end time of the operation in nanoseconds
     */
    void updateRtt(long startTime, long endTime) {
        if (rttTimer == null) {
            return;
        }
        long rtt = endTime - startTime;
        rttTimer.record(rtt, TimeUnit.NANOSECONDS);
    }

    void updateRtt(long delta) {
        if (rttTimer == null) {
            return;
        }

        rttTimer.record(delta, TimeUnit.NANOSECONDS);
    }

    void updateWaitTime(long startWait, long endWait) {
        if (queueWaitTimer == null) {
            return;
        }
        queueWaitTimer.record(endWait - startWait, TimeUnit.NANOSECONDS);
    }
}
