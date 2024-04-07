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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.context.Contexts;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.SpanLifeCycleListener;
import io.helidon.tracing.WritableBaggage;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;

class OpenTelemetrySpan implements Span {
    private final io.opentelemetry.api.trace.Span delegate;
    private final Baggage baggage;
    private final List<SpanLifeCycleListener> spanLifeCycleListeners;
    private Limited limited;

    OpenTelemetrySpan(io.opentelemetry.api.trace.Span span, List<SpanLifeCycleListener> spanLifeCycleListeners) {
        this(span, new MutableOpenTelemetryBaggage(), spanLifeCycleListeners);
    }

    OpenTelemetrySpan(io.opentelemetry.api.trace.Span span, Baggage baggage, List<SpanLifeCycleListener> spanLifeCycleListeners) {
        delegate = span;
        this.baggage = baggage;
        this.spanLifeCycleListeners = spanLifeCycleListeners;
    }

    @Override
    public Span tag(String key, String value) {
        delegate.setAttribute(key, value);
        return this;
    }

    @Override
    public Span tag(String key, Boolean value) {
        delegate.setAttribute(key, value);
        return this;
    }

    @Override
    public Span tag(String key, Number value) {
        if (value instanceof Double || value instanceof Float) {
            delegate.setAttribute(key, value.doubleValue());
        } else {
            delegate.setAttribute(key, value.longValue());
        }
        return this;
    }

    @Override
    public void status(Status status) {
        switch (status) {
        case OK -> delegate.setStatus(StatusCode.OK);
        case ERROR -> delegate.setStatus(StatusCode.ERROR);
        default -> {
        }
        }
    }

    @Override
    public SpanContext context() {
        return new OpenTelemetrySpanContext(otelContextWithSpanAndBaggage());
    }

    @Override
    public void addEvent(String name, Map<String, ?> attributes) {
        delegate.addEvent(name, toAttributes(attributes));
    }

    @Override
    public void end() {
        delegate.end();
        spanLifeCycleListeners.forEach(listener -> listener.afterEnd(limited()));
    }

    @Override
    public void end(Throwable t) {
        delegate.recordException(t);
        delegate.setStatus(StatusCode.ERROR);
        delegate.end();
        spanLifeCycleListeners.forEach(listener -> listener.afterEnd(limited(), t));
    }

    @Override
    public Scope activate() {
        io.opentelemetry.context.Scope scope = otelContextWithSpanAndBaggage().makeCurrent();
        var result = new OpenTelemetryScope(this, scope, spanLifeCycleListeners);
        UnsupportedOperationException ex = new UnsupportedOperationException();
        spanLifeCycleListeners.forEach(listener -> {
            try {
                listener.afterActivate(limited(), result.limited());
            } catch (Throwable t) {
                ex.addSuppressed(t);
            }
        });
        if (ex.getSuppressed().length > 0) {
            // Force the scope closed, because otherwise we'll pollute the context with a span
            // and baggage that should not be there. Even though we are about to throw an exception,
            // the caller might catch it so we need to clean up the context.
            scope.close();
            throw ex;
        }
        return result;
    }

    @Override
    public Span baggage(String key, String value) {
        if (baggage instanceof WritableBaggage writableBaggage) {
            writableBaggage.set(key, value);
        } else {
            throw new UnsupportedOperationException(
                    "Attempt to set baggage on a span with read-only baggage (perhaps from context");
        }
        return this;
    }

    @Override
    public Optional<String> baggage(String key) {
        return Optional.ofNullable(baggage.getEntryValue(key));
    }

    @Override
    public WritableBaggage baggage() {
        return baggage instanceof WritableBaggage writableBaggage ? writableBaggage : writableBaggage(baggage);
    }

    @Override
    public <T> T unwrap(Class<T> spanClass) {
        if (spanClass.isInstance(delegate)) {
            return spanClass.cast(delegate);
        }
        if (spanClass.isInstance(this)) {
            return spanClass.cast(this);
        }
        throw new IllegalArgumentException("Cannot provide an instance of " + spanClass.getName()
                                                   + ", telemetry span is: " + delegate.getClass().getName());
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

    // Check if OTEL Context is already available in Global Helidon Context.
    // If not – use Current context.
    private static Context getContext() {
        return Contexts.context()
                .flatMap(ctx -> ctx.get(Context.class))
                .orElseGet(Context::current);
    }

    /**
     * Writable wrapper around non-writable baggage.
     *
     * <p>
     *     Used only if the baggage in the current context is not our writable variety when it is extracted and attached to
     *     the current span. This should be very rare.
     * </p>
     *
     * @param baggage non-writable baggage to wrap
     * @return wrapper
     */
    private static WritableBaggage writableBaggage(Baggage baggage) {
        return new WritableBaggage() {
            @Override
            public WritableBaggage set(String key, String value) {
                throw new UnsupportedOperationException("Attempt to modify read-only baggage");
            }

            @Override
            public Optional<String> get(String key) {
                return Optional.ofNullable(baggage.getEntryValue(key));
            }

            @Override
            public Set<String> keys() {
                return baggage.asMap().keySet();
            }

            @Override
            public boolean containsKey(String key) {
                return baggage.asMap().containsKey(key);
            }
        };
    }

    private Context otelContextWithSpanAndBaggage() {
        // Because the Helidon tracing API links baggage with a span, any OTel context we create for the span
        // needs to have the baggage with it.
        return getContext().with(delegate).with(baggage);
    }

    private Attributes toAttributes(Map<String, ?> attributes) {
        AttributesBuilder builder = Attributes.builder();
        attributes.forEach((key, value) -> {
            if (value instanceof Long l) {
                builder.put(key, l);
            } else if (value instanceof Boolean b) {
                builder.put(key, b);
            } else if (value instanceof Double d) {
                builder.put(key, d);
            } else {
                builder.put(key, String.valueOf(value));
            }
        });
        return builder.build();
    }

    private record Limited(OpenTelemetrySpan delegate) implements Span {

        @Override
            public Span tag(String key, String value) {
                delegate.tag(key, value);
                return this;
            }

            @Override
            public Span tag(String key, Boolean value) {
                delegate.tag(key, value);
                return this;
            }

            @Override
            public Span tag(String key, Number value) {
                delegate.tag(key, value);
                return this;
            }

            @Override
            public void status(Status status) {
                throw new UnsupportedOperationException();
            }

            @Override
            public SpanContext context() {
                return delegate.context();
            }

            @Override
            public void addEvent(String name, Map<String, ?> attributes) {
                delegate.addEvent(name, attributes);
            }

            @Override
            public void end() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void end(Throwable t) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Scope activate() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Span baggage(String key, String value) {
                delegate.baggage().set(key, value);
                return this;
            }

            @Override
            public Optional<String> baggage(String key) {
                return delegate.baggage(key);
            }

            @Override
            public WritableBaggage baggage() {
                return delegate.baggage();
            }
        }
}
