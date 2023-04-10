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
package io.helidon.tracing.opentracing;

import java.util.HashMap;
import java.util.Map;

import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanContext;

import io.opentracing.Tracer;
import io.opentracing.tag.Tags;

class OpenTracingSpan implements Span {
    private final Tracer tracer;
    private final io.opentracing.Span delegate;
    private final OpenTracingContext context;

    OpenTracingSpan(Tracer tracer, io.opentracing.Span delegate) {
        this.tracer = tracer;
        this.delegate = delegate;
        this.context = new OpenTracingContext(delegate.context());
    }


    @Override
    public void tag(String key, String value) {
        delegate.setTag(key, value);
    }

    @Override
    public void tag(String key, Boolean value) {
        delegate.setTag(key, value);
    }

    @Override
    public void tag(String key, Number value) {
        delegate.setTag(key, value);
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
    }

    @Override
    public void end(Throwable throwable) {
        status(Status.ERROR);
        delegate.log(Map.of("event", "error",
                            "error.kind", "Exception",
                            "error.object", throwable,
                            "message", throwable.getMessage()));
        delegate.finish();
    }

    @Override
    public Scope activate() {
        return new OpenTracingScope(tracer.activateSpan(delegate));
    }

    @Override
    public Span setBaggageItem(String key, String value) {
        return (OpenTracingSpan) delegate.setBaggageItem(key, value);
    }

    @Override
    public String getBaggageItem(String key) {
        return delegate.getBaggageItem(key);
    }

    @Override
    public <T> T unwrap(Class<T> spanClass) {
        if (spanClass.isAssignableFrom(delegate.getClass())) {
            return spanClass.cast(delegate);
        }
        throw new IllegalArgumentException("Cannot provide an instance of " + spanClass.getName()
                                                   + ", open tracing span is: " + delegate.getClass().getName());
    }
}
