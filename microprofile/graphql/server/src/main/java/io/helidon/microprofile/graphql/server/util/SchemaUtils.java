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

package io.helidon.microprofile.graphql.server.util;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.json.bind.annotation.JsonbProperty;

import io.helidon.microprofile.graphql.server.model.Schema;
import io.helidon.microprofile.graphql.server.model.SchemaEnum;
import io.helidon.microprofile.graphql.server.model.SchemaFieldDefinition;
import io.helidon.microprofile.graphql.server.model.SchemaInputType;
import io.helidon.microprofile.graphql.server.model.SchemaScalar;
import io.helidon.microprofile.graphql.server.model.SchemaType;

import graphql.GraphQLException;
import graphql.Scalars;
import graphql.scalars.ExtendedScalars;
import graphql.schema.DataFetcher;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.Enum;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Id;
import org.eclipse.microprofile.graphql.Input;
import org.eclipse.microprofile.graphql.Interface;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Type;

import static io.helidon.microprofile.graphql.server.util.SchemaUtils.DiscoveredMethod.READ;

/**
 * Various utilities for generating {@link Schema}s from classes.
 */
public class SchemaUtils {

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(SchemaUtils.class.getName());

    /**
     * List of supported scalars keyed by the full class name.
     */
    private static final Map<String, SchemaScalar> SUPPORTED_SCALARS = new HashMap<>() {{
        put(OffsetTime.class.getName(), new SchemaScalar("Time", OffsetTime.class.getName(), ExtendedScalars.Time));
        put(LocalTime.class.getName(), new SchemaScalar("LocalTime", OffsetTime.class.getName(), ExtendedScalars.Time));
        put(Object.class.getName(), new SchemaScalar("Object", Object.class.getName(), ExtendedScalars.Object));
        put(OffsetDateTime.class.getName(),
            new SchemaScalar("DateTime", OffsetDateTime.class.getName(), ExtendedScalars.DateTime));
        put(LocalDate.class.getName(), new SchemaScalar("Date", LocalDate.class.getName(), ExtendedScalars.Date));
        put(BigDecimal.class.getName(), new SchemaScalar("BigDecimal", Long.class.getName(), Scalars.GraphQLBigDecimal));
        put(BigInteger.class.getName(), new SchemaScalar("BigInteger", Long.class.getName(), Scalars.GraphQLBigInteger));
        put(Long.class.getName(), new SchemaScalar("BigInteger", Long.class.getName(), Scalars.GraphQLBigInteger));
    }};

    /**
     * List of types the should map to a GraphQL Float.
     */
    private static final List<String> FLOAT_LIST = new ArrayList<>() {{
        add("double");
        add("Double");
        add("java.lang.Double");
        add("float");
        add("Float");
        add("java.lang.Float");
        add("java.lang.Number");
    }};

    private static final List<String> BOOLEAN_LIST = new ArrayList<>() {{
        add("boolean");
        add(Boolean.class.getName());
    }};

