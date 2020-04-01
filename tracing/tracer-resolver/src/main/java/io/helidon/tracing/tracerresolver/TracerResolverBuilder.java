/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.tracing.tracerresolver;

import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.tracing.TracerBuilder;

import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.noop.NoopTracerFactory;
import io.opentracing.util.GlobalTracer;

class TracerResolverBuilder implements TracerBuilder<TracerResolverBuilder> {
    private static final Logger LOGGER = Logger.getLogger(TracerResolverBuilder.class.getName());

    private String helidonServiceName;
    private boolean enabled = true;
    private boolean registerGlobal;

    @Override
    public TracerResolverBuilder serviceName(String name) {
        this.helidonServiceName = name;
        return this;
    }

    @Override
    public TracerResolverBuilder collectorProtocol(String protocol) {
        return this;
    }

    @Override
    public TracerResolverBuilder collectorPort(int port) {
        return this;
    }

    @Override
    public TracerResolverBuilder collectorHost(String host) {
        return this;
    }

    @Override
    public TracerResolverBuilder collectorPath(String path) {
        return this;
    }

    @Override
    public TracerResolverBuilder addTracerTag(String key, String value) {
        return this;
    }

    @Override
    public TracerResolverBuilder addTracerTag(String key, Number value) {
        return this;
    }

    @Override
    public TracerResolverBuilder addTracerTag(String key, boolean value) {
        return this;
    }

    @Override
    public TracerResolverBuilder config(Config config) {
        config.get("enabled").asBoolean().ifPresent(this::enabled);
        config.get("service").asString().ifPresent(this::serviceName);
        config.get("global").asBoolean().ifPresent(this::registerGlobal);

        return this;
    }

    @Override
    public TracerResolverBuilder enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    @Override
    public TracerResolverBuilder registerGlobal(boolean global) {
        this.registerGlobal = global;
        return this;
    }

    @Override
    public Tracer build() {
        Tracer tracer;
        if (enabled) {
            tracer = TracerResolver.resolveTracer();
            if (null == tracer) {
                tracer = NoopTracerFactory.create();
                LOGGER.info("TracerResolver not configured, tracing is disabled");
            } else {
                LOGGER.info("Using resolved tracer (all Helidon specific configuration options ignored): " + tracer);
            }
        } else {
            LOGGER.info("TracerResolver tracer is explicitly disabled for " + helidonServiceName + ".");
            tracer = NoopTracerFactory.create();
        }

        if (registerGlobal) {
            GlobalTracer.registerIfAbsent(tracer);
        }

        return tracer;
    }
}
