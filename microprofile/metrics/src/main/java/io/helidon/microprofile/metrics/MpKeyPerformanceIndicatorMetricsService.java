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

import java.util.logging.Logger;

import javax.annotation.Priority;

import io.helidon.common.LazyValue;
import io.helidon.metrics.SeKeyPerformanceIndicatorMetricsService;
import io.helidon.webserver.KeyPerformanceIndicatorMetricsService;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;


/**
 * MP implementation of the {@link KeyPerformanceIndicatorMetricsService}.
 */
@Priority(400)
public class MpKeyPerformanceIndicatorMetricsService extends SeKeyPerformanceIndicatorMetricsService {

    private static final Logger LOGGER = Logger.getLogger(MpKeyPerformanceIndicatorMetricsService.class.getName());

    // Use a LazyValue here so tests that disable discovery will not fail. Normally, MetricsSupport invokes initialize.
    private final LazyValue<MpKeyPerformanceIndicatorMetrics> metrics =
            LazyValue.create(() -> new MpKeyPerformanceIndicatorMetrics(metricsNamePrefix()));

    /**
     * Creates a new instance of the service.
     */
    public MpKeyPerformanceIndicatorMetricsService() {
        super(LOGGER);
    }

    @Override
    protected void primeMetrics() {
        metrics.get();
    }

    @Override
    public Context metricsSupportHandlerContext() {
        return isExtendedKpiEnabled() ? new MpExtendedContext(metrics.get()) : new MpBasicContext(metrics.get());
    }

    @Override
    public Context jerseyContext() {
        return isExtendedKpiEnabled() ? new MpExtendedContext(metrics.get()) : new MpBasicContext(metrics.get());
    }

    /**
     * MP implementation of the KPI metrics service context without extended metrics support.
     */
    private class MpBasicContext extends SeBasicContext {

        private MpBasicContext(MpKeyPerformanceIndicatorMetrics metrics) {
            super(metrics);
        }
    }

    /**
     * MP implementation of the KPI metrics service context with extended metrics support.
     */
    private class MpExtendedContext extends SeExtendedContext {

        private final MpKeyPerformanceIndicatorMetrics metrics;

        private MpExtendedContext(MpKeyPerformanceIndicatorMetrics metrics) {
            super(metrics);
            this.metrics = metrics;
        }

        @Override
        public void requestStarted() {
            super.requestStarted();
            metrics.load.mark();
        }
    }

    class MpKeyPerformanceIndicatorMetrics extends SeKeyPerformanceIndicatorMetrics {

        static final String LOAD_NAME = "load";
        static final String QUEUED_NAME = "queued";

        private final Meter load;

        private MpKeyPerformanceIndicatorMetrics(String metricsNamePrefix) {
            super(metricsNamePrefix);

            load = isExtendedKpiEnabled() ? vendorRegistry().meter(Metadata.builder()
                    .withName(metricsNamePrefix + LOAD_NAME)
                    .withDisplayName("Incoming request load")
                    .withDescription("Measures the load of running requests")
                    .withType(MetricType.METERED)
                    .withUnit(MetricUnits.NONE)
                    .build())
                : null;

            if (isExtendedKpiEnabled()) {
                vendorRegistry().register(Metadata.builder()
                        .withName(metricsNamePrefix + QUEUED_NAME)
                        .withDisplayName("Queued requests")
                        .withDescription("Measure queued requests")
                        .withType(MetricType.METERED)
                        .withUnit(MetricUnits.NONE)
                        .build(), new QueuedRequestsMeter(totalMeter(), load));
            }
        }
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
}
