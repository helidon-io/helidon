/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.types;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The default implementation for {@link AnnotationAndValue}.
 */
public class DefaultAnnotationAndValue implements AnnotationAndValue, Comparable<AnnotationAndValue> {
    private final TypeName typeName;
    private final String value;
    private final Map<String, String> values;

    /**
     * Ctor.
     *
     * @param b the builder
     * @see #builder()
     */
    protected DefaultAnnotationAndValue(Builder b) {
        this.typeName = b.typeName;
        this.value = b.value;
        this.values = Map.copyOf(b.values);
    }

    @Override
    public String toString() {
        String result = getClass().getSimpleName() + "(typeName=" + typeName();
        if (values != null && !values.isEmpty()) {
            result += ", values=" + values();
        } else if (value != null) {
            result += ", value=" + value().orElse(null);
        }
        return result + ")";
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(typeName());
    }

    @Override
    public boolean equals(Object another) {
        if (!(another instanceof AnnotationAndValue)) {
            return false;
        }
        if (!Objects.equals(typeName(), ((AnnotationAndValue) another).typeName())) {
            return false;
        }
        if (Objects.nonNull(values) && (another instanceof DefaultAnnotationAndValue)
                && Objects.equals(values(), ((DefaultAnnotationAndValue) another).values())) {
            return true;
        } else if (Objects.nonNull(values) && values.size() > 1) {
            return false;
        }
        String thisValue = value().orElse("");
        String anotherValue = ((AnnotationAndValue) another).value().orElse("");
        return thisValue.equals(anotherValue);
    }

    @Override
    public TypeName typeName() {
        return typeName;
    }

    @Override
    public Optional<String> value() {
        if (Objects.nonNull(value)) {
            return Optional.of(value);
        }
        return value("value");
    }

    @Override
    public Optional<String> value(String name) {
        return Objects.isNull(values) ? Optional.empty() : Optional.ofNullable(values.get(name));
    }

    @Override
    public Map<String, String> values() {
        return values;
    }

    /**
     * Creates an instance for an annotation with no value.
     *
     * @param annoType the annotation type
     * @return the new instance
     */
    public static DefaultAnnotationAndValue create(Class<? extends Annotation> annoType) {
        return builder()
                .type(annoType)
                .build();
    }

    /**
     * Creates an instance for an annotation with no value.
     *
     * @param annoType the annotation type
     * @return the new instance
     */
    public static DefaultAnnotationAndValue create(TypeName annoType) {
        return builder()
                .typeName(annoType)
                .build();
    }

    /**
     * Creates an instance for an annotation with a value.
     *
     * @param annoType the annotation type
     * @param value the annotation value
     * @return the new instance
     */
    public static DefaultAnnotationAndValue create(Class<? extends Annotation> annoType, String value) {
        return create(DefaultTypeName.create(annoType), value);
    }

    /**
     * Creates an instance for an annotation with a value.
     *
     * @param annoType the annotation type
     * @param values the annotation values
     * @return the new instance
     */
    public static DefaultAnnotationAndValue create(Class<? extends Annotation> annoType, Map<String, String> values) {
        return create(DefaultTypeName.create(annoType), values);
    }

    /**
     * Creates an instance for an annotation with a value.
     *
     * @param annoTypeName the annotation type name
     * @param value the annotation value
     * @return the new instance
     */
    public static DefaultAnnotationAndValue create(TypeName annoTypeName, String value) {
        return DefaultAnnotationAndValue.builder().typeName(annoTypeName).value(value).build();
    }

    /**
     * Creates an instance for annotation with zero or more values.
     *
     * @param annoTypeName the annotation type name
     * @param values the annotation values
     * @return the new instance
     */
    public static DefaultAnnotationAndValue create(TypeName annoTypeName, Map<String, String> values) {
        return DefaultAnnotationAndValue.builder().typeName(annoTypeName).values(values).build();
    }

    /**
     * Attempts to find the annotation in the provided collection.
     *
     * @param annoTypeName  the annotation type name
     * @param coll          the collection to search
     * @return the result of the find
     */
    public static Optional<? extends AnnotationAndValue> findFirst(String annoTypeName,
                                                         Collection<? extends AnnotationAndValue> coll) {
        assert (!annoTypeName.isBlank());
        return coll.stream()
                .filter(it -> it.typeName().name().equals(annoTypeName))
                .findFirst();
    }

    @Override
    public int compareTo(AnnotationAndValue other) {
        return typeName().compareTo(other.typeName());
    }


    /**
     * Creates a builder for {@link io.helidon.pico.types.AnnotationAndValue}.
     *
     * @return a fluent builder
     */
    public static Builder builder() {
        return new Builder();
    }


    /**
     * Fluent API builder for {@link io.helidon.pico.types.DefaultAnnotationAndValue}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, DefaultAnnotationAndValue> {
        private final Map<String, String> values = new LinkedHashMap<>();

        private TypeName typeName;
        private String value;

        /**
         * Default ctor.
         */
        protected Builder() {
        }

        @Override
        public DefaultAnnotationAndValue build() {
            return new DefaultAnnotationAndValue(this);
        }

        /**
         * Set the type name.
         *
         * @param val   the new type name value
         * @return this fluent builder
         */
        public Builder typeName(TypeName val) {
            Objects.requireNonNull(val);
            this.typeName = val;
            return this;
        }

        /**
         * Set the value.
         *
         * @param val   the new value
         * @return this fluent builder
         */
        public Builder value(String val) {
            Objects.requireNonNull(val);
            this.value = val;
            return this;
        }

        /**
         * Set the attribute key/value tuples.
         *
         * @param val   the new values
         * @return this fluent builder
         */
        public Builder values(Map<String, String> val) {
            Objects.requireNonNull(val);
            this.values.clear();
            this.values.putAll(val);
            return this;
        }

        /**
         * Annotation type name from annotation type.
         *
         * @param annoType annotation class
         * @return updated builder
         */
        public Builder type(Class<? extends Annotation> annoType) {
            Objects.requireNonNull(annoType);
            return typeName(DefaultTypeName.create(annoType));
        }
    }
}
