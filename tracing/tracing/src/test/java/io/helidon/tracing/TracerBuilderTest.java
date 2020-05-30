/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.tracing;

import java.net.URI;
import java.util.LinkedList;
import java.util.List;

import io.helidon.config.Config;

import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracer;
import io.opentracing.noop.NoopTracerFactory;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link TracerBuilder}.
 */
class TracerBuilderTest {
    @Test
    void testNoOpTracerBuilder() {
        Tracer tracer = TracerBuilder.create("my-service")
                .serviceName("his-service")
                .collectorHost("host")
                .collectorPath("/path")
                .collectorPort(1414)
                .collectorProtocol("https")
                .collectorUri(URI.create("https://host:1414/path"))
                .config(Config.create())
                .addTracerTag("key1", "value1")
                .addTracerTag("key2", 49)
                .addTracerTag("key3", true)
                .enabled(true)
                .registerGlobal(true)
                .build();

        assertThat(tracer, notNullValue());
        assertThat(tracer, instanceOf(NoopTracer.class));
    }

    @Test
    void testNoOpTracerBuilderFromConfig() {
        Tracer tracer = TracerBuilder.create(Config.create())
                .build();

        assertThat(tracer, notNullValue());
        assertThat(tracer, instanceOf(NoopTracer.class));
    }

    @Test
    void testCorrectReturnType() {
        Tracer tracer = TracerBuilder.create(Config.empty())
                .build();

        assertThat(tracer, instanceOf(NoopTracer.class));
    }


    @Test
    void testDefaultMethods() {
        MyTracerBuilder myBuilder = new MyTracerBuilder();

        TracerBuilder<MyTracerBuilder> builder = myBuilder;

        Tracer tracer = builder
                .serviceName("service-name")
                .collectorHost("hosts")
                .collectorPath("/paths")
                .collectorPort(1415)
                .collectorProtocol("http")
                .collectorUri(URI.create("https://host:1414/path"))
                .config(Config.create())
                .addTracerTag("key1", "value1")
                .addTracerTag("key2", 49)
                .addTracerTag("key3", true)
                .enabled(true)
                .registerGlobal(true)
                // make sure we do not lose the builder type
                .first("first")
                .second("second")
                .build();

        assertThat(tracer, notNullValue());
        assertThat(tracer, instanceOf(NoopTracer.class));

        // now test the default methods
        assertThat(tracer, sameInstance(myBuilder.tracer));

        // and URI
        assertThat(myBuilder.protocol, is("https"));
        assertThat(myBuilder.host, is("host"));
        assertThat(myBuilder.path, is("/path"));
    }

    private static class MyTracerBuilder implements TracerBuilder<MyTracerBuilder> {
        private final List<Tag<?>> tags = new LinkedList<>();
        private String name;
        private String protocol;
        private int port;
        private String host;
        private String path;
        private Config config;
        private boolean enabled;
        private boolean global;
        private Tracer tracer;

        @Override
        public MyTracerBuilder serviceName(String name) {
            this.name = name;
            return this;
        }

        @Override
        public MyTracerBuilder collectorProtocol(String protocol) {
            this.protocol = protocol;
            return this;
        }

        @Override
        public MyTracerBuilder collectorPort(int port) {
            this.port = port;
            return this;
        }

        @Override
        public MyTracerBuilder collectorHost(String host) {
            this.host = host;
            return this;
        }

        @Override
        public MyTracerBuilder collectorPath(String path) {
            this.path = path;
            return this;
        }

        @Override
        public MyTracerBuilder addTracerTag(String key, String value) {
            tags.add(Tag.create(key, value));
            return this;
        }

        @Override
        public MyTracerBuilder addTracerTag(String key, Number value) {
            tags.add(Tag.create(key, value));
            return this;
        }

        @Override
        public MyTracerBuilder addTracerTag(String key, boolean value) {
            tags.add(Tag.create(key, value));
            return this;
        }

        @Override
        public MyTracerBuilder config(Config config) {
            this.config = config;
            return this;
        }

        @Override
        public MyTracerBuilder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        @Override
        public MyTracerBuilder registerGlobal(boolean global) {
            this.global = global;
            return this;
        }

        public MyTracerBuilder first(String first) {
            return this;
        }

        public MyTracerBuilder second(String second) {
            return this;
        }

        @Override
        public Tracer build() {
            tracer = NoopTracerFactory.create();
            return tracer;
        }
    }
}