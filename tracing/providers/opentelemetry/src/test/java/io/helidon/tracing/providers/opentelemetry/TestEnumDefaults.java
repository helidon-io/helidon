/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

class TestEnumDefaults {

    @Test
    void checkPropagationFormatDefault() {

        OpenTelemetryTracerBuilder.PropagationFormat[] defaultsFromString =
                // These are the defaults documented in the "Properties for context propagation" section here:
                // https://opentelemetry.io/docs/languages/java/configuration/#properties-general
                {OpenTelemetryTracerBuilder.PropagationFormat.TRACE_CONTEXT,
                        OpenTelemetryTracerBuilder.PropagationFormat.BAGGAGE};

        assertThat("Propagation format defaults",
                   OpenTelemetryTracerBuilder.PropagationFormat.DEFAULT,
                   hasItems(defaultsFromString));

    }

    @Test
    void checkExporterProtocolDefault() {
        OpenTelemetryTracerBuilder.ExporterProtocol defaultFromString = OpenTelemetryTracerBuilder.ExporterProtocol.create(
                OpenTelemetryTracerBuilder.ExporterProtocol.DEFAULT_STRING);

        assertThat("Exporter protocol default",
                   defaultFromString,
                   equalTo(OpenTelemetryTracerBuilder.ExporterProtocol.GRPC));
    }

    @Test
    void checkSamplerTypeDefault() {
        OpenTelemetryTracerBuilder.SamplerType defaultFromString = OpenTelemetryTracerBuilder.SamplerType.create(
                OpenTelemetryTracerBuilder.SamplerType.DEFAULT_STRING);

        assertThat("Sampler type default",
                   defaultFromString,
                   equalTo(OpenTelemetryTracerBuilder.SamplerType.PARENT_BASED_ALWAYS_ON));
    }

    @Test
    void checkSpanProcessorTypeDefault() {
        OpenTelemetryTracerBuilder.SpanProcessorType defaultFromString = OpenTelemetryTracerBuilder.SpanProcessorType.create(
                OpenTelemetryTracerBuilder.SpanProcessorType.DEFAULT_STRING);

        assertThat("Span processor type default",
                   defaultFromString,
                   equalTo(OpenTelemetryTracerBuilder.SpanProcessorType.BATCH));
    }
}
