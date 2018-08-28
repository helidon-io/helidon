/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.zipkin;

import java.util.Map;

import io.opentracing.Span;
import io.opentracing.SpanContext;

/**
 * The ZipkinSpan delegates to another {@link Span} while finishing the
 * {@link Span} with tag {@code ss} Zipkin understands as an end of the span.
 *
 * @see <a href="http://zipkin.io/pages/instrumenting.html#core-data-structures">Zipkin Attributes</a>
 * @see <a href="https://github.com/openzipkin/zipkin/issues/962">Zipkin Missing Service Name</a>
 */
class ZipkinSpan implements Span {
    private final Span span;

    ZipkinSpan(Span span) {
        this.span = span;
    }

    @Override
    public SpanContext context() {
        return span.context();
    }

    @Override
    public void finish() {
        span.log("ss");
        span.finish();
    }

    @Override
    public void finish(long finishMicros) {
        span.log("ss");
        span.finish(finishMicros);
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
}
