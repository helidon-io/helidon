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
package io.helidon.microprofile.metrics;

import io.helidon.metrics.KeyPerformanceIndicatorMetricsServices;
import io.helidon.webserver.KeyPerformanceIndicatorMetricsConfig;
import io.helidon.webserver.KeyPerformanceIndicatorMetricsService;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;

/**
 * Container for implementations -- basic and extended -- of the {@link KeyPerformanceIndicatorMetricsService} for queueable
 * (ie., Jersey/MP) requests.
 */
abstract class QueueableRequestKeyPerformanceIndicatorMetricsServices extends KeyPerformanceIndicatorMetricsServices {

    /**
     * KPI metrics service for basic metrics applied to Jersey apps.
     */
    static class Basic extends KeyPerformanceIndicatorMetricsServices.Basic {

        private final QueueableRequestKeyPerformanceIndicatorMetrics.Basic basicKpiMetrics;

        Basic(String metricsNamePrefix) {
            this(new QueueableRequestKeyPerformanceIndicatorMetrics.Basic(metricsNamePrefix));
        }

        Basic(QueueableRequestKeyPerformanceIndicatorMetrics.Basic basicKpiMetrics) {
            super(basicKpiMetrics);
            this.basicKpiMetrics = basicKpiMetrics;
        }

        @Override
        public KeyPerformanceIndicatorMetricsService.Context context() {
            return new Context(basicKpiMetrics);
        }

        // Primarily for symmetry, in case future work requires some logic here.
        static class Context extends KeyPerformanceIndicatorMetricsServices.Basic.Context {
            Context(QueueableRequestKeyPerformanceIndicatorMetrics.Basic basicKpiMetrics) {
                super(basicKpiMetrics);
            }
        }
    }

    /**
     * KPI metrics service for extended metrics applied to Jersey apps.
     */
    static class Extended extends KeyPerformanceIndicatorMetricsServices.Extended {

        private final QueueableRequestKeyPerformanceIndicatorMetrics.Extended extendedKpiMetrics;

        Extended(String metricsNamePrefix, KeyPerformanceIndicatorMetricsConfig kpiConfig) {
            this(new QueueableRequestKeyPerformanceIndicatorMetrics.Extended(metricsNamePrefix, kpiConfig));
        }

        Extended(QueueableRequestKeyPerformanceIndicatorMetrics.Extended extendedKpiMetrics) {
            super(extendedKpiMetrics);
            this.extendedKpiMetrics = extendedKpiMetrics;
        }

        @Override
        public KeyPerformanceIndicatorMetricsService.Context context() {
            return new Context(extendedKpiMetrics);
        }

        /**
         * Extended context implementation for queueable (Jersey) requests.
         *
         * This impl suppreses the metrics update logic which normally runs in {@code MetricsSupport} when a request handling
         * begins and ends, and instead it performs that logic when {@code JerseySupport} starts and detects the completion of
         * the request processing.
         */
        private static class Context extends KeyPerformanceIndicatorMetricsServices.Extended.Context {

            private Context(QueueableRequestKeyPerformanceIndicatorMetrics.Extended metrics) {
                super(metrics);
            }

            @Override
            public void requestHandlingStarted() {
            }

            @Override
            public void requestProcessingStarted() {
                beginMeasuringTimedRequest();
            }

            @Override
            public void requestProcessingCompleted(boolean isSuccessful) {
                finishMeasuringTimedRequest(isSuccessful);
            }

            @Override
            public void requestHandlingCompleted(boolean isSuccessful) {
            }
        }
    }

    /**
     * Specialization of the KPI metrics which maintains a separate {@code load} {@code Meter}.
     * <p>
     * The non-Jersey implementation's {@code load} metric is basically a wrapper around {@code totalMeter}. Here, {@code load}
     * is an independent metric so it will track rates of processed requests; {@code totalMeter} tracks rates of received
     * requests which will be different if requests are queued.
     * </p>
     */
    interface QueueableRequestKeyPerformanceIndicatorMetrics extends KeyPerformanceIndicatorMetrics {

        static KeyPerformanceIndicatorMetrics create(String metricsNamePrefix,
                KeyPerformanceIndicatorMetricsConfig kpiConfig) {
            return kpiConfig.isExtended()
                    ? new Extended(metricsNamePrefix, kpiConfig)
                    : new Basic(metricsNamePrefix);
        }

        // Same as the superclass but declared here for symmetry and future-proofing.
        class Basic extends KeyPerformanceIndicatorMetrics.Basic {

            private Basic(String metricsNamePrefix) {
                super(metricsNamePrefix);
            }
        }

        /**
         * Extended KPI metrics for supporting queueable requests, for which the load metric (rate of requests processed) might
         * differ from the totalMeter metric (rate of requests received).
         */
        class Extended extends KeyPerformanceIndicatorMetrics.Extended {

            private Extended(String metricsNamePrefix, KeyPerformanceIndicatorMetricsConfig kpiConfig) {
                super(metricsNamePrefix, kpiConfig);
            }

            @Override
            protected Meter registerLoad(String metricsNamePrefix) {
                return kpiMetricRegistry().meter(Metadata.builder()
                        .withName(metricsNamePrefix + KeyPerformanceIndicatorMetricsService.LOAD_NAME)
                        .withDisplayName(LOAD_DISPLAY_NAME)
                        .withDescription(LOAD_DESCRIPTION)
                        .withType(MetricType.METERED)
                        .withUnit(MetricUnits.NONE)
                        .build());
            }
        }
    }
}
