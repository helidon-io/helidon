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

package io.helidon.pico;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Objects;

import io.helidon.common.types.AnnotationAndValue;
import io.helidon.common.types.DefaultAnnotationAndValue;
import io.helidon.common.types.DefaultTypeName;
import io.helidon.common.types.TypeName;

import jakarta.inject.Named;

/**
 * Describes a {@link jakarta.inject.Qualifier} type annotation associated with a service being provided or dependant upon.
 * In Pico these are generally determined at compile time to avoid any use of reflection at runtime.
 */
public class DefaultQualifierAndValue extends DefaultAnnotationAndValue
        implements QualifierAndValue, Comparable<AnnotationAndValue> {

    /**
     * Represents a {@link jakarta.inject.Named} type name with no value.
     */
    public static final TypeName NAMED = DefaultTypeName.create(Named.class);

    /**
     * Represents a wildcard {@link #NAMED} qualifier.
     */
    public static final QualifierAndValue WILDCARD_NAMED = DefaultQualifierAndValue.createNamed("*");

    /**
     * Constructor using the builder.
     *
     * @param b the builder
     * @see #builder()
     */
    protected DefaultQualifierAndValue(Builder b) {
        super(b);
    }

    /**
     * Creates a {@link jakarta.inject.Named} qualifier.
     *
     * @param name the name
     * @return named qualifier
     */
    public static DefaultQualifierAndValue createNamed(String name) {
        Objects.requireNonNull(name);
        return (DefaultQualifierAndValue) builder().typeName(NAMED).value(name).build();
    }

    /**
     * Creates a qualifier from an annotation.
     *
     * @param qualifierType the qualifier type
     * @return qualifier
     */
    public static DefaultQualifierAndValue create(Class<? extends Annotation> qualifierType) {
        Objects.requireNonNull(qualifierType);
        return (DefaultQualifierAndValue) builder().typeName(DefaultTypeName.create(qualifierType)).build();
    }

    /**
     * Creates a qualifier.
     *
     * @param qualifierType the qualifier type
     * @param val           the value
     * @return qualifier
     */
    public static DefaultQualifierAndValue create(Class<? extends Annotation> qualifierType, String val) {
        Objects.requireNonNull(qualifierType);
        return (DefaultQualifierAndValue) builder().typeName(DefaultTypeName.create(qualifierType)).value(val).build();
    }

    /**
     * Creates a qualifier.
     *
     * @param qualifierTypeName the qualifier
     * @param val               the value
     * @return qualifier
     */
    public static DefaultQualifierAndValue create(String qualifierTypeName, String val) {
        return (DefaultQualifierAndValue) builder()
                .typeName(DefaultTypeName.createFromTypeName(qualifierTypeName))
                .value(val)
                .build();
    }

    /**
     * Creates a qualifier.
     *
     * @param qualifierType the qualifier
     * @param val           the value
     * @return qualifier
     */
    public static DefaultQualifierAndValue create(TypeName qualifierType, String val) {
        return (DefaultQualifierAndValue) builder()
                .typeName(qualifierType)
                .value(val)
                .build();
    }

    /**
     * Creates a qualifier.
     *
     * @param qualifierType the qualifier
     * @param vals           the values
     * @return qualifier
     */
    public static DefaultQualifierAndValue create(TypeName qualifierType, Map<String, String> vals) {
        return (DefaultQualifierAndValue) builder()
                .typeName(qualifierType)
                .values(vals)
                .build();
    }

    /**
     * Converts from an {@link AnnotationAndValue} to a {@link QualifierAndValue}.
     *
     * @param annotationAndValue the annotation and value
     * @return the qualifier and value equivalent
     */
    public static QualifierAndValue convert(AnnotationAndValue annotationAndValue) {
        if (annotationAndValue instanceof QualifierAndValue) {
            return (QualifierAndValue) annotationAndValue;
        }

        return (QualifierAndValue) builder()
                .typeName(annotationAndValue.typeName())
                .values(annotationAndValue.values())
                .update(it -> annotationAndValue.value().ifPresent(it::value))
                .build();
    }

    @Override
    public int compareTo(AnnotationAndValue other) {
        return typeName().compareTo(other.typeName());
    }


    /**
     * Creates a builder for {@link QualifierAndValue}.
     *
     * @return a fluent builder
     */
    public static Builder builder() {
        return new Builder();
    }


    /**
     * The fluent builder.
     */
    public static class Builder extends DefaultAnnotationAndValue.Builder {
        /**
         * Fluent builder constructor.
         */
        protected Builder() {
        }

        /**
         * Build the instance.
         *
         * @return the built instance
         */
        @Override
        public DefaultQualifierAndValue build() {
            return new DefaultQualifierAndValue(this);
        }
    }

}
