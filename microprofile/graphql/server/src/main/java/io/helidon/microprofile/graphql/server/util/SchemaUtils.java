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
import java.beans.MethodDescriptor;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
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
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.json.bind.annotation.JsonbProperty;

import io.helidon.microprofile.graphql.server.model.Schema;
import io.helidon.microprofile.graphql.server.model.SchemaArgument;
import io.helidon.microprofile.graphql.server.model.SchemaEnum;
import io.helidon.microprofile.graphql.server.model.SchemaFieldDefinition;
import io.helidon.microprofile.graphql.server.model.SchemaInputType;
import io.helidon.microprofile.graphql.server.model.SchemaScalar;
import io.helidon.microprofile.graphql.server.model.SchemaType;

import graphql.Scalars;
import graphql.scalars.ExtendedScalars;
import graphql.schema.DataFetcher;
import org.eclipse.microprofile.graphql.DefaultValue;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.Enum;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Id;
import org.eclipse.microprofile.graphql.Input;
import org.eclipse.microprofile.graphql.Interface;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;
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
        put(long.class.getName(), new SchemaScalar("BigInteger", Long.class.getName(), Scalars.GraphQLBigInteger));
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
     * List of all Java primitive objects.
     */
    private static final List<String> JAVA_PRIMITIVE_OBJECTS = new ArrayList<>() {{
        addAll(STRING_LIST);
        addAll(FLOAT_LIST);
        addAll(INTEGER_LIST);
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
     * Class for long primitive.
     */
    protected static final String LONG_PRIMITIVE = long.class.getName();

    /**
     * Class for Long object.
     */
    protected static final String LONG_OBJECT = Long.class.getName();

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
        if (!jandexUtils.hasIndex()) {
            return generateSchemaFromClasses();
        }

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
            LOGGER.info("processing class " + clazz.getName());
            // only include interfaces and concrete classes/enums
            if (clazz.isInterface() || (!clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers()))) {
                // Discover Enum via annotation
                if (clazz.isAnnotationPresent(org.eclipse.microprofile.graphql.Enum.class)) {
                    schema.addEnum(generateEnum(clazz));
                    continue;
                }

                // Type, Interface, Input are all treated similarly
                Type typeAnnotation = clazz.getAnnotation(Type.class);
                Interface interfaceAnnotation = clazz.getAnnotation(Interface.class);
                Input inputAnnotation = clazz.getAnnotation(Input.class);

                if (typeAnnotation != null || interfaceAnnotation != null) {
                    // interface or type
                    if (interfaceAnnotation != null && !clazz.isInterface()) {
                        throw new RuntimeException("Class " + clazz.getName() + " has been annotated with"
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

                // obtain top level query API's
                if (clazz.isAnnotationPresent(GraphQLApi.class)) {
                    addRootQueriesToSchema(rootQueryType, schema, clazz);
                }
            }
        }

        schema.addType(rootQueryType);

        // create any types that are still unresolved. e.g. an Order that contains OrderLine objects
        // we must also ensure if the unresolved type contains another unresolved type then we process it
        while (setUnresolvedTypes.size() > 0) {
            String returnType = setUnresolvedTypes.iterator().next();

            setUnresolvedTypes.remove(returnType);
            try {
                LOGGER.info("Checking unresolved type " + returnType);
                String simpleName = getSimpleName(returnType);

                SchemaScalar scalar = getScalar(returnType);
                if (scalar != null) {
                    if (!schema.containsScalarWithName(scalar.getName())) {
                        schema.addScalar(scalar);
                    }
                    // update the return type with the scalar
                    updateLongTypes(schema, returnType, scalar.getName());
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
            schema.getTypes().stream().filter(t -> !t.isInterface() && t.getValueClassName() != null).forEach(type -> {
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
        }

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
        if (descriptionAnnotation != null && !"".equals(descriptionAnnotation.value())) {
            type.setDescription(descriptionAnnotation.value());
        }

        for (Map.Entry<String, DiscoveredMethod> entry : retrieveGetterBeanMethods(Class.forName(realReturnType)).entrySet()) {
            DiscoveredMethod discoveredMethod = entry.getValue();
            String valueTypeName = discoveredMethod.getReturnType();
            SchemaFieldDefinition fd = newFieldDefinition(discoveredMethod, null);
            type.addFieldDefinition(fd);

            if (discoveredMethod.getReturnType().equals(fd.getReturnType())) {
                LOGGER.info("Adding unresolved type of " + valueTypeName + " for method "
                                    + discoveredMethod.getName() + " on type " + realReturnType);
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
    @SuppressWarnings("rawtypes")
    private void addRootQueriesToSchema(SchemaType schemaType, Schema schema, Class<?> clazz)
            throws IntrospectionException, ClassNotFoundException {
        for (Map.Entry<String, DiscoveredMethod> entry : retrieveAllAnnotatedBeanMethods(clazz).entrySet()) {
            DiscoveredMethod discoveredMethod = entry.getValue();
            Method method = discoveredMethod.getMethod();

            SchemaFieldDefinition fd = newFieldDefinition(discoveredMethod, getMethodName(method));

            // add all the arguments and check to see if they contain types that are not yet known
            for (SchemaArgument a : discoveredMethod.getArguments()) {
                String originalTypeName = a.getArgumentType();
                String typeName = getGraphQLType(originalTypeName);

                a.setArgumentType(typeName);
                String returnType = a.getArgumentType();

                if (originalTypeName.equals(returnType) && !ID.equals(returnType)) {
                    // type name has not changed, so this must be either a Scalar, Enum or a Type
                    // Note: Interfaces are not currently supported as InputTypes in 1.0 of the Specification
                    // if is Scalar or enum then add to unresolved types and they will be dealt with
                    if (getScalar(returnType) != null || isEnumClass(returnType)) {
                        setUnresolvedTypes.add(returnType);
                    } else {
                        // create the input Type here
                        SchemaInputType inputType = generateType(returnType).createInputType("Input");
                        schema.addInputType(inputType);
                        a.setArgumentType(inputType.getName());

                        // if this new Type contains any types, then they must also have
                        // InputTypes created for them if they are not enums or scalars
                        //                        Set<SchemaInputType> setInputTypes = new HashSet<>();
                        //                        setInputTypes.add(inputType);
                        //
                        //                        while (setInputTypes.size() > 0) {
                        //                            SchemaInputType type = setInputTypes.iterator().next();
                        //                            setInputTypes.remove(type);
                        //                            // check each field definition to see if any return types are unknown
                        //                           InputTypes
                        //                            type.getFieldDefinitions().forEach(fdi -> {
                        //                                String fdReturnType = fdi.getReturnType();
                        //                                String fdTypeName = getGraphQLType(fdReturnType);
                        //                                if (fdTypeName.equals(fdi.getReturnType())) {
                        //                                    // has not changed so must be either an unknown input type or
                        //                                   Scalar or enum
                        //                                    if (getScalar(fdReturnType) != null || isEnumClass(fdReturnType)) {
                        //                                        setUnresolvedTypes.add(fdReturnType);
                        //                                    }
                        //                                    else {
                        //                                        SchemaInputType newInputType = generateType(fdReturnType)
                        //                                       .createInputType("Input");
                        //                                    }
                        //                                }
                        //                            });
                        //                        }
                    }
                }
                fd.addArgument(a);
            }

            DataFetcher dataFetcher = DataFetcherUtils
                    .newMethodDataFetcher(clazz, method, fd.getArguments().toArray(new SchemaArgument[0]));
            fd.setDataFetcher(dataFetcher);

            schemaType.addFieldDefinition(fd);

            // check for scalar return type
            checkScalars(schema, schemaType);

            String returnType = discoveredMethod.getReturnType();
            // check to see if this is a known type
            if (returnType.equals(fd.getReturnType()) && !setUnresolvedTypes.contains(returnType)) {
                // value class was unchanged meaning we need to resolve
                setUnresolvedTypes.add(returnType);
            }
        }
    }

    /**
     * Add the given {@link SchemaType} to the {@link Schema}.
     *
     * @param schema the {@link Schema} to add to
     * @throws IntrospectionException
     * @throws ClassNotFoundException
     */
    private void addTypeToSchema(Schema schema, SchemaType type)
            throws IntrospectionException, ClassNotFoundException {

        String valueClassName = type.getValueClassName();
        retrieveGetterBeanMethods(Class.forName(valueClassName)).forEach((k, v) -> {

            SchemaFieldDefinition fd = newFieldDefinition(v, null);
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
                    .map(Object::toString)
                    .forEach(newSchemaEnum::addValue);
            return newSchemaEnum;
        }
        return null;
    }

    /**
     * Return a new {@link SchemaFieldDefinition} with the given field and class.
     *
     * @param method       the {@link DiscoveredMethod}
     * @param optionalName optional name for the field definition
     * @return a {@link SchemaFieldDefinition}
     */
    @SuppressWarnings("rawTypes")
    private SchemaFieldDefinition newFieldDefinition(DiscoveredMethod method, String optionalName) {
        String sValueClassName = method.getReturnType();
        DataFetcher dataFetcher = null;
        boolean isArrayReturnType = method.isArrayReturnType || method.isCollectionType() || method.isMap();

        if (isArrayReturnType) {
            if (method.isMap) {
                // add DataFetcher that will just retrieve the values() from the map
                // dataFetcher = DataFetcherUtils.newMapValuesDataFetcher(fieldName);
                // TODO
            }
        }

        SchemaFieldDefinition fd = new SchemaFieldDefinition(optionalName != null
                                                                     ? optionalName
                                                                     : method.name,
                                                             getGraphQLType(sValueClassName),
                                                             isArrayReturnType,
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
        if (nameAnnotation != null && !"".equals(nameAnnotation.value())) {
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
                || STRING.equals(className) || BOOLEAN.equalsIgnoreCase(className)
                || LONG_OBJECT.equals(className) || LONG_PRIMITIVE.equals(className)) {
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
    @SuppressWarnings("unchecked")
    private void updateLongTypes(Schema schema, String longReturnType, String shortReturnType) {
        // concatenate both the SchemaType and SchemaInputType
        Stream streamInputTypes = schema.getInputTypes().stream().map(it -> (SchemaType) it);
        Stream<SchemaType> streamAll = Stream.concat(streamInputTypes, schema.getTypes().stream());
        streamAll.forEach(t -> {
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
     * Return a {@link Map} of all the discovered methods which have the {@link Query} annotation.
     *
     * @param clazz Class to introspect
     * @return a {@link Map} of the methods and return types
     * @throws IntrospectionException if there were errors introspecting classes
     */
    protected static Map<String, DiscoveredMethod> retrieveAllAnnotatedBeanMethods(Class<?> clazz)
            throws IntrospectionException {
        Map<String, DiscoveredMethod> mapDiscoveredMethods = new HashMap<>();
        for (Method m : getAllMethods(clazz)) {
            if (m.getAnnotation(Query.class) != null) {
                DiscoveredMethod discoveredMethod = generateDiscoveredMethod(m, clazz, null);
                mapDiscoveredMethods.put(discoveredMethod.getName(), discoveredMethod);
            }
        }
        return mapDiscoveredMethods;
    }

    /**
     * Retrieve only the getter methods for the {@link Class}.
     *
     * @param clazz the {@link Class} to introspect
     * @return a {@link Map} of the methods and return types
     * @throws IntrospectionException if there were errors introspecting classes
     */
    protected static Map<String, DiscoveredMethod> retrieveGetterBeanMethods(Class<?> clazz) throws IntrospectionException {
        Map<String, DiscoveredMethod> mapDiscoveredMethods = new HashMap<>();

        for (Method m : getAllMethods(clazz)) {
            if (m.getName().equals("getClass")) {
                continue;
            }
            Optional<PropertyDescriptor> optionalPd = Arrays.stream(Introspector.getBeanInfo(clazz).getPropertyDescriptors())
                    .filter(p -> p.getReadMethod() != null && p.getReadMethod().getName().equals(m.getName())).findFirst();
            if (optionalPd.isPresent()) {
                // this is a getter method, include it here
                DiscoveredMethod discoveredMethod = generateDiscoveredMethod(m, clazz, optionalPd.get());
                mapDiscoveredMethods.put(discoveredMethod.getName(), discoveredMethod);
            }

        }
        return mapDiscoveredMethods;
    }

    /**
     * Return all {@link Method}s for a given {@link Class}.
     *
     * @param clazz the {@link Class} to introspect
     * @return all {@link Method}s for a given {@link Class}
     * @throws IntrospectionException
     */
    protected static List<Method> getAllMethods(Class<?> clazz) throws IntrospectionException {
        return Arrays.asList(Introspector.getBeanInfo(clazz).getMethodDescriptors())
                .stream()
                .map(MethodDescriptor::getMethod)
                .collect(Collectors.toList());
    }

    /**
     * Generate a {@link DiscoveredMethod} from the given arguments.
     *
     * @param method {@link Method} being introspected
     * @param clazz  {@link Class} being introspected
     * @param pd     {@link PropertyDescriptor} for the property being introspected (may be null if retrieving all methods as in
     *               the case for a {@link Query} annotation)
     * @return a {@link DiscoveredMethod}
     */
    private static DiscoveredMethod generateDiscoveredMethod(Method method, Class<?> clazz, PropertyDescriptor pd) {

        String name = method.getName();
        String varName;

        if (pd != null) {
            // this is a getter method
            String prefix = null;
            if (name.startsWith("is")) {
                prefix = "is";
            } else if (name.startsWith("get")) {
                prefix = "get";
            }

            // remove the prefix and make first letter lowercase
            varName = name.replaceAll(prefix, "");
            varName = varName.substring(0, 1).toLowerCase() + varName.substring(1);
        } else {
            // may be any method, e.g. from GraphQLApi annotated class
            varName = name;
        }

        // check for either Name or JsonbProperty annotations on method or field
        String annotatedName = getMethodName(method);
        if (annotatedName != null) {
            varName = annotatedName;
        } else if (pd != null) {
            // check the field only if this is a getter
            annotatedName = getFieldName(clazz, pd.getName());
            if (annotatedName != null) {
                varName = annotatedName;
            }
        }

        Class<?> returnClazz = method.getReturnType();
        String returnClazzName = returnClazz.getName();

        if (pd != null) {
            boolean fieldHasIdAnnotation = false;
            // check for Id annotation on class or field associated with the method
            // and if present change the type to ID
            try {
                Field field = clazz.getDeclaredField(pd.getName());
                fieldHasIdAnnotation = field != null && field.getAnnotation(Id.class) != null;
            } catch (NoSuchFieldException e) {
                // ignore
            }

            if (fieldHasIdAnnotation || method.getAnnotation(Id.class) != null) {
                if (!isValidIDType(returnClazz)) {
                    throw new RuntimeException("A class of type " + returnClazz + " is not allowed to be an @Id");
                }
                returnClazzName = ID;
            }
        }

        DiscoveredMethod discoveredMethod = new DiscoveredMethod();
        discoveredMethod.setName(varName);
        discoveredMethod.setMethodType(READ);
        discoveredMethod.setMethod(method);

        Parameter[] parameters = method.getParameters();
        if (parameters != null && parameters.length > 0) {
            // process the parameters for the method

            java.lang.reflect.Type[] genericParameterTypes = method.getGenericParameterTypes();
            int i = 0;
            for (Parameter parameter : parameters) {
                Name paramNameAnnotation = parameter.getAnnotation(Name.class);
                String parameterName = paramNameAnnotation != null
                        && !paramNameAnnotation.value().isBlank()
                        ? paramNameAnnotation.value()
                        : parameter.getName();
                DefaultValue defaultValueAnnotations = parameter.getAnnotation(DefaultValue.class);
                // TODO: Add default value processing here

                Class<?> paramType = parameter.getType();

                ReturnType returnType = getReturnType(paramType, genericParameterTypes[i++]);
                if (parameter.getAnnotation(Id.class) != null) {
                    if (!isValidIDType(paramType)) {
                        throw new RuntimeException("A class of type " + paramType + " is not allowed to be an @Id");
                    }
                    returnType.setReturnClass(ID);
                }

                discoveredMethod
                        .addArgument(new SchemaArgument(parameterName, returnType.getReturnClass(), false, null, paramType));
            }
        }

        // process the return type for the method
        ReturnType realReturnType = getReturnType(returnClazz, method.getGenericReturnType());
        if (realReturnType.getReturnClass() != null && !ID.equals(returnClazzName)) {
            discoveredMethod.setArrayReturnType(realReturnType.isArrayType());
            discoveredMethod.setCollectionType(realReturnType.getCollectionType());
            discoveredMethod.setMap(realReturnType.isMap());
            discoveredMethod.setReturnType(realReturnType.getReturnClass());
        } else {
            discoveredMethod.setName(varName);
            discoveredMethod.setReturnType(returnClazzName);
            discoveredMethod.setMethod(method);
        }

        return discoveredMethod;
    }

    /**
     * Return true if the {@link Class} is a valid {@link Class} to apply the {@link Id} annotation.
     *
     * @param clazz {@link Class} to check
     * @return true if the {@link Class} is a valid {@link Class} to apply the {@link Id} annotation
     */
    private static boolean isValidIDType(Class<?> clazz) {
        return clazz.equals(Long.class) || clazz.equals(Integer.class)
                || clazz.equals(java.util.UUID.class) || clazz.equals(int.class)
                || clazz.equals(String.class) || clazz.equals(long.class);
    }

    /**
     * Return the {@link ReturnType} for this return class and method.
     *
     * @param returnClazz       return type
     * @param genericReturnType generic return {@link java.lang.reflect.Type} may be null
     * @return a {@link ReturnType}
     */
    private static ReturnType getReturnType(Class<?> returnClazz, java.lang.reflect.Type genericReturnType) {
        ReturnType actualReturnType = new ReturnType();
        String returnClazzName = returnClazz.getName();
        if (Collection.class.isAssignableFrom(returnClazz)) {
            actualReturnType.setCollectionType(returnClazzName);
            if (genericReturnType instanceof ParameterizedType) {
                ParameterizedType paramReturnType = (ParameterizedType) genericReturnType;
                actualReturnType.setReturnClass(paramReturnType.getActualTypeArguments()[0].getTypeName());
            } else {
                actualReturnType.setReturnClass(Object.class.getName());
            }
        } else if (Map.class.isAssignableFrom(returnClazz)) {
            actualReturnType.setMap(true);
            if (genericReturnType instanceof ParameterizedType) {
                ParameterizedType paramReturnType = (ParameterizedType) genericReturnType;
                // we are only interested in the value type as for this implementation we return a collection of values()
                actualReturnType.setReturnClass(paramReturnType.getActualTypeArguments()[1].getTypeName());
            } else {
                actualReturnType.setReturnClass(Object.class.getName());
            }
        } else if (!returnClazzName.isEmpty() && returnClazzName.startsWith("[")) {
            // return type is array of either primitives or Objects/Interfaces.
            actualReturnType.setArrayType(true);
            String sPrimitiveType = PRIMITIVE_ARRAY_MAP.get(returnClazzName);
            if (sPrimitiveType != null) {
                actualReturnType.setReturnClass(sPrimitiveType);
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
                    LOGGER.warning("Multi-dimension arrays are not yet supported. Ignoring class " + returnClazzName);
                } else {
                    actualReturnType.setReturnClass(returnClazzName.substring(2, returnClazzName.length() - 1));
                }
            }
        } else {
            actualReturnType.setReturnClass(returnClazzName);
        }
        return actualReturnType;
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
        Name nameAnnotation = method.getAnnotation(Name.class);
        JsonbProperty jsonbPropertyAnnotation = method.getAnnotation(JsonbProperty.class);
        if (queryAnnotation != null && !queryAnnotation.value().isBlank()) {
            return queryAnnotation.value();
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
     * Return the list of Java primitive types which are Objects.
     *
     * @return the list of Java primitive types
     */
    public static List<String> getJavaPrimitiveObjects() {
        return JAVA_PRIMITIVE_OBJECTS;
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
         * The {@link List} of {@link SchemaArgument}s for this method.
         */
        private List<SchemaArgument> listArguments = new ArrayList<>();

        /**
         * The actual method.
         */
        private Method method;

        /**
         * Default constructor.
         */
        public DiscoveredMethod() {
        }

        /**
         * Return the name.
         *
         * @return the name
         */
        public String getName() {
            return name;
        }

        /**
         * Set the name.
         *
         * @param name the name
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * Return the return type.
         *
         * @return the return type
         */
        public String getReturnType() {
            return returnType;
        }

        /**
         * Set the return type.
         *
         * @param returnType the return type
         */
        public void setReturnType(String returnType) {
            this.returnType = returnType;
        }

        /**
         * Return the collection type.
         *
         * @return the collection
         */
        public String getCollectionType() {
            return collectionType;
        }

        /**
         * Set the collection type.
         *
         * @param collectionType the collection type
         */
        public void setCollectionType(String collectionType) {
            this.collectionType = collectionType;
        }

        /**
         * Indicates if the method is a map return type.
         *
         * @return if the method is a map return type
         */
        public boolean isMap() {
            return isMap;
        }

        /**
         * Set if the method is a map return type.
         *
         * @param map if the method is a map return type
         */
        public void setMap(boolean map) {
            isMap = map;
        }

        /**
         * Return the method type.
         *
         * @return the method type
         */
        public int getMethodType() {
            return methodType;
        }

        /**
         * Set the method type either READ or WRITE.
         *
         * @param methodType the method type
         */
        public void setMethodType(int methodType) {
            this.methodType = methodType;
        }

        /**
         * Indicates if the method returns an array.
         *
         * @return if the method returns an array.
         */
        public boolean isArrayReturnType() {
            return isArrayReturnType;
        }

        /**
         * Indicates if the method returns an array.
         *
         * @param arrayReturnType if the method returns an array
         */
        public void setArrayReturnType(boolean arrayReturnType) {
            isArrayReturnType = arrayReturnType;
        }

        /**
         * Indicates if the method is a collection type.
         *
         * @return if the method is a collection type
         */
        public boolean isCollectionType() {
            return collectionType != null;
        }

        /**
         * Returns the {@link Method}.
         *
         * @return the {@link Method}
         */
        public Method getMethod() {
            return method;
        }

        /**
         * Sets the {@link Method}.
         *
         * @param method the {@link Method}
         */
        public void setMethod(Method method) {
            this.method = method;
        }

        /**
         * Return the {@link List} of {@link SchemaArgument}s.
         *
         * @return the {@link List} of {@link SchemaArgument}
         */
        public List<SchemaArgument> getArguments() {
            return this.listArguments;
        }

        /**
         * Add a {@link SchemaArgument}.
         *
         * @param argument a {@link SchemaArgument}
         */
        public void addArgument(SchemaArgument argument) {
            listArguments.add(argument);
        }

        @Override
        public String toString() {
            return "DiscoveredMethod{"
                    + "name='" + name + '\''
                    + ", returnType='" + returnType + '\''
                    + ", methodType=" + methodType
                    + ", collectionType='" + collectionType + '\''
                    + ", isArrayReturnType=" + isArrayReturnType
                    + ", isMap=" + isMap
                    + ", listArguments=" + listArguments
                    + ", method=" + method + '}';
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

    /**
     * Defines return types for methods or parameters.
     */
    public static class ReturnType {

        /**
         * Return class.
         */
        private String returnClass;

        /**
         * Indicates if this is an array type.
         */
        private boolean isArrayType = false;

        /**
         * Indicates if this is a {@link Map}.
         */
        private boolean isMap = false;

        /**
         * Return the type of collection.
         */
        private String collectionType;

        /**
         * Default constructor.
         */
        public ReturnType() {
        }

        /**
         * Return the return class.
         *
         * @return the return class
         */
        public String getReturnClass() {
            return returnClass;
        }

        /**
         * Sets the return class.
         *
         * @param returnClass the return class
         */
        public void setReturnClass(String returnClass) {
            this.returnClass = returnClass;
        }

        /**
         * Indicates if this is an array type.
         *
         * @return if this is an array type
         */
        public boolean isArrayType() {
            return isArrayType;
        }

        /**
         * Set if this is an array type.
         *
         * @param arrayType if this is an array type
         */
        public void setArrayType(boolean arrayType) {
            isArrayType = arrayType;
        }

        /**
         * Indicates if this is a {@link Map}.
         *
         * @return if this is a {@link Map}
         */
        public boolean isMap() {
            return isMap;
        }

        /**
         * Set if this is a {@link Map}.
         *
         * @param map if this is a {@link Map}
         */
        public void setMap(boolean map) {
            isMap = map;
        }

        /**
         * Return the type of collection.
         *
         * @return the type of collection
         */
        public String getCollectionType() {
            return collectionType;
        }

        /**
         * Set the type of collection.
         *
         * @param collectionType the type of collection
         */
        public void setCollectionType(String collectionType) {
            this.collectionType = collectionType;
        }
    }
}
