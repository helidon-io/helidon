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

package io.helidon.pico.types;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
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
        this.values = Objects.isNull(b.values) ? Collections.emptyMap() : Collections.unmodifiableMap(b.values);
    }

    @Override
    public String toString() {
        String result = getClass().getSimpleName() + "(typeName=" + typeName();
        if (Objects.nonNull(values)) {
            result += ", values=" + values();
        } else if (Objects.nonNull(value)) {
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
     * Creates an instance for an annotation with a value.
     *
     * @param annoType the annotation type
     * @return the new instance
     */
    public static DefaultAnnotationAndValue create(Class<? extends Annotation> annoType) {
        return create(annoType, (String) null);
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
     * Attempts to find the annotation in the provided collection, and if not found will throw an exception.
     *
     * @param annoTypeName  the annotation type name
     * @param coll          the collection to search
     * @param mustHaveValue true if the result must have a value
     * @return the result of the find
     * @throws java.lang.AssertionError if not found, or not found to have a non-blank value present
     */
    public static AnnotationAndValue getFirst(String annoTypeName,
                                               Collection<? extends AnnotationAndValue> coll,
                                               boolean mustHaveValue) {
        assert (AnnotationAndValue.hasNonBlankValue(annoTypeName));
        Objects.requireNonNull(coll, "collection is required");
        Optional<? extends AnnotationAndValue> result =  coll.stream()
                .filter(it -> it.typeName().name().equals(annoTypeName))
                .findFirst();
        if (result.isPresent() && mustHaveValue && !result.get().hasNonBlankValue()) {
            result = Optional.empty();
        }
        return result.orElseThrow(() -> new AssertionError("Unable to find " + annoTypeName));
    }

    /**
     * Attempts to find the annotation in the provided collection.
     *
     * @param annoTypeName  the annotation type name
     * @param coll          the collection to search
     * @return the result of the find.
     */
    public static Optional<? extends AnnotationAndValue> findFirst(String annoTypeName,
                                                         Collection<? extends AnnotationAndValue> coll) {
        assert (AnnotationAndValue.hasNonBlankValue(annoTypeName));
        Objects.requireNonNull(coll, "collection is required");
        return coll.stream()
                .filter(it -> it.typeName().name().equals(annoTypeName))
                .findFirst();
    }

    /**
     * The same as calling {@link #findFirst(String, java.util.Collection)} with an added optional check for the value being
     * present and non-blank.
     *
     * @param annoTypeName  the annotation type name
     * @param coll          the collection to search
     * @param mustHaveValue true if the result must have a non-blank value
     * @return the result of the find.
     */
    public static Optional<? extends AnnotationAndValue> findFirst(TypeName annoTypeName,
                                                         Collection<? extends AnnotationAndValue> coll,
                                                         boolean mustHaveValue) {
        Objects.requireNonNull(coll, "collection is required");
        Optional<? extends AnnotationAndValue> result =  coll.stream()
                .filter(it -> it.typeName().equals(annoTypeName))
                .findFirst();
        if (result.isPresent() && mustHaveValue && !result.get().hasNonBlankValue()) {
            result = Optional.empty();
        }
        return result;
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
     * The fluent builder.
     */
    public static class Builder {
        private TypeName typeName;
        private String value;
        private Map<String, String> values;

        /**
         * Default ctor.
         */
        protected Builder() {
        }

        /**
         * Set the type name.
         *
         * @param val   the new type name value
         * @return this fluent builder
         */
        public Builder typeName(TypeName val) {
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
            this.values = new LinkedHashMap<>(val);
            return this;
        }

        /**
         * Build the instance.
         *
         * @return the built instance
         */
        public DefaultAnnotationAndValue build() {
            Objects.requireNonNull(typeName, "type name is required");
            return new DefaultAnnotationAndValue(this);
        }
    }

}
