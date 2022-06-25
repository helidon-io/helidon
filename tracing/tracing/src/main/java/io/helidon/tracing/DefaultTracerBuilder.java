package io.helidon.tracing;

import io.helidon.config.Config;

public class DefaultTracerBuilder implements TracerBuilder<DefaultTracerBuilder> {
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
}
