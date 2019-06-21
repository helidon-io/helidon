/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.config.Config;

import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;

/**
 * No op tracer builder - used when there is no tracer service available.
 */
final class NoOpBuilder implements TracerBuilder<NoOpBuilder> {
    private NoOpBuilder() {
    }

    static TracerBuilder<?> create() {
        return new NoOpBuilder();
    }
    @Override
    public NoOpBuilder serviceName(String name) {
        return this;
    }

    @Override
    public NoOpBuilder collectorUri(URI uri) {
        return this;
    }

    @Override
    public NoOpBuilder collectorProtocol(String protocol) {
        return this;
    }

    @Override
    public NoOpBuilder collectorPort(int port) {
        return this;
    }

    @Override
    public NoOpBuilder collectorHost(String host) {
        return this;
    }

    @Override
    public NoOpBuilder collectorPath(String path) {
        return this;
    }

    @Override
    public NoOpBuilder addTracerTag(String key, String value) {
        return this;
    }

    @Override
    public NoOpBuilder config(Config config) {
        return this;
    }

    @Override
    public NoOpBuilder enabled(boolean enabled) {
        return this;
    }

    @Override
    public NoOpBuilder addTracerTag(String key, Number value) {
        return this;
    }

    @Override
    public NoOpBuilder addTracerTag(String key, boolean value) {
        return this;
    }

    @Override
    public NoOpBuilder registerGlobal(boolean global) {
        return this;
    }

    @Override
    public Tracer build() {
        return NoopTracerFactory.create();
    }
}
