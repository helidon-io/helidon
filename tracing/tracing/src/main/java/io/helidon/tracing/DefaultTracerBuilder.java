/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import io.helidon.config.Config;

class DefaultTracerBuilder implements TracerBuilder<DefaultTracerBuilder> {
    private final TracerBuilder<?> delegate;

    DefaultTracerBuilder(TracerBuilder<?> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Tracer build() {
        return delegate.build();
    }

    @Override
    public DefaultTracerBuilder serviceName(String name) {
        delegate.serviceName(name);
        return this;
    }

    @Override
    public DefaultTracerBuilder collectorProtocol(String protocol) {
        delegate.collectorProtocol(protocol);
        return this;
    }

    @Override
    public DefaultTracerBuilder collectorPort(int port) {
        delegate.collectorPort(port);
        return this;
    }

    @Override
    public DefaultTracerBuilder collectorHost(String host) {
        delegate.collectorHost(host);
        return this;
    }

    @Override
    public DefaultTracerBuilder collectorPath(String path) {
        delegate.collectorPath(path);
        return this;
    }

    @Override
    public DefaultTracerBuilder addTracerTag(String key, String value) {
        delegate.addTracerTag(key, value);
        return this;
    }

    @Override
    public DefaultTracerBuilder addTracerTag(String key, Number value) {
        delegate.addTracerTag(key, value);
        return this;
    }

    @Override
    public DefaultTracerBuilder addTracerTag(String key, boolean value) {
        delegate.addTracerTag(key, value);
        return this;
    }

    @Override
    public DefaultTracerBuilder config(Config config) {
        delegate.config(config);
        return this;
    }

    @Override
    public DefaultTracerBuilder enabled(boolean enabled) {
        delegate.enabled(enabled);
        return this;
    }

    @Override
    public DefaultTracerBuilder registerGlobal(boolean global) {
        delegate.registerGlobal(global);
        return this;
    }

    @Override
    public <B> B unwrap(Class<B> builderClass) {
        return delegate.unwrap(builderClass);
    }
}
