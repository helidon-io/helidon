/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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
     * Creates an instance for an annotation with a value.
     *
     * @param annoTypeName the annotation type name
     * @param value        the annotation value
     * @return the new instance
     */
    static Annotation create(TypeName annoTypeName, String value) {
        return AnnotationSupport.create(annoTypeName, value);
    }

    /**
     * Creates an instance for annotation with zero or more values.
     *
     * @param annoTypeName the annotation type name
     * @param values       the annotation values
     * @return the new instance
     */
    static Annotation create(TypeName annoTypeName, Map<String, ?> values) {
        return AnnotationSupport.create(annoTypeName, values);
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

    @Override
    default int compareTo(Annotation o) {
        return AnnotationSupport.compareTo(this, o);
    }

    /**
     * The value property.
     *
     * @return the string value of value property
     */
    default Optional<String> value() {
        return AnnotationBlueprint.super.value();
    }

    /**
     * Get a value of an annotation property.
     *
     * @param property name of the annotation property
     * @return string value of the property
     */
    default Optional<String> getValue(String property) {
        return AnnotationBlueprint.super.getValue(property);
    }

    /**
     * Annotation property for the {@value #VALUE_PROPERTY} property.
     *
     * @return annotation property
     */
    default Optional<AnnotationProperty> property() {
        return AnnotationBlueprint.super.property();
    }

    /**
     * Annotation property for the defined name.
     *
     * @param property property name
     * @return annotation property
     */
    default Optional<AnnotationProperty> property(String property) {
        return AnnotationBlueprint.super.property(property);
    }

    /**
     * Value of the annotation as an object.
     * The type can be either String, or any primitive type, or {@link io.helidon.common.types.Annotation},
     * or list of these.
     *
     * @return object value
     */
    default Optional<Object> objectValue() {
        return AnnotationBlueprint.super.objectValue();
    }

    /**
     * Value of the annotation property as an object.
     * The type can be either String, or any primitive type, or {@link io.helidon.common.types.Annotation},
     * or list of these.
     *
     * @param property name of the annotation property
     * @return object value
     */
    default Optional<Object> objectValue(String property) {
        return AnnotationBlueprint.super.objectValue(property);
    }

    /**
     * Typed value of the property "{@code value}".
     *
     * @return value if present
     */
    default Optional<String> stringValue() {
        return AnnotationBlueprint.super.stringValue();
    }

    /**
     * Typed value of a named property.
     *
     * @param property name of the annotation property
     * @return value if present
     */
    default Optional<String> stringValue(String property) {
        return AnnotationBlueprint.super.stringValue(property);
    }

    /**
     * Typed value of the property "{@code value}" that is defined as an array.
     * This will also work for a single values property.
     *
     * @return list of defined values if present
     */
    default Optional<List<String>> stringValues() {
        return AnnotationBlueprint.super.stringValues();
    }

    /**
     * Typed values of a property that is defined as an array.
     * This will also work for a single values property.
     *
     * @param property name of the annotation property
     * @return list of defined values if present
     */
    default Optional<List<String>> stringValues(String property) {
        return AnnotationBlueprint.super.stringValues(property);
    }

    /**
     * Typed value of the property "{@code value}".
     *
     * @return value if present
     */
    default Optional<Integer> intValue() {
        return AnnotationBlueprint.super.intValue();
    }

    /**
     * Typed value of a named property.
     *
     * @param property name of the annotation property
     * @return value if present
     */
    default Optional<Integer> intValue(String property) {
        return AnnotationBlueprint.super.intValue(property);
    }

    /**
     * Typed value of the property "{@code value}" that is defined as an array.
     * This will also work for a single values property.
     *
     * @return list of defined values if present
     */
    default Optional<List<Integer>> intValues() {
        return AnnotationBlueprint.super.intValues();
    }

    /**
     * Typed values of a property that is defined as an array.
     * This will also work for a single values property.
     *
     * @param property name of the annotation property
     * @return list of defined values if present
     */
    default Optional<List<Integer>> intValues(String property) {
        return AnnotationBlueprint.super.intValues(property);
    }

    /**
     * Typed value of the property "{@code value}".
     *
     * @return value if present
     */
    default Optional<Long> longValue() {
        return AnnotationBlueprint.super.longValue();
    }

    /**
     * Typed value of a named property.
     *
     * @param property name of the annotation property
     * @return value if present
     */
    default Optional<Long> longValue(String property) {
        return AnnotationBlueprint.super.longValue(property);
    }

    /**
     * Typed value of the property "{@code value}" that is defined as an array.
     * This will also work for a single values property.
     *
     * @return list of defined values if present
     */
    default Optional<List<Long>> longValues() {
        return AnnotationBlueprint.super.longValues();
    }

    /**
     * Typed values of a property that is defined as an array.
     * This will also work for a single values property.
     *
     * @param property name of the annotation property
     * @return list of defined values if present
     */
    default Optional<List<Long>> longValues(String property) {
        return AnnotationBlueprint.super.longValues(property);
    }

    /**
     * Typed value of the property "{@code value}".
     *
     * @return value if present
     */
    default Optional<Boolean> booleanValue() {
        return AnnotationBlueprint.super.booleanValue();
    }

    /**
     * Typed value of a named property.
     *
     * @param property name of the annotation property
     * @return value if present
     */
    default Optional<Boolean> booleanValue(String property) {
        return AnnotationBlueprint.super.booleanValue(property);
    }

    /**
     * Typed value of the property "{@code value}" that is defined as an array.
     * This will also work for a single values property.
     *
     * @return list of defined values if present
     */
    default Optional<List<Boolean>> booleanValues() {
        return AnnotationBlueprint.super.booleanValues();
    }

    /**
     * Typed values of a property that is defined as an array.
     * This will also work for a single values property.
     *
     * @param property name of the annotation property
     * @return list of defined values if present
     */
    default Optional<List<Boolean>> booleanValues(String property) {
        return AnnotationBlueprint.super.booleanValues(property);
    }

    /**
     * Typed value of the property "{@code value}".
     *
     * @return value if present
     */
    default Optional<Byte> byteValue() {
        return AnnotationBlueprint.super.byteValue();
    }

    /**
     * Typed value of a named property.
     *
     * @param property name of the annotation property
     * @return value if present
     */
    default Optional<Byte> byteValue(String property) {
        return AnnotationBlueprint.super.byteValue(property);
    }

    /**
     * Typed value of the property "{@code value}" that is defined as an array.
     * This will also work for a single values property.
     *
     * @return list of defined values if present
     */
    default Optional<List<Byte>> byteValues() {
        return AnnotationBlueprint.super.byteValues();
    }

    /**
     * Typed values of a property that is defined as an array.
     * This will also work for a single values property.
     *
     * @param property name of the annotation property
     * @return list of defined values if present
     */
    default Optional<List<Byte>> byteValues(String property) {
        return AnnotationBlueprint.super.byteValues(property);
    }

    /**
     * Typed value of the property "{@code value}".
     *
     * @return value if present
     */
    default Optional<Character> charValue() {
        return AnnotationBlueprint.super.charValue();
    }

    /**
     * Typed value of a named property.
     *
     * @param property name of the annotation property
     * @return value if present
     */
    default Optional<Character> charValue(String property) {
        return AnnotationBlueprint.super.charValue(property);
    }

    /**
     * Typed value of the property "{@code value}" that is defined as an array.
     * This will also work for a single values property.
     *
     * @return list of defined values if present
     */
    default Optional<List<Character>> charValues() {
        return AnnotationBlueprint.super.charValues();
    }

    /**
     * Typed values of a property that is defined as an array.
     * This will also work for a single values property.
     *
     * @param property name of the annotation property
     * @return list of defined values if present
     */
    default Optional<List<Character>> charValues(String property) {
        return AnnotationBlueprint.super.charValues(property);
    }

    /**
     * Typed value of the property "{@code value}".
     *
     * @return value if present
     */
    default Optional<Short> shortValue() {
        return AnnotationBlueprint.super.shortValue();
    }

    /**
     * Typed value of a named property.
     *
     * @param property name of the annotation property
     * @return value if present
     */
    default Optional<Short> shortValue(String property) {
        return AnnotationBlueprint.super.shortValue(property);
    }

    /**
     * Typed value of the property "{@code value}" that is defined as an array.
     * This will also work for a single values property.
     *
     * @return list of defined values if present
     */
    default Optional<List<Short>> shortValues() {
        return AnnotationBlueprint.super.shortValues();
    }

    /**
     * Typed values of a property that is defined as an array.
     * This will also work for a single values property.
     *
     * @param property name of the annotation property
     * @return list of defined values if present
     */
    default Optional<List<Short>> shortValues(String property) {
        return AnnotationBlueprint.super.shortValues(property);
    }

    /**
     * Typed value of the property "{@code value}".
     *
     * @return value if present
     */
    default Optional<Float> floatValue() {
        return AnnotationBlueprint.super.floatValue();
    }

    /**
     * Typed value of a named property.
     *
     * @param property name of the annotation property
     * @return value if present
     */
    default Optional<Float> floatValue(String property) {
        return AnnotationBlueprint.super.floatValue(property);
    }

    /**
     * Typed value of the property "{@code value}" that is defined as an array.
     * This will also work for a single values property.
     *
     * @return list of defined values if present
     */
    default Optional<List<Float>> floatValues() {
        return AnnotationBlueprint.super.floatValues();
    }

    /**
     * Typed values of a property that is defined as an array.
     * This will also work for a single values property.
     *
     * @param property name of the annotation property
     * @return list of defined values if present
     */
    default Optional<List<Float>> floatValues(String property) {
        return AnnotationBlueprint.super.floatValues(property);
    }

    /**
     * Typed value of the property "{@code value}".
     *
     * @return value if present
     */
    default Optional<Double> doubleValue() {
        return AnnotationBlueprint.super.doubleValue();
    }

    /**
     * Typed value of a named property.
     *
     * @param property name of the annotation property
     * @return value if present
     */
    default Optional<Double> doubleValue(String property) {
        return AnnotationBlueprint.super.doubleValue(property);
    }

    /**
     * Typed value of the property "{@code value}" that is defined as an array.
     * This will also work for a single values property.
     *
     * @return list of defined values if present
     */
    default Optional<List<Double>> doubleValues() {
        return AnnotationBlueprint.super.doubleValues();
    }

    /**
     * Typed values of a property that is defined as an array.
     * This will also work for a single values property.
     *
     * @param property name of the annotation property
     * @return list of defined values if present
     */
    default Optional<List<Double>> doubleValues(String property) {
        return AnnotationBlueprint.super.doubleValues(property);
    }

    /**
     * Typed value of the property "{@code value}".
     *
     * @return value if present
     */
    default Optional<Class<?>> classValue() {
        return AnnotationBlueprint.super.classValue();
    }

    /**
     * Typed value of a named property.
     *
     * @param property name of the annotation property
     * @return value if present
     */
    default Optional<Class<?>> classValue(String property) {
        return AnnotationBlueprint.super.classValue(property);
    }

    /**
     * Typed value of the property "{@code value}" that is defined as an array.
     * This will also work for a single values property.
     *
     * @return list of defined values if present
     */
    default Optional<List<Class<?>>> classValues() {
        return AnnotationBlueprint.super.classValues();
    }

    /**
     * Typed values of a property that is defined as an array.
     * This will also work for a single values property.
     *
     * @param property name of the annotation property
     * @return list of defined values if present
     */
    default Optional<List<Class<?>>> classValues(String property) {
        return AnnotationBlueprint.super.classValues(property);
    }

    /**
     * Typed value of the property "{@code value}".
     * Alternative for {@link #classValue()}.
     *
     * @return value if present
     */
    default Optional<TypeName> typeValue() {
        return AnnotationBlueprint.super.typeValue();
    }

    /**
     * Typed value of a named property.
     * Alternative for {@link #classValue(String)}.
     *
     * @param property name of the annotation property
     * @return value if present
     */
    default Optional<TypeName> typeValue(String property) {
        return AnnotationBlueprint.super.typeValue(property);
    }

    /**
     * Typed value of the property "{@code value}" that is defined as an array.
     * This will also work for a single values property.
     * Alternative for {@link #classValues()}.
     *
     * @return list of defined values if present
     */
    default Optional<List<TypeName>> typeValues() {
        return AnnotationBlueprint.super.typeValues();
    }

    /**
     * Typed values of a property that is defined as an array.
     * This will also work for a single values property.
     * Alternative for {@link #classValues(String)}.
     *
     * @param property name of the annotation property
     * @return list of defined values if present
     */
    default Optional<List<TypeName>> typeValues(String property) {
        return AnnotationBlueprint.super.typeValues(property);
    }

    /**
     * Typed value of the property "{@code value}".
     *
     * @return value if present
     */
    default Optional<Annotation> annotationValue() {
        return AnnotationBlueprint.super.annotationValue();
    }

    /**
     * Typed value of a named property.
     *
     * @param property name of the annotation property
     * @return value if present
     */
    default Optional<Annotation> annotationValue(String property) {
        return AnnotationBlueprint.super.annotationValue(property);
    }

    /**
     * Typed value of the property "{@code value}" that is defined as an array.
     * This will also work for a single values property.
     *
     * @return list of defined values if present
     */
    default Optional<List<Annotation>> annotationValues() {
        return AnnotationBlueprint.super.annotationValues();
    }

    /**
     * Typed values of a property that is defined as an array.
     * This will also work for a single values property.
     *
     * @param property name of the annotation property
     * @return list of defined values if present
     */
    default Optional<List<Annotation>> annotationValues(String property) {
        return AnnotationBlueprint.super.annotationValues(property);
    }

    /**
     * Typed value of the property "{@code value}".
     *
     * @param type class of the enumeration
     * @param <T>  type of the enumeration
     * @return value if present
     */
    default <T extends java.lang.Enum<T>> Optional<T> enumValue(Class<T> type) {
        return AnnotationBlueprint.super.enumValue(type);
    }

    /**
     * Typed value of a named property.
     *
     * @param property name of the annotation property
     * @param type     class of the enumeration
     * @param <T>      type of the enumeration
     * @return value if present
     */
    default <T extends java.lang.Enum<T>> Optional<T> enumValue(String property, Class<T> type) {
        return AnnotationBlueprint.super.enumValue(property, type);
    }

    /**
     * Typed value of the property "{@code value}" that is defined as an array.
     * This will also work for a single values property.
     *
     * @param type class of the enumeration
     * @param <T>  type of the enumeration
     * @return list of defined values if present
     */
    default <T extends java.lang.Enum<T>> Optional<List<T>> enumValues(Class<T> type) {
        return AnnotationBlueprint.super.enumValues(type);
    }

    /**
     * Typed values of a property that is defined as an array.
     * This will also work for a single values property.
     *
     * @param property name of the annotation property
     * @param type     class of the enumeration
     * @param <T>      type of the enumeration
     * @return list of defined values if present
     */
    default <T extends java.lang.Enum<T>> Optional<List<T>> enumValues(String property, Class<T> type) {
        return AnnotationBlueprint.super.enumValues(property, type);
    }

    /**
     * Check if {@link io.helidon.common.types.Annotation#metaAnnotations()} contains an annotation of the provided type.
     * <p>
     * Note: we ignore {@link java.lang.annotation.Target}, {@link java.lang.annotation.Inherited},
     * {@link java.lang.annotation.Documented}, and {@link java.lang.annotation.Retention}.
     *
     * @param annotationType type of annotation
     * @return {@code true} if the annotation is declared on this annotation, or is inherited from a declared annotation
     */
    default boolean hasMetaAnnotation(TypeName annotationType) {
        return AnnotationBlueprint.super.hasMetaAnnotation(annotationType);
    }

    /**
     * The type name, e.g., {@link java.util.Objects} -> "java.util.Objects".
     *
     * @return the annotation type name
     */
    @Override
    TypeName typeName();

    /**
     * Key-value map of all the annotation properties.
     *
     * @return key-value pairs of all the properties present
     * @deprecated use {@link io.helidon.common.types.Annotation#properties} instead, and accessor methods on this interface
     */
    @Deprecated(since = "4.3.0", forRemoval = true)
    @Override
    Map<String, Object> values();

    /**
     * List of properties defined on this annotation.
     *
     * @return properties
     */
    @Override
    Map<String, AnnotationProperty> properties();

    /**
     * A list of inherited annotations (from the whole hierarchy).
     *
     * @return list of all annotations declared on the annotation type, or inherited from them
     */
    @Override
    List<Annotation> metaAnnotations();

    /**
     * Fluent API builder base for {@link Annotation}.
     *
     * @param <BUILDER> type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends Annotation.BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends Annotation> implements Prototype.Builder<BUILDER, PROTOTYPE> {

        private final List<Annotation> metaAnnotations = new ArrayList<>();
        private boolean isMetaAnnotationsMutated;
        private boolean isPropertiesMutated;
        private boolean isValuesMutated;
        private Map<String, AnnotationProperty> properties = new LinkedHashMap<>();
        private Map<String, Object> values = new LinkedHashMap<>();
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
            if (!this.isValuesMutated) {
                this.values.clear();
            }
            addValues(prototype.values());
            if (!this.isPropertiesMutated) {
                this.properties.clear();
            }
            addProperties(prototype.properties());
            if (!this.isMetaAnnotationsMutated) {
                this.metaAnnotations.clear();
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
            if (this.isValuesMutated) {
                if (builder.isValuesMutated) {
                    addValues(builder.values());
                }
            } else {
                values(builder.values());
            }
            if (this.isPropertiesMutated) {
                if (builder.isPropertiesMutated) {
                    addProperties(builder.properties());
                }
            } else {
                properties(builder.properties());
            }
            if (this.isMetaAnnotationsMutated) {
                if (builder.isMetaAnnotationsMutated) {
                    addMetaAnnotations(builder.metaAnnotations());
                }
            } else {
                metaAnnotations(builder.metaAnnotations());
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
         * @param consumer consumer of builder of the annotation type name
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
         * @param supplier supplier of the annotation type name
         * @return updated builder instance
         * @see #typeName()
         */
        public BUILDER typeName(Supplier<? extends TypeName> supplier) {
            Objects.requireNonNull(supplier);
            this.typeName(supplier.get());
            return self();
        }

        /**
         * Key-value map of all the annotation properties.
         * This method replaces all values with the new ones.
         *
         * @param values key-value pairs of all the properties present
         * @return updated builder instance
         * @deprecated use {@link io.helidon.common.types.Annotation#properties} instead, and accessor methods on this interface
         * @see #values()
         */
        @Deprecated(since = "4.3.0", forRemoval = true)
        public BUILDER values(Map<String, ?> values) {
            Objects.requireNonNull(values);
            this.values.clear();
            this.values.putAll(values);
            this.isValuesMutated = true;
            return self();
        }

        /**
         * Key-value map of all the annotation properties.
         * This method keeps existing values, then puts all new values into the map.
         *
         * @param values key-value pairs of all the properties present
         * @return updated builder instance
         * @deprecated use {@link io.helidon.common.types.Annotation#properties} instead, and accessor methods on this interface
         * @see #values()
         */
        @Deprecated(since = "4.3.0", forRemoval = true)
        public BUILDER addValues(Map<String, ?> values) {
            Objects.requireNonNull(values);
            this.values.putAll(values);
            this.isValuesMutated = true;
            return self();
        }

        /**
         * Key-value map of all the annotation properties.
         * This method adds a new value to the map, or replaces it if the key already exists.
         *
         * @param key key to add or replace
         * @param value new value for the key
         * @return updated builder instance
         * @deprecated use {@link io.helidon.common.types.Annotation#properties} instead, and accessor methods on this interface
         * @see #values()
         */
        @Deprecated(since = "4.3.0", forRemoval = true)
        public BUILDER putValue(String key, Object value) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(value);
            this.values.put(key, value);
            this.isValuesMutated = true;
            return self();
        }

        /**
         * List of properties defined on this annotation.
         * This method replaces all values with the new ones.
         *
         * @param properties properties
         * @return updated builder instance
         * @see #properties()
         */
        public BUILDER properties(Map<String, ? extends AnnotationProperty> properties) {
            Objects.requireNonNull(properties);
            this.properties.clear();
            this.properties.putAll(properties);
            this.isPropertiesMutated = true;
            return self();
        }

        /**
         * List of properties defined on this annotation.
         * This method keeps existing values, then puts all new values into the map.
         *
         * @param properties properties
         * @return updated builder instance
         * @see #properties()
         */
        public BUILDER addProperties(Map<String, ? extends AnnotationProperty> properties) {
            Objects.requireNonNull(properties);
            this.properties.putAll(properties);
            this.isPropertiesMutated = true;
            return self();
        }

        /**
         * List of properties defined on this annotation.
         * This method adds a new value to the map, or replaces it if the key already exists.
         *
         * @param key key to add or replace
         * @param property new value for the key
         * @return updated builder instance
         * @see #properties()
         */
        public BUILDER putProperty(String key, AnnotationProperty property) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(property);
            this.properties.put(key, property);
            this.isPropertiesMutated = true;
            return self();
        }

        /**
         * Clear all metaAnnotations.
         *
         * @return updated builder instance
         * @see #metaAnnotations()
         */
        public BUILDER clearMetaAnnotations() {
            this.isMetaAnnotationsMutated = true;
            this.metaAnnotations.clear();
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
            this.isMetaAnnotationsMutated = true;
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
            this.isMetaAnnotationsMutated = true;
            this.metaAnnotations.addAll(metaAnnotations);
            return self();
        }

        /**
         * A list of inherited annotations (from the whole hierarchy).
         *
         * @param metaAnnotation add single list of all annotations declared on the annotation type, or inherited from them
         * @return updated builder instance
         * @see #metaAnnotations()
         */
        public BUILDER addMetaAnnotation(Annotation metaAnnotation) {
            Objects.requireNonNull(metaAnnotation);
            this.metaAnnotations.add(metaAnnotation);
            this.isMetaAnnotationsMutated = true;
            return self();
        }

        /**
         * A list of inherited annotations (from the whole hierarchy).
         *
         * @param consumer consumer of builder for list of all annotations declared on the annotation type, or inherited from them
         * @return updated builder instance
         * @see #metaAnnotations()
         */
        public BUILDER addMetaAnnotation(Consumer<Annotation.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = Annotation.builder();
            consumer.accept(builder);
            this.addMetaAnnotation(builder.build());
            return self();
        }

        /**
         * The type name, e.g., {@link java.util.Objects} -> "java.util.Objects".
         *
         * @return the annotation type name
         */
        public Optional<TypeName> typeName() {
            return Optional.ofNullable(typeName);
        }

        /**
         * Key-value map of all the annotation properties.
         *
         * @return key-value pairs of all the properties present
         * @deprecated use {@link io.helidon.common.types.Annotation#properties} instead, and accessor methods on this interface
         */
        @Deprecated(since = "4.3.0", forRemoval = true)
        public Map<String, Object> values() {
            return values;
        }

        /**
         * List of properties defined on this annotation.
         *
         * @return properties
         */
        public Map<String, AnnotationProperty> properties() {
            return properties;
        }

        /**
         * A list of inherited annotations (from the whole hierarchy).
         *
         * @return list of all annotations declared on the annotation type, or inherited from them
         */
        public List<Annotation> metaAnnotations() {
            return metaAnnotations;
        }

        @Override
        public String toString() {
            return "AnnotationBuilder{"
                    + "typeName=" + typeName + ","
                    + "properties=" + properties
                    + "}";
        }

        /**
         * Handles providers and decorators.
         */
        protected void preBuildPrototype() {
            new AnnotationSupport.AnnotationDecorator().decorate(this);
        }

        /**
         * Validates required properties.
         */
        protected void validatePrototype() {
            Errors.Collector collector = Errors.collector();
            if (typeName == null) {
                collector.fatal(getClass(), "Property \"typeName\" must not be null, but not set");
            }
            collector.collect().checkValid();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class AnnotationImpl implements Annotation {

            private final List<Annotation> metaAnnotations;
            private final Map<String, AnnotationProperty> properties;
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
                this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(builder.properties()));
                this.metaAnnotations = List.copyOf(builder.metaAnnotations());
            }

            @Override
            public TypeName typeName() {
                return typeName;
            }

            @Override
            @Deprecated(since = "4.3.0", forRemoval = true)
            public Map<String, Object> values() {
                return values;
            }

            @Override
            public Map<String, AnnotationProperty> properties() {
                return properties;
            }

            @Override
            public List<Annotation> metaAnnotations() {
                return metaAnnotations;
            }

            @Override
            public String toString() {
                return "Annotation{"
                        + "typeName=" + typeName + ","
                        + "properties=" + properties
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
                    && Objects.equals(properties, other.properties());
            }

            @Override
            public int hashCode() {
                return Objects.hash(typeName, properties);
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
