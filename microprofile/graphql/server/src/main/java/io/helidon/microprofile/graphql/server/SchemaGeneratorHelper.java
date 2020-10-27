/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.graphql.server;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.json.bind.annotation.JsonbProperty;
import javax.json.bind.annotation.JsonbTransient;

import graphql.scalars.ExtendedScalars;
import org.eclipse.microprofile.graphql.DefaultValue;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.Enum;
import org.eclipse.microprofile.graphql.Id;
import org.eclipse.microprofile.graphql.Ignore;
import org.eclipse.microprofile.graphql.Input;
import org.eclipse.microprofile.graphql.Interface;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;
import org.eclipse.microprofile.graphql.Type;

import static io.helidon.microprofile.graphql.server.CustomScalars.CUSTOM_BIGDECIMAL_SCALAR;
import static io.helidon.microprofile.graphql.server.CustomScalars.CUSTOM_BIGINTEGER_SCALAR;
import static io.helidon.microprofile.graphql.server.CustomScalars.CUSTOM_FLOAT_SCALAR;
import static io.helidon.microprofile.graphql.server.CustomScalars.CUSTOM_INT_SCALAR;
import static io.helidon.microprofile.graphql.server.CustomScalars.CUSTOM_OFFSET_DATE_TIME_SCALAR;
import static io.helidon.microprofile.graphql.server.CustomScalars.CUSTOM_ZONED_DATE_TIME_SCALAR;
import static io.helidon.microprofile.graphql.server.CustomScalars.FORMATTED_CUSTOM_DATE_SCALAR;
import static io.helidon.microprofile.graphql.server.CustomScalars.FORMATTED_CUSTOM_DATE_TIME_SCALAR;
import static io.helidon.microprofile.graphql.server.CustomScalars.FORMATTED_CUSTOM_TIME_SCALAR;
import static io.helidon.microprofile.graphql.server.ElementGenerator.OPEN_SQUARE;
import static io.helidon.microprofile.graphql.server.FormattingHelper.getDefaultDateTimeFormat;
import static io.helidon.microprofile.graphql.server.SchemaGenerator.GET;
import static io.helidon.microprofile.graphql.server.SchemaGenerator.IS;
import static io.helidon.microprofile.graphql.server.SchemaGenerator.SET;

/**
 * Helper class for {@link SchemaGenerator}.
 */
public final class SchemaGeneratorHelper {

    /**
     * {@link OffsetTime} class name.
     */
    protected static final String OFFSET_TIME_CLASS = OffsetTime.class.getName();

    /**
     * {@link LocalTime} class name.
     */
    protected static final String LOCAL_TIME_CLASS = LocalTime.class.getName();

    /**
     * {@link OffsetDateTime} class name.
     */
    protected static final String OFFSET_DATE_TIME_CLASS = OffsetDateTime.class.getName();

    /**
     * {@link ZonedDateTime} class name.
     */
    protected static final String ZONED_DATE_TIME_CLASS = ZonedDateTime.class.getName();

    /**
     * {@link LocalDateTime} class name.
     */
    protected static final String LOCAL_DATE_TIME_CLASS = LocalDateTime.class.getName();

    /**
     * {@link LocalDate} class name.
     */
    protected static final String LOCAL_DATE_CLASS = LocalDate.class.getName();

    /**
     * {@link BigDecimal} class name.
     */
    protected static final String BIG_DECIMAL_CLASS = BigDecimal.class.getName();

    /**
     * {@link Long} class name.
     */
    protected static final String LONG_CLASS = Long.class.getName();

    /**
     * Class name for long primitive.
     */
    protected static final String LONG_PRIMITIVE_CLASS = long.class.getName();

    /**
     * {@link Float} class name.
     */
    protected static final String FLOAT_CLASS = Float.class.getName();

    /**
     * Class name for float primitive.
     */
    protected static final String FLOAT_PRIMITIVE_CLASS = float.class.getName();

    /**
     * {@link Double} class name.
     */
    protected static final String DOUBLE_CLASS = Double.class.getName();

    /**
     * Class name for double primitive.
     */
    protected static final String DOUBLE_PRIMITIVE_CLASS = double.class.getName();

    /**
     * Class name for {@link BigInteger}.
     */
    protected static final String BIG_INTEGER_CLASS = BigInteger.class.getName();

    /**
     * Class name for {@link Integer}.
     */
    protected static final String INTEGER_CLASS = Integer.class.getName();

    /**
     * Class name for int.
     */
    protected static final String INTEGER_PRIMITIVE_CLASS = int.class.getName();

    /**
     * Class name for {@link Byte}.
     */
    protected static final String BYTE_CLASS = Byte.class.getName();

