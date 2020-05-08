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

package io.helidon.microprofile.opentracing.tck;

import java.net.URI;

import io.helidon.config.Config;
import io.helidon.tracing.TracerBuilder;

import io.opentracing.Tracer;
import io.opentracing.mock.MockTracer;

public final class OpentracingJavaMockTracerBuilder implements TracerBuilder<OpentracingJavaMockTracerBuilder> {

    private OpentracingJavaMockTracerBuilder() {
    }

    public static TracerBuilder<OpentracingJavaMockTracerBuilder> create() {
        return new OpentracingJavaMockTracerBuilder();
    }

    @Override
    public OpentracingJavaMockTracerBuilder serviceName(String name) {
        return this;
    }

    @Override
    public OpentracingJavaMockTracerBuilder collectorUri(URI uri) {
        return this;
    }

    @Override
    public OpentracingJavaMockTracerBuilder collectorProtocol(String protocol) {
        return this;
    }

    @Override
    public OpentracingJavaMockTracerBuilder collectorHost(String host) {
        return this;
    }

    @Override
    public OpentracingJavaMockTracerBuilder collectorPort(int port) {
        return this;
    }

    @Override
    public OpentracingJavaMockTracerBuilder collectorPath(String path) {
        return this;
    }

    @Override
    public OpentracingJavaMockTracerBuilder enabled(boolean enabled) {
        return this;
    }

    @Override
    public OpentracingJavaMockTracerBuilder registerGlobal(boolean global) {
        return this;
    }

    @Override
    public OpentracingJavaMockTracerBuilder addTracerTag(String key, String value) {
        return this;
    }

    @Override
    public OpentracingJavaMockTracerBuilder addTracerTag(String key, Number value) {
        return this;
    }

    @Override
    public OpentracingJavaMockTracerBuilder addTracerTag(String key, boolean value) {
        return this;
    }

    @Override
    public OpentracingJavaMockTracerBuilder config(Config config) {
        return this;
    }

    /**
     * Builds a mock {@link Tracer}.
     *
     * @return the tracer
     */
    @Override
    public Tracer build() {
        return new MockTracer();
    }
}
