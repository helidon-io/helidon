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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

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
 */
@Prototype.Blueprint
@Prototype.CustomMethods(AnnotationSupport.class)
@Prototype.Implement("java.lang.Comparable<Annotation>")
interface AnnotationBlueprint {

    /**
     * The "{@code value}" property name.
     */
    String VALUE_PROPERTY = "value";

    /**
     * The type name, e.g., {@link java.util.Objects} -> "java.util.Objects".
     *
     * @return the annotation type name
     */
    @Option.Required
    TypeName typeName();

    /**
     * Get a key-value of all the annotation properties.
     *
     * @return key-value pairs of all the properties present
     */
    @Option.Singular
    Map<String, Object> values();

    /**
     * A list of inherited annotations (from the whole hierarchy).
     *
     * @return list of all annotations declared on the annotation type, or inherited from them
     */
    @Option.Redundant
    @Option.Singular
    List<Annotation> metaAnnotations();

    /**
     * The value property.
     *
     * @return the string value of value property
     */
    default Optional<String> value() {
        return getValue(VALUE_PROPERTY);
    }

    /**
     * Get a value of an annotation property.
     *
     * @param property name of the annotation property
     * @return string value of the property
     */
    default Optional<String> getValue(String property) {
        return AnnotationSupport.asString(typeName(), values(), property);
    }

    /**
     * Value of the annotation as an object.
     * The type can be either String, or any primitive type, or {@link io.helidon.common.types.Annotation},
     * or list of these.
     *
     * @return object value
     */
    default Optional<Object> objectValue() {
        return objectValue(VALUE_PROPERTY);
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
        return Optional.ofNullable(values().get(property));
    }

    /**
     * Typed value of the property "{@code value}".
     *
     * @return value if present
     */
    default Optional<String> stringValue() {
        return value();
    }

    /**
     * Typed value of a named property.
     *
     * @param property name of the annotation property
     * @return value if present
     */
    default Optional<String> stringValue(String property) {
        return getValue(property);
    }

    /**
     * Typed value of the property "{@code value}" that is defined as an array.
     * This will also work for a single values property.
     *
     * @return list of defined values if present
     */
    default Optional<List<String>> stringValues() {
        return stringValues(VALUE_PROPERTY);
    }

    /**
     * Typed values of a property that is defined as an array.
     * This will also work for a single values property.
     *
     * @param property name of the annotation property
     * @return list of defined values if present
     */
    default Optional<List<String>> stringValues(String property) {
        return AnnotationSupport.asStrings(typeName(), values(), property);
    }

    /**
     * Typed value of the property "{@code value}".
     *
     * @return value if present
     */
    default Optional<Integer> intValue() {
        return intValue(VALUE_PROPERTY);
    }

    /**
     * Typed value of a named property.
     *
     * @param property name of the annotation property
     * @return value if present
     */
    default Optional<Integer> intValue(String property) {
        return AnnotationSupport.asInt(typeName(), values(), property);
    }

    /**
     * Typed value of the property "{@code value}" that is defined as an array.
     * This will also work for a single values property.
     *
     * @return list of defined values if present
     */
    default Optional<List<Integer>> intValues() {
        return intValues(VALUE_PROPERTY);
    }

    /**
     * Typed values of a property that is defined as an array.
     * This will also work for a single values property.
     *
     * @param property name of the annotation property
     * @return list of defined values if present
     */
    default Optional<List<Integer>> intValues(String property) {
        return AnnotationSupport.asInts(typeName(), values(), property);
    }

    /**
     * Typed value of the property "{@code value}".
     *
     * @return value if present
     */
    default Optional<Long> longValue() {
        return longValue(VALUE_PROPERTY);
    }

    /**
     * Typed value of a named property.
     *
     * @param property name of the annotation property
     * @return value if present
     */
    default Optional<Long> longValue(String property) {
        return AnnotationSupport.asLong(typeName(), values(), property);
    }

    /**
     * Typed value of the property "{@code value}" that is defined as an array.
     * This will also work for a single values property.
     *
     * @return list of defined values if present
     */
    default Optional<List<Long>> longValues() {
        return longValues(VALUE_PROPERTY);
    }

    /**
     * Typed values of a property that is defined as an array.
     * This will also work for a single values property.
     *
     * @param property name of the annotation property
     * @return list of defined values if present
     */
    default Optional<List<Long>> longValues(String property) {
        return AnnotationSupport.asLongs(typeName(), values(), property);
    }

    /**
     * Typed value of the property "{@code value}".
     *
     * @return value if present
     */
    default Optional<Boolean> booleanValue() {
        return booleanValue(VALUE_PROPERTY);
    }

    /**
     * Typed value of a named property.
     *
     * @param property name of the annotation property
     * @return value if present
     */
    default Optional<Boolean> booleanValue(String property) {
        return AnnotationSupport.asBoolean(typeName(), values(), property);
    }

