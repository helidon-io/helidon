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
package io.helidon.tracing.providers.opentelemetry;

import java.util.Map;

import io.helidon.tracing.Tracer;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;

class TestGlobalTracerAssignment {

    @Test
    void assignGlobalTracer() {
        OpenTelemetry openTelemetry = OpenTelemetry.noop();
        io.opentelemetry.api.trace.Tracer otelTracer = openTelemetry.getTracer("testTracer");

        Tracer.global(HelidonOpenTelemetry.create(openTelemetry, otelTracer, Map.of()));
        // A bug in the Helidon OTel tracer provider caused an exception to be thrown even when the caller provides the correct
        // type to Tracer.global(tracer). If we get here, the bug fix is working.
    }
}
