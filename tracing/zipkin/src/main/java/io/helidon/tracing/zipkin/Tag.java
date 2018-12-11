/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.tracing.zipkin;

import java.util.Objects;

import io.helidon.tracing.TracerBuilder;

import io.opentracing.Span;

/**
 * Tag abstraction to be used with {@link TracerBuilder#addTracerTag(String, String)}.
 */
abstract class Tag<T> {
    protected final String key;
    protected final T value;

    static Tag<String> create(String key, String value) {
        return new StringTag(key, value);
    }

    static Tag<Number> create(String key, Number value) {
        return new NumericTag(key, value);
    }

    static Tag<Boolean> create(String key, boolean value) {
        return new BooleanTag(key, value);
    }

    private Tag(String key, T value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);

        this.key = key;
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Tag)) {
            return false;
        }
        Tag<?> tag = (Tag<?>) o;
        return key.equals(tag.key) &&
                value.equals(tag.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public String toString() {
        return key + "=" + value;
    }

    public abstract void apply(Span span);

    private static final class StringTag extends Tag<String> {
        private StringTag(String key, String value) {
            super(key, value);
        }

        @Override
        public void apply(Span span) {
            span.setTag(key, value);
        }
    }

    private static final class BooleanTag extends Tag<Boolean> {
        private BooleanTag(String key, boolean value) {
            super(key, value);
        }

        @Override
        public void apply(Span span) {
            span.setTag(key, value);
        }
    }

    private static final class NumericTag extends Tag<Number> {
        private NumericTag(String key, Number value) {
            super(key, value);
        }

        @Override
        public void apply(Span span) {
            span.setTag(key, value);
        }
    }
}
