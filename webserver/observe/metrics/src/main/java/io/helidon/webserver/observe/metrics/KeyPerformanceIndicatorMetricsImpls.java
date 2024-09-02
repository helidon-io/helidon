/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
package io.helidon.webserver.observe.metrics;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.context.ContextValue;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.KeyPerformanceIndicatorMetricsConfig;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.webserver.KeyPerformanceIndicatorSupport;

class KeyPerformanceIndicatorMetricsImpls {

    /**
     * Name for metric recording number of requests currently deferred (received but not yet processing).
     */
    static final String DEFERRED_NAME = "deferred";
    /**
     * Name for metric counting total requests received.
     */
    static final String REQUESTS_COUNT_NAME = "count";
    /**
     * Name for metric recording current number of requests being processed.
     */
    static final String INFLIGHT_REQUESTS_NAME = "inFlight";
    /**
     * Name for metric recording rate of requests with processing time exceeding a threshold.
     */
    static final String LONG_RUNNING_REQUESTS_NAME = "longRunning";
    /**
     * Name for metric recording number requests currently being processed.
     */
    static final String LOAD_NAME = "load";
    static final String KPI_METERS_SCOPE = Meter.Scope.VENDOR;

    private static final ContextValue<KpiMetrics> KPI_METRICS = ContextValue.create(KpiMetrics.class, KpiMetrics::new);

    private KeyPerformanceIndicatorMetricsImpls() {
    }

    /**
     * Provides a KPI metrics instance.
     *
     * @param kpiMeterRegistry meter registry holding the KPI metrics
     * @param meterNamePrefix  prefix to use for the created metrics (e.g., "requests")
     * @param kpiConfig        KPI metrics config which may influence the construction of the metrics
     * @return properly prepared new KPI metrics instance
     */
    static KeyPerformanceIndicatorSupport.Metrics get(MeterRegistry kpiMeterRegistry,
                                                      String meterNamePrefix,
                                                      KeyPerformanceIndicatorMetricsConfig kpiConfig) {
        return KPI_METRICS.get()
                .kpiMetrics
                .computeIfAbsent(meterNamePrefix, prefix ->
                        kpiConfig.extended()
                                ? new Extended(kpiMeterRegistry, meterNamePrefix, kpiConfig)
                                : new Basic(kpiMeterRegistry, meterNamePrefix));
    }

    static void close() {
        KPI_METRICS.get().kpiMetrics.clear();
    }

    /**
     * Basic KPI metrics.
     */
    private static class Basic implements KeyPerformanceIndicatorSupport.Metrics {

        private final Counter totalCount;
        private final MeterRegistry meterRegistry;
        private final List<Meter> meters = new ArrayList<>();

        protected Basic(MeterRegistry kpiMeterRegistry, String meterNamePrefix) {
            meterRegistry = kpiMeterRegistry;
            totalCount = add(kpiMeterRegistry.getOrCreate(
                    Counter.builder(meterNamePrefix + REQUESTS_COUNT_NAME)
                            .description(
                                    "Each request (regardless of HTTP method) will increase this counter")
                            .scope(KPI_METERS_SCOPE)));
        }

        @Override
        public void onRequestReceived() {
            totalCount.increment();
        }

        @Override
        public void close() {
            meters.forEach(meterRegistry::remove);
            KPI_METRICS.get()
                    .kpiMetrics
                    .clear();
        }

        protected <M extends Meter> M add(M meter) {
            meters.add(meter);
            return meter;
        }

        protected Counter totalCount() {
            return totalCount;
        }
    }

    /**
     * Extended KPI metrics.
     */
    private static class Extended extends Basic {

        protected static final String LOAD_DESCRIPTION =
                "Measures the total number of in-flight requests over the life of the server";
        private final Gauge inflightRequests;
        private final DeferredRequests deferredRequests;
        private final Counter longRunningRequests;
        private final Counter load;
        // The deferred-requests metric is derived from load and totalCount, so no need to have a reference to update
        // it directly.
        private final long longRunningRequestThresdholdMs;
        private AtomicInteger inflightRequestsCount = new AtomicInteger();

        protected Extended(MeterRegistry kpiMeterRegistry,
                           String meterNamePrefix,
                           KeyPerformanceIndicatorMetricsConfig kpiConfig) {
            this(kpiMeterRegistry, meterNamePrefix, kpiConfig.longRunningRequestThreshold());
        }

        private Extended(MeterRegistry kpiMeterRegistry, String meterNamePrefix, Duration longRunningRequestThreshold) {
            super(kpiMeterRegistry, meterNamePrefix);
            this.longRunningRequestThresdholdMs = longRunningRequestThreshold.toMillis();

            inflightRequests = kpiMeterRegistry.getOrCreate(Gauge.builder(meterNamePrefix + INFLIGHT_REQUESTS_NAME,
                                                                          inflightRequestsCount,
                                                                          AtomicInteger::get)
                                                                    .scope(KPI_METERS_SCOPE));

            longRunningRequests = kpiMeterRegistry.getOrCreate(
                    Counter.builder(meterNamePrefix + LONG_RUNNING_REQUESTS_NAME)
                            .description("Measures the total number of long-running requests and rates at which they occur")
                            .scope(KPI_METERS_SCOPE)
            );

            load = kpiMeterRegistry.getOrCreate(Counter.builder(meterNamePrefix + LOAD_NAME)
                                                        .description(LOAD_DESCRIPTION)
                                                        .scope(KPI_METERS_SCOPE));

            deferredRequests = new DeferredRequests();
            kpiMeterRegistry.getOrCreate(Gauge.builder(meterNamePrefix + DEFERRED_NAME,
                                                       deferredRequests,
                                                       DeferredRequests::value)
                                                 .description("Measures deferred requests")
                                                 .scope(KPI_METERS_SCOPE));
        }

        @Override
        public void onRequestReceived() {
            super.onRequestReceived();
            deferredRequests.deferRequest();
        }

        @Override
        public void onRequestStarted() {
            super.onRequestStarted();
            inflightRequestsCount.incrementAndGet();
            load.increment();
            deferredRequests.startRequest();
        }

        @Override
        public void onRequestCompleted(boolean isSuccessful, long processingTimeMs) {
            super.onRequestCompleted(isSuccessful, processingTimeMs);
            inflightRequestsCount.decrementAndGet();
            if (processingTimeMs >= longRunningRequestThresdholdMs) {
                longRunningRequests.increment();
            }
            deferredRequests.completeRequest();
        }

        /**
         * {@code Counter} which exposes the number of deferred requests as derived from the hit counter (arrivals) - load counter
         * (processing).
         */
        private static class DeferredRequests {

            private long hits;
            private long load;

            private DeferredRequests() {
            }

            void deferRequest() {
                hits++;
            }

            void startRequest() {
                load++;
            }

            void completeRequest() {
                hits--;
                load--;
            }

            double value() {
                return hits - load;
            }
        }
    }

    private static class KpiMetrics {
        private final Map<String, KeyPerformanceIndicatorSupport.Metrics> kpiMetrics = new HashMap<>();
    }
}
