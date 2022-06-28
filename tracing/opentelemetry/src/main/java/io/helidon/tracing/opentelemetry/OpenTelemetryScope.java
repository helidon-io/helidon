package io.helidon.tracing.opentelemetry;

import io.helidon.tracing.Scope;

class OpenTelemetryScope implements Scope {
    private final io.opentelemetry.context.Scope delegate;

    OpenTelemetryScope(io.opentelemetry.context.Scope scope) {
        delegate = scope;
    }

    @Override
    public void close() {
        delegate.close();
    }
}
