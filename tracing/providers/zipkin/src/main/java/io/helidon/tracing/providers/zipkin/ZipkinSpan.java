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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.tracing.Baggage;
import io.helidon.tracing.Scope;
import io.helidon.tracing.SpanListener;
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
class ZipkinSpan implements Span {
    private static final System.Logger LOGGER = System.getLogger(ZipkinSpan.class.getName());

    private final Span span;
    private final boolean isClient;
    private final List<SpanListener> spanListeners;
    private Limited limited;
    private final Set<String> baggageKeys = new HashSet<>();


    ZipkinSpan(Span span, boolean isClient, List<SpanListener> spanListeners) {
        this.span = span;
        this.isClient = isClient;
        this.spanListeners = spanListeners;
    }

    @Override
    public SpanContext context() {
        return span.context();
    }

    @Override
    public void finish() {
        finishLog();
        span.finish();
        ZipkinTracer.invokeListeners(spanListeners, LOGGER, listener -> listener.ended(limited()));
    }

    @Override
    public void finish(long finishMicros) {
        finishLog();
        span.finish(finishMicros);
        ZipkinTracer.invokeListeners(spanListeners, LOGGER, listener-> listener.ended(limited()));
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
        baggageKeys.add(key);
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

    Limited limited() {
        if (limited == null && !spanListeners.isEmpty()) {
            limited = new Limited(this, span.context(), new WritableBaggage() {
                @Override
                public WritableBaggage set(String key, String value) {
                    setBaggageItem(key, value);
                    return this;
                }

                @Override
                public Optional<String> get(String key) {
                    return Optional.ofNullable(getBaggageItem(key));
                }

                @Override
                public Set<String> keys() {
                    return baggageKeys;
                }

                @Override
                public boolean containsKey(String key) {
                    return baggageKeys.contains(key);
                }
            });
        }
        return limited;
    }

    private void finishLog() {
        if (isClient) {
            span.log("cr");
        } else {
            span.log("ss");
        }
    }

    private record Limited(ZipkinSpan delegateSpan, SpanContext delegateSpanContext, WritableBaggage baggage)
            implements io.helidon.tracing.Span {

        Limited(ZipkinSpan delegateSpan, SpanContext delegateSpanContext) {
            this(delegateSpan, delegateSpanContext, writableBaggage(delegateSpan));
        }

        private static WritableBaggage writableBaggage(ZipkinSpan delegateSpan) {
            return new WritableBaggage() {
                @Override
                public WritableBaggage set(String key, String value) {
                    delegateSpan.setBaggageItem(key, value);
                    return this;
                }

                @Override
                public Optional<String> get(String key) {
                    return Optional.ofNullable(delegateSpan.getBaggageItem(key));
                }

                @Override
                public Set<String> keys() {
                    return delegateSpan.baggageKeys;
                }

                @Override
                public boolean containsKey(String key) {
                    return delegateSpan.baggageKeys.contains(key);
                }
            };
        }

        @Override
        public io.helidon.tracing.Span tag(String key, String value) {
            delegateSpan.setTag(key, value);
            return this;
        }

        @Override
        public io.helidon.tracing.Span tag(String key, Boolean value) {
            delegateSpan.setTag(key, value);
            return this;
        }

        @Override
        public io.helidon.tracing.Span tag(String key, Number value) {
            delegateSpan.setTag(key, value);
            return this;
        }

        @Override
        public void status(Status status) {
            throw new SpanListener.ForbiddenOperationException();
        }

        @Override
        public io.helidon.tracing.SpanContext context() {
            return new io.helidon.tracing.SpanContext() {
                @Override
                public String traceId() {
                    return delegateSpanContext.toTraceId();
                }

                @Override
                public String spanId() {
                    return delegateSpanContext.toSpanId();
                }

                @Override
                public void asParent(Builder<?> spanBuilder) {
                    spanBuilder.parent(this);
                }

                @Override
                public Baggage baggage() {
                    return baggage;
                }
            };
        }

        @Override
        public void addEvent(String name, Map<String, ?> attributes) {
            Map<String, Object> newMap = new HashMap<>(attributes);
            newMap.put("event", name);
            delegateSpan.log(newMap);
        }

        @Override
        public void end() {
            throw new SpanListener.ForbiddenOperationException();
        }

        @Override
        public void end(Throwable t) {
            throw new SpanListener.ForbiddenOperationException();
        }

        @Override
        public Scope activate() {
            throw new SpanListener.ForbiddenOperationException();
        }

        @Override
        public io.helidon.tracing.Span baggage(String key, String value) {
            delegateSpan.setBaggageItem(key, value);
            return this;
        }

        @Override
        public Optional<String> baggage(String key) {
            return Optional.ofNullable(delegateSpan.getBaggageItem(key));
        }

        @Override
        public WritableBaggage baggage() {
            return baggage;
        }

        @Override
        public <T> T unwrap(Class<T> spanClass) {
            if (spanClass.isInstance(this)) {
                return spanClass.cast(this);
            }
            Span span = delegateSpan.unwrap();
            if (spanClass.isInstance(span)) {
                return spanClass.cast(span);
            }
            throw new IllegalArgumentException("Cannot provide an instance of " + spanClass.getName()
                                                       + ", Zipkin span is: " + delegateSpan.getClass().getName());

        }
    }
}
