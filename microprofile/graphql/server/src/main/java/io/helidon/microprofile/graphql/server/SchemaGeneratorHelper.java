/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.json.bind.annotation.JsonbProperty;
import javax.json.bind.annotation.JsonbTransient;

import graphql.Scalars;
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
import org.eclipse.microprofile.graphql.NonNull;
import org.eclipse.microprofile.graphql.Query;
import org.eclipse.microprofile.graphql.Type;

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
     * List of supported scalars keyed by the full class name.
     */
    static final Map<String, SchemaScalar> SUPPORTED_SCALARS = new HashMap<>() {{
        // Object Scalar
        put(Object.class.getName(), new SchemaScalar("Object", Object.class.getName(), ExtendedScalars.Object, null));

        // Time scalars
        put(OffsetTime.class.getName(),
            new SchemaScalar("Time", OffsetTime.class.getName(), CustomTimeScalar.INSTANCE, "HH:mm:ssZ"));
        put(LocalTime.class.getName(),
            new SchemaScalar("Time", LocalTime.class.getName(), CustomTimeScalar.INSTANCE, "HH:mm:ss"));

        // DateTime scalars
        put(OffsetDateTime.class.getName(),
            new SchemaScalar("DateTime", OffsetDateTime.class.getName(), CustomDateTimeScalar.INSTANCE,
                             "yyyy-MM-dd'T'HH:mm:ssZ"));
        put(ZonedDateTime.class.getName(),
            new SchemaScalar("DateTime", ZonedDateTime.class.getName(), CustomDateTimeScalar.INSTANCE,
                             "yyyy-MM-dd'T'HH:mm:ssZ'['VV']'"));
        put(LocalDateTime.class.getName(),
            new SchemaScalar("DateTime", LocalDateTime.class.getName(), CustomDateTimeScalar.INSTANCE, "yyyy-MM-dd'T'HH:mm:ss"));

        // Date scalar
        put(LocalDate.class.getName(), new SchemaScalar("Date", LocalDate.class.getName(), ExtendedScalars.Date, "yyyy-MM-dd"));

        // BigDecimal scalars
        put(BigDecimal.class.getName(), new SchemaScalar(BIG_DECIMAL, Long.class.getName(), Scalars.GraphQLBigDecimal, null));

        // BigInter scalars
        put(BigInteger.class.getName(), new SchemaScalar(BIG_INTEGER, Long.class.getName(), Scalars.GraphQLBigInteger, null));
        put(long.class.getName(), new SchemaScalar(BIG_INTEGER, Long.class.getName(), Scalars.GraphQLBigInteger, null));
        put(Long.class.getName(), new SchemaScalar(BIG_INTEGER, Long.class.getName(), Scalars.GraphQLBigInteger, null));
    }};

    /**
     * List of types the should map to a GraphQL Float.
     */
    static final List<String> FLOAT_LIST = new ArrayList<>() {{
        add("double");
        add("Double");
        add("java.lang.Double");
        add("float");
        add("Float");
        add("java.lang.Float");
        add("java.lang.Number");
    }};

    /**
     * List of types that should map to a GraphQL Boolean.
     */
    static final List<String> BOOLEAN_LIST = new ArrayList<>() {{
        add("boolean");
        add(Boolean.class.getName());
    }};

    /**
     * List of types that should map to a GraphQL Int.
     */
    static final List<String> INTEGER_LIST = new ArrayList<>() {{
        add("short");
        add("int");
        add("Short");
        add("Integer");
        add("java.lang.Integer");
        add("java.lang.Short");
        add("java.lang.Byte");
        add("byte");
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
    protected static final String INT = "Int";

    /**
     * GraphQL Float.
     */
    protected static final String FLOAT = "Float";

    /**
     * GraphQL String.
     */
    protected static final String STRING = "String";

    /**
     * GraphQL ID.
     */
    protected static final String ID = "ID";

    /**
     * GraphQL Boolean.
     */
    protected static final String BOOLEAN = "Boolean";

    /**
     * Class name for long primitive.
     */
    protected static final String LONG_PRIMITIVE = long.class.getName();

    /**
     * Class name for Long object.
     */
    protected static final String LONG_OBJECT = Long.class.getName();

    /**
     * Class name for {@link BigDecimal}.
     */
    protected static final String BIG_DECIMAL_OBJECT = BigDecimal.class.getName();

    /**
     * Class name for {@link BigInteger}.
     */
    protected static final String BIG_INTEGER_OBJECT = BigInteger.class.getName();

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
     * @return true of the class name is a primitive or array of primitives.
     */
    protected static boolean isPrimitive(String clazz) {
        return JAVA_PRIMITIVE_TYPES.contains(clazz)
                || PRIMITIVE_ARRAY_MAP.values().stream().filter(v -> v.contains(clazz)).count() > 0L;
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
        if (INT.equals(className)
                || FLOAT.equals(className) || ID.equals(className)
                || STRING.equals(className) || BOOLEAN.equalsIgnoreCase(className)
                || LONG_OBJECT.equals(className) || LONG_PRIMITIVE.equals(className)) {
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
        return SUPPORTED_SCALARS.values().stream().anyMatch((s -> s.getName().equals(scalarName)));
    }

    /**
     * Return the GraphQL type for the given Java type.
     *
     * @param className fully qualified class name
     * @return the GraphQL type
     */
    protected static String getGraphQLType(String className) {
        if (INTEGER_LIST.contains(className)) {
            return INT;
        } else if (FLOAT_LIST.contains(className)) {
            return FLOAT;
        } else if (BOOLEAN_LIST.contains(className)) {
            return BOOLEAN;
        } else if (STRING_LIST.contains(className)) {
            return STRING;
        } else if (java.util.UUID.class.getName().equals(className)) {
            return ID;
        }

        return className;
    }

    /**
     * Return true if the type type is a GraphQLType.
     *
     * @param type the type to check
     * @return true if the type type is a GraphQLType
     */
    protected static boolean isGraphQLType(String type) {
        return INT.equals(type) || FLOAT.equals(type)
                || BOOLEAN.equals(type) || STRING.equals(type)
                || ID.equals(type);
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
     * @param parameter {@link Parameter} to check
     * @return the {@link DefaultValue} annotation value if it exists or null
     */
    protected static String getDefaultValueAnnotationValue(Parameter parameter) {
        DefaultValue defaultValueAnnotation = parameter.getAnnotation(DefaultValue.class);
        if (defaultValueAnnotation != null && !"".equals(defaultValueAnnotation.value())) {
            return defaultValueAnnotation.value();
        }
        return null;
    }

    /**
     * Return the {@link DefaultValue} annotation value if it exists for a {@link Field} or null.
     *
     * @param field {@link Field} to check
     * @return the {@link DefaultValue} annotation value if it exists or null
     */
    protected static String getDefaultValueAnnotationValue(Field field) {
        DefaultValue defaultValueAnnotation = field.getAnnotation(DefaultValue.class);
        if (defaultValueAnnotation != null && !"".equals(defaultValueAnnotation.value())) {
            return defaultValueAnnotation.value();
        }
        return null;
    }

    /**
     * Return the {@link DefaultValue} annotation value if it exists for a {@link Method} or null.
     *
     * @param method {@link Method} to check
     * @return the {@link DefaultValue} annotation value if it exists or null
     */
    protected static String getDefaultValueAnnotationValue(Method method) {
        DefaultValue defaultValueAnnotation = method.getAnnotation(DefaultValue.class);
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
        String fmt = format == null || format.length != 2
                ? null : format[0] + (DEFAULT_LOCALE.equals(format[1]) ? "" : " " + format[1]);
        if (description == null && fmt == null) {
            return null;
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
        }

        if ("".equals(name)) {
            name = getNameAnnotationValue(clazz);
        }

        return name == null || name.isBlank() ? clazz.getSimpleName() : name;
    }

    /**
     * Checks a {@link SchemaType} for {@link SchemaFieldDefinition}s which contain known scalars and replace them with the scalar
     * name.
     *
     * @param schema {@link Schema} to check scalars for.
     * @param type   {@link SchemaType} to check
     */
    protected static void checkScalars(Schema schema, SchemaType type) {
        type.getFieldDefinitions().forEach(fd -> {
            SchemaScalar scalar = getScalar(fd.getReturnType());
            if (scalar != null) {
                fd.setReturnType(scalar.getName());
                if (!schema.containsScalarWithName(scalar.getName())) {
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
     * Return the inner most root type such as {@link String} for a List of List of String.
     *
     * @param genericReturnType the {@link java.lang.reflect.Type}
     * @param index             the index to use, either 0 for {@link Collection} or 1 for {@link Map}
     * @return the inner most root type
     */
    protected static RootTypeResult getRootTypeName(java.lang.reflect.Type genericReturnType, int index) {
        int level = 1;
        boolean isReturnTypeMandatory;
        if (genericReturnType instanceof ParameterizedType) {
            ParameterizedType paramReturnType = (ParameterizedType) genericReturnType;
            // loop until we get the actual return type in the case we have List<List<Type>>
            java.lang.reflect.Type actualTypeArgument = paramReturnType.getActualTypeArguments()[index];
            while (actualTypeArgument instanceof ParameterizedType) {
                level++;
                ParameterizedType parameterizedType2 = (ParameterizedType) actualTypeArgument;
                actualTypeArgument = parameterizedType2.getActualTypeArguments()[index];
            }
            Class<?> clazz = actualTypeArgument.getClass();
            isReturnTypeMandatory = clazz.getAnnotation(NonNull.class) != null
                    || isPrimitive(clazz.getName());
            return new RootTypeResult(((Class<?>) actualTypeArgument).getName(), level, isReturnTypeMandatory);
        } else {
            Class<?> clazz = genericReturnType.getClass();
            isReturnTypeMandatory = clazz.getAnnotation(NonNull.class) != null
                    || isPrimitive(clazz.getName());
            return new RootTypeResult(((Class<?>) genericReturnType).getName(), level, isReturnTypeMandatory);
        }
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
     * Represents a result for the method getRootTypeName.
     */
    public static class RootTypeResult {

        /**
         * The root type of the {@link Collection} or {@link Map}.
         */
        private final String rootTypeName;

        /**
         * The number of levels in total.
         */
        private final int levels;

        /**
         * Indicates if the return type is mandatory.
         */
        private boolean isReturnTypeMandatory;

        /**
         * Construct a root type result.
         *
         * @param rootTypeName          root type of the {@link Collection} or {@link Map}
         * @param isReturnTypeMandatory indicates if the return type is mandatory
         * @param levels                number of levels in total
         */
        public RootTypeResult(String rootTypeName, int levels, boolean isReturnTypeMandatory) {
            this.rootTypeName = rootTypeName;
            this.levels = levels;
            this.isReturnTypeMandatory = isReturnTypeMandatory;
        }

        /**
         * Return the root type of the {@link Collection} or {@link Map}.
         *
         * @return root type of the {@link Collection} or {@link Map}
         */
        public String getRootTypeName() {
            return rootTypeName;
        }

        /**
         * Return the number of levels in total.
         *
         * @return the number of levels in total
         */
        public int getLevels() {
            return levels;
        }

        /**
         * Indicates if the return type is mandatory.
         *
         * @return if the return type is mandatory
         */
        public boolean isReturnTypeMandatory() {
            return isReturnTypeMandatory;
        }

        /**
         * Set if the return type is mandatory.
         *
         * @param returnTypeMandatory if the return type is mandatory
         */
        public void setReturnTypeMandatory(boolean returnTypeMandatory) {
            isReturnTypeMandatory = returnTypeMandatory;
        }
    }
}
