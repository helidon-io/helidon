/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.common.types;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.builder.api.Prototype;
import io.helidon.common.Errors;

/**
 * An annotation with defined values.
 * <p>
 * Annotations can have the following values:
 * <ul>
 *     <li>String - {@code @TheAnnotation("some-value")}</li>
 *     <li>int - {@code @TheAnnotation(49)}</li>
 *     <li>long - {@code @TheAnnotation(49L)}</li>
 *     <li>boolean - {@code @TheAnnotation(true)}</li>
 *     <li>byte - {@code @TheAnnotation(49)}</li>
 *     <li>char - {@code @TheAnnotation('x')}</li>
 *     <li>short - {@code @TheAnnotation(1)}</li>
 *     <li>float - {@code @TheAnnotation(4.2f)}</li>
 *     <li>double - {@code @TheAnnotation(4.2)}</li>
 *     <li>enum value - {@code @TheAnnotation(MyEnum.OPTION_1)}, default representation is String</li>
 *     <li>Class - {@code @TheAnnotation(MyType.class)}, default representation is String</li>
 *     <li>another annotation - {@code @TheAnnotation(@TheOtherAnnotation("some-value"))}</li>
 *     <li>arrays of the above - {@code @TheAnnotation({"first-value", "second-value"})}, represented as List</li>
 * </ul>
 * These types will be built into this type, with a few rules:
 * <ul>
 *     <li>Any type except for another annotation and array is available as String (similar for arrays)</li>
 *     <li>primitive types - only available as boxed types</li>
 *     <li>Class - use string, the method that provides class instances uses {@link java.lang.Class#forName(String)},
 *          which is not ideal, and may require additional configuration in native image</li>
 *     <li>enum value - available as a String or enum value</li>
 *     <li>another annotation - available as instance(s) of Annotation</li>
 *     <li>arrays - available as a {@link java.util.List} of values</li>
 * </ul>
 *
 * @see #builder()
 */
public interface Annotation extends AnnotationBlueprint, Prototype.Api, Comparable<Annotation> {

    /**
     * Create a new fluent API builder to customize configuration.
     *
     * @return a new builder
     */
    static Annotation.Builder builder() {
        return new Annotation.Builder();
    }

    /**
     * Create a new fluent API builder from an existing instance.
     *
     * @param instance an existing instance used as a base for the builder
     * @return a builder based on an instance
     */
    static Annotation.Builder builder(Annotation instance) {
        return Annotation.builder().from(instance);
    }

    /**
     * Creates an instance for an annotation with no value.
     *
     * @param annoType the annotation type
     * @return the new instance
     */
    static Annotation create(Class<? extends java.lang.annotation.Annotation> annoType) {
        return AnnotationSupport.create(annoType);
    }

    /**
     * Creates an instance for an annotation with no value.
     *
     * @param annoType the annotation type
     * @return the new instance
     */
    static Annotation create(TypeName annoType) {
        return AnnotationSupport.create(annoType);
    }

    /**
     * Creates an instance for an annotation with a value.
     *
     * @param annoType the annotation type
     * @param value    the annotation value
     * @return the new instance
     */
    static Annotation create(Class<? extends java.lang.annotation.Annotation> annoType, String value) {
        return AnnotationSupport.create(annoType, value);
    }

    /**
     * Creates an instance for an annotation with a value.
     *
     * @param annoType the annotation type
     * @param values   the annotation values
     * @return the new instance
     */
    static Annotation create(Class<? extends java.lang.annotation.Annotation> annoType, Map<String, ?> values) {
        return AnnotationSupport.create(annoType, values);
    }

    /**
     * Creates an instance for an annotation with a value.
     *
     * @param annoTypeName the annotation type name
     * @param value the annotation value
     * @return the new instance
     */
    static Annotation create(TypeName annoTypeName, String value) {
        return AnnotationSupport.create(annoTypeName, value);
    }

    /**
     * Creates an instance for annotation with zero or more values.
     *
     * @param annoTypeName the annotation type name
     * @param values the annotation values
     * @return the new instance
     */
    static Annotation create(TypeName annoTypeName, Map<String, ?> values) {
        return AnnotationSupport.create(annoTypeName, values);
    }

