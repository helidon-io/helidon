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

package io.helidon.docs.se;
// tag::snippet_1_imports[]
import java.util.Map;
import io.helidon.telemetry.otelconfig.HelidonOpenTelemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
// end::snippet_1_imports[]

class TelemetrySnippets {

    // Example if user code creates an OpenTelemetry explicitly and then wants to make it known throughout OTel and Helidon.
    void snippet_1() {

        // tag::snippet_1[]

        // Application code using the OpenTelemetry API or the Helidon OpenTelemetry API or both.
        OpenTelemetry customOpenTelemetry = prepareOpenTelemetry();

        // App code to build any tags to be applied to every span.
        Map<String, String> tags = prepareTags();

        HelidonOpenTelemetry.global(customOpenTelemetry,
                                    "your-service-name",
                                    tags);

        // end::snippet_1[]
    }

    void snippet_2() {

        // Example of user code using OpenTelemetry autoconfig.

        // tag::snippet_2[]
        OpenTelemetry otel = AutoConfiguredOpenTelemetrySdk.builder()
                .setResultAsGlobal()
                .build()
                .getOpenTelemetrySdk();

        // end::snippet_2[]
    }

    void snippet_3() {

        // Example of user code using OpenTelemetry autoconfig and setting the global OpenTelemetry instance.

        // tag::snippet_3[]
        OpenTelemetry otel = AutoConfiguredOpenTelemetrySdk.builder()
                .build()
                .getOpenTelemetrySdk();

        // end::snippet_3[]

        // tag::snippet_3a[]
        var globalOtel = GlobalOpenTelemetry.get();
        // end::snippet_3a[]

        // tag::snippet_3b[]
        var meter = otel.getMeter("helidon-otel-example-app");
        var tracer = otel.getTracer("helidon-otel-example-app");
        // end::snippet_3b[]

        // tag::snippet_3c[]
        var myCounter = meter.counterBuilder("my-counter")
                .setDescription("An example counter")
                .build();
        // ...
        myCounter.add(1L);
        // end::snippet_3c[]

        // tag::snippet_3d[]
        var mySpan = tracer.spanBuilder("my-span")
                .setSpanKind(SpanKind.SERVER)
                .startSpan();

        try (Scope ignored = mySpan.makeCurrent()) {

            // Do work worth tracing.

            mySpan.setStatus(StatusCode.OK);
        } catch (Throwable t) {
            mySpan.setStatus(StatusCode.ERROR);
        } finally {
            mySpan.end();
        }

        // end::snippet_3d[]


    }



    private OpenTelemetry prepareOpenTelemetry() {
        return HelidonOpenTelemetry.builder().build().openTelemetry();
    }

    private Map<String, String> prepareTags() {
        return Map.of();
    }
}
