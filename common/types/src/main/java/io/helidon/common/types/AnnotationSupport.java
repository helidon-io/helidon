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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Prototype;

final class AnnotationSupport {
    private AnnotationSupport() {
    }

    /**
     * Creates an instance for an annotation with a value.
     *
     * @param annoTypeName the annotation type name
     * @param value        the annotation value
     * @return the new instance
     */
    @Prototype.FactoryMethod
    public static Annotation create(TypeName annoTypeName,
                                    String value) {
        return Annotation.builder().typeName(annoTypeName).value(value).build();
    }

    /**
     * Creates an instance for annotation with zero or more values.
     *
     * @param annoTypeName the annotation type name
     * @param values       the annotation values
     * @return the new instance
     */
    @Prototype.FactoryMethod
    public static Annotation create(TypeName annoTypeName,
                                    Map<String, ?> values) {
        return Annotation.builder().typeName(annoTypeName).values(values).build();
    }

    /**
     * Creates an instance for an annotation with no value.
     *
     * @param annoType the annotation type
     * @return the new instance
     */
    @Prototype.FactoryMethod
    static Annotation create(Class<? extends java.lang.annotation.Annotation> annoType) {
        return Annotation.builder()
                .typeName(TypeName.create(annoType))
                .build();
    }

    /**
     * Creates an instance for an annotation with no value.
     *
     * @param annoType the annotation type
     * @return the new instance
     */
    @Prototype.FactoryMethod
    static Annotation create(TypeName annoType) {
        return Annotation.builder()
                .typeName(annoType)
                .build();
    }

    /**
     * Creates an instance for an annotation with a value.
     *
     * @param annoType the annotation type
     * @param value    the annotation value
     * @return the new instance
     */
    @Prototype.FactoryMethod
    static Annotation create(Class<? extends java.lang.annotation.Annotation> annoType,
                             String value) {
        return create(TypeName.create(annoType), value);
    }

    /**
     * Creates an instance for an annotation with a value.
     *
     * @param annoType the annotation type
     * @param values   the annotation values
     * @return the new instance
     */
    @Prototype.FactoryMethod
    static Annotation create(Class<? extends java.lang.annotation.Annotation> annoType,
                             Map<String, ?> values) {
        return create(TypeName.create(annoType), values);
    }

    @Prototype.PrototypeMethod
    @Prototype.Annotated("java.lang.Override")
    static int compareTo(Annotation me, Annotation o) {
        return me.typeName().compareTo(o.typeName());
    }

    /**
     * Annotation type name from annotation type.
     *
     * @param builder  builder to update
     * @param annoType annotation class
     */
    @Prototype.BuilderMethod
    static void type(Annotation.BuilderBase<?, ?> builder, Type annoType) {
        builder.typeName(TypeName.create(annoType));
    }

    /**
     * Configure the value of this annotation (property of name {@code value}).
     *
     * @param builder builder to update
     * @param value   value of the annotation
     */
    @Prototype.BuilderMethod
    static void value(Annotation.BuilderBase<?, ?> builder, String value) {
        builder.putValue("value", value);
    }

    static Optional<String> asString(TypeName typeName, Map<String, Object> values, String property) {
        Object value = values.get(property);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(asString(typeName, property, value));
    }