    /**
     * Class name for byte.
     */
    protected static final String BYTE_PRIMITIVE_CLASS = byte.class.getName();

    /**
     * Class name for {@link Short}.
     */
    protected static final String SHORT_CLASS = Short.class.getName();

    /**
     * Class name for short.
     */
    protected static final String SHORT_PRIMITIVE_CLASS = short.class.getName();

    /**
     * Formatted Date scalar.
     */
    public static final String FORMATTED_DATE_SCALAR = "FormattedDate";

    /**
     * Formatted DateTime scalar.
     */
    public static final String FORMATTED_DATETIME_SCALAR = "FormattedDateTime";

    /**
     * Formatted DateTime scalar.
     */
    public static final String FORMATTED_OFFSET_DATETIME_SCALAR = "FormattedOffsetDateTime";

    /**
     * Formatted DateTime scalar.
     */
    public static final String FORMATTED_ZONED_DATETIME_SCALAR = "FormattedZonedDateTime";

    /**
     * Formatted Time Scalar.
     */
    public static final String FORMATTED_TIME_SCALAR = "FormattedTime";

    /**
     * Formatted Int.
     */

    /**
     * Date scalar (with default formatting).
     */
    public static final String DATE_SCALAR = "Date";

    /**
     * DateTime scalar (with default formatting).
     */
    public static final String DATETIME_SCALAR = "DateTime";

    /**
     * Time Scalar (with default formatting).
     */
    public static final String TIME_SCALAR = "Time";

    /**
     * Defines a {@link BigDecimal} type.
     */
    static final String BIG_DECIMAL = "BigDecimal";

    /**
     * Defines a {@link BigInteger} type.
     */
    static final String BIG_INTEGER = "BigInteger";

    /**
     * Value that indicates that default {@link java.util.Locale}.
     */
    static final String DEFAULT_LOCALE = "##default";

    /**
     * GraphQL Int.
     */
    public static final String INT = "Int";

    /**
     * GraphQL Float.
     */
    public static final String FLOAT = "Float";

    /**
     * GraphQL String.
     */
    public static final String STRING = "String";

    /**
     * GraphQL ID.
     */
    public static final String ID = "ID";

    /**
     * GraphQL Boolean.
     */
    public static final String BOOLEAN = "Boolean";

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(SchemaGeneratorHelper.class.getName());

    /**
     * Indicates empty annotations.
     */
    private static final Annotation[] EMPTY_ANNOTATIONS = new Annotation[0];