    /**
     * Fluent API builder base for {@link Annotation}.
     *
     * @param <BUILDER> type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends Annotation.BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends Annotation> implements Prototype.Builder<BUILDER, PROTOTYPE> {

        private final List<Annotation> metaAnnotations = new ArrayList<>();
        private final Map<String, Object> values = new LinkedHashMap<>();
        private boolean isMetaAnnotationsMutated;
        private TypeName typeName;

        /**
         * Protected to support extensibility.
         */
        protected BuilderBase() {
        }

        /**
         * Update this builder from an existing prototype instance. This method disables automatic service discovery.
         *
         * @param prototype existing prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(Annotation prototype) {
            typeName(prototype.typeName());
            addValues(prototype.values());
            if (!isMetaAnnotationsMutated) {
                metaAnnotations.clear();
            }
            addMetaAnnotations(prototype.metaAnnotations());
            return self();
        }

        /**
         * Update this builder from an existing prototype builder instance.
         *
         * @param builder existing builder prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(Annotation.BuilderBase<?, ?> builder) {
            builder.typeName().ifPresent(this::typeName);
            addValues(builder.values);
            if (isMetaAnnotationsMutated) {
                if (builder.isMetaAnnotationsMutated) {
                    addMetaAnnotations(builder.metaAnnotations);
                }
            } else {
                metaAnnotations.clear();
                addMetaAnnotations(builder.metaAnnotations);
            }
            return self();
        }

        /**
         * Annotation type name from annotation type.
         *
         * @param annoType annotation class
         * @return updated builder instance
         */
        public BUILDER type(Type annoType) {
            AnnotationSupport.type(this, annoType);
            return self();
        }

        /**
         * Configure the value of this annotation (property of name {@code value}).
         *
         * @param value   value of the annotation
         * @return updated builder instance
         */
        public BUILDER value(String value) {
            AnnotationSupport.value(this, value);
            return self();
        }

        /**
         * The type name, e.g., {@link java.util.Objects} -> "java.util.Objects".
         *
         * @param typeName the annotation type name
         * @return updated builder instance
         * @see #typeName()
         */
        public BUILDER typeName(TypeName typeName) {
            Objects.requireNonNull(typeName);
            this.typeName = typeName;
            return self();
        }

        /**
         * The type name, e.g., {@link java.util.Objects} -> "java.util.Objects".
         *
         * @param consumer consumer of builder for
         *                 the annotation type name
         * @return updated builder instance
         * @see #typeName()
         */
        public BUILDER typeName(Consumer<TypeName.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeName.builder();
            consumer.accept(builder);
            this.typeName(builder.build());
            return self();
        }

        /**
         * The type name, e.g., {@link java.util.Objects} -> "java.util.Objects".
         *
         * @param supplier supplier of
         *                 the annotation type name
         * @return updated builder instance
         * @see #typeName()
         */
        public BUILDER typeName(Supplier<? extends TypeName> supplier) {
            Objects.requireNonNull(supplier);
            this.typeName(supplier.get());
            return self();
        }

        /**
         * This method replaces all values with the new ones.
         *
         * @param values key-value pairs of all the properties present
         * @return updated builder instance
         * @see #values()
         */
        public BUILDER values(Map<? extends String, ?> values) {
            Objects.requireNonNull(values);
            this.values.clear();
            this.values.putAll(values);
            return self();
        }

        /**
         * This method keeps existing values, then puts all new values into the map.
         *
         * @param values key-value pairs of all the properties present
         * @return updated builder instance
         * @see #values()
         */
        public BUILDER addValues(Map<? extends String, ?> values) {
            Objects.requireNonNull(values);
            this.values.putAll(values);
            return self();
        }

