/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

package io.helidon.tracing.jaeger;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;

import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.extension.trace.propagation.JaegerPropagator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link JaegerTracerBuilder}.
 */
class JaegerTracerBuilderTest {
    private static Config config;

    @BeforeAll
    static void initClass() {
        config = Config.create().get("tracing");
    }

    @Test
    void testNoConfig() {
        assertThrows(IllegalArgumentException.class, () -> TracerBuilder.create(Config.empty()).build());
    }

    @Test
    void testBadConfig() {
        assertThrows(IllegalArgumentException.class, () -> TracerBuilder.create(config.get("jaeger-very-bad")).build());
    }

    @Test
    void testConfigDefaults() {
        TracerBuilder<?> builder = TracerBuilder.create(config.get("jaeger-defaults"));

        JaegerTracerBuilder jBuilder = builder.unwrap(JaegerTracerBuilder.class);

        assertThat(jBuilder.serviceName(), is("helidon-service"));
        assertThat("Tags", jBuilder.tags(), is(Map.of()));
        assertThat("Protocol", jBuilder.protocol(), is("http"));
        assertThat("Host", jBuilder.host(), is("localhost"));
        assertThat("Port", jBuilder.port(), is(14250));
        assertThat("Path", jBuilder.path(), nullValue());
        assertThat("Enabled", jBuilder.isEnabled(), is(true));
        assertThat("Sampler type", jBuilder.samplerType(), is(JaegerTracerBuilder.SamplerType.CONSTANT));
        assertThat("Span Processor type", jBuilder.spanProcessorType(), is(JaegerTracerBuilder.SpanProcessorType.BATCH));
        assertThat("Sampler param", jBuilder.samplerParam(), is(Integer.valueOf(1)));
        assertThat("Exporter timeout", jBuilder.exporterTimeout(), is(Duration.ofSeconds(10)));
        assertThat("Schedule delay", jBuilder.scheduleDelay(), is(Duration.ofSeconds(5)));
        assertThat("Max Queue Size", jBuilder.maxQueueSize(), is(2048));
        assertThat("Max Export Batch Size", jBuilder.maxExportBatchSize(), is(512));
    }

    @Test
    void testConfigDisabled() {
        TracerBuilder<?> builder = TracerBuilder.create(config.get("jaeger-disabled"));

        JaegerTracerBuilder jBuilder = builder.unwrap(JaegerTracerBuilder.class);

        assertThat(jBuilder.serviceName(), is("helidon-service"));

        Tracer tracer = builder.build();
        assertThat(tracer, sameInstance(Tracer.noOp()));
    }

    @Test
    void testFullHttp() {
        TracerBuilder<?> builder = TracerBuilder.create(config.get("jaeger-full-http"));

        JaegerTracerBuilder jBuilder = builder.unwrap(JaegerTracerBuilder.class);

        assertThat(jBuilder.serviceName(), is("helidon-full-http"));

        assertThat("Enabled", jBuilder.isEnabled(), is(true));
        assertThat("Protocol", jBuilder.protocol(), is("https"));
        assertThat("Host", jBuilder.host(), is("192.168.1.3"));
        assertThat("Port", jBuilder.port(), is(14240));
        assertThat("Path", jBuilder.path(), is("/api/traces/mine"));
        assertThat("Sampler type", jBuilder.samplerType(), is(JaegerTracerBuilder.SamplerType.RATIO));
        assertThat("Span Processor type", jBuilder.spanProcessorType(), is(JaegerTracerBuilder.SpanProcessorType.SIMPLE));
        assertThat("Sampler param", jBuilder.samplerParam(), is(0.5));
        assertThat("Tags", jBuilder.tags(), is(Map.of(
                "tag1", "tag1-value",
                "tag2", "tag2-value",
                "tag3", "true",
                "tag4", "false",
                "tag5", "145",
                "tag6", "741"
        )));

        List<TextMapPropagator> propagators = jBuilder.createPropagators();
        assertThat(propagators, hasSize(3));

        assertThat(propagators.get(0), instanceOf(B3Propagator.class));
        assertThat(propagators.get(1), instanceOf(JaegerPropagator.class));
        assertThat(propagators.get(2), instanceOf(W3CBaggagePropagator.class));
    }
}