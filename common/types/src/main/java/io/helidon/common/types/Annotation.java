/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import java.util.Objects;

import io.helidon.common.Errors;

/**
 * An annotation with defined values.
 *
 * @see #builder()
 */
@io.helidon.common.Generated(value = "io.helidon.builder.processor.BlueprintProcessor",
                             trigger = "io.helidon.common.types.AnnotationBlueprint")
public interface Annotation extends AnnotationBlueprint, io.helidon.builder.api.Prototype, Comparable<Annotation> {
    /**
     * Create a new fluent API builder to customize configuration.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new fluent API builder from an existing instance.
     *
     * @param instance an existing instance used as a base for the builder
     * @return a builder based on an instance
     */
    static Builder builder(Annotation instance) {
        return Annotation.builder().from(instance);
    }

    /**
     *Creates an instance for an annotation with no value.
     *
     * @param annoType the annotation type
     * @return the new instance
     */
    static Annotation create(Class<? extends java.lang.annotation.Annotation> annoType) {
        return AnnotationSupport.create(annoType);
    }

    /**
     *Creates an instance for an annotation with no value.
     *
     * @param annoType the annotation type
     * @return the new instance
     */
    static Annotation create(TypeName annoType) {
        return AnnotationSupport.create(annoType);
    }

    /**
     *Creates an instance for an annotation with a value.
     *
     * @param annoType the annotation type
     * @param value the annotation value
     * @return the new instance
     */
    static Annotation create(Class<? extends java.lang.annotation.Annotation> annoType, String value) {
        return AnnotationSupport.create(annoType, value);
    }

    /**
     *Creates an instance for an annotation with a value.
     *
     * @param annoType the annotation type
     * @param values the annotation values
     * @return the new instance
     */
    static Annotation create(Class<? extends java.lang.annotation.Annotation> annoType,
                             java.util.Map<String, String> values) {
        return AnnotationSupport.create(annoType, values);
    }

    /**
     *Creates an instance for an annotation with a value.
     *
     * @param annoTypeName the annotation type name
     * @param value the annotation value
     * @return the new instance
     */
    static Annotation create(TypeName annoTypeName, String value) {
        return AnnotationSupport.create(annoTypeName, value);
    }

    /**
     *Creates an instance for annotation with zero or more values.
     *
     * @param annoTypeName the annotation type name
     * @param values the annotation values
     * @return the new instance
     */
    static Annotation create(TypeName annoTypeName, java.util.Map<String, String> values) {
        return AnnotationSupport.create(annoTypeName, values);
    }

    /**
     * Fluent API builder base for {@link io.helidon.common.types.Annotation}.
     *
     * @param <BUILDER> type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends Annotation>
            implements io.helidon.builder.api.Prototype.Builder<BUILDER, PROTOTYPE>, Annotation {
        private final java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
        private TypeName typeName;

        /**
         * Protected to support extensibility.
         *
         */
        protected BuilderBase() {
        }

        /**
         * Update this builder from an existing prototype instance.
         *
         * @param prototype existing prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(Annotation prototype) {
            typeName(prototype.typeName());
            addValues(prototype.values());
            return me();
        }

        /**
         * Update this builder from an existing prototype builder instance.
         *
         * @param builder existing builder prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(Annotation.BuilderBase<?, ?> builder) {
            if (builder.typeName() != null) {
                typeName(builder.typeName());
            }
            addValues(builder.values());
            return me();
        }

        /**
         * Handles providers and interceptors.
         */
        protected void preBuildPrototype() {
        }

        /**
         * Validates required properties.
         */
        protected void validatePrototype() {
            Errors.Collector collector = Errors.collector();
            if (typeName == null) {
                collector.fatal(getClass(), "Property \"type-name\" is required, but not set");
            }
            collector.collect().checkValid();
        }

        @Override
        public int compareTo(Annotation o) {
            return AnnotationSupport.compareTo(this, o);
        }

        /**
         *Annotation type name from annotation type.
         *
         *@param annoType annotation class
         *@return updated builder instance
         */
        public BUILDER type(java.lang.reflect.Type annoType) {
            AnnotationSupport.type(this, annoType);
            return me();
        }

        /**
         *Configure the value of this annotation (property of name {@code value}).
         *
         *@param value value of the annotation
         *@return updated builder instance
         */
        public BUILDER value(String value) {
            AnnotationSupport.value(this, value);
            return me();
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
            return me();
        }

        /**
         * The type name, e.g., {@link java.util.Objects} -> "java.util.Objects".
         *
         * @param consumer consumer of builder for
         * the annotation type name
         * @return updated builder instance
         * @see #typeName()
         */
        public BUILDER typeName(java.util.function.Consumer<TypeName.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeName.builder();
            consumer.accept(builder);
            this.typeName(builder.build());
            return me();
        }

        /**
         * Get a key-value of all the annotation properties.
         *
         * This method replaces all values with the new ones.
         * @param values key-value pairs of all the properties present
         * @return updated builder instance
         * @see #values()
         */
        public BUILDER values(java.util.Map<? extends String, ? extends String> values) {
            Objects.requireNonNull(values);
            this.values.clear();
            this.values.putAll(values);
            return me();
        }

        /**
         * Get a key-value of all the annotation properties.
         *
         * This method keeps existing values, then puts all new values into the map.
         * @param values key-value pairs of all the properties present
         * @return updated builder instance
         * @see #values()
         */
        public BUILDER addValues(java.util.Map<? extends String, ? extends String> values) {
            Objects.requireNonNull(values);
            this.values.putAll(values);
            return me();
        }

        /**
         * Get a key-value of all the annotation properties.
         *
         * This method adds a new value to the map, or replaces it if the key already exists.
         * @param key key to add or replace
         * @param value new value for the key
         * @return updated builder instance
         * @see #values()
         */
        public BUILDER putValue(String key, String value) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(value);
            this.values.put(key, value);
            return me();
        }

        /**
         * The type name, e.g., {@link java.util.Objects} -> "java.util.Objects".
         *
         * @return the type name
         */
        @Override
        public TypeName typeName() {
            return typeName;
        }

        /**
         * Get a key-value of all the annotation properties.
         *
         * @return the values
         */
        @Override
        public java.util.Map<String, String> values() {
            return values;
        }

        @Override
        public String toString() {
            return "AnnotationBuilder{"
                    + "typeName=" + typeName + ","
                    + "values=" + values
                    + "}";
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class AnnotationImpl implements Annotation {
            private final TypeName typeName;
            private final java.util.Map<String, String> values;

            /**
             * Create an instance providing a builder.
             * @param builder extending builder base of this prototype
             */
            protected AnnotationImpl(BuilderBase<?, ?> builder) {
                this.typeName = builder.typeName();
                this.values = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(builder.values()));
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
            public java.util.Map<String, String> values() {
                return values;
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
                return Objects.equals(typeName, other.typeName()) && Objects.equals(values, other.values());
            }

            @Override
            public int hashCode() {
                return Objects.hash(typeName, values);
            }
        }
    }

    /**
     * Fluent API builder for {@link io.helidon.common.types.Annotation}.
     */
    class Builder extends BuilderBase<Builder, Annotation> implements io.helidon.common.Builder<Builder, Annotation> {
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
