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
package io.helidon.reactive.metrics;

import java.util.HashMap;
import java.util.Map;

import io.helidon.metrics.api.KeyPerformanceIndicatorMetricsSettings;
import io.helidon.metrics.api.Registry;
import io.helidon.metrics.api.RegistryFactory;
import io.helidon.reactive.webserver.KeyPerformanceIndicatorSupport;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
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

    static final String KPI_METRICS_REGISTRY_TYPE = Registry.VENDOR_SCOPE;

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

        protected Basic(String metricsNamePrefix) {
            kpiMetricRegistry = RegistryFactory.getInstance()
                    .getRegistry(KPI_METRICS_REGISTRY_TYPE);
            totalCount = kpiMetricRegistry().counter(Metadata.builder()
                    .withName(metricsNamePrefix + REQUESTS_COUNT_NAME)
                    .withDescription("Each request (regardless of HTTP method) will increase this counter")
                    .withUnit(MetricUnits.NONE)
                    .build());
        }

        @Override
        public void onRequestReceived() {
            totalCount.inc();
        }

        protected MetricRegistry kpiMetricRegistry() {
            return kpiMetricRegistry;
        }

        protected Counter totalCount() {
            return totalCount;
        }
    }

    /**
     * Extended KPI metrics.
     */
    private static class Extended extends Basic {

        private final Gauge<Integer> inflightRequests;
        private final DeferredRequests deferredRequests;
        private final Counter longRunningRequests;
        private final Counter load;
        private final long longRunningRequestThresdholdMs;
        // The deferred-requests metric is derived from load and totalMeter, so no need to have a reference to update
        // it directly.

        private int inflightRequestsCount;

        protected static final String LOAD_DISPLAY_NAME = "Requests load";
        protected static final String LOAD_DESCRIPTION =
                "Measures the total number of in-flight requests and rates at which they occur";

        protected Extended(String metricsNamePrefix, KeyPerformanceIndicatorMetricsSettings kpiConfig) {
            super(metricsNamePrefix);
            longRunningRequestThresdholdMs = kpiConfig.longRunningRequestThresholdMs();

            inflightRequests = kpiMetricRegistry().gauge(Metadata.builder()
                                                                 .withName(metricsNamePrefix + INFLIGHT_REQUESTS_NAME)
                                                                 .withDescription(
                                                                         "Measures the number of currently in-flight requests")
                                                                 .withUnit(MetricUnits.NONE)
                                                                 .build(),
                                                         () -> inflightRequestsCount);

            longRunningRequests = kpiMetricRegistry().counter(Metadata.builder()
                    .withName(metricsNamePrefix + LONG_RUNNING_REQUESTS_NAME)
                    .withDescription("Measures the total number of long-running requests and rates at which they occur")
                    .withUnit(MetricUnits.NONE)
                    .build());

            load = kpiMetricRegistry().counter(Metadata.builder()
                    .withName(metricsNamePrefix + LOAD_NAME)
                    .withDescription(LOAD_DESCRIPTION)
                    .withUnit(MetricUnits.NONE)
                    .build());

            deferredRequests = new DeferredRequests();
            kpiMetricRegistry().gauge(Metadata.builder()
                                              .withName(metricsNamePrefix + DEFERRED_NAME)
                                              .withDescription("Measures deferred requests")
                                              .withUnit(MetricUnits.NONE)
                                              .build(),
                                      deferredRequests,
                                      DeferredRequests::getValue);
        }

        @Override
        public void onRequestReceived() {
            super.onRequestReceived();
            deferredRequests.deferRequest();
        }

        @Override
        public void onRequestStarted() {
            super.onRequestStarted();
            inflightRequestsCount++;
            load.inc();
            deferredRequests.startRequest();
        }

        @Override
        public void onRequestCompleted(boolean isSuccessful, long processingTimeMs) {
            super.onRequestCompleted(isSuccessful, processingTimeMs);
            inflightRequestsCount--;
            if (processingTimeMs >= longRunningRequestThresdholdMs) {
                longRunningRequests.inc();
            }
            deferredRequests.completeRequest();
        }

        /**
         * {@code Meter} which exposes the number of deferred requests as derived from the hit meter (arrivals) - load meter
         * (processing).
         */
        private static class DeferredRequests implements Gauge<Long> {

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

            @Override
            public Long getValue() {
                return hits - load;
            }
        }
    }
}
