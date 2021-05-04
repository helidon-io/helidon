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

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Priority;

import io.helidon.common.LazyValue;
import io.helidon.webserver.KeyPerformanceIndicatorMetricsService;

import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;

/**
 * SE implementation of {@link KeyPerformanceIndicatorMetricsService}.
 * <p>
 *     If the MP implementation is also present, then it will override this implementation by virtue of its more urgent priority.
 * </p>
 * <p>
 *     This implementation (which the MP implementation extends) always creates and updates the count and meter for all requests.
 *     If the extended key performance indicators are enabled via config or the {@link MetricsSupport.Builder} then
 *     this implementation also creates and updates the meters for in-flight and long-running requests.
 * </p>
 */
@Priority(1000)
public class SeKeyPerformanceIndicatorMetricsService implements KeyPerformanceIndicatorMetricsService {

    private static final Logger LOGGER = Logger.getLogger(SeKeyPerformanceIndicatorMetricsService.class.getName());

    private String metricsNamePrefix = MetricsSupport.DEFAULT_METRICS_NAME_PREFIX;

    // Use a LazyValue here so tests that disable discovery will not fail. Normally, MetricsSupport invokes initialize which
    // primes the lazy value early but tests which disable discovery short-circuit that.
    private final LazyValue<SeKeyPerformanceIndicatorMetrics> metrics =
            LazyValue.create(() -> new SeKeyPerformanceIndicatorMetrics(metricsNamePrefix));

    private final KeyPerformanceIndicatorMetricsConfig kpiConfig;
    private final long longRunningThresholdNanos;

    /**
     * Creates a new SE service instance.
     */
    public SeKeyPerformanceIndicatorMetricsService() {
        this(LOGGER);
    }

    protected SeKeyPerformanceIndicatorMetricsService(Logger logger) {
        kpiConfig = MetricsSupport.keyPerformanceIndicatorMetricsConfig();
        longRunningThresholdNanos = kpiConfig.longRunningRequestThresholdMs() * 1000 * 1000;
        if (kpiConfig.isExtendedKpiEnabled) {
            logger.log(Level.INFO, "Key performance indicator metrics enabled");
        }
    }

    @Override
    public void initialize(String metricsNamePrefix) {
        this.metricsNamePrefix = metricsNamePrefix;
        primeMetrics();
    }

    protected void primeMetrics() {
        metrics.get(); // Trigger creation immediately where possible. Does not happen in tests with discovery disabled.
    }

    @Override
    public Context metricsSupportHandlerContext() {
        return isExtendedKpiEnabled() ? new SeExtendedContext(metrics.get()) : new SeBasicContext(metrics.get());
    }

    protected boolean isExtendedKpiEnabled() {
        return kpiConfig.isExtendedKpiEnabled;
    }

    protected String metricsNamePrefix() {
        return metricsNamePrefix;
    }

    /**
     * Configuration for KPI metrics.
     */
    static class KeyPerformanceIndicatorMetricsConfig {

        private boolean isExtendedKpiEnabled = MetricsSupport.EXTENDED_KEY_PERFORMANCE_INDICATORS_ENABLED_DEFAULT;
        private long longRunningRequestThresholdMs = MetricsSupport.LONG_RUNNING_REQUESTS_THRESHOLD_MS_DEFAULT;

        void enableExtendedKpi(boolean isExtendedKpiEnabled) {
            this.isExtendedKpiEnabled = isExtendedKpiEnabled;
        }

        boolean isExtendedKpiEnabled() {
            return isExtendedKpiEnabled;
        }

        void longRunningRequestThresholdMs(long value) {
            longRunningRequestThresholdMs = value;
        }

        long longRunningRequestThresholdMs() {
            return longRunningRequestThresholdMs;
        }
    }

    /**
     * SE implementation of the KPI metrics service context without extended metrics support.
     */
    protected class SeBasicContext implements KeyPerformanceIndicatorMetricsService.Context {

