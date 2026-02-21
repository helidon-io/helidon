/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

import java.lang.reflect.Method;

import io.helidon.webserver.http.ServerRequest;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.Timer;

/**
 * Represents metrics-related work done before and/or after Helidon processes REST requests.
 * <p>
 *     An optional MP metrics feature (which Helidon implements) updates a {@link Timer} for each REST request
 *     successfully processed or throwing a mapped exception and updates a {@link Counter} for each unmapped exception thrown
 *     during the handling of a REST request.
 * <p>
 *     If the automatic metrics configuration is not set, then the CDI extension registers all needed meters during
 *     initialization.
 */
interface SyntheticRestRequestWorkItem extends MetricWorkItem {

    boolean isMeasured(ServerRequest request);

    MetricID successfulTimerMetricID();

    Timer successfulTimer();

    MetricID unmappedExceptionCounterMetricID();

    Counter unmappedExceptionCounter();

    static SyntheticRestRequestWorkItem create(MetricID successfulTimerMetricID,
                                               Timer successfulTimer,
                                               MetricID unmappedExceptionCounterMetricID,
                                               Counter unmappedExceptionCounter) {
        return new Prepared(successfulTimerMetricID,
                                                successfulTimer,
                                                unmappedExceptionCounterMetricID,
                                                unmappedExceptionCounter);
    }

    static SyntheticRestRequestWorkItem create(MetricsCdiExtension metricsCdiExtension,
                                               Class<?> measuredClass,
                                               Method measuredMethod) {
        return new AdHoc(metricsCdiExtension, measuredClass, measuredMethod);
    }

    record Prepared(MetricID successfulTimerMetricID,
                    Timer successfulTimer,
                    MetricID unmappedExceptionCounterMetricID,
                    Counter unmappedExceptionCounter) implements SyntheticRestRequestWorkItem {

        @Override
        public boolean isMeasured(ServerRequest request) {
            return true;
        }
    }

    class AdHoc implements SyntheticRestRequestWorkItem {

        private final MetricsCdiExtension metricsCdiExtension;
        private final Class<?> measuredClass;
        private final Method measuredMethod;

        private MetricID successfulTimerMetricId;
        private Timer successfulTimer;
        private MetricID unmappedExceptionCounterMetricId;
        private Counter unmappedExceptionCounter;

        private AdHoc(MetricsCdiExtension metricsCdiExtension, Class<?> measuredClass, Method measuredMethod) {
            this.metricsCdiExtension = metricsCdiExtension;
            this.measuredClass = measuredClass;
            this.measuredMethod = measuredMethod;
        }

        @Override
        public boolean isMeasured(ServerRequest  request) {
            return metricsCdiExtension.isMeasuredForAutomaticRestMetrics(request);
        }

        @Override
        public MetricID successfulTimerMetricID() {
            if (successfulTimerMetricId == null) {
                successfulTimerMetricId = MetricsCdiExtension.restEndpointTimerMetricID(measuredClass, measuredMethod);
            }
            return successfulTimerMetricId;
        }

        @Override
        public Timer successfulTimer() {
            if (successfulTimer == null) {
                successfulTimer = MetricsCdiExtension.restEndpointTimer(measuredClass, measuredMethod);
            }
            return successfulTimer;
        }

        @Override
        public MetricID unmappedExceptionCounterMetricID() {
            if (unmappedExceptionCounterMetricId == null) {
                unmappedExceptionCounterMetricId = metricsCdiExtension.restEndpointCounterMetricID(measuredClass, measuredMethod);
            }
            return unmappedExceptionCounterMetricId;
        }

        @Override
        public Counter unmappedExceptionCounter() {
            if (unmappedExceptionCounter == null) {
                unmappedExceptionCounter = MetricsCdiExtension.restEndpointCounter(measuredClass, measuredMethod);
            }
            return unmappedExceptionCounter;
        }
    }
}
