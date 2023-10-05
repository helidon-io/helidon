/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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
package io.helidon.tracing;

import java.util.Objects;

/**
 * Tag abstraction that can be used with {@link TracerBuilder#addTracerTag(String, String)}.
 *
 * @param <T> type of the tag
 */
public abstract class Tag<T> {
    /**
     * Tag marking a component that triggers this span.
     */
    public static final TagSource<String> COMPONENT = new StringTagSource("component");
    /**
     * Http method used to invoke this request.
     */
    public static final TagSource<String> HTTP_METHOD = new StringTagSource("http.method");
    /**
     * URL of the HTTP request.
     */
    public static final TagSource<String> HTTP_URL = new StringTagSource("http.url");
    /**
     * HTTP version.
     */
    public static final TagSource<String> HTTP_VERSION = new StringTagSource("http.version");
    /**
     * Status code that was returned.
     */
    public static final TagSource<Integer> HTTP_STATUS = new NumberTagSource<>("http.status_code");

    /**
     * Tag marking a Database type.
     */
    public static final TagSource<String> DB_TYPE = new StringTagSource("db.type");

    /**
     * Tag marking a Database statement.
     */
    public static final TagSource<String> DB_STATEMENT = new StringTagSource("db.statement");

    private final String key;
    private final T value;

    /**
     * Create a string tag.
     * @param key tag name
     * @param value tag value
     * @return string tag
     */
    public static Tag<String> create(String key, String value) {
        return new StringTag(key, value);
    }

    /**
     * Create a numeric tag.
     *
     * @param key tag name
     * @param value tag value
     * @return numeric tag
     */
    public static Tag<Number> create(String key, Number value) {
        return new NumericTag(key, value);
    }

    /**
     * Create a boolean tag.
     * @param key tag name
     * @param value tag value
     * @return boolean tag
     */
    public static Tag<Boolean> create(String key, boolean value) {
        return new BooleanTag(key, value);
    }

    /**
     * Tag name.
     * @return tag name
     */
    public String key() {
        return key;
    }

    /**
     * Tag value.
     * @return tag value
     */
    public T value() {
        return value;
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
        return key.equals(tag.key)
                && value.equals(tag.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public String toString() {
        return key + "=" + value;
    }

    /**
     * Configure this tag on the span provided.
     *
     * @param span span to apply the tag on
     */
    public abstract void apply(Span span);
    /**
     * Configure this tag on the span builder.
     *
     * @param spanBuilder span builder to apply the tag on
     */
    public abstract void apply(Span.Builder<?> spanBuilder);

    /**
     * Tag source (a type that can create tags). Use by {@link #HTTP_METHOD} and other constants to easily create new tags.
     *
     * @param <T> type of the tag
     */
    public interface TagSource<T> {
        /**
         * Create a tag with value.
         *
         * @param value value of the tag
         * @return tag instance
         */
        Tag<? super T> create(T value);
    }

    private static final class StringTag extends Tag<String> {
        private StringTag(String key, String value) {
            super(key, value);
        }

        @Override
        public void apply(Span span) {
            span.tag(key(), value());
        }

        @Override
        public void apply(Span.Builder<?> spanBuilder) {
            spanBuilder.tag(key(), value());
        }
    }

    private static final class BooleanTag extends Tag<Boolean> {
        private BooleanTag(String key, boolean value) {
            super(key, value);
        }

        @Override
        public void apply(Span span) {
            span.tag(key(), value());
        }

        @Override
        public void apply(Span.Builder<?> spanBuilder) {
            spanBuilder.tag(key(), value());
        }
    }

    private static final class NumericTag extends Tag<Number> {
        private NumericTag(String key, Number value) {
            super(key, value);
        }

        @Override
        public void apply(Span span) {
            span.tag(key(), value());
        }

        @Override
        public void apply(Span.Builder<?> spanBuilder) {
            spanBuilder.tag(key(), value());
        }
    }

    private static class StringTagSource implements TagSource<String> {
        private final String name;

        protected StringTagSource(String name) {
            this.name = name;
        }

        @Override
        public Tag<String> create(String value) {
            return new StringTag(name, value);
        }
    }

    private static class NumberTagSource<T extends Number> implements TagSource<T> {
        private final String name;

        protected NumberTagSource(String name) {
            this.name = name;
        }

        @Override
        public Tag<Number> create(T value) {
            return new NumericTag(name, value);
        }
    }

    private static class BooleanTagSource implements TagSource<Boolean> {
        private final String component;

        protected BooleanTagSource(String component) {
            this.component = component;
        }

        @Override
        public Tag<Boolean> create(Boolean value) {
            return new BooleanTag(component, value);
        }
    }
}
