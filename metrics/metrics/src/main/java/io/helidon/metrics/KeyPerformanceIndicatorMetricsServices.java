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

import io.helidon.webserver.KeyPerformanceIndicatorMetricsConfig;
import io.helidon.webserver.KeyPerformanceIndicatorMetricsService;

import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;

/**
 * <em>Helidon Internal Use Only</em> - SE implementations of {@link KeyPerformanceIndicatorMetricsService} and related classes.
 * <p>
 *     Container for implementations of {@link KeyPerformanceIndicatorMetricsService} which
 *     always update the count and meter for all received requests. If the extended key performance indicator metrics are enabled
 *     via config or the {@link MetricsSupport.Builder} then
 *     this implementation also creates and updates the meters for in-flight and long-running requests.
 * </p>
 */
public class KeyPerformanceIndicatorMetricsServices {

    protected KeyPerformanceIndicatorMetricsServices() {
    }

    /**
     * Skeletal implementation of {@link KeyPerformanceIndicatorMetricsService.Context}, exposing methods for updating the KPI
     * metrics before and after a request has run.
     */
    protected abstract static class BaseContext implements KeyPerformanceIndicatorMetricsService.Context {

        private final KeyPerformanceIndicatorMetrics kpiMetrics;

        protected BaseContext(KeyPerformanceIndicatorMetrics kpiMetrics) {
            this.kpiMetrics = kpiMetrics;
            recordReceivingRequest();
        }

        protected final void recordReceivingRequest() {
            kpiMetrics.onRequestReceived();
        }

        protected final void beginMeasuringRequest() {
            kpiMetrics.onRequestStarted();
        }

        protected final void finishMeasuringRequest(boolean isSuccessful) {
            kpiMetrics.onRequestCompleted(isSuccessful);
        }

        protected final void finishMeasuringRequest(boolean isSuccessful, long processingTimeMs) {
            kpiMetrics.onRequestCompleted(isSuccessful, processingTimeMs);
        }
    }

    /**
     * Basic variant of {@link KeyPerformanceIndicatorMetricsService} for environments that directly process requests
     * (non-Jersey).
     */
    protected static class Basic implements KeyPerformanceIndicatorMetricsService {

        private final KeyPerformanceIndicatorMetrics.Basic kpiMetrics;

        protected Basic(String metricsNamePrefix, KeyPerformanceIndicatorMetricsConfig kpiConfig) {
            this(new KeyPerformanceIndicatorMetrics.Basic(metricsNamePrefix));
        }

        protected Basic(KeyPerformanceIndicatorMetrics.Basic kpiMetrics) {
            this.kpiMetrics = kpiMetrics;
        }

        @Override
        public KeyPerformanceIndicatorMetricsService.Context context() {
            return new Context(kpiMetrics);
        }

        /**
         * SE implementation of the KPI metrics service context without extended metrics support.
         */
        protected static class Context extends BaseContext {

            protected Context(KeyPerformanceIndicatorMetrics.Basic basicKpiMetrics) {
                super(basicKpiMetrics);
            }

            @Override
            public void requestHandlingStarted() {
                beginMeasuringRequest();
            }

            @Override
            public void requestHandlingCompleted(boolean isSuccessful) {
                finishMeasuringRequest(isSuccessful);
            }
        }
    }

    /**
     * Extended variant of {@link KeyPerformanceIndicatorMetricsService} for {@code MetricsSupport} (SE).
     */
    protected static class Extended extends Basic {

        private final KeyPerformanceIndicatorMetrics.Extended extendedKpiMetrics;

        protected Extended(String metricsNamePrefix, KeyPerformanceIndicatorMetricsConfig kpiConfig) {
            this(new KeyPerformanceIndicatorMetrics.Extended(metricsNamePrefix, kpiConfig));
        }

        protected Extended(KeyPerformanceIndicatorMetrics.Extended extendedKpiMetrics) {
            super(extendedKpiMetrics);
            this.extendedKpiMetrics = extendedKpiMetrics;
        }

        @Override
        public KeyPerformanceIndicatorMetricsService.Context context() {
            return new Context(extendedKpiMetrics);
        }

