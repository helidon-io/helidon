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
package io.helidon.metrics.jaeger;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;

import static io.helidon.metrics.jaeger.HelidonJaegerMetricsFactory.tags;

class HelidonJaegerGauge implements io.jaegertracing.internal.metrics.Gauge {

    private static final Logger LOGGER = Logger.getLogger(HelidonJaegerGauge.class.getName());

    private GaugeImpl gauge = new GaugeImpl();

    static HelidonJaegerGauge create(MetricRegistry metricRegistry, String name, Map<String, String> tags) {
        return new HelidonJaegerGauge(metricRegistry, name, tags);
    }

    private HelidonJaegerGauge(MetricRegistry metricRegistry, String name, Map<String, String> tags) {

        MetricID metricID = new MetricID(name, tags(tags));
        Metric metric = metricRegistry.getMetrics().get(metricID);

        if (metric == null) {
            metricRegistry.register(
                    Metadata.builder()
                            .withName(name)
                            .withDisplayName("Jaeger tracing " + name)
                            .withDescription("Jaeger tracing gauge for " + name)
                            .withType(MetricType.GAUGE)
                            .withUnit(MetricUnits.NONE)
//                            .reusable(true)
                            .build(),
                    new GaugeImpl(),
                    tags(tags));
        } else if (metric instanceof GaugeImpl) {
            LOGGER.log(Level.FINE, String.format(
                    "Creating new Jaeger gauge name=%s, tags=%s; unexpectedly found it already in registry; adopting it",
                    name, tags));
            gauge = (GaugeImpl) metric;
        } else {
            LOGGER.log(Level.WARNING, String.format(
                    "Attempt to add Jaeger tracing gauge name=%s,tags=%s when metric of type %s already registered",
                    name, tags, MetricType.from(metric.getClass())));
        }
    }

    @Override
    public void update(long amount) {
        gauge.setValue(amount);
    }

    private static class GaugeImpl implements Gauge<Long> {

        private static final Long DEFAULT_VALUE = 0L;

        private Long value = DEFAULT_VALUE;

        @Override
        public Long getValue() {
            return value;
        }

        void setValue(Long value) {
            this.value = value != null ? value : DEFAULT_VALUE;
        }
    }
}
