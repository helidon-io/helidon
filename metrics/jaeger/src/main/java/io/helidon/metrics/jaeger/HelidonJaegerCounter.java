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

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;

import static io.helidon.metrics.jaeger.HelidonJaegerMetricsFactory.tags;

class HelidonJaegerCounter implements io.jaegertracing.internal.metrics.Counter {

    private final Counter counter;

    static HelidonJaegerCounter create(MetricRegistry metricRegistry, String name, Map<String, String> tags) {
        return new HelidonJaegerCounter(metricRegistry, name, tags);
    }

    private HelidonJaegerCounter(MetricRegistry metricRegistry, String name, Map<String, String> tags) {
        this.counter = metricRegistry.counter(
                Metadata.builder()
                    .withName(name)
                    .withDisplayName("Jaeger tracing " + name)
                    .withDescription("Jaeger tracing counter for " + name)
                    .withType(MetricType.COUNTER)
                    .withUnit(MetricUnits.NONE)
                    .build(),
                tags(tags));
    }

    @Override
    public void inc(long delta) {
        counter.inc(delta);
    }
}
