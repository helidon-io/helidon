/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.declarative.tests.tracing;

import java.util.Map;
import java.util.function.Supplier;

import io.helidon.service.registry.Service;
import io.helidon.tracing.Tracer;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

@Service.Singleton
public class TestTracerFactory implements Supplier<Tracer> {
    private final TestSpanExporter exporter = new TestSpanExporter();
    private final OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(SdkTracerProvider.builder()
                                       .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                                       .build())
            .build();

    @Override
    public Tracer get() {
        return io.helidon.tracing.providers.opentelemetry.HelidonOpenTelemetry.create(openTelemetry,
                                                                                      openTelemetry.getTracer("unit-test"),
                                                                                      Map.of());
    }

    @Service.PreDestroy
    public void shutdown() {
        openTelemetry.close();
        exporter.close();
    }

    TestSpanExporter exporter() {
        return exporter;
    }
}