        /**
         * SE implementation of the KPI metrics service context with extended metrics support.
         */
        protected static class Context extends Basic.Context {

            private long requestStartTimeMs;

            protected Context(KeyPerformanceIndicatorMetrics.Extended extendedKpiMetrics) {
                super(extendedKpiMetrics);
            }

            @Override
            public void requestHandlingStarted() {
                beginMeasuringTimedRequest();
            }

            @Override
            public void requestHandlingCompleted(boolean isSuccessful) {
                finishMeasuringTimedRequest(isSuccessful);
            }

            protected void beginMeasuringTimedRequest() {
                beginMeasuringRequest();
                requestStartTimeMs = System.currentTimeMillis();
            }

            protected void finishMeasuringTimedRequest(boolean isSuccessful) {
                finishMeasuringRequest(isSuccessful, System.currentTimeMillis() - requestStartTimeMs);
            }
        }
    }

    /**
     * Key performance indicator metrics behavior.
     *
     * Implementations encapsulate the details of which metrics to register (depending on whether extended KPI metrics are
     * enabled) and how those metrics are updated in response to progress in the handling and processing of requests.
     */
    protected interface KeyPerformanceIndicatorMetrics {

        /**
         * The registry type where KPI metrics are registered.
         */
        MetricRegistry.Type KPI_METRICS_REGISTRY_TYPE = MetricRegistry.Type.VENDOR;

        /**
         * Creates a new KPI metrics instance.
         *
         * @param metricsNamePrefix prefix to use for the created metrics
         * @param kpiConfig KPI metrics config which may influence the construction of the metrics
         * @return properly prepared new KPI metrics instance
         */
        static KeyPerformanceIndicatorMetrics create(String metricsNamePrefix,
                KeyPerformanceIndicatorMetricsConfig kpiConfig) {
            return kpiConfig.isExtended()
                    ? new Extended(metricsNamePrefix, kpiConfig)
                    : new Basic(metricsNamePrefix);
        }

        /**
         * Invoked from a Context when a request has been received.
         */
        default void onRequestReceived() {
        }

        /**
         * Invoked from a Context when processing on a request has been started.
         */
        default void onRequestStarted() {
        }

        /**
         * Invoked from a Context when processing on a request has finished (and there is need to time the request).
         *
         * @param isSuccessful indicates if the request processing succeeded
         */
        default void onRequestCompleted(boolean isSuccessful) {
        }

        /**
         * Invoked from a Context when processing on a request has finished and the request was timed.
         *
         * @param isSuccessful indicates if the request processing succeeded
         * @param processingTimeMs duration of the request processing in milliseconds
         */
        default void onRequestCompleted(boolean isSuccessful, long processingTimeMs) {
        }

        /**
         * Basic KPI metrics.
         *
         * Identical for non-Jersey (SE) and Jersey (MP) servers.
         */
        class Basic implements KeyPerformanceIndicatorMetrics {

            private final MetricRegistry kpiMetricRegistry;

            private final Counter totalCount;
            private final Meter totalMeter;

