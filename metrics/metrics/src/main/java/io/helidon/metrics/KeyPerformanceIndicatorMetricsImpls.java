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
package io.helidon.metrics;

import java.util.HashMap;
import java.util.Map;

import io.helidon.metrics.api.KeyPerformanceIndicatorMetricsSettings;
import io.helidon.metrics.api.RegistryFactory;
import io.helidon.webserver.KeyPerformanceIndicatorSupport;

import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;

class KeyPerformanceIndicatorMetricsImpls {

    /**
     * Prefix for key performance indicator metrics names.
     */
    static final String METRICS_NAME_PREFIX = "requests";

    /**
     * Name for metric counting total requests received.
     */
    static final String REQUESTS_COUNT_NAME = "count";

    /**
     * Name for metric recording rate of requests received.
     */
    static final String REQUESTS_METER_NAME = "meter";

    /**
     * Name for metric recording current number of requests being processed.
     */
    static final String INFLIGHT_REQUESTS_NAME = "inFlight";

    /**
     * Name for metric recording rate of requests with processing time exceeding a threshold.
     */
    static final String LONG_RUNNING_REQUESTS_NAME = "longRunning";

    /**
     * Name for metric recording rate of requests processed.
     */
    static final String LOAD_NAME = "load";

    /**
     * Name for metric recording rate of requests deferred before processing.
     */
    public static final String DEFERRED_NAME = "deferred";

    static final MetricRegistry.Type KPI_METRICS_REGISTRY_TYPE = MetricRegistry.Type.VENDOR;

    private static final Map<String, KeyPerformanceIndicatorSupport.Metrics> KPI_METRICS = new HashMap<>();

    private KeyPerformanceIndicatorMetricsImpls() {
    }

    /**
     * Provides a KPI metrics instance.
     *
     * @param metricsNamePrefix prefix to use for the created metrics
     * @param kpiConfig         KPI metrics config which may influence the construction of the metrics
     * @return properly prepared new KPI metrics instance
     */
    static KeyPerformanceIndicatorSupport.Metrics get(String metricsNamePrefix,
            KeyPerformanceIndicatorMetricsSettings kpiConfig) {
        return KPI_METRICS.computeIfAbsent(metricsNamePrefix, prefix ->
             kpiConfig.isExtended()
                    ? new Extended(metricsNamePrefix, kpiConfig)
                    : new Basic(metricsNamePrefix));
    }

    /**
     * Basic KPI metrics.
     */
    private static class Basic implements KeyPerformanceIndicatorSupport.Metrics {

        private final MetricRegistry kpiMetricRegistry;

        private final Counter totalCount;
        private final Meter totalMeter;

        protected Basic(String metricsNamePrefix) {
            kpiMetricRegistry = RegistryFactory.getInstance()
                    .getRegistry(KPI_METRICS_REGISTRY_TYPE);
            totalCount = kpiMetricRegistry().counter(Metadata.builder()
                    .withName(metricsNamePrefix + REQUESTS_COUNT_NAME)
                    .withDisplayName("Total number of HTTP requests")
                    .withDescription("Each request (regardless of HTTP method) will increase this counter")
                    .withType(MetricType.COUNTER)
                    .withUnit(MetricUnits.NONE)
                    .build());

            totalMeter = kpiMetricRegistry().meter(Metadata.builder()
                    .withName(metricsNamePrefix + REQUESTS_METER_NAME)
                    .withDisplayName("Meter for overall HTTP requests")
                    .withDescription("Each request will mark the meter to see overall throughput")
                    .withType(MetricType.METERED)
                    .withUnit(MetricUnits.NONE)
                    .build());
        }

        @Override
        public void onRequestReceived() {
            totalCount.inc();
            totalMeter.mark();
        }

        protected MetricRegistry kpiMetricRegistry() {
            return kpiMetricRegistry;
        }

        protected Meter totalMeter() {
            return totalMeter;
        }
    }

    /**
     * Extended KPI metrics.
     */
    private static class Extended extends Basic {