    /**
     * List of supported scalars keyed by the full class name.
     */
    static final Map<String, SchemaScalar> SUPPORTED_SCALARS = new HashMap<>() {{
        // Object Scalar
        put(Object.class.getName(), new SchemaScalar("Object", Object.class.getName(), ExtendedScalars.Object, null));

        // Time scalars
        put(OffsetTime.class.getName(),
            new SchemaScalar(FORMATTED_TIME_SCALAR, OFFSET_TIME_CLASS, FORMATTED_CUSTOM_TIME_SCALAR, "HH[:mm][:ss]Z"));
        put(LocalTime.class.getName(),
            new SchemaScalar(FORMATTED_TIME_SCALAR, LOCAL_TIME_CLASS, FORMATTED_CUSTOM_TIME_SCALAR, "HH[:mm][:ss]"));

        // DateTime scalars
        put(OFFSET_DATE_TIME_CLASS,
            new SchemaScalar(FORMATTED_OFFSET_DATETIME_SCALAR, OFFSET_DATE_TIME_CLASS, CUSTOM_OFFSET_DATE_TIME_SCALAR,
                             "yyyy-MM-dd'T'HH[:mm][:ss]Z"));
        put(ZONED_DATE_TIME_CLASS,
            new SchemaScalar(FORMATTED_ZONED_DATETIME_SCALAR, ZONED_DATE_TIME_CLASS, CUSTOM_ZONED_DATE_TIME_SCALAR,
                             "yyyy-MM-dd'T'HH[:mm][:ss]Z'['VV']'"));
        put(LOCAL_DATE_TIME_CLASS,
            new SchemaScalar(FORMATTED_DATETIME_SCALAR, LOCAL_DATE_TIME_CLASS, FORMATTED_CUSTOM_DATE_TIME_SCALAR,
                             "yyyy-MM-dd'T'HH[:mm][:ss]"));

        // Date scalar
        put(LOCAL_DATE_CLASS, new SchemaScalar(FORMATTED_DATE_SCALAR, LOCAL_DATE_CLASS, FORMATTED_CUSTOM_DATE_SCALAR,
                                               "yyyy-MM-dd"));

        // BigDecimal scalars
        put(BIG_DECIMAL_CLASS, new SchemaScalar(BIG_DECIMAL, BIG_DECIMAL_CLASS, CUSTOM_BIGDECIMAL_SCALAR, null));

        // BigInteger scalars
        put(BIG_INTEGER_CLASS, new SchemaScalar(BIG_INTEGER, BIG_INTEGER_CLASS, CUSTOM_BIGINTEGER_SCALAR, null));
        put(LONG_PRIMITIVE_CLASS, new SchemaScalar(BIG_INTEGER, LONG_CLASS, CUSTOM_BIGINTEGER_SCALAR, null));
        put(LONG_CLASS, new SchemaScalar(BIG_INTEGER, LONG_CLASS, CUSTOM_BIGINTEGER_SCALAR, null));

        // Int scalars
        put(INTEGER_CLASS, new SchemaScalar(INT, INTEGER_CLASS, CUSTOM_INT_SCALAR, null));
        put(INTEGER_PRIMITIVE_CLASS, new SchemaScalar(INT, INTEGER_PRIMITIVE_CLASS, CUSTOM_INT_SCALAR, null));
        put(BYTE_CLASS, new SchemaScalar(INT, BYTE_CLASS, CUSTOM_INT_SCALAR, null));
        put(BYTE_PRIMITIVE_CLASS, new SchemaScalar(INT, BYTE_PRIMITIVE_CLASS, CUSTOM_INT_SCALAR, null));
        put(SHORT_CLASS, new SchemaScalar(INT, SHORT_CLASS, CUSTOM_INT_SCALAR, null));
        put(SHORT_PRIMITIVE_CLASS, new SchemaScalar(INT, SHORT_PRIMITIVE_CLASS, CUSTOM_INT_SCALAR, null));

        // Float scalars
        put(FLOAT_CLASS, new SchemaScalar(FLOAT, FLOAT_CLASS, CUSTOM_FLOAT_SCALAR, null));
        put(FLOAT_PRIMITIVE_CLASS, new SchemaScalar(FLOAT, FLOAT_PRIMITIVE_CLASS, CUSTOM_FLOAT_SCALAR, null));
        put(DOUBLE_CLASS, new SchemaScalar(FLOAT, DOUBLE_CLASS, CUSTOM_FLOAT_SCALAR, null));
        put(DOUBLE_PRIMITIVE_CLASS, new SchemaScalar(FLOAT, DOUBLE_PRIMITIVE_CLASS, CUSTOM_FLOAT_SCALAR, null));
    }};

    /**
     * List of types that should map to a GraphQL Boolean.
     */
    static final List<String> BOOLEAN_LIST = new ArrayList<>() {{
        add("boolean");
        add("java.lang.Boolean");
    }};

    /**
     * List of types that should map to a GraphQL String.
     */
    static final List<String> STRING_LIST = new ArrayList<>() {{
        add("java.lang.String");
        add("java.lang.Character");
        add("char");
    }};

    /**
     * List of array primitive types and their array mapping. See https://docs.oracle.com/javase/6/docs/api/java/lang/Class
     * .html#getName%28%29
     */
    static final Map<String, String> PRIMITIVE_ARRAY_MAP = new HashMap<>() {{
        put("[Z", "boolean");
        put("[B", "byte");
        put("[C", "char");
        put("[D", "double");
        put("[F", "float");
        put("[I", "int");
        put("[J", "long");
        put("[S", "short");
    }};

    /**
     * List of all Java primitives.
     */
    static final List<String> JAVA_PRIMITIVE_TYPES = new ArrayList<>() {{
        add("byte");
        add("short");
        add("int");
        add("long");
        add("float");
        add("double");
        add("boolean");
        add("char");
    }};

    /**
     * Private no-args constructor.
     */
    private SchemaGeneratorHelper() {
    }

    /**
     * Return the simple name from a given class as a String. This takes into account any annotations that may be present.
     *
     * @param className class name
     * @return the simple class name
     * @throws ClassNotFoundException if invalid class name
     */
    protected static String getSimpleName(String className)
            throws ClassNotFoundException {
        return getSimpleName(className, false);
    }

    /**
     * Return true of the {@link Class} is a primitive or array of primitives.
     *
     * @param clazz {@link Class} to check
     * @return true of the {@link Class} is a primitive or array of primitives.
     */
    protected static boolean isPrimitive(Class<?> clazz) {
        return isPrimitive(clazz.getName());
    }

    /**
     * Return true of the class name is a primitive or array of primitives.
     *
     * @param clazz class name to check
     * @return true if the class name is a primitive or array of primitives.
     */
    protected static boolean isPrimitive(String clazz) {
        return JAVA_PRIMITIVE_TYPES.contains(clazz)
                || PRIMITIVE_ARRAY_MAP.values().stream().anyMatch(v -> v.contains(clazz));
    }