        /**
         * This method adds a new value to the map, or replaces it if the key already exists.
         *
         * @param key key to add or replace
         * @param value new value for the key
         * @return updated builder instance
         * @see #values()
         */
        public BUILDER putValue(String key, Object value) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(value);
            this.values.put(key, value);
            return self();
        }

        /**
         * A list of inherited annotations (from the whole hierarchy).
         *
         * @param metaAnnotations list of all annotations declared on the annotation type, or inherited from them
         * @return updated builder instance
         * @see #metaAnnotations()
         */
        public BUILDER metaAnnotations(List<? extends Annotation> metaAnnotations) {
            Objects.requireNonNull(metaAnnotations);
            isMetaAnnotationsMutated = true;
            this.metaAnnotations.clear();
            this.metaAnnotations.addAll(metaAnnotations);
            return self();
        }

        /**
         * A list of inherited annotations (from the whole hierarchy).
         *
         * @param metaAnnotations list of all annotations declared on the annotation type, or inherited from them
         * @return updated builder instance
         * @see #metaAnnotations()
         */
        public BUILDER addMetaAnnotations(List<? extends Annotation> metaAnnotations) {
            Objects.requireNonNull(metaAnnotations);
            isMetaAnnotationsMutated = true;
            this.metaAnnotations.addAll(metaAnnotations);
            return self();
        }

        /**
         * A list of inherited annotations (from the whole hierarchy).
         *
         * @param metaAnnotation list of all annotations declared on the annotation type, or inherited from them
         * @return updated builder instance
         * @see #metaAnnotations()
         */
        public BUILDER addMetaAnnotation(Annotation metaAnnotation) {
            Objects.requireNonNull(metaAnnotation);
            this.metaAnnotations.add(metaAnnotation);
            isMetaAnnotationsMutated = true;
            return self();
        }

        /**
         * A list of inherited annotations (from the whole hierarchy).
         *
         * @param consumer list of all annotations declared on the annotation type, or inherited from them
         * @return updated builder instance
         * @see #metaAnnotations()
         */
        public BUILDER addMetaAnnotation(Consumer<Annotation.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = Annotation.builder();
            consumer.accept(builder);
            this.metaAnnotations.add(builder.build());
            return self();
        }

        /**
         * The type name, e.g., {@link java.util.Objects} -> "java.util.Objects".
         *
         * @return the type name
         */
        public Optional<TypeName> typeName() {
            return Optional.ofNullable(typeName);
        }

        /**
         * Get a key-value of all the annotation properties.
         *
         * @return the values
         */
        public Map<String, Object> values() {
            return values;
        }

        /**
         * A list of inherited annotations (from the whole hierarchy).
         *
         * @return the meta annotations
         */
        public List<Annotation> metaAnnotations() {
            return metaAnnotations;
        }

        @Override
        public String toString() {
            return "AnnotationBuilder{"
                    + "typeName=" + typeName + ","
                    + "values=" + values
                    + "}";
        }

        /**
         * Handles providers and decorators.
         */
        protected void preBuildPrototype() {
        }

        /**
         * Validates required properties.
         */
        protected void validatePrototype() {
            Errors.Collector collector = Errors.collector();
            if (typeName == null) {
                collector.fatal(getClass(), "Property \"typeName\" is required, but not set");
            }
            collector.collect().checkValid();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class AnnotationImpl implements Annotation {

            private final List<Annotation> metaAnnotations;
            private final Map<String, Object> values;
            private final TypeName typeName;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected AnnotationImpl(Annotation.BuilderBase<?, ?> builder) {
                this.typeName = builder.typeName().get();
                this.values = Collections.unmodifiableMap(new LinkedHashMap<>(builder.values()));
                this.metaAnnotations = List.copyOf(builder.metaAnnotations());
            }

            @Override
            public int compareTo(Annotation o) {
                return AnnotationSupport.compareTo(this, o);
            }

            @Override
            public TypeName typeName() {
                return typeName;
            }

            @Override
            public Map<String, Object> values() {
                return values;
            }

            @Override
            public List<Annotation> metaAnnotations() {
                return metaAnnotations;
            }

            @Override
            public String toString() {
                return "Annotation{"
                        + "typeName=" + typeName + ","
                        + "values=" + values
                        + "}";
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof Annotation other)) {
                    return false;
                }
                return Objects.equals(typeName, other.typeName())
                    && Objects.equals(values, other.values());
            }

            @Override
            public int hashCode() {
                return Objects.hash(typeName, values);
            }

        }

    }

    /**
     * Fluent API builder for {@link Annotation}.
     */
    class Builder extends Annotation.BuilderBase<Annotation.Builder, Annotation> implements io.helidon.common.Builder<Annotation.Builder, Annotation> {

        private Builder() {
        }

        @Override
        public Annotation buildPrototype() {
            preBuildPrototype();
            validatePrototype();
            return new AnnotationImpl(this);
        }

        @Override
        public Annotation build() {
            return buildPrototype();
        }

    }

}