        private final ConcurrentGauge inflightRequests;
        private final Meter longRunningRequests;
        private final Meter load;
        private final long longRunningRequestThresdholdMs;
        // The deferred-requests metric is derived from load and totalMeter, so no need to have a reference to update
        // it directly.

        protected static final String LOAD_DISPLAY_NAME = "Requests load";
        protected static final String LOAD_DESCRIPTION =
                "Measures the total number of in-flight requests and rates at which they occur";

        protected Extended(String metricsNamePrefix, KeyPerformanceIndicatorMetricsSettings kpiConfig) {
            super(metricsNamePrefix);
            longRunningRequestThresdholdMs = kpiConfig.longRunningRequestThresholdMs();

            inflightRequests = kpiMetricRegistry().concurrentGauge(Metadata.builder()
                    .withName(metricsNamePrefix + INFLIGHT_REQUESTS_NAME)
                    .withDisplayName("Current number of in-flight requests")
                    .withDescription("Measures the number of currently in-flight requests")
                    .withType(MetricType.CONCURRENT_GAUGE)
                    .withUnit(MetricUnits.NONE)
                    .build());

            longRunningRequests = kpiMetricRegistry().meter(Metadata.builder()
                    .withName(metricsNamePrefix + LONG_RUNNING_REQUESTS_NAME)
                    .withDisplayName("Long-running requests")
                    .withDescription("Measures the total number of long-running requests and rates at which they occur")
                    .withType(MetricType.METERED)
                    .withUnit(MetricUnits.NONE)
                    .build());

            load = kpiMetricRegistry().meter(Metadata.builder()
                    .withName(metricsNamePrefix + LOAD_NAME)
                    .withDisplayName(LOAD_DISPLAY_NAME)
                    .withDescription(LOAD_DESCRIPTION)
                    .withType(MetricType.METERED)
                    .withUnit(MetricUnits.NONE)
                    .build());

            kpiMetricRegistry().register(Metadata.builder()
                    .withName(metricsNamePrefix + DEFERRED_NAME)
                    .withDisplayName("Deferred requests")
                    .withDescription("Measures deferred requests")
                    .withType(MetricType.METERED)
                    .withUnit(MetricUnits.NONE)
                    .build(), new DeferredRequestsMeter(totalMeter(), load));
        }

        @Override
        public void onRequestStarted() {
            super.onRequestStarted();
            inflightRequests.inc();
            load.mark();
        }

        @Override
        public void onRequestCompleted(boolean isSuccessful, long processingTimeMs) {
            super.onRequestCompleted(isSuccessful, processingTimeMs);
            inflightRequests.dec();
            if (processingTimeMs >= longRunningRequestThresdholdMs) {
                longRunningRequests.mark();
            }
        }

        /**
         * {@code Meter} which exposes the number of deferred requests as derived from the hit meter (arrivals) - load meter
         * (processing).
         */
        private static class DeferredRequestsMeter implements Meter {

            private final Meter hitRate;
            private final Meter load;

            private DeferredRequestsMeter(Meter hitRate, Meter load) {
                this.hitRate = hitRate;
                this.load = load;
            }

            @Override
            public void mark() {
            }

            @Override
            public void mark(long n) {
            }

            @Override
            public long getCount() {
                return hitRate.getCount() - load.getCount();
            }

            @Override
            public double getFifteenMinuteRate() {
                return Double.max(0, hitRate.getFifteenMinuteRate() - load.getFifteenMinuteRate());
            }

            @Override
            public double getFiveMinuteRate() {
                return Double.max(0, hitRate.getFiveMinuteRate() - load.getFiveMinuteRate());
            }

            @Override
            public double getMeanRate() {
                return Double.max(0, hitRate.getMeanRate() - load.getMeanRate());
            }

            @Override
            public double getOneMinuteRate() {
                return Double.max(0, hitRate.getOneMinuteRate() - load.getOneMinuteRate());
            }
        }
    }
}
