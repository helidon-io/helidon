/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.tracing.providers.jaeger;

import io.helidon.common.LazyValue;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider;
import io.opentelemetry.sdk.trace.export.SpanExporter;

public class TestSpanExporterProvider implements ConfigurableSpanExporterProvider {

    private static final LazyValue<TestSpanExporter> SPAN_EXPORTER = LazyValue.create(TestSpanExporter::new);

    public TestSpanExporterProvider() {
        System.err.println("provider ctor");
    }

    @Override
    public SpanExporter createExporter(ConfigProperties configProperties) {
        return SPAN_EXPORTER.get();
    }

    @Override
    public String getName() {
        return "in-memory";
    }

    static TestSpanExporter exporter() {
        if (SPAN_EXPORTER.isLoaded()) {
            return SPAN_EXPORTER.get();
        }
        throw new IllegalStateException("Attempt to retrieve TestSpanExporter before it has been created");
    }
}