            protected Basic(String metricsNamePrefix) {
                kpiMetricRegistry = RegistryFactory.getInstance()
                        .getRegistry(KPI_METRICS_REGISTRY_TYPE);
                totalCount = kpiMetricRegistry().counter(Metadata.builder()
                        .withName(metricsNamePrefix + KeyPerformanceIndicatorMetricsService.REQUESTS_COUNT_NAME)
                        .withDisplayName("Total number of HTTP requests")
                        .withDescription("Each request (regardless of HTTP method) will increase this counter")
                        .withType(MetricType.COUNTER)
                        .withUnit(MetricUnits.NONE)
                        .build());

                totalMeter = kpiMetricRegistry().meter(Metadata.builder()
                        .withName(metricsNamePrefix + KeyPerformanceIndicatorMetricsService.REQUESTS_METER_NAME)
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
        class Extended extends Basic {

            private final ConcurrentGauge inflightRequests;
            private final Meter longRunningRequests;
            private final Meter load;
            private final long longRunningRequestThresdholdMs;
            // The queued-requests metric is derived from load and totalMeter, so no need to have a reference to update
            // it directly.

            protected static final String LOAD_DISPLAY_NAME = "Requests load";
            protected static final String LOAD_DESCRIPTION =
                    "Measures the total number of in-flight requests and rates at which they occur";

            protected Extended(String metricsNamePrefix, KeyPerformanceIndicatorMetricsConfig kpiConfig) {
                super(metricsNamePrefix);
                longRunningRequestThresdholdMs = kpiConfig.longRunningRequestThresholdMs();

                inflightRequests = kpiMetricRegistry().concurrentGauge(Metadata.builder()
                        .withName(metricsNamePrefix + KeyPerformanceIndicatorMetricsService.INFLIGHT_REQUESTS_NAME)
                        .withDisplayName("Current number of in-flight requests")
                        .withDescription("Measures the number of currently in-flight requests")
                        .withType(MetricType.CONCURRENT_GAUGE)
                        .withUnit(MetricUnits.NONE)
                        .build());

                longRunningRequests = kpiMetricRegistry().meter(Metadata.builder()
                        .withName(metricsNamePrefix + KeyPerformanceIndicatorMetricsService.LONG_RUNNING_REQUESTS_NAME)
                        .withDisplayName("Long-running requests")
                        .withDescription("Measures the total number of long-running requests and rates at which they occur")
                        .withType(MetricType.METERED)
                        .withUnit(MetricUnits.NONE)
                        .build());

                this.load = registerLoad(metricsNamePrefix);

                kpiMetricRegistry().register(Metadata.builder()
                        .withName(metricsNamePrefix + KeyPerformanceIndicatorMetricsService.QUEUED_NAME)
                        .withDisplayName("Queued requests")
                        .withDescription("Measure queued requests")
                        .withType(MetricType.METERED)
                        .withUnit(MetricUnits.NONE)
                        .build(), new QueuedRequestsMeter(totalMeter(), load));
            }

            protected Meter registerLoad(String metricsNamePrefix) {
                // When measuring direct (non-queueable) requests, load is the same as totalMeter so delegate (except for mark;
                // we don't want to double-update totalMeter).
                return kpiMetricRegistry().register(Metadata.builder()
                        .withName(metricsNamePrefix + KeyPerformanceIndicatorMetricsService.LOAD_NAME)
                        .withDisplayName(LOAD_DISPLAY_NAME)
                        .withDescription(LOAD_DESCRIPTION)
                        .withType(MetricType.METERED)
                        .withUnit(MetricUnits.NONE)
                        .build(),
                    new DelegatingReadOnlyMeter(totalMeter()));
            }

            @Override
            public void onRequestStarted() {
                super.onRequestStarted();
                inflightRequests.inc();
            }

            @Override
            public void onRequestCompleted(boolean isSuccessful, long processingTimeMs) {
                super.onRequestCompleted(isSuccessful, processingTimeMs);
                inflightRequests.dec();
                if (processingTimeMs >= longRunningRequestThresdholdMs) {
                    longRunningRequests.mark();
                }
                load.mark();
            }

            /**
             * {@code Meter} which exposes the number of queued requests as derived from the hit meter (arrivals) - load meter
             * (processing).
             */
            private static class QueuedRequestsMeter implements Meter {

                private final Meter hitRate;
                private final Meter load;

                private QueuedRequestsMeter(Meter hitRate, Meter load) {
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

            private static class DelegatingReadOnlyMeter implements Meter {

                private final Meter delegate;

                private DelegatingReadOnlyMeter(Meter delegate) {
                    this.delegate = delegate;
                }

                @Override
                public void mark() {
                    // no-op
                }

                @Override
                public void mark(long n) {
                    // no-op
                }

                @Override
                public long getCount() {
                    return delegate.getCount();
                }

                @Override
                public double getFifteenMinuteRate() {
                    return delegate.getFifteenMinuteRate();
                }

                @Override
                public double getFiveMinuteRate() {
                    return delegate.getFiveMinuteRate();
                }

                @Override
                public double getMeanRate() {
                    return delegate.getMeanRate();
                }

                @Override
                public double getOneMinuteRate() {
                    return delegate.getOneMinuteRate();
                }
            }
        }
    }
}
