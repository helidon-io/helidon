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
 *
 */
package io.helidon.microprofile.metrics;

import io.helidon.config.Config;
import io.helidon.metrics.MetricsSupport;

/**
 * CDI-specific implementation of {@code MetricsSupport} for visibility of {@code inflightRequests} metric from elsewhere in
 * this package.
 */
class MetricsSupportForCdi extends MetricsSupport {

    static MetricsSupportForCdi createMetricsSupport(Config config) {
        MetricsSupportBuilderForCdi builder = new MetricsSupportBuilderForCdi();
        // Disable the SE-style metrics update; instead use a filter which better measures async endpoints)\.
        builder.updateInflightFromHandler(false);
        builder.config(config);

        return builder.build();
    }

    MetricsSupportForCdi(Builder builder) {
        super(builder);
    }

    @Override
    protected org.eclipse.microprofile.metrics.ConcurrentGauge inflightRequests() {
        return super.inflightRequests();
    }

    /**
     * CDI-specific implementation of {@code MetricsSupport.Builder} to disable the SE-style handler-based update of the
     * inflight requests update.
     */
    static class MetricsSupportBuilderForCdi extends Builder {

        @Override
        protected Builder updateInflightFromHandler(boolean updateInflightFromHandler) {
            return super.updateInflightFromHandler(updateInflightFromHandler);
        }

        @Override
        public MetricsSupportForCdi build() {
            return super.build(MetricsSupportForCdi::new);
        }
    }
}