    /**
     * Typed value of the property "{@code value}" that is defined as an array.
     * This will also work for a single values property.
     *
     * @return list of defined values if present
     */
    default Optional<List<Boolean>> booleanValues() {
        return booleanValues(VALUE_PROPERTY);
    }

    /**
     * Typed values of a property that is defined as an array.
     * This will also work for a single values property.
     *
     * @param property name of the annotation property
     * @return list of defined values if present
     */
    default Optional<List<Boolean>> booleanValues(String property) {
        return AnnotationSupport.asBooleans(typeName(), values(), property);
    }

    /**
     * Typed value of the property "{@code value}".
     *
     * @return value if present
     */
    default Optional<Byte> byteValue() {
        return byteValue(VALUE_PROPERTY);
    }

    /**
     * Typed value of a named property.
     *
     * @param property name of the annotation property
     * @return value if present
     */
    default Optional<Byte> byteValue(String property) {
        return AnnotationSupport.asByte(typeName(), values(), property);
    }

    /**
     * Typed value of the property "{@code value}" that is defined as an array.
     * This will also work for a single values property.
     *
     * @return list of defined values if present
     */
    default Optional<List<Byte>> byteValues() {
        return byteValues(VALUE_PROPERTY);
    }

    /**
     * Typed values of a property that is defined as an array.
     * This will also work for a single values property.
     *
     * @param property name of the annotation property
     * @return list of defined values if present
     */
    default Optional<List<Byte>> byteValues(String property) {
        return AnnotationSupport.asBytes(typeName(), values(), property);
    }

    /**
     * Typed value of the property "{@code value}".
     *
     * @return value if present
     */
    default Optional<Character> charValue() {
        return charValue(VALUE_PROPERTY);
    }

    /**
     * Typed value of a named property.
     *
     * @param property name of the annotation property
     * @return value if present
     */
    default Optional<Character> charValue(String property) {
        return AnnotationSupport.asCharacter(typeName(), values(), property);
    }

    /**
     * Typed value of the property "{@code value}" that is defined as an array.
     * This will also work for a single values property.
     *
     * @return list of defined values if present
     */
    default Optional<List<Character>> charValues() {
        return charValues(VALUE_PROPERTY);
    }

    /**
     * Typed values of a property that is defined as an array.
     * This will also work for a single values property.
     *
     * @param property name of the annotation property
     * @return list of defined values if present
     */
    default Optional<List<Character>> charValues(String property) {
        return AnnotationSupport.asCharacters(typeName(), values(), property);
    }

    /**
     * Typed value of the property "{@code value}".
     *
     * @return value if present
     */
    default Optional<Short> shortValue() {
        return shortValue(VALUE_PROPERTY);
    }

    /**
     * Typed value of a named property.
     *
     * @param property name of the annotation property
     * @return value if present
     */
    default Optional<Short> shortValue(String property) {
        return AnnotationSupport.asShort(typeName(), values(), property);
    }

    /**
     * Typed value of the property "{@code value}" that is defined as an array.
     * This will also work for a single values property.
     *
     * @return list of defined values if present
     */
    default Optional<List<Short>> shortValues() {
        return shortValues(VALUE_PROPERTY);
    }

    /**
     * Typed values of a property that is defined as an array.
     * This will also work for a single values property.
     *
     * @param property name of the annotation property
     * @return list of defined values if present
     */
    default Optional<List<Short>> shortValues(String property) {
        return AnnotationSupport.asShorts(typeName(), values(), property);
    }

    /**
     * Typed value of the property "{@code value}".
     *
     * @return value if present
     */
    default Optional<Float> floatValue() {
        return floatValue(VALUE_PROPERTY);
    }

    /**
     * Typed value of a named property.
     *
     * @param property name of the annotation property
     * @return value if present
     */
    default Optional<Float> floatValue(String property) {
        return AnnotationSupport.asFloat(typeName(), values(), property);
    }

    /**
     * Typed value of the property "{@code value}" that is defined as an array.
     * This will also work for a single values property.
     *
     * @return list of defined values if present
     */
    default Optional<List<Float>> floatValues() {
        return floatValues(VALUE_PROPERTY);
    }

    /**
     * Typed values of a property that is defined as an array.
     * This will also work for a single values property.
     *
     * @param property name of the annotation property
     * @return list of defined values if present
     */
    default Optional<List<Float>> floatValues(String property) {
        return AnnotationSupport.asFloats(typeName(), values(), property);
    }

    /**
     * Typed value of the property "{@code value}".
     *
     * @return value if present
     */
    default Optional<Double> doubleValue() {
        return doubleValue(VALUE_PROPERTY);
    }

    /**
     * Typed value of a named property.
     *
     * @param property name of the annotation property
     * @return value if present
     */
    default Optional<Double> doubleValue(String property) {
        return AnnotationSupport.asDouble(typeName(), values(), property);
    }