    /**
     * Return true of the class name is an array of primitives.
     *
     * @param clazz class name to check
     * @return true true of the class name is an array of primitives.
     */
    protected static boolean isPrimitiveArray(String clazz) {
        return PRIMITIVE_ARRAY_MAP.values().stream().anyMatch(v -> v.contains(clazz));
    }

    /**
     * Return true of the class name is an array of primitives.
     *
     * @param clazz {@link Class} to check
     * @return true true of the class name is an array of primitives.
     */
    protected static boolean isPrimitiveArray(Class<?> clazz) {
        return PRIMITIVE_ARRAY_MAP.containsKey(clazz.getName());
    }

    /**
     * Return the simple name from a given class as a String. This takes into account any annotations that may be present.
     *
     * @param className                 class name
     * @param ignoreInputNameAnnotation indicates if we should ignore the name from {@link Input} annotation as we should not
     *                                  change the name of a type if it as and {@link Input} annotation
     * @return the simple class name
     * @throws ClassNotFoundException if invalid class name
     */
    protected static String getSimpleName(String className, boolean ignoreInputNameAnnotation)
            throws ClassNotFoundException {
        if (ID.equals(className)
                || STRING.equals(className) || BOOLEAN.equalsIgnoreCase(className)
                || getScalar(className) != null) {
            return className;
        }
        // return the type name taking into account any annotations
        Class<?> clazz = Class.forName(className);
        return getTypeName(clazz, ignoreInputNameAnnotation);
    }

    /**
     * Returns a {@link SchemaScalar} if one matches the known list of scalars available from the {@link ExtendedScalars} helper.
     *
     * @param clazzName class name to check for
     * @return a {@link SchemaScalar} if one matches the known list of scalars or null if none found
     */
    protected static SchemaScalar getScalar(String clazzName) {
        return SUPPORTED_SCALARS.get(clazzName);
    }

    /**
     * Returns true if the give name is a scalar with that name.
     *
     * @param scalarName the scalae name to check
     * @return true if the give name is a scalar with that name
     */
    protected static boolean isScalar(String scalarName) {
        return SUPPORTED_SCALARS.values().stream().anyMatch((s -> s.name().equals(scalarName)));
    }

    /**
     * Return the GraphQL type for the given Java type.
     *
     * @param className fully qualified class name
     * @return the GraphQL type
     */
    protected static String getGraphQLType(String className) {
        if (BOOLEAN_LIST.contains(className)) {
            return BOOLEAN;
        } else if (STRING_LIST.contains(className)) {
            return STRING;
        } else if (java.util.UUID.class.getName().equals(className)) {
            return ID;
        }

        return className;
    }

    /**
     * Return true of the name is a Date, DateTime, or Time scalar.
     *
     * @param scalarName scalar name
     * @return true of the name is a Date, DateTime, or Time scalar
     */
    protected static boolean isDateTimeScalar(String scalarName) {
        return FORMATTED_DATE_SCALAR.equals(scalarName)
                || FORMATTED_TIME_SCALAR.equals(scalarName)
                || FORMATTED_OFFSET_DATETIME_SCALAR.equals(scalarName)
                || FORMATTED_ZONED_DATETIME_SCALAR.equals(scalarName)
                || FORMATTED_DATETIME_SCALAR.equals(scalarName);
    }

    /**
     * Return true if the type type is a native GraphQLType.
     *
     * @param type the type to check
     * @return true if the type type is a GraphQLType
     */
    protected static boolean isGraphQLType(String type) {
        return BOOLEAN.equals(type) || STRING.equals(type) || ID.equals(type);
    }

    /**
     * Return the field name after checking both the {@link Name} and {@link JsonbProperty} annotations are present on the field
     * name.<p> Name will take precedence if both are specified.
     *
     * @param clazz     {@link Class} to check
     * @param fieldName field name to check
     * @return the field name or null if none exist
     */
    protected static String getFieldName(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            Name nameAnnotation = field.getAnnotation(Name.class);
            // Name annotation is specified so use this and don't bother checking JsonbProperty
            if (nameAnnotation != null && !nameAnnotation.value().isBlank()) {
                return nameAnnotation.value();
            }
            // check for JsonbProperty
            JsonbProperty jsonbPropertyAnnotation = field.getAnnotation(JsonbProperty.class);
            return jsonbPropertyAnnotation != null && !jsonbPropertyAnnotation.value().isBlank()
                    ? jsonbPropertyAnnotation.value()
                    : null;

        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    /**
     * Return the field name after checking both the {@link Name} and {@link JsonbProperty} annotations are present on the {@link
     * Method}.<p> Name will take precedence if both are specified.
     *
     * @param method {@link Method} to check
     * @return the field name or null if non exist
     */
    protected static String getMethodName(Method method) {
        if (method != null) {
            Query queryAnnotation = method.getAnnotation(Query.class);
            Mutation mutationAnnotation = method.getAnnotation(Mutation.class);
            Name nameAnnotation = method.getAnnotation(Name.class);
            JsonbProperty jsonbPropertyAnnotation = method.getAnnotation(JsonbProperty.class);
            if (queryAnnotation != null && !queryAnnotation.value().isBlank()) {
                return queryAnnotation.value();
            }
            if (mutationAnnotation != null && !mutationAnnotation.value().isBlank()) {
                return mutationAnnotation.value();
            }
            if (nameAnnotation != null && !nameAnnotation.value().isBlank()) {
                // Name annotation is specified so use this and don't bother checking JsonbProperty
                return nameAnnotation.value();
            }
            if (jsonbPropertyAnnotation != null && !jsonbPropertyAnnotation.value().isBlank()) {
                return jsonbPropertyAnnotation.value();
            }
        }
        return null;
    }

    /**
     * Return a Class from a class name and ignore any exceptions.
     *
     * @param clazzName the class name as a String
     * @return a Class name
     */
    protected static Class<?> getSafeClass(String clazzName) {
        try {
            return Class.forName(clazzName);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * Return true if the class is a date, time or date/time.
     *
     * @param clazz the {@link Class} to check
     * @return true if the class is a date, time or date/time
     */
    protected static boolean isDateTimeClass(Class<?> clazz) {
        return clazz != null && (
                clazz.equals(LocalDate.class)
                        || clazz.equals(LocalTime.class)
                        || clazz.equals(LocalDateTime.class)
                        || clazz.equals(OffsetTime.class)
                        || clazz.equals(ZonedDateTime.class)
                        || clazz.equals(OffsetDateTime.class));
    }

    /**
     * Return true if the {@link Class} is a valid {@link Class} to apply the {@link Id} annotation.
     *
     * @param clazz {@link Class} to check
     * @return true if the {@link Class} is a valid {@link Class} to apply the {@link Id} annotation
     */
    protected static boolean isValidIDType(Class<?> clazz) {
        return clazz.equals(Long.class) || clazz.equals(Integer.class)
                || clazz.equals(java.util.UUID.class) || clazz.equals(int.class)
                || clazz.equals(String.class) || clazz.equals(long.class);
    }

    /**
     * Return true if the fully qualified class is an enum.
     *
     * @param clazz class to check
     * @return true if the fully qualified class is an enum.
     */
    protected static boolean isEnumClass(String clazz) {
        try {
            return (Class.forName(clazz)).isEnum();
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Return the {@link Name} annotation value if it exists or null.
     *
     * @param clazz {@link Class} to check
     * @return the {@link Name} annotation value if it exists or null
     */
    protected static String getNameAnnotationValue(Class<?> clazz) {
        Name nameAnnotation = clazz.getAnnotation(Name.class);
        if (nameAnnotation != null && !"".equals(nameAnnotation.value())) {
            return nameAnnotation.value();
        }
        return null;
    }

    /**
     * Return the {@link DefaultValue} annotation value if it exists for a {@link Parameter} or null.
     *
     * @param annotatedElement {@link AnnotatedElement} to check
     * @return the {@link DefaultValue} annotation value if it exists or null
     */
    protected static String getDefaultValueAnnotationValue(AnnotatedElement annotatedElement) {
        DefaultValue defaultValueAnnotation = annotatedElement.getAnnotation(DefaultValue.class);
        if (defaultValueAnnotation != null && !"".equals(defaultValueAnnotation.value())) {
            return defaultValueAnnotation.value();
        }
        return null;
    }

    /**
     * Return the default description based upon the format and description.
     *
     * @param format      format
     * @param description description
     * @return the default description
     */
    protected static String getDefaultDescription(String[] format, String description) {
        String fmt = format == null || format.length != 2 || (format[0] == null || format[1] == null)
                ? null : format[0] + (DEFAULT_LOCALE.equals(format[1]) ? "" : " " + format[1]);
        if (description == null && fmt == null) {
            return null;
        }

        // for the format display replace all [] (optionals) with null so TCK works
        if (fmt != null) {
            fmt = fmt.replaceAll("\\[", "").replaceAll("]", "");
        }
        return description == null
                ? fmt.trim() : fmt == null
                ? description : description + " (" + fmt.trim() + ")";
    }

    /**
     * Return the type method name taking into account the is/set/get prefix.
     *
     * @param method       {@link Method} to check
     * @param isStrictTest indicates if a strict test for setters and getters should be carried out.
     * @return the method name
     */
    protected static String stripMethodName(Method method, boolean isStrictTest) {
        String name = method.getName();
        boolean isPublic = Modifier.isPublic(method.getModifiers());
        boolean isSetterName = name.matches("^set[A-Z].*");
        boolean isGetterName = name.matches("^get[A-Z].*") || name.matches("^is[A-Z].*");
        Parameter[] parameters = method.getParameters();

        if (isStrictTest) {
            boolean isSetter = isPublic
                    && method.getReturnType().equals(void.class)
                    && parameters.length == 1
                    && isSetterName;
            boolean isGetter = isPublic
                    && !method.getReturnType().equals(void.class)
                    && parameters.length == 0
                    && isGetterName;

            if (!isGetter && !isSetter) {
                return name;
            }
        } else {
            // non-strict test so just check names
            if (!isPublic || !isGetterName && !isSetterName) {
                return name;
            }
        }

        String varName;
        if (name.startsWith(IS) || name.startsWith(GET) || name.startsWith(SET)) {
            String prefix;
            if (name.startsWith(IS)) {
                prefix = IS;
            } else if (name.startsWith(GET)) {
                prefix = GET;
            } else if (name.startsWith(SET)) {
                prefix = SET;
            } else {
                prefix = "";
            }

            // remove the prefix and make first letter lowercase
            varName = name.replaceAll(prefix, "");
            varName = varName.substring(0, 1).toLowerCase() + varName.substring(1);
        } else {
            // may be any method, e.g. from GraphQLApi annotated class
            varName = name;
        }

        return varName;
    }

    /**
     * Return correct name for a Type or Enum based upon the value of the annotation or the {@link Name}.
     *
     * @param clazz {@link Class} to introspect.
     * @return the correct name
     */
    protected static String getTypeName(Class<?> clazz) {
        return getTypeName(clazz, false);
    }

    /**
     * Return the array of {@link Annotation}s on a {@link Parameter} that are parameterized types.
     *
     * @param field {@link Field} to introspect
     * @param index index of type generic type. 0 = List/Collection 1 = Map
     * @return the array of {@link Annotation}s on a {@link Parameter}
     */
    protected static Annotation[] getFieldAnnotations(Field field, int index) {
        if (field.getAnnotatedType() instanceof AnnotatedParameterizedType) {
            return getAnnotationsFromType((AnnotatedParameterizedType) field.getAnnotatedType(), index);
        }

        return EMPTY_ANNOTATIONS;
    }

    /**
     * Return the array of {@link Annotation}s on a {@link Method} that are parameterized types.
     *
     * @param method {@link Method} to introspect
     * @param index  index of type generic type. 0 = List/Collection 1 = Map
     * @return the array of {@link Annotation}s on a {@link Parameter}
     */
    protected static Annotation[] getMethodAnnotations(Method method, int index) {
        if (method.getAnnotatedReturnType() instanceof AnnotatedParameterizedType) {
            return getAnnotationsFromType((AnnotatedParameterizedType) method.getAnnotatedReturnType(), index);
        }

        return EMPTY_ANNOTATIONS;
    }

    /**
     * Return the array of {@link Annotation}s on a {@link Parameter} that are parameterized types.
     *
     * @param parameter {@link Parameter} to introspect
     * @param index     index of type generic type. 0 = List/Collection 1 = Map
     * @return the array of {@link Annotation}s on a {@link Parameter}
     */
    protected static Annotation[] getParameterAnnotations(Parameter parameter, int index) {

        if (parameter.getAnnotatedType() instanceof AnnotatedParameterizedType) {
            return getAnnotationsFromType((AnnotatedParameterizedType) parameter.getAnnotatedType(), index);
        }

        return EMPTY_ANNOTATIONS;
    }

    /**
     * Returns the annotations from the given {@link AnnotatedParameterizedType}.
     *
     * @param apt   {@link AnnotatedParameterizedType}
     * @param index index of type generic type. 0 = List/Collection 1 = Map
     * @return the annotations from the given {@link AnnotatedParameterizedType}
     */
    private static Annotation[] getAnnotationsFromType(AnnotatedParameterizedType apt, int index) {
        if (apt != null) {

            // loop until we find the root annotated type
            AnnotatedType annotatedActualTypeArgument = apt.getAnnotatedActualTypeArguments()[index];
            while (annotatedActualTypeArgument instanceof AnnotatedParameterizedType) {
                AnnotatedParameterizedType parameterizedType2 = (AnnotatedParameterizedType) annotatedActualTypeArgument;
                annotatedActualTypeArgument = parameterizedType2.getAnnotatedActualTypeArguments()[index];
            }

            if (annotatedActualTypeArgument != null) {
                return annotatedActualTypeArgument.getAnnotations();
            }
        }
        return EMPTY_ANNOTATIONS;
    }

    /**
     * Return the annotation that matches the type.
     *
     * @param annotations array of {@link Annotation}s to search
     * @param type        the {@link Type} to find
     * @return the annotation that matches the type
     */
    protected static Annotation getAnnotationValue(Annotation[] annotations, java.lang.reflect.Type type) {
        if (annotations != null) {
            for (Annotation annotation : annotations) {
                if (annotation.annotationType().equals(type)) {
                    return annotation;
                }
            }
        }
        return null;
    }

    /**
     * Return correct name for a Type or Enum based upon the value of the annotation or the {@link Name}.
     *
     * @param clazz                     {@link Class} to introspect.
     * @param ignoreInputNameAnnotation indicates if we should ignore the name from {@link Input} annotation as we should not
     *                                  change the name of a type if it as and {@link Input} annotation
     * @return the correct name
     */
    protected static String getTypeName(Class<?> clazz, boolean ignoreInputNameAnnotation) {
        Type typeAnnotation = clazz.getAnnotation(Type.class);
        Interface interfaceAnnotation = clazz.getAnnotation(Interface.class);
        Input inputAnnotation = ignoreInputNameAnnotation ? null : clazz.getAnnotation(Input.class);
        Enum enumAnnotation = clazz.getAnnotation(Enum.class);
        Query queryAnnotation = clazz.getAnnotation(Query.class);
        Mutation mutationAnnotation = clazz.getAnnotation(Mutation.class);

        String name = "";
        if (typeAnnotation != null) {
            name = typeAnnotation.value();
        } else if (interfaceAnnotation != null) {
            name = interfaceAnnotation.value();
        } else if (inputAnnotation != null) {
            name = inputAnnotation.value();
        } else if (enumAnnotation != null) {
            name = enumAnnotation.value();
        } else if (queryAnnotation != null) {
            name = queryAnnotation.value();
        } else if (mutationAnnotation != null) {
            name = mutationAnnotation.value();
        }

        if ("".equals(name)) {
            name = getNameAnnotationValue(clazz);
        }

        ensureValidName(LOGGER, name);

        return name == null || name.isBlank() ? clazz.getSimpleName() : name;
    }

    /**
     * Ensure the provided name is a valid GraphQL name.
     *
     * @param logger {@link Logger} to log to
     * @param name   to validate
     */
    protected static void ensureValidName(Logger logger, String name) {
        if (name != null && !isValidGraphQLName(name)) {
            ensureConfigurationException(LOGGER, "The name '" + name + "' is not a valid "
                    + "GraphQL name and cannot be used.");
        }
    }

    /**
     * Checks a {@link SchemaType} for {@link SchemaFieldDefinition}s which contain known scalars and replace them with the scalar
     * name.
     *
     * @param schema {@link Schema} to check scalars for.
     * @param type   {@link SchemaType} to check
     */
    protected static void checkScalars(Schema schema, SchemaType type) {
        type.fieldDefinitions().forEach(fd -> {
            SchemaScalar scalar = getScalar(fd.returnType());
            if (scalar != null) {
                fd.returnType(scalar.name());
                if (!schema.containsScalarWithName(scalar.name())) {
                    schema.addScalar(scalar);
                }
            }
        });
    }

    /**
     * Returns current format or if none exists, then the default if it exists for the scalar.
     *
     * @param scalarName     scalar name to check
     * @param clazzName      class name to check
     * @param existingFormat the existing format
     * @return current format or if none exists, then the default if it exists for the scalar
     */
    protected static String[] ensureFormat(String scalarName, String clazzName, String[] existingFormat) {
        if (existingFormat == null || (existingFormat[0] == null && existingFormat[1] == null && isScalar(scalarName))) {
            String[] defaultFormat = getDefaultDateTimeFormat(scalarName, clazzName);
            if (defaultFormat != null) {
                return defaultFormat;
            }
        }
        return existingFormat;
    }

    /**
     * Return the number of array levels in the class.
     *
     * @param clazz the class name retrieved via Class.getName()
     * @return the number of array levels in the class
     */
    protected static int getArrayLevels(String clazz) {
        int c = 0;
        for (int i = 0; i < clazz.length(); i++) {
            if (clazz.charAt(i) == '[') {
                c++;
            }
        }
        return c;
    }

    /**
     * Indicates if the class is an array type.
     *
     * @param clazz the class name retrieved via Class.getName()
     * @return true if the class is an array type
     */
    protected static boolean isArrayType(String clazz) {
        return clazz.startsWith(OPEN_SQUARE);
    }

    /**
     * Return the root array class from the given class.
     *
     * @param clazz the class name retrieved via Class.getName()
     * @return the root class name
     */
    protected static String getRootArrayClass(String clazz) {
        if (clazz == null || "".equals(clazz.trim()) || clazz.length() < 2) {
            throw new IllegalArgumentException("Class must be not null");
        }
        // check to see if it is a primitive array
        String type = PRIMITIVE_ARRAY_MAP.get(clazz.substring(clazz.length() - 2));
        if (type != null) {
            return type;
        }
        // must be an object
        return clazz.replaceAll("\\[", "").replaceAll(";", "").replaceAll("^L", "");
    }

    /**
     * Indicates if the method should be ignored.
     *
     * @param method      {@link Method} to check
     * @param isInputType indicates if this is an input type
     * @return true if the method should be ignored
     */
    protected static boolean shouldIgnoreMethod(Method method, boolean isInputType) {
        Ignore ignore = method.getAnnotation(Ignore.class);
        JsonbTransient jsonbTransient = method.getAnnotation(JsonbTransient.class);

        // default case
        if (ignore == null && jsonbTransient == null) {
            return false;
        }

        // at least one of the annotations is present on the method
        String methodName = method.getName();
        String prefix = methodName.startsWith(SET)
                ? SET : methodName.startsWith(GET)
                ? GET
                : null;

        // if @Ignore or @JsonbTransient is on getter method then exclude from output type
        // if @Ignore or @JsonbTransient is on setter methods then excludes from input type
        if (GET.equals(prefix) && !isInputType) {
            return true;
        } else {
            return SET.equals(prefix) && isInputType;
        }
    }

    /**
     * Return true if the provided field should be ignored.
     *
     * @param clazz     {@link Class} to check field on
     * @param fieldName field name to check
     * @return true if the {@link Field} should be ignored
     */
    protected static boolean shouldIgnoreField(Class<?> clazz, String fieldName) {
        Field field = null;
        try {
            field = clazz.getDeclaredField(fieldName);
            return field != null
                    && (field.getAnnotation(Ignore.class) != null || field.getAnnotation(JsonbTransient.class) != null);
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    /**
     * Safely return the value of the {@link Description} annotation.
     *
     * @param description {@link Description} annotation
     * @return the description or null
     */
    protected static String getDescription(Description description) {
        return description == null || "".equals(description.value())
                ? null
                : description.value();
    }

    /**
     * Validates that a name is valid according to the graphql spec at. Ref: http://spec.graphql.org/June2018/#sec-Names
     *
     * @param name name to validate
     * @return true if the name is valid
     */
    protected static boolean isValidGraphQLName(String name) {
        return name != null && name.matches("[_A-Za-z][_0-9A-Za-z]*") && !name.startsWith("__");
    }

    /**
     * Ensures a {@link RuntimeException} with the message supplied is thrown and logged.
     *
     * @param message message to throw
     * @param logger  the {@link Logger} to use
     */
    protected static void ensureRuntimeException(Logger logger, String message) {
        ensureRuntimeException(logger, message, null);
    }

    /**
     * Ensures a {@link RuntimeException} with the message supplied is thrown and logged.
     *
     * @param message message to throw
     * @param cause   cause of the erro
     * @param logger  the {@link Logger} to use
     */
    protected static void ensureRuntimeException(Logger logger, String message, Throwable cause) {
        logger.warning(message);
        if (cause != null) {
            logger.warning(getStackTrace(cause));
        }
        throw new RuntimeException(message, cause);
    }

    /**
     * Ensures a {@link GraphQLConfigurationException} with the message suppleid is thrown and logged.
     *
     * @param message message to throw
     * @param logger  the {@link Logger} to use
     */
    protected static void ensureConfigurationException(Logger logger, String message) {
        ensureConfigurationException(logger, message, null);
    }

    /**
     * Ensures a {@link GraphQLConfigurationException} with the message supplied is thrown and logged.
     *
     * @param message message to throw
     * @param cause   cause of the erro
     * @param logger  the {@link Logger} to use
     */
    protected static void ensureConfigurationException(Logger logger, String message, Throwable cause) {
        logger.warning(message);
        if (cause != null) {
            logger.warning(getStackTrace(cause));
        }
        throw new GraphQLConfigurationException(message, cause);
    }

    /**
     * Returns the stacktrace of the given {@link Throwable}.
     * @param throwable {@link Throwable} to get stack tracek for
     * @return the stacktrace of the given {@link Throwable}
     */
    protected static String getStackTrace(Throwable throwable) {
        StringWriter stack = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stack));
        return stack.toString();
    }
}
