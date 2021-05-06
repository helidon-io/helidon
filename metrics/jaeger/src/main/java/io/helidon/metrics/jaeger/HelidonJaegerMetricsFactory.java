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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.helidon.metrics.RegistryFactory;

import io.jaegertracing.internal.metrics.Counter;
import io.jaegertracing.internal.metrics.Gauge;
import io.jaegertracing.internal.metrics.Timer;
import io.jaegertracing.spi.MetricsFactory;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;

/**
 * Exposes Jaeger tracing metrics as Helidon vendor metrics.
 */
public class HelidonJaegerMetricsFactory implements MetricsFactory {

    private final MetricRegistry vendorRegistry = RegistryFactory.getInstance()
            .getRegistry(MetricRegistry.Type.VENDOR);

    @Override
    public Counter createCounter(String name, Map<String, String> jaegerTags) {
        return HelidonJaegerCounter.create(vendorRegistry, name, jaegerTags);
    }

    @Override
    public Timer createTimer(String name, Map<String, String> jaegerTags) {
        return HelidonJaegerTimer.create(vendorRegistry, name, jaegerTags);
    }

    @Override
    public Gauge createGauge(String name, Map<String, String> jaegerTags) {
        return HelidonJaegerGauge.create(vendorRegistry, name, jaegerTags);
    }


    static Tag[] tags(Map<String, String> jaegerTags) {
        if (jaegerTags == null) {
            return new Tag[0];
        }
        List<Tag> result = new ArrayList<>(jaegerTags.size());
        jaegerTags.forEach((name, value) -> result.add(new Tag(name, value)));
        return result.toArray(new Tag[0]);
    }
}
