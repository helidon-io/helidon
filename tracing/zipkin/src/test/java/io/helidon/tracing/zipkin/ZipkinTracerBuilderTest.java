/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.tracing.zipkin;

import java.util.List;

import io.helidon.config.Config;
import io.helidon.tracing.Tag;
import io.helidon.tracing.TracerBuilder;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link ZipkinTracerBuilder}.
 */
class ZipkinTracerBuilderTest {
    private static Config config;

    @BeforeAll
    static void initClass() {
        config = Config.create();
    }

    @Test
    void testConfigDefaults() {
        TracerBuilder<?> builder = TracerBuilder.create(config.get("tracing.zipkin-defaults"));

        ZipkinTracerBuilder zBuilder = (ZipkinTracerBuilder) builder;

        assertThat(zBuilder.tags(), is(List.of()));
        assertThat(zBuilder.serviceName(), is("helidon-service"));
        assertThat(zBuilder.protocol(), is(ZipkinTracerBuilder.DEFAULT_PROTOCOL));
        assertThat(zBuilder.host(), is(ZipkinTracerBuilder.DEFAULT_ZIPKIN_HOST));
        assertThat(zBuilder.port(), is(ZipkinTracerBuilder.DEFAULT_ZIPKIN_PORT));
        assertThat(zBuilder.path(), nullValue());
        assertThat(zBuilder.version(), is(ZipkinTracerBuilder.DEFAULT_VERSION));
        assertThat(zBuilder.sender(), nullValue());
        assertThat(zBuilder.userInfo(), nullValue());
        assertThat(zBuilder.isEnabled(), is(ZipkinTracerBuilder.DEFAULT_ENABLED));
    }

    @Test
    void testConfigDisabled() {
        TracerBuilder<?> builder = TracerBuilder.create(config.get("tracing.zipkin-disabled"));

        ZipkinTracerBuilder zBuilder = (ZipkinTracerBuilder) builder;

        assertThat(zBuilder.tags(), is(List.of()));
        assertThat(zBuilder.serviceName(), is("helidon-service"));
        assertThat(zBuilder.protocol(), is(ZipkinTracerBuilder.DEFAULT_PROTOCOL));
        assertThat(zBuilder.host(), is(ZipkinTracerBuilder.DEFAULT_ZIPKIN_HOST));
        assertThat(zBuilder.port(), is(ZipkinTracerBuilder.DEFAULT_ZIPKIN_PORT));
        assertThat(zBuilder.path(), nullValue());
        assertThat(zBuilder.version(), is(ZipkinTracerBuilder.DEFAULT_VERSION));
        assertThat(zBuilder.sender(), nullValue());
        assertThat(zBuilder.userInfo(), nullValue());
        assertThat(zBuilder.isEnabled(), is(false));

        Tracer tracer = zBuilder.build();
        assertThat(tracer, instanceOf(NoopTracer.class));
    }

    @Test
    void testConfigDisabledNoService() {
        TracerBuilder<?> builder = TracerBuilder.create(config.get("tracing.zipkin-disabled-no-service"));

        ZipkinTracerBuilder zBuilder = (ZipkinTracerBuilder) builder;

        assertThat(zBuilder.tags(), is(List.of()));
        assertThat(zBuilder.serviceName(), nullValue());
        assertThat(zBuilder.protocol(), is(ZipkinTracerBuilder.DEFAULT_PROTOCOL));
        assertThat(zBuilder.host(), is(ZipkinTracerBuilder.DEFAULT_ZIPKIN_HOST));
        assertThat(zBuilder.port(), is(ZipkinTracerBuilder.DEFAULT_ZIPKIN_PORT));
        assertThat(zBuilder.path(), nullValue());
        assertThat(zBuilder.version(), is(ZipkinTracerBuilder.DEFAULT_VERSION));
        assertThat(zBuilder.sender(), nullValue());
        assertThat(zBuilder.userInfo(), nullValue());
        assertThat(zBuilder.isEnabled(), is(false));

        Tracer tracer = zBuilder.build();
        assertThat(tracer, instanceOf(NoopTracer.class));
    }

    @Test
    void testConfigBad() {
        assertThrows(IllegalArgumentException.class, () -> TracerBuilder.create(config.get("tracing.zipkin-bad")));
        assertThrows(IllegalArgumentException.class, () -> TracerBuilder.create(config.get("tracing.zipkin-very-bad")).build());
    }

    @Test
    void testConfigCustomized() {
        TracerBuilder<?> builder = TracerBuilder.create(config.get("tracing.zipkin-full"));

        ZipkinTracerBuilder zBuilder = (ZipkinTracerBuilder) builder;

        assertThat(zBuilder.serviceName(), is("helidon-service"));
        assertThat(zBuilder.protocol(), is("https"));
        assertThat(zBuilder.host(), is("192.168.1.1"));
        assertThat(zBuilder.port(), is(9987));
        assertThat(zBuilder.path(), is("/api/v47"));
        assertThat(zBuilder.version(), is(ZipkinTracerBuilder.Version.V1));
        assertThat(zBuilder.sender(), nullValue());
        assertThat(zBuilder.userInfo(), nullValue());
        assertThat(zBuilder.isEnabled(), is(ZipkinTracerBuilder.DEFAULT_ENABLED));

        assertThat(zBuilder.tags(), hasItems(
                Tag.create("tag1", "tag1-value"),
                Tag.create("tag2", "tag2-value"),
                Tag.create("tag3", true),
                Tag.create("tag4", false),
                Tag.create("tag5", 145),
                Tag.create("tag6", 741)
        ));
    }

    @Test
    void testActiveSpan() {
        Tracer tracer = TracerBuilder.create("unit-test-active-span")
                .collectorPort(49087)
                .build();
        Span span = tracer.buildSpan("unit-operation")
                .start();
        tracer.activateSpan(span);

        span.finish();
    }
}