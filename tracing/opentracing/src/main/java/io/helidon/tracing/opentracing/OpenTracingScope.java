package io.helidon.tracing.opentracing;

import io.helidon.tracing.Scope;

class OpenTracingScope implements Scope {
    private final io.opentracing.Scope scope;

    OpenTracingScope(io.opentracing.Scope scope) {
        this.scope = scope;
    }

    @Override
    public void close() throws Exception {
        scope.close();
    }
}