        private final SeKeyPerformanceIndicatorMetrics metrics;

        protected SeBasicContext(SeKeyPerformanceIndicatorMetrics metrics) {
            this.metrics = metrics;
        }

        @Override
        public void requestStarted() {
            metrics.totalCount.inc();
            metrics.totalMeter.mark();
        }

        protected SeKeyPerformanceIndicatorMetrics metrics() {
            return metrics;
        }
    }

    /**
     * SE implementation of the KPI metrics service context with extended metrics support.
     */
    protected class SeExtendedContext extends SeBasicContext {

        private final long arrivalTime = System.nanoTime();

        protected SeExtendedContext(SeKeyPerformanceIndicatorMetrics metrics) {
            super(metrics);
        }

        @Override
        public void requestStarted() {
            super.requestStarted();
            metrics().inflightRequests.inc();
        }

        @Override
        public void requestCompleted(boolean isSuccessful) {
            super.requestCompleted(isSuccessful);
            metrics().inflightRequests.dec();
            if (System.nanoTime() - arrivalTime >= longRunningThresholdNanos) {
                metrics().longRunningRequests.mark();
            }
        }
    }
    /**
     * SE implementation of the KPI metrics.
     */
    protected class SeKeyPerformanceIndicatorMetrics {

        static final String REQUESTS_COUNT_NAME = "count";
        static final String REQUESTS_METER_NAME = "meter";
        static final String INFLIGHT_REQUESTS_NAME = "in-flight";
        static final String LONG_RUNNING_REQUESTS_NAME = "long-running";

        // The next two metrics are always updated by the MetricsSupport handler context.
        // They are not controlled by the metrics.extended-key-performance-indicators.enabled config or builder setting.
        private final Counter totalCount;
        private final Meter totalMeter;

        private final ConcurrentGauge inflightRequests;
        private final Meter longRunningRequests;
        private final MetricRegistry vendorRegistry;

        protected SeKeyPerformanceIndicatorMetrics(String metricsNamePrefix) {
            vendorRegistry = RegistryFactory.getInstance()
                    .getRegistry(MetricRegistry.Type.VENDOR);

            totalCount = vendorRegistry.counter(Metadata.builder()
                    .withName(metricsNamePrefix + REQUESTS_COUNT_NAME)
                    .withDisplayName("Total number of HTTP requests")
                    .withDescription("Each request (regardless of HTTP method) will increase this counter")
                    .withType(MetricType.COUNTER)
                    .withUnit(MetricUnits.NONE)
                    .build());

            totalMeter = vendorRegistry.meter(Metadata.builder()
                    .withName(metricsNamePrefix + REQUESTS_METER_NAME)
                    .withDisplayName("Meter for overall HTTP requests")
                    .withDescription("Each request will mark the meter to see overall throughput")
                    .withType(MetricType.METERED)
                    .withUnit(MetricUnits.NONE)
                    .build());

            inflightRequests = isExtendedKpiEnabled() ? vendorRegistry.concurrentGauge(Metadata.builder()
                    .withName(metricsNamePrefix + INFLIGHT_REQUESTS_NAME)
                    .withDisplayName("Current number of in-flight requests")
                    .withDescription("Measures the number of currently in-flight requests")
                    .withType(MetricType.CONCURRENT_GAUGE)
                    .withUnit(MetricUnits.NONE)
                    .build())
                : null;

            longRunningRequests = isExtendedKpiEnabled() ? vendorRegistry.meter(Metadata.builder()
                    .withName(metricsNamePrefix + LONG_RUNNING_REQUESTS_NAME)
                    .withDisplayName("Long-running requests")
                    .withDescription("Measures the total number of long-running requests and rates at which they occur")
                    .withType(MetricType.METERED)
                    .withUnit(MetricUnits.NONE)
                    .build())
                : null;
        }

        protected MetricRegistry vendorRegistry() {
            return vendorRegistry;
        }

        protected Meter totalMeter() {
            return totalMeter;
        }
    }
}