    static Optional<List<String>> asStrings(TypeName typeName, Map<String, Object> values, String property) {
        Object value = values.get(property);
        if (value == null) {
            return Optional.empty();
        }

        // already a list
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object o : list) {
                result.add(asString(typeName, property, o));
            }
            return Optional.of(List.copyOf(result));
        }

        // a single value
        return Optional.of(List.of(asString(typeName, property, value)));
    }

    static Optional<Integer> asInt(TypeName typeName, Map<String, Object> values, String property) {
        Object value = values.get(property);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(asInt(typeName, property, value));
    }

    static Optional<List<Integer>> asInts(TypeName typeName, Map<String, Object> values, String property) {
        Object value = values.get(property);
        if (value == null) {
            return Optional.empty();
        }

        // already a list
        if (value instanceof List<?> list) {
            List<Integer> result = new ArrayList<>();
            for (Object o : list) {
                result.add(asInt(typeName, property, o));
            }
            return Optional.of(List.copyOf(result));
        }

        // a single value
        return Optional.of(List.of(asInt(typeName, property, value)));
    }

    static Optional<Long> asLong(TypeName typeName, Map<String, Object> values, String property) {
        Object value = values.get(property);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(asLong(typeName, property, value));
    }

    static Optional<List<Long>> asLongs(TypeName typeName, Map<String, Object> values, String property) {
        Object value = values.get(property);
        if (value == null) {
            return Optional.empty();
        }

        // already a list
        if (value instanceof List<?> list) {
            List<Long> result = new ArrayList<>();
            for (Object o : list) {
                result.add(asLong(typeName, property, o));
            }
            return Optional.of(List.copyOf(result));
        }

        // a single value
        return Optional.of(List.of(asLong(typeName, property, value)));
    }

    static Optional<Boolean> asBoolean(TypeName typeName, Map<String, Object> values, String property) {
        Object value = values.get(property);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(asBoolean(typeName, property, value));
    }

    static Optional<List<Boolean>> asBooleans(TypeName typeName, Map<String, Object> values, String property) {
        Object value = values.get(property);
        if (value == null) {
            return Optional.empty();
        }

        // already a list
        if (value instanceof List<?> list) {
            List<Boolean> result = new ArrayList<>();
            for (Object o : list) {
                result.add(asBoolean(typeName, property, o));
            }
            return Optional.of(List.copyOf(result));
        }

        // a single value
        return Optional.of(List.of(asBoolean(typeName, property, value)));
    }

    static Optional<Byte> asByte(TypeName typeName, Map<String, Object> values, String property) {
        Object value = values.get(property);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(asByte(typeName, property, value));
    }

    static Optional<List<Byte>> asBytes(TypeName typeName, Map<String, Object> values, String property) {
        Object value = values.get(property);
        if (value == null) {
            return Optional.empty();
        }

        // already a list
        if (value instanceof List<?> list) {
            List<Byte> result = new ArrayList<>();
            for (Object o : list) {
                result.add(asByte(typeName, property, o));
            }
            return Optional.of(List.copyOf(result));
        }

        // a single value
        return Optional.of(List.of(asByte(typeName, property, value)));
    }

    static Optional<Character> asCharacter(TypeName typeName, Map<String, Object> values, String property) {
        Object value = values.get(property);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(asCharacter(typeName, property, value));
    }

    static Optional<List<Character>> asCharacters(TypeName typeName, Map<String, Object> values, String property) {
        Object value = values.get(property);
        if (value == null) {
            return Optional.empty();
        }

        // already a list
        if (value instanceof List<?> list) {
            List<Character> result = new ArrayList<>();
            for (Object o : list) {
                result.add(asCharacter(typeName, property, o));
            }
            return Optional.of(List.copyOf(result));
        }

        // a single value
        return Optional.of(List.of(asCharacter(typeName, property, value)));
    }

    static Optional<Short> asShort(TypeName typeName, Map<String, Object> values, String property) {
        Object value = values.get(property);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(asShort(typeName, property, value));
    }

    static Optional<List<Short>> asShorts(TypeName typeName, Map<String, Object> values, String property) {
        Object value = values.get(property);
        if (value == null) {
            return Optional.empty();
        }

        // already a list
        if (value instanceof List<?> list) {
            List<Short> result = new ArrayList<>();
            for (Object o : list) {
                result.add(asShort(typeName, property, o));
            }
            return Optional.of(List.copyOf(result));
        }

        // a single value
        return Optional.of(List.of(asShort(typeName, property, value)));
    }

    static Optional<Float> asFloat(TypeName typeName, Map<String, Object> values, String property) {
        Object value = values.get(property);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(asFloat(typeName, property, value));
    }

    static Optional<List<Float>> asFloats(TypeName typeName, Map<String, Object> values, String property) {
        Object value = values.get(property);
        if (value == null) {
            return Optional.empty();
        }

        // already a list
        if (value instanceof List<?> list) {
            List<Float> result = new ArrayList<>();
            for (Object o : list) {
                result.add(asFloat(typeName, property, o));
            }
            return Optional.of(List.copyOf(result));
        }

        // a single value
        return Optional.of(List.of(asFloat(typeName, property, value)));
    }

    static Optional<Double> asDouble(TypeName typeName, Map<String, Object> values, String property) {
        Object value = values.get(property);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(asDouble(typeName, property, value));
    }

    static Optional<List<Double>> asDoubles(TypeName typeName, Map<String, Object> values, String property) {
        Object value = values.get(property);
        if (value == null) {
            return Optional.empty();
        }

        // already a list
        if (value instanceof List<?> list) {
            List<Double> result = new ArrayList<>();
            for (Object o : list) {
                result.add(asDouble(typeName, property, o));
            }
            return Optional.of(List.copyOf(result));
        }

        // a single value
        return Optional.of(List.of(asDouble(typeName, property, value)));
    }

    static Optional<Class<?>> asClass(TypeName typeName, Map<String, Object> values, String property) {
        Object value = values.get(property);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(asClass(typeName, property, value));
    }

    static Optional<List<Class<?>>> asClasses(TypeName typeName, Map<String, Object> values, String property) {
        Object value = values.get(property);
        if (value == null) {
            return Optional.empty();
        }

        // already a list
        if (value instanceof List<?> list) {
            List<Class<?>> result = new ArrayList<>();
            for (Object o : list) {
                result.add(asClass(typeName, property, o));
            }
            return Optional.of(List.copyOf(result));
        }

        // a single value
        return Optional.of(List.of(asClass(typeName, property, value)));
    }

    static Optional<TypeName> asTypeName(TypeName typeName, Map<String, Object> values, String property) {
        Object value = values.get(property);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(asTypeName(typeName, property, value));
    }

    static Optional<List<TypeName>> asTypeNames(TypeName typeName, Map<String, Object> values, String property) {
        Object value = values.get(property);
        if (value == null) {
            return Optional.empty();
        }

        // already a list
        if (value instanceof List<?> list) {
            List<TypeName> result = new ArrayList<>();
            for (Object o : list) {
                result.add(asTypeName(typeName, property, o));
            }
            return Optional.of(List.copyOf(result));
        }

        // a single value
        return Optional.of(List.of(asTypeName(typeName, property, value)));
    }

    static Optional<Annotation> asAnnotation(TypeName typeName, Map<String, Object> values, String property) {
        Object value = values.get(property);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(asAnnotation(typeName, property, value));
    }

    static Optional<List<Annotation>> asAnnotations(TypeName typeName, Map<String, Object> values, String property) {
        Object value = values.get(property);
        if (value == null) {
            return Optional.empty();
        }

        // already a list
        if (value instanceof List<?> list) {
            List<Annotation> result = new ArrayList<>();
            for (Object o : list) {
                result.add(asAnnotation(typeName, property, o));
            }
            return Optional.of(List.copyOf(result));
        }

        // a single value
        return Optional.of(List.of(asAnnotation(typeName, property, value)));
    }

    static <T extends Enum<T>> Optional<T> asEnum(TypeName typeName, Map<String, Object> values, String property, Class<T> type) {
        Object value = values.get(property);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(asEnum(typeName, property, value, type));
    }

    static <T extends Enum<T>> Optional<List<T>> asEnums(TypeName typeName,
                                                         Map<String, Object> values,
                                                         String property,
                                                         Class<T> type) {
        Object value = values.get(property);
        if (value == null) {
            return Optional.empty();
        }

        // already a list
        if (value instanceof List<?> list) {
            List<T> result = new ArrayList<>();
            for (Object o : list) {
                result.add(asEnum(typeName, property, o, type));
            }
            return Optional.of(List.copyOf(result));
        }

        // a single value
        return Optional.of(List.of(asEnum(typeName, property, value, type)));
    }

    private static int asInt(TypeName typeName, String property, Object value) {
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof String str) {
            return Integer.parseInt(str);
        }

        throw new IllegalArgumentException(typeName.fqName() + " property " + property
                                                   + " of type " + value.getClass().getName()
                                                   + " cannot be converted to Integer");
    }

    private static long asLong(TypeName typeName, String property, Object value) {
        if (value instanceof Long number) {
            return number;
        }
        if (value instanceof String str) {
            return Long.parseLong(str);
        }

        throw new IllegalArgumentException(typeName.fqName() + " property " + property
                                                   + " of type " + value.getClass().getName()
                                                   + " cannot be converted to Long");
    }

    private static boolean asBoolean(TypeName typeName, String property, Object value) {
        if (value instanceof Boolean number) {
            return number;
        }
        if (value instanceof String str) {
            return Boolean.parseBoolean(str);
        }

        throw new IllegalArgumentException(typeName.fqName() + " property " + property
                                                   + " of type " + value.getClass().getName()
                                                   + " cannot be converted to Boolean");
    }

    private static String asString(TypeName typeName, String property, Object value) {
        if (value instanceof String str) {
            return str;
        }

        if (value instanceof TypeName tn) {
            return tn.fqName();
        }

        if (value instanceof EnumValue ev) {
            return ev.name();
        }

        if (value instanceof List<?>) {
            throw new IllegalArgumentException(typeName.fqName() + " property " + property
                                                       + " is a list, cannot be converted to String");
        }

        if (value instanceof Annotation) {
            throw new IllegalArgumentException(typeName.fqName() + " property " + property
                                                       + " is a nested annotation, cannot be converted to String");
        }

        return String.valueOf(value);
    }

    private static byte asByte(TypeName typeName, String property, Object value) {
        if (value instanceof Byte number) {
            return number;
        }
        if (value instanceof String str) {
            return Byte.parseByte(str);
        }

        throw new IllegalArgumentException(typeName.fqName() + " property " + property
                                                   + " of type " + value.getClass().getName()
                                                   + " cannot be converted to Byte");
    }

    private static char asCharacter(TypeName typeName, String property, Object value) {
        if (value instanceof Character number) {
            return number;
        }
        if (value instanceof String str) {
            if (str.length() != 1) {
                throw new IllegalArgumentException(typeName.fqName() + " property " + property
                                                           + " of type String and value \"" + str + "\""
                                                           + " cannot be converted to Character");
            }
            return str.charAt(0);
        }

        throw new IllegalArgumentException(typeName.fqName() + " property " + property
                                                   + " of type " + value.getClass().getName()
                                                   + " cannot be converted to Character");
    }

    private static short asShort(TypeName typeName, String property, Object value) {
        if (value instanceof Short number) {
            return number;
        }
        if (value instanceof String str) {
            return Short.parseShort(str);
        }

        throw new IllegalArgumentException(typeName.fqName() + " property " + property
                                                   + " of type " + value.getClass().getName()
                                                   + " cannot be converted to Short");
    }

    private static float asFloat(TypeName typeName, String property, Object value) {
        if (value instanceof Float number) {
            return number;
        }
        if (value instanceof String str) {
            return Float.parseFloat(str);
        }

        throw new IllegalArgumentException(typeName.fqName() + " property " + property
                                                   + " of type " + value.getClass().getName()
                                                   + " cannot be converted to Float");
    }

    private static double asDouble(TypeName typeName, String property, Object value) {
        if (value instanceof Double number) {
            return number;
        }
        if (value instanceof String str) {
            return Double.parseDouble(str);
        }

        throw new IllegalArgumentException(typeName.fqName() + " property " + property
                                                   + " of type " + value.getClass().getName()
                                                   + " cannot be converted to Double");
    }

    private static Class<?> asClass(TypeName typeName, String property, Object value) {
        if (value instanceof Class<?> theClass) {
            return theClass;
        }

        String className = switch (value) {
            case TypeName tn -> tn.name();
            case String str -> str;
            default -> {
                throw new IllegalArgumentException(typeName.fqName() + " property " + property
                                                           + " of type " + value.getClass().getName()
                                                           + " cannot be converted to Class");
            }
        };

        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(typeName.fqName() + " property " + property
                                                       + " of type String and value \"" + className + "\""
                                                       + " cannot be converted to Class");
        }
    }

    private static TypeName asTypeName(TypeName typeName, String property, Object value) {
        if (value instanceof TypeName tn) {
            return tn;
        }
        if (value instanceof Class<?> theClass) {
            return TypeName.create(theClass);
        }
        if (value instanceof String str) {
            return TypeName.create(str);
        }

        throw new IllegalArgumentException(typeName.fqName() + " property " + property
                                                   + " of type " + value.getClass().getName()
                                                   + " cannot be converted to TypeName");
    }

    private static Annotation asAnnotation(TypeName typeName, String property, Object value) {
        if (value instanceof Annotation annotation) {
            return annotation;
        }

        throw new IllegalArgumentException(typeName.fqName() + " property " + property
                                                   + " of type " + value.getClass().getName()
                                                   + " cannot be converted to Annotation");
    }

    private static <T extends Enum<T>> T asEnum(TypeName typeName, String property, Object value, Class<T> type) {
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        if (value instanceof String str) {
            return Enum.valueOf(type, str);
        }
        if (value instanceof EnumValue enumValue) {
            if (enumValue.type().equals(TypeName.create(type))) {
                return Enum.valueOf(type, enumValue.name());
            }

            throw new IllegalStateException("Property " + property + " is of enum type for enum "
                                                    + enumValue.type().fqName() + ", yet you requested "
                                                    + type.getName());
        }

        throw new IllegalArgumentException(typeName.fqName() + " property " + property
                                                   + " of type " + value.getClass().getName()
                                                   + " cannot be converted to Enum of type "
                                                   + type.getName());
    }
}
