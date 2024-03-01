/*
 * Copyright (c) 2018, 2024 Oracle and/or its affiliates.
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

package io.helidon.tracing.providers.zipkin;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import io.helidon.tracing.SpanInfo;
import io.helidon.tracing.WritableBaggage;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tag;

/**
 * The ZipkinSpan delegates to another {@link Span} while finishing the
 * {@link Span} with tag {@code ss} Zipkin understands as an end of the span.
 *
 * @see <a href="http://zipkin.io/pages/instrumenting.html#core-data-structures">Zipkin Attributes</a>
 * @see <a href="https://github.com/openzipkin/zipkin/issues/962">Zipkin Missing Service Name</a>
 */
class ZipkinSpan implements Span, SpanInfo {
    private final Span span;
    private final boolean isClient;

    ZipkinSpan(Span span, boolean isClient) {
        this.span = span;
        this.isClient = isClient;
    }

    @Override
    public SpanContext context() {
        return span.context();
    }

    @Override
    public void finish() {
        finishLog();
        span.finish();
        ZipkinTracerProvider.lifeCycleListeners().forEach(listener -> listener.afterEnd(this));
    }

    @Override
    public void finish(long finishMicros) {
        finishLog();
        span.finish(finishMicros);
        ZipkinTracerProvider.lifeCycleListeners().forEach(listener -> listener.afterEnd(this));
    }

    @Override
    public Span setTag(String key, String value) {
        span.setTag(key, value);
        return this;
    }

    @Override
    public Span setTag(String key, boolean value) {
        span.setTag(key, value);
        return this;
    }

    @Override
    public Span setTag(String key, Number value) {
        span.setTag(key, value);
        return this;
    }

    @Override
    public Span log(Map<String, ?> fields) {
        span.log(fields);
        return this;
    }

    @Override
    public Span log(long timestampMicroseconds, Map<String, ?> fields) {
        span.log(timestampMicroseconds, fields);
        return this;
    }

    @Override
    public Span log(String event) {
        span.log(event);
        return this;
    }

    @Override
    public Span log(long timestampMicroseconds, String event) {
        span.log(timestampMicroseconds, event);
        return this;
    }

    @Override
    public Span setBaggageItem(String key, String value) {
        span.setBaggageItem(key, value);
        return this;
    }

    @Override
    public String getBaggageItem(String key) {
        return span.getBaggageItem(key);
    }

    @Override
    public Span setOperationName(String operationName) {
        span.setOperationName(operationName);
        return this;
    }

    @Override
    public <T> Span setTag(Tag<T> tag, T value) {
        span.setTag(tag, value);
        return this;
    }

    Span unwrap() {
        return span;
    }

    @Override
    public void addEvent(String name, Map<String, ?> attributes) {
        Map<String, Object> attrsWithName = new HashMap<>(attributes);
        attrsWithName.put("event", name);
        span.log(attrsWithName);
    }

    @Override
    public WritableBaggage baggage() {
        return new WritableBaggage() {
            @Override
            public WritableBaggage set(String key, String value) {
                span.setBaggageItem(key, value);
                return this;
            }

            @Override
            public Optional<String> get(String key) {
                return Optional.ofNullable(span.getBaggageItem(key));
            }

            @Override
            public Set<String> keys() {
                return StreamSupport.stream(context().baggageItems().spliterator(), false)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet());
            }

            @Override
            public boolean containsKey(String key) {
                return getBaggageItem(key) != null;
            }
        };
    }

    @Override
    public SpanInfo tag(io.helidon.tracing.Tag<?> tag) {
        switch (tag.value()) {
            case Boolean b -> setTag(tag.key(), b);
            case Number n -> setTag(tag.key(), n);
            case String s -> setTag(tag.key(), s);
            default -> setTag(tag.key(), tag.value().toString());
        }
        return this;
    }

    @Override
    public SpanInfo tag(String key, String value) {
        setTag(key, value);
        return this;
    }

    @Override
    public SpanInfo tag(String key, Boolean value) {
        setTag(key, value);
        return this;
    }

    @Override
    public SpanInfo tag(String key, Number value) {
        setTag(key, value);
        return this;
    }

    private void finishLog() {
        if (isClient) {
            span.log("cr");
        } else {
            span.log("ss");
        }
    }
}
