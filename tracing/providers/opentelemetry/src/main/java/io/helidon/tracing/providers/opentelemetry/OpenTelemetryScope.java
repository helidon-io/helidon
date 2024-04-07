/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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
package io.helidon.tracing.providers.opentelemetry;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.tracing.Scope;
import io.helidon.tracing.SpanLifeCycleListener;

class OpenTelemetryScope implements Scope {
    private final OpenTelemetrySpan span;
    private final io.opentelemetry.context.Scope delegate;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final List<SpanLifeCycleListener> spanLifeCycleListeners;
    private Limited limited;

    OpenTelemetryScope(OpenTelemetrySpan span,
                       io.opentelemetry.context.Scope scope,
                       List<SpanLifeCycleListener> spanLifeCycleListeners) {
        this.span = span;
        delegate = scope;
        this.spanLifeCycleListeners = spanLifeCycleListeners;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true) && delegate != null) {
            delegate.close();
            spanLifeCycleListeners.forEach(listener -> listener.afterClose(span.limited(), limited()));
        }
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    Limited limited() {
        if (limited !=  null) {
            return limited;
        }
        if (spanLifeCycleListeners.isEmpty()) {
            return null;
        }
        limited = new Limited(this);
        return limited;
    }

    private record Limited(OpenTelemetryScope delegate) implements Scope {

        @Override
            public void close() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isClosed() {
                return delegate.isClosed();
            }
        }
}
