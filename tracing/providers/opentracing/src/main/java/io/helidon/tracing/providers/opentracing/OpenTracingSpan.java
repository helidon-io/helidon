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
package io.helidon.tracing.providers.opentracing;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.SpanLifeCycleListener;
import io.helidon.tracing.UnsupportedActivationException;
import io.helidon.tracing.WritableBaggage;

import io.opentracing.Tracer;
import io.opentracing.tag.Tags;

class OpenTracingSpan implements Span {
    private final Tracer tracer;
    private final io.opentracing.Span delegate;
    private final OpenTracingContext context;
    private final WritableBaggage baggage;
    private final List<SpanLifeCycleListener> spanLifeCycleListeners;
    private Limited limited;

    OpenTracingSpan(Tracer tracer, io.opentracing.Span delegate, List<SpanLifeCycleListener> spanLifeCycleListeners) {
        this.tracer = tracer;
        this.delegate = delegate;
        this.context = new OpenTracingContext(delegate.context());
        this.spanLifeCycleListeners = spanLifeCycleListeners;
        baggage = OpenTracingBaggage.create(context, delegate);
    }


    @Override
    public Span tag(String key, String value) {
        delegate.setTag(key, value);
        return this;
    }

    @Override
    public Span tag(String key, Boolean value) {
        delegate.setTag(key, value);
        return this;
    }

    @Override
    public Span tag(String key, Number value) {
        delegate.setTag(key, value);
        return this;
    }

    @Override
    public void status(Status status) {
        if (status == Status.ERROR) {
            Tags.ERROR.set(delegate, true);
        }
    }

    @Override
    public SpanContext context() {
        return context;
    }

    @Override
    public void addEvent(String name, Map<String, ?> attributes) {
        Map<String, Object> newMap = new HashMap<>(attributes);
        newMap.put("event", name);
        delegate.log(newMap);
    }

    @Override
    public void end() {
        delegate.finish();
        spanLifeCycleListeners.forEach(listener -> listener.afterEnd(limited()));
    }

    @Override
    public void end(Throwable throwable) {
        status(Status.ERROR);
        delegate.log(Map.of("event", "error",
                            "error.kind", "Exception",
                            "error.object", throwable,
                            "message", throwable.getMessage() != null ? throwable.getMessage() : "none"));
        delegate.finish();
        spanLifeCycleListeners.forEach(listener -> listener.afterEnd(limited(), throwable));
    }

    @Override
    public Scope activate() {
        var result = new OpenTracingScope(this, tracer.activateSpan(delegate), spanLifeCycleListeners);
        UnsupportedActivationException ex = new UnsupportedActivationException("Error activating span", result);
        spanLifeCycleListeners.forEach(listener -> {
            try {
                listener.afterActivate(limited(), result.limited());
            } catch (Throwable t) {
                ex.addSuppressed(t);
            }
        });

        if (ex.getSuppressed().length > 0) {
            throw ex;
        }
        return result;
    }

    @Override
    public Span baggage(String key, String value) {
        Objects.requireNonNull(key, "Baggage Key cannot be null");
        Objects.requireNonNull(value, "Baggage Value cannot be null");

        baggage.set(key, value);
        return this;
    }

    @Override
    public Optional<String> baggage(String key) {
        Objects.requireNonNull(key, "Baggage Key cannot be null");
        return baggage.get(key);
    }

    @Override
    public WritableBaggage baggage() {
        return baggage;
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
                                                   + ", open tracing span is: " + delegate.getClass().getName());
    }

    Limited limited() {
        if (limited == null) {
            if (!spanLifeCycleListeners.isEmpty()) {
                limited = new Limited(this);
            }
        }
        return limited;
    }

    private record Limited(OpenTracingSpan delegate) implements Span {

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
            delegate.baggage(key, value);
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

        @Override
        public <T> T unwrap(Class<T> spanClass) {
            if (spanClass.isInstance(this)) {
                return spanClass.cast(this);
            }
            return delegate.unwrap(spanClass);
        }
    }
}