    /**
     * Typed value of the property "{@code value}" that is defined as an array.
     * This will also work for a single values property.
     *
     * @return list of defined values if present
     */
    default Optional<List<Double>> doubleValues() {
        return doubleValues(VALUE_PROPERTY);
    }

    /**
     * Typed values of a property that is defined as an array.
     * This will also work for a single values property.
     *
     * @param property name of the annotation property
     * @return list of defined values if present
     */
    default Optional<List<Double>> doubleValues(String property) {
        return AnnotationSupport.asDoubles(typeName(), values(), property);
    }

    /**
     * Typed value of the property "{@code value}".
     *
     * @return value if present
     */
    default Optional<Class<?>> classValue() {
        return classValue(VALUE_PROPERTY);
    }

    /**
     * Typed value of a named property.
     *
     * @param property name of the annotation property
     * @return value if present
     */
    default Optional<Class<?>> classValue(String property) {
        return AnnotationSupport.asClass(typeName(), values(), property);
    }

    /**
     * Typed value of the property "{@code value}" that is defined as an array.
     * This will also work for a single values property.
     *
     * @return list of defined values if present
     */
    default Optional<List<Class<?>>> classValues() {
        return classValues(VALUE_PROPERTY);
    }

    /**
     * Typed values of a property that is defined as an array.
     * This will also work for a single values property.
     *
     * @param property name of the annotation property
     * @return list of defined values if present
     */
    default Optional<List<Class<?>>> classValues(String property) {
        return AnnotationSupport.asClasses(typeName(), values(), property);
    }

    /**
     * Typed value of the property "{@code value}".
     * Alternative for {@link #classValue()}.
     *
     * @return value if present
     */
    default Optional<TypeName> typeValue() {
        return typeValue(VALUE_PROPERTY);
    }

    /**
     * Typed value of a named property.
     * Alternative for {@link #classValue(String)}.
     *
     * @param property name of the annotation property
     * @return value if present
     */
    default Optional<TypeName> typeValue(String property) {
        return AnnotationSupport.asTypeName(typeName(), values(), property);
    }

    /**
     * Typed value of the property "{@code value}" that is defined as an array.
     * This will also work for a single values property.
     * Alternative for {@link #classValues()}.
     *
     * @return list of defined values if present
     */
    default Optional<List<TypeName>> typeValues() {
        return typeValues(VALUE_PROPERTY);
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
        return AnnotationSupport.asTypeNames(typeName(), values(), property);
    }

    /**
     * Typed value of the property "{@code value}".
     *
     * @return value if present
     */
    default Optional<Annotation> annotationValue() {
        return annotationValue(VALUE_PROPERTY);
    }

    /**
     * Typed value of a named property.
     *
     * @param property name of the annotation property
     * @return value if present
     */
    default Optional<Annotation> annotationValue(String property) {
        return AnnotationSupport.asAnnotation(typeName(), values(), property);
    }

    /**
     * Typed value of the property "{@code value}" that is defined as an array.
     * This will also work for a single values property.
     *
     * @return list of defined values if present
     */
    default Optional<List<Annotation>> annotationValues() {
        return annotationValues(VALUE_PROPERTY);
    }

    /**
     * Typed values of a property that is defined as an array.
     * This will also work for a single values property.
     *
     * @param property name of the annotation property
     * @return list of defined values if present
     */
    default Optional<List<Annotation>> annotationValues(String property) {
        return AnnotationSupport.asAnnotations(typeName(), values(), property);
    }

    /**
     * Typed value of the property "{@code value}".
     *
     * @param type class of the enumeration
     * @param <T>  type of the enumeration
     * @return value if present
     */
    default <T extends Enum<T>> Optional<T> enumValue(Class<T> type) {
        return enumValue(VALUE_PROPERTY, type);
    }

    /**
     * Typed value of a named property.
     *
     * @param property name of the annotation property
     * @param type     class of the enumeration
     * @param <T>      type of the enumeration
     * @return value if present
     */
    default <T extends Enum<T>> Optional<T> enumValue(String property, Class<T> type) {
        return AnnotationSupport.asEnum(typeName(), values(), property, type);
    }

    /**
     * Typed value of the property "{@code value}" that is defined as an array.
     * This will also work for a single values property.
     *
     * @param type class of the enumeration
     * @param <T>  type of the enumeration
     * @return list of defined values if present
     */
    default <T extends Enum<T>> Optional<List<T>> enumValues(Class<T> type) {
        return enumValues(VALUE_PROPERTY, type);
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
    default <T extends Enum<T>> Optional<List<T>> enumValues(String property, Class<T> type) {
        return AnnotationSupport.asEnums(typeName(), values(), property, type);
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
        for (Annotation metaAnnotation : metaAnnotations()) {
            if (metaAnnotation.typeName().equals(annotationType)) {
                return true;
            }
            if (metaAnnotation.hasMetaAnnotation(annotationType)) {
                return true;
            }
        }
        return false;
    }
}