    /**
     * List of types that should map to a GraphQL Int.
     */
    private static final List<String> INTEGER_LIST = new ArrayList<>() {{
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
    private static final List<String> STRING_LIST = new ArrayList<>() {{
        add("java.lang.String");
        add("java.lang.Character");
        add("char");
    }};

    /**
     * List of all Java primitives.
     */
    private static final List<String> JAVA_PRIMITIVE_TYPES = new ArrayList<>() {{
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
     * List of all Java primitive objects.
     */
    private static final List<String> JAVA_PRIMITIVE_OBJECTS = new ArrayList<>() {{
        addAll(STRING_LIST);
        addAll(FLOAT_LIST);
        addAll(INTEGER_LIST);
    }};

    /**
     * List of array primitive types and their array mapping. See https://docs.oracle.com/javase/6/docs/api/java/lang/Class
     * .html#getName%28%29
     */
    private static final Map<String, String> PRIMITIVE_ARRAY_MAP = new HashMap<>() {{
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
     * {@link JandexUtils} instance to hold indexes.
     */
    private JandexUtils jandexUtils;

    /**
     * Holds the {@link Set} of unresolved types while processing the annotations.
     */
    private Set<String> setUnresolvedTypes = new HashSet<>();

    /**
     * Construct a {@link SchemaUtils} instance.
     */
    public SchemaUtils() {
        jandexUtils = new JandexUtils();
        jandexUtils.loadIndex();
        if (!jandexUtils.hasIndex()) {
            String message = "Unable to find or load jandex index file: "
                    + jandexUtils.getIndexFile() + ".\nEnsure you are using the "
                    + "jandex-maven-plugin when you are building your application";
            LOGGER.warning(message);
        }
    }

    /**
     * Generate a {@link Schema} by scanning all discovered classes via the Jandex plugin.
     *
     * @return a {@link Schema}
     */
    public Schema generateSchema() throws IntrospectionException, ClassNotFoundException {

        List<Class<?>> listClasses = jandexUtils.getIndex()
                .getKnownClasses()
                .stream()
                .map(ci -> getSafeClass(ci.toString()))
                .collect(Collectors.toList());

        return generateSchemaFromClasses(listClasses.toArray(new Class<?>[0]));
    }

    /**
     * Generate a {@link Schema} from a given array of classes.  The classes are checked to see if they contain any of the
     * annotations from the microprofile spec.
     *
     * @param clazzes array of classes to check
     * @return a {@link Schema}
     */
    protected Schema generateSchemaFromClasses(Class<?>... clazzes) throws IntrospectionException, ClassNotFoundException {
        Schema schema = new Schema();
        List<SchemaType> listSchemaTypes = new ArrayList<>();
        setUnresolvedTypes.clear();

        SchemaType rootQueryType = new SchemaType(schema.getQueryName(), null);

        for (Class<?> clazz : clazzes) {
            // only include interfaces and concrete classes/enums
            if (clazz.isInterface() || (!clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers()))) {
                // Discover Enum via annotation
                if (clazz.isAnnotationPresent(org.eclipse.microprofile.graphql.Enum.class)) {
                    schema.addEnum(generateEnum(clazz));
                    continue;
                }

                //
                // Type, Interface, Input are all treated similarly
                //
                Type typeAnnotation = clazz.getAnnotation(Type.class);
                Interface interfaceAnnotation = clazz.getAnnotation(Interface.class);
                Input inputAnnotation = clazz.getAnnotation(Input.class);

                if (typeAnnotation != null || interfaceAnnotation != null) {
                    // interface or type
                    if (interfaceAnnotation != null && !clazz.isInterface()) {
                        throw new GraphQLException("Class " + clazz.getName() + " has been annotated with"
                                                           + " @Interface but is not one");
                    }

                    // assuming value for annotation overrides @Name
                    String typeName = getTypeName(clazz);
                    SchemaType type = new SchemaType(typeName.isBlank() ? clazz.getSimpleName() : typeName, clazz.getName());
                    type.setIsInterface(clazz.isInterface());
                    Description descriptionAnnotation = clazz.getAnnotation(Description.class);
                    if (descriptionAnnotation != null) {
                        type.setDescription(descriptionAnnotation.value());
                    }

                    // add the discovered type
                    addTypeToSchema(schema, type);

                    if (type.isInterface()) {
                        // is an interface so check for any implementors and add them to
                        jandexUtils.getKnownImplementors(clazz.getName()).forEach(c -> setUnresolvedTypes.add(c.getName()));

                    }
                } else if (inputAnnotation != null) {
                    // InputType
                    String inputTypeName = inputAnnotation.value();
                    listSchemaTypes.add(new SchemaInputType(inputTypeName.isBlank() ? clazz.getSimpleName() : inputTypeName,
                                                            clazz.getName()));
                }

                //
                // obtain top level query API's
                //
                if (clazz.isAnnotationPresent(GraphQLApi.class)) {
                    addRootQueriesToSchema(rootQueryType, schema, clazz);
                }
            }
        }

        // create any types that are still unresolved. e.g. an Order that contains OrderLine objects
        // we must also ensure if the unresolved type contains another unresolved type then we process it
        while (setUnresolvedTypes.size() > 0) {
            String returnType = setUnresolvedTypes.iterator().next();

            setUnresolvedTypes.remove(returnType);
            try {
                String simpleName = getSimpleName(returnType);
                //String simpleName = getSimpleName(returnType);

                SchemaScalar scalar = getScalar(returnType);
                if (scalar != null) {
                    if (!schema.containsScalarWithName(scalar.getName())) {
                        schema.addScalar(scalar);
                        // update the return type with the scalar
                        updateLongTypes(schema, returnType, scalar.getName());
                    }
                } else if (isEnumClass(returnType)) {
                    SchemaEnum newEnum = generateEnum(Class.forName(returnType));
                    if (!schema.containsEnumWithName(simpleName)) {
                        schema.addEnum(newEnum);
                    }
                    updateLongTypes(schema, returnType, newEnum.getName());
                } else {
                    // we will either know this type already or need to add it
                    boolean fExists = schema.getTypes().stream()
                            .filter(t -> t.getName().equals(simpleName)).count() > 0;
                    if (!fExists) {
                        SchemaType newType = generateType(returnType);

                        // update any return types to the discovered scalars
                        checkScalars(schema, newType);
                        schema.addType(newType);

                        // schema.addType(newType.createInputType("Input"));
                    }
                    // need to update any FieldDefinitions that contained the original "long" type of c
                    updateLongTypes(schema, returnType, simpleName);
                }
            } catch (Exception e) {
                throw new RuntimeException("Cannot get GraphQL type for " + returnType, e);
            }
        }

        // look though all of interface type and see if any of the known types implement
        // the interface and if so, add the interface to the type
        schema.getTypes().stream().filter(SchemaType::isInterface).forEach(it -> {
            schema.getTypes().stream().filter(t -> !t.isInterface()).forEach(type -> {
                Class<?> interfaceClass = getSafeClass(it.getValueClassName());
                Class<?> typeClass = getSafeClass(type.getValueClassName());
                if (interfaceClass != null
                        && typeClass != null
                        && interfaceClass.isAssignableFrom(typeClass)) {
                    type.setImplementingInterface(it.getName());
                }
            });
        });

        // process the @GraphQLApi annotated classes
        if (rootQueryType.getFieldDefinitions().size() == 0) {
            LOGGER.warning("Unable to find any classes with @GraphQLApi annotation."
                                   + "Unable to build schema");
        } else {
            // add in "Query" object to for searching for all Objects and individual object

        }
        schema.addType(rootQueryType);

        return schema;
    }

    /**
     * Generate a {@link SchemaType} from a given class.
     *
     * @param realReturnType the class to generate type from
     * @return a {@link SchemaType}
     * @throws IntrospectionException
     * @throws ClassNotFoundException
     */
    private SchemaType generateType(String realReturnType)
            throws IntrospectionException, ClassNotFoundException {

        String simpleName = getSimpleName(realReturnType);
        SchemaType type = new SchemaType(simpleName, realReturnType);
        Description descriptionAnnotation = Class.forName(realReturnType).getAnnotation(Description.class);
        if (descriptionAnnotation != null && !descriptionAnnotation.value().isBlank()) {
            type.setDescription(descriptionAnnotation.value());
        }

        for (Map.Entry<String, DiscoveredMethod> entry : retrieveBeanMethods(Class.forName(realReturnType)).entrySet()) {
            DiscoveredMethod discoveredMethod = entry.getValue();
            String valueTypeName = discoveredMethod.getReturnType();
            SchemaFieldDefinition fd = newFieldDefinition(discoveredMethod);
            type.addFieldDefinition(fd);

            if (setUnresolvedTypes != null && discoveredMethod.getReturnType().equals(fd.getReturnType())) {
                // value class was unchanged meaning we need to resolve
                setUnresolvedTypes.add(valueTypeName);
            }
        }
        return type;
    }

    /**
     * Add all the queries in the annotated class to the root query defined by the {@link SchemaType}.
     *
     * @param schemaType the root query type
     * @param clazz      {@link Class} to introspect
     * @throws IntrospectionException
     */
    private void addRootQueriesToSchema(SchemaType schemaType, Schema schema, Class<?> clazz) throws IntrospectionException {
        retrieveBeanMethods(clazz).forEach((k, v) -> {
            String methodName = k;
            DiscoveredMethod method = v;

            DataFetcher dataFetcher = null;

            SchemaFieldDefinition fd = newFieldDefinition(v);
            if (dataFetcher != null) {
                fd.setDataFetcher(dataFetcher);
            }
            schemaType.addFieldDefinition(fd);

            checkScalars(schema, schemaType);

            String returnType = v.getReturnType();
            // check to see if this is a known type
            if (returnType.equals(fd.getReturnType()) && !setUnresolvedTypes.contains(returnType)) {
                // value class was unchanged meaning we need to resolve
                setUnresolvedTypes.add(returnType);
            }
        });
    }

    /**
     * Add the type to the {@link Schema}.
     *
     * @param schema the {@link Schema} to add to
     * @throws IntrospectionException
     * @throws ClassNotFoundException
     */
    private void addTypeToSchema(Schema schema, SchemaType type)
            throws IntrospectionException, ClassNotFoundException {

        String valueClassName = type.getValueClassName();
        retrieveBeanMethods(Class.forName(valueClassName)).forEach((k, v) -> {

            SchemaFieldDefinition fd = newFieldDefinition(v);
            type.addFieldDefinition(fd);

            checkScalars(schema, type);

            String returnType = v.getReturnType();
            // check to see if this is a known type
            if (returnType.equals(fd.getReturnType()) && !setUnresolvedTypes.contains(returnType)) {
                // value class was unchanged meaning we need to resolve
                setUnresolvedTypes.add(returnType);
            }
        });

        // check if this Type is an interface then obtain all concrete classes that implement the type
        // and add them to the set of unresolved types
        if (type.isInterface()) {
            Collection<Class<?>> setConcreteClasses = jandexUtils.getKnownImplementors(valueClassName);
            setConcreteClasses.forEach(c -> setUnresolvedTypes.add(c.getName()));
        }

        schema.addType(type);
    }

    /**
     * Generate an {@link SchemaEnum} from a given  {@link java.lang.Enum}.
     *
     * @param clazz the {@link java.lang.Enum} to introspect
     * @return a new {@link SchemaEnum} or null if the class provided is not an {@link java.lang.Enum}
     */
    private SchemaEnum generateEnum(Class<?> clazz) {
        if (clazz.isEnum()) {
            // check for the Enum annotation as this method may be called from different paths
            org.eclipse.microprofile.graphql.Enum annotation = clazz
                    .getAnnotation(org.eclipse.microprofile.graphql.Enum.class);
            String name = annotation == null ? "" : annotation.value();
            // only check for Name annotation if the Enum didn't have a value
            if ("".equals(name)) {
                // check to see if this class has @Name annotation
                String nameValue = getNameAnnotationValue(clazz);
                if (nameValue != null) {
                    name = nameValue;
                }

            }
            SchemaEnum newSchemaEnum = new SchemaEnum(getTypeName(clazz));

            Arrays.stream(clazz.getEnumConstants())
                    .map(v -> v.toString())
                    .forEach(newSchemaEnum::addValue);
            return newSchemaEnum;
        }
        return null;
    }

    /**
     * Return a new {@link SchemaFieldDefinition} with the given field and class.
     *
     * @param method the {@link DiscoveredMethod}
     * @return a {@link SchemaFieldDefinition}
     */
    @SuppressWarnings("rawTypes")
    private SchemaFieldDefinition newFieldDefinition(DiscoveredMethod method) {
        String sValueClassName = method.getReturnType();
        DataFetcher dataFetcher = null;
        boolean isArrayReturnType = method.isArrayReturnType || method.isCollectionType() || method.isMap();

        if (isArrayReturnType) {

            if (method.isMap) {
                // add DataFetcher that will just retrieve the values() from the map
                // dataFetcher = DataFetcherUtils.newMapValuesDataFetcher(fieldName);
            }
        }

        SchemaFieldDefinition fd = new SchemaFieldDefinition(method.name, getGraphQLType(sValueClassName), isArrayReturnType,
                                                             false);
        fd.setDataFetcher(dataFetcher);
        return fd;
    }

    /**
     * Checks a {@link SchemaType} for {@link SchemaFieldDefinition}s which contain known scalars and replace them with the scalar
     * name.
     *
     * @param schema {@link Schema} to check scalars for.
     * @param type   {@link SchemaType} to check
     */
    private void checkScalars(Schema schema, SchemaType type) {
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
     * Return the {@link Name} annotation value if it exists or null.
     *
     * @param clazz {@link Class} to check
     * @return the {@link Name} annotation value if it exists or null
     */
    protected String getNameAnnotationValue(Class<?> clazz) {
        Name nameAnnotation = clazz.getAnnotation(Name.class);
        if (nameAnnotation != null && !nameAnnotation.value().isBlank()) {
            return nameAnnotation.value();
        }
        return null;
    }

    /**
     * Return correct name for a Type or Enum based upon the value of the annotation or the {@link Name}.
     *
     * @param clazz {@link Class} to introspect.
     * @return the correct name
     */
    protected String getTypeName(Class<?> clazz) {
        Type typeAnnotation = clazz.getAnnotation(Type.class);
        Interface interfaceAnnotation = clazz.getAnnotation(Interface.class);
        Input inputAnnotation = clazz.getAnnotation(Input.class);
        Enum enumAnnotation = clazz.getAnnotation(Enum.class);

        String name = "";
        if (typeAnnotation != null) {
            name = typeAnnotation.value();
        } else if (interfaceAnnotation != null) {
            name = interfaceAnnotation.value();
        } else if (inputAnnotation != null) {
            name = inputAnnotation.value();
        } else if (enumAnnotation != null) {
            name = enumAnnotation.value();
        }

        if (name.isBlank()) {
            name = getNameAnnotationValue(clazz);
        }

        return name == null || name.isBlank() ? clazz.getSimpleName() : name;
    }

    /**
     * Return the simple name from a given class as a String. This takes into account any annotations that may be present.
     *
     * @param className class name
     * @return the simple class name
     * @throws ClassNotFoundException if invalid class name
     */
    protected String getSimpleName(String className)
            throws ClassNotFoundException {
        if (INT.equals(className)
                || FLOAT.equals(className) || ID.equals(className)
                || STRING.equals(className) || BOOLEAN.equalsIgnoreCase(className)) {
            return className;
        }
        // return the type name taking into account any annotations
        Class<?> clazz = Class.forName(className);
        return getTypeName(clazz);
    }

    /**
     * Look in the given {@link Schema} for any field definitions, arguments and key value classes that contain the return type of
     * the long return type and replace with short return type.
     *
     * @param schema          schema to introspect
     * @param longReturnType  long return type
     * @param shortReturnType short return type
     */
    private void updateLongTypes(Schema schema, String longReturnType, String shortReturnType) {
        schema.getTypes().forEach(t -> {
            t.getFieldDefinitions().forEach(fd -> {
                if (fd.getReturnType().equals(longReturnType)) {
                    fd.setReturnType(shortReturnType);
                }

                // check arguments
                fd.getArguments().forEach(a -> {
                    if (a.getArgumentType().equals(longReturnType)) {
                        a.setArgumentType(shortReturnType);
                    }
                });
            });
        });
    }

    /**
     * Return true if the fully qualified class is an enum.
     *
     * @param clazz class to check
     * @return true if the fully qualified class is an enum.
     */
    private boolean isEnumClass(String clazz) {
        try {
            return (Class.forName(clazz)).isEnum();
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Returns a {@link SchemaScalar} if one matches the known list of scalars available from the {@link ExtendedScalars} helper.
     *
     * @param clazzName class name to check for
     * @return a {@link SchemaScalar} if one matches the known list of scalars or null if none found
     */
    private static SchemaScalar getScalar(String clazzName) {
        return SUPPORTED_SCALARS.get(clazzName);
    }

    /**
     * Return a Class from a class name and ignore any exceptions.
     *
     * @param clazzName the class name as a String
     * @return a Class name
     */
    public static Class<?> getSafeClass(String clazzName) {
        try {
            return Class.forName(clazzName);
        } catch (ClassNotFoundException e) {
            return null;
        }
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
     * Return a {@link Map} of the discovered methods. Key is the short name and the value
     * <p>
     *
     * @param clazz Class to introspect
     * @return a {@link Map} of the methods and return types
     * @throws IntrospectionException if there were errors introspecting classes
     */
    protected static Map<String, DiscoveredMethod> retrieveBeanMethods(Class<?> clazz)
            throws IntrospectionException {
        Map<String, DiscoveredMethod> mapDiscoveredMethods = new HashMap<>();

        Arrays.asList(Introspector.getBeanInfo(clazz).getPropertyDescriptors())
                .stream()
                .filter(p -> p.getReadMethod() != null
                        && !p.getReadMethod().getName().equals("getClass"))
                .forEach(pd -> {
                    Method readMethod = pd.getReadMethod();
                    Method writeMethod = pd.getWriteMethod();

                    DiscoveredMethod discoveredMethod = generateDiscoveredMethod(readMethod, clazz, pd);

                    if (discoveredMethod != null) {
                        mapDiscoveredMethods.put(discoveredMethod.getName(), discoveredMethod);
                    }
                });
        return mapDiscoveredMethods;
    }

    /**
     * Generate a {@link DiscoveredMethod} from the given arguments.
     *
     * @param method {@link Method} being introspected
     * @param clazz  {@link Class} being introspected
     * @param pd     {@link PropertyDescriptor} for the property being introspected
     * @return a {@link DiscoveredMethod}
     */
    private static DiscoveredMethod generateDiscoveredMethod(Method method, Class<?> clazz, PropertyDescriptor pd) {
        DiscoveredMethod discoveredMethod = null;

        String name = method.getName();
        String prefix = null;
        String varName;
        if (name.startsWith("is")) {
            prefix = "is";
        } else if (name.startsWith("get")) {
            prefix = "get";
        }

        // remove the prefix and make first letter lowercase
        varName = name.replaceAll(prefix, "");
        varName = varName.substring(0, 1).toLowerCase() + varName.substring(1);
        // check for either Name or JsonbProperty annotations on method or field
        String annotatedName = getMethodName(method);
        if (annotatedName != null) {
            varName = annotatedName;
        } else {
            // check the field
            annotatedName = getFieldName(clazz, pd.getName());
            if (annotatedName != null) {
                varName = annotatedName;
            }
        }

        Class returnClazz = method.getReturnType();
        String returnClazzName = returnClazz.getName();

        boolean fieldHasIdAnnotation = false;
        // check for Id annotation on class or field associated with the read method
        // and if present change the type to ID
        try {
            Field field = clazz.getDeclaredField(pd.getName());
            fieldHasIdAnnotation = field != null && field.getAnnotation(Id.class) != null;
        } catch (NoSuchFieldException e) {
            // ignore
        }

        if (fieldHasIdAnnotation || method.getAnnotation(Id.class) != null) {
            returnClazzName = ID;
        }

        // check various array types
        if (Collection.class.isAssignableFrom(returnClazz)) {
            java.lang.reflect.Type returnType = method.getGenericReturnType();
            if (returnType instanceof ParameterizedType) {
                ParameterizedType paramReturnType = (ParameterizedType) returnType;
                discoveredMethod = new DiscoveredMethod(varName,
                                                        paramReturnType.getActualTypeArguments()[0].getTypeName(),
                                                        READ, method);
                discoveredMethod.setCollectionType(returnClazzName);
            }
        } else if (Map.class.isAssignableFrom(returnClazz)) {
            java.lang.reflect.Type returnType = method.getGenericReturnType();
            if (returnType instanceof ParameterizedType) {
                ParameterizedType paramReturnType = (ParameterizedType) returnType;
                // we are only interested in the value type as for this implementation we return a collection of
                //values()
                discoveredMethod = new DiscoveredMethod(varName,
                                                        paramReturnType.getActualTypeArguments()[1].getTypeName(),
                                                        READ, method);
                discoveredMethod.setMap(true);
            }
        } else if (!returnClazzName.isEmpty() && returnClazzName.startsWith("[")) {
            // return type is array of either primitives or Objects/Interfaces.
            String sPrimitiveType = PRIMITIVE_ARRAY_MAP.get(returnClazzName);
            if (sPrimitiveType != null) {
                // is an array of primitives
                discoveredMethod = new DiscoveredMethod(varName, sPrimitiveType, READ, method);
                discoveredMethod.setArrayReturnType(true);
            } else {
                // Array of Object/Interface
                // We are only supporting single level arrays currently
                int cArrayCount = 0;
                for (int i = 0; i < returnClazzName.length(); i++) {
                    if (returnClazzName.charAt(i) == '[') {
                        cArrayCount++;
                    }
                }

                if (cArrayCount > 1) {
                    LOGGER.warning("Multi-dimension arrays are not yet supported. Ignoring field " + name);
                } else {
                    String sRealReturnClass = returnClazzName.substring(2, returnClazzName.length() - 1);
                    discoveredMethod = new DiscoveredMethod(varName, sRealReturnClass, READ, method);
                    discoveredMethod.setArrayReturnType(true);
                }
            }
        } else {
            discoveredMethod = new DiscoveredMethod(varName, returnClazzName, READ, method);
        }

        return discoveredMethod;
    }

    /**
     * Return the field name after checking both the {@link Name} and {@link JsonbProperty} annotations are present on the {@link
     * Method}.<p> Name will take precedence if both are specified.
     *
     * @param method {@link Method} to check
     * @return the field name or null if non exist
     */
    protected static String getMethodName(Method method) {
        Name nameAnnotation = method.getAnnotation(Name.class);
        JsonbProperty jsonbPropertyAnnotation = method.getAnnotation(JsonbProperty.class);
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
     * Return the field name after checking both the {@link Name} and {@link JsonbProperty} annotations are present on the field
     * name..<p> Name will take precedence if both are specified.
     *
     * @param clazz     {@link Class} to check
     * @param fieldName field name to check
     * @return the field name or null if none exist
     */
    protected static String getFieldName(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            if (field != null) {
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
            }
            return null;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    /**
     * Defines discovered methods for a class.
     */
    public static class DiscoveredMethod {

        /**
         * Indicates read method.
         */
        public static final int READ = 0;

        /**
         * Indicates write method.
         */
        public static final int WRITE = 1;

        /**
         * Name of the discovered method.
         */
        private String name;

        /**
         * Return type of the method.
         */
        private String returnType;

        /**
         * type of method.
         */
        private int methodType;

        /**
         * If the return type is a {@link Collection} then this is the type of {@link Collection} and the returnType will be
         * return type for the collection.
         */
        private String collectionType;

        /**
         * Indicates if the return type is an array.
         */
        private boolean isArrayReturnType;

        /**
         * Indicates if the return type is a {@link Map}. Note: In the 1.0 mciroprofile spec the behaviour if {@link Map} is
         * undefined.
         */
        private boolean isMap;

        /**
         * The actual method.
         */
        private Method method;

        /**
         * Constructor using name and return type.
         *
         * @param name       name of the method
         * @param returnType return type
         * @param methodType type of the method
         */
        public DiscoveredMethod(String name, String returnType, int methodType, Method method) {
            this.name = name;
            this.returnType = returnType;
            this.methodType = methodType;
            this.method = method;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getReturnType() {
            return returnType;
        }

        public void setReturnType(String returnType) {
            this.returnType = returnType;
        }

        public String getCollectionType() {
            return collectionType;
        }

        public void setCollectionType(String collectionType) {
            this.collectionType = collectionType;
        }

        public boolean isMap() {
            return isMap;
        }

        public void setMap(boolean map) {
            isMap = map;
        }

        public int getMethodType() {
            return methodType;
        }

        public void setMethodType(int methodType) {
            this.methodType = methodType;
        }

        public boolean isArrayReturnType() {
            return isArrayReturnType;
        }

        public void setArrayReturnType(boolean arrayReturnType) {
            isArrayReturnType = arrayReturnType;
        }

        public boolean isCollectionType() {
            return collectionType != null;
        }

        public Method getMethod() {
            return method;
        }

        public void setMethod(Method method) {
            this.method = method;
        }

        @Override
        public String toString() {
            return "DiscoveredMethod{"
                    + "name='" + name + '\''
                    + ", returnType='" + returnType + '\''
                    + ", methodType=" + methodType
                    + ", collectionType='" + collectionType + '\''
                    + ", isArrayReturnType=" + isArrayReturnType
                    + ", method=" + method
                    + ", isMap=" + isMap + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DiscoveredMethod that = (DiscoveredMethod) o;
            return methodType == that.methodType
                    && isArrayReturnType == that.isArrayReturnType
                    && isMap == that.isMap
                    && Objects.equals(name, that.name)
                    && Objects.equals(returnType, that.returnType)
                    && Objects.equals(method, that.method)
                    && Objects.equals(collectionType, that.collectionType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, returnType, methodType, method,
                                collectionType, isArrayReturnType, isMap);
        }
    }

}
