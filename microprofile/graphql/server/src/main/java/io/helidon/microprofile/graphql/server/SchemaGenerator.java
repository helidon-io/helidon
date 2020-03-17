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

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.MethodDescriptor;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.json.bind.annotation.JsonbTransient;

import graphql.schema.DataFetcher;
import org.eclipse.microprofile.graphql.DefaultValue;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Id;
import org.eclipse.microprofile.graphql.Ignore;
import org.eclipse.microprofile.graphql.Input;
import org.eclipse.microprofile.graphql.Interface;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;
import org.eclipse.microprofile.graphql.Source;
import org.eclipse.microprofile.graphql.Type;

import static io.helidon.microprofile.graphql.server.SchemaGenerator.DiscoveredMethod.MUTATION_TYPE;
import static io.helidon.microprofile.graphql.server.SchemaGenerator.DiscoveredMethod.QUERY_TYPE;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.ID;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.checkScalars;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getArrayLevels;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getFieldName;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getGraphQLType;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getMethodName;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getNameAnnotationValue;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getRootArrayClass;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getRootTypeName;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getSafeClass;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getScalar;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getSimpleName;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getTypeName;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.isArrayType;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.isEnumClass;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.isGraphQLType;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.isValidIDType;

/**
 * Various utilities for generating {@link Schema}s from classes.
 */
public class SchemaGenerator {

    /**
     * Constant "is".
     */
    private static final String IS = "is";

    /**
     * Constant "get".
     */
    private static final String GET = "get";

    /**
     * Constant "set".
     */
    private static final String SET = "set";

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(SchemaGenerator.class.getName());

    /**
     * {@link JandexUtils} instance to hold indexes.
     */
    private JandexUtils jandexUtils;

    /**
     * Holds the {@link Set} of unresolved types while processing the annotations.
     */
    private Set<String> setUnresolvedTypes = new HashSet<>();

    /**
     * Holds the {@link Set} of additional methods that need to be added to types.
     */
    private Set<DiscoveredMethod> setAdditionalMethods = new HashSet<>();

    /**
     * Construct a {@link SchemaGenerator} instance.
     */
    public SchemaGenerator() {
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
        setAdditionalMethods.clear();

        SchemaType rootQueryType = new SchemaType(schema.getQueryName(), null);
        SchemaType rootMutationType = new SchemaType(schema.getMutationName(), null);

        // process any specific classes with the Input, Type or Interface annotations
        for (Class<?> clazz : clazzes) {
            LOGGER.log(Level.FINER, "Processing class " + clazz.getName());
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
                    String clazzName = clazz.getName();
                    String simpleName = clazz.getSimpleName();

                    SchemaInputType inputType = generateType(clazzName).createInputType("");
                    // if the name of the InputType was not changed then append "Input"
                    if (inputType.getName().equals(simpleName)) {
                        inputType.setName(inputType.getName() + "Input");
                    }
                    if (!schema.containsInputTypeWithName(inputType.getName())) {
                        schema.addInputType(inputType);
                        checkInputType(schema, inputType);
                    }
                }

                // obtain top level query API's
                if (clazz.isAnnotationPresent(GraphQLApi.class)) {
                    processGraphQLApiAnnotations(rootQueryType, rootMutationType, schema, clazz);
                }
            }
        }

        schema.addType(rootQueryType);
        schema.addType(rootMutationType);

        // process unresolved types
        processUnresolvedTypes(schema);

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

        // process any additional methods requires via the @Source annotation
        for (DiscoveredMethod dm : setAdditionalMethods) {
            // add the discovered method to the type
            SchemaType type = schema.getTypeByClass(dm.source);
            if (type != null) {
                SchemaFieldDefinition fd = newFieldDefinition(dm, null);
                // add all arguments which are not source arguments
                if (dm.getArguments().size() > 0) {
                    dm.getArguments().stream().filter(a -> !a.isSourceArgument())
                            .forEach(fd::addArgument);
                }

                fd.setDataFetcher(DataFetcherUtils.newMethodDataFetcher(
                        dm.method.getDeclaringClass(), dm.method, dm.getSource(),
                        fd.getArguments().toArray(new SchemaArgument[0])));
                type.addFieldDefinition(fd);

                String simpleName = getSimpleName(fd.getReturnType());
                String returnType = fd.getReturnType();
                if (!simpleName.equals(returnType)) {
                    updateLongTypes(schema, returnType, simpleName);
                }
            }
        }

        // process the @GraphQLApi annotated classes
        if (rootQueryType.getFieldDefinitions().size() == 0) {
            LOGGER.warning("Unable to find any classes with @GraphQLApi annotation."
                                   + "Unable to build schema");
        }

        return schema;
    }

    /**
     * Process any unresolved types.
     *
     * @param schema {@link Schema} to add types to
     */
    private void processUnresolvedTypes(Schema schema) {
        // create any types that are still unresolved. e.g. an Order that contains OrderLine objects
        // also ensure if the unresolved type contains another unresolved type then we process it
        while (setUnresolvedTypes.size() > 0) {
            String returnType = setUnresolvedTypes.iterator().next();

            setUnresolvedTypes.remove(returnType);
            try {
                //LOGGER.info("Checking unresolved type " + returnType);
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

            if (!ID.equals(valueTypeName) && valueTypeName.equals(fd.getReturnType())) {
                LOGGER.info("In generateType: Adding unresolved type of " + valueTypeName + " for method "
                                    + discoveredMethod.getName() + " on type " + realReturnType);
                // value class was unchanged meaning we need to resolve
                setUnresolvedTypes.add(valueTypeName);
            }
        }
        return type;
    }

    /**
     * Process a class with a {@link GraphQLApi} annotation.
     *
     * @param rootQueryType    the root query type
     * @param rootMutationType the root mutation type
     * @param clazz            {@link Class} to introspect
     * @throws IntrospectionException
     */
    @SuppressWarnings("rawtypes")
    private void processGraphQLApiAnnotations(SchemaType rootQueryType,
                                              SchemaType rootMutationType,
                                              Schema schema,
                                              Class<?> clazz)
            throws IntrospectionException, ClassNotFoundException {

        LOGGER.info("Processing " + clazz.getName());
        for (Map.Entry<String, DiscoveredMethod> entry : retrieveAllAnnotatedBeanMethods(clazz).entrySet()) {
            DiscoveredMethod discoveredMethod = entry.getValue();
            Method method = discoveredMethod.getMethod();

            LOGGER.info("Processing method " + discoveredMethod.getName() + ", " + discoveredMethod.getReturnType());

            SchemaFieldDefinition fd = null;

            // only include discovered methods in the original type where either the source is null
            // or the source is not null and it had a query annotation
            String source = discoveredMethod.getSource();
            if (source == null || discoveredMethod.isQueryAnnotated()) {
                fd = newFieldDefinition(discoveredMethod, getMethodName(method));
            }

            // if the source was not null, save it for later processing on the correct type
            if (source != null) {
                String additionReturnType = getGraphQLType(discoveredMethod.getReturnType());
                setAdditionalMethods.add(discoveredMethod);
                // add the unresolved type for the source
                if (!isGraphQLType(additionReturnType)) {
                    setUnresolvedTypes.add(additionReturnType);
                }
            }

            SchemaType schemaType = discoveredMethod.methodType == QUERY_TYPE
                    ? rootQueryType
                    : rootMutationType;

            // add all the arguments and check to see if they contain types that are not yet known
            // this check is done no matter if the field definition is going to be created or not
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
                        if (!schema.containsInputTypeWithName(inputType.getName())) {
                            schema.addInputType(inputType);
                        }
                        a.setArgumentType(inputType.getName());

                        //check the input type for any other input types
                        checkInputType(schema, inputType);
                    }
                }
                if (fd != null) {
                    fd.addArgument(a);
                }
            }

            if (fd != null) {
                DataFetcher dataFetcher = DataFetcherUtils
                        .newMethodDataFetcher(clazz, method, null, fd.getArguments().toArray(new SchemaArgument[0]));
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
    }

    /**
     * Check this new {@link SchemaInputType} contains any types, then they must also have InputTypes created for them if they are
     * not enums or scalars.
     *
     * @param schema          {@link Schema} to add to
     * @param schemaInputType {@link SchemaInputType} to check
     * @throws IntrospectionException if issues with introspection
     * @throws ClassNotFoundException if class not found
     */
    private void checkInputType(Schema schema, SchemaInputType schemaInputType)
            throws IntrospectionException, ClassNotFoundException {
        // if this new Type contains any types, then they must also have
        // InputTypes created for them if they are not enums or scalars
        Set<SchemaInputType> setInputTypes = new HashSet<>();
        setInputTypes.add(schemaInputType);

        // setInputTypes contains all InputTypes that need to be checked for types other than
        // Enum, Scalar or GraphQL Type
        while (setInputTypes.size() > 0) {
            SchemaInputType type = setInputTypes.iterator().next();
            setInputTypes.remove(type);
            LOGGER.info("Checking input type: " + type.getName());
            // check each field definition to see if any return types are unknownInputTypes
            for (SchemaFieldDefinition fdi : type.getFieldDefinitions()) {
                String fdReturnType = fdi.getReturnType();

                if (!isGraphQLType(fdReturnType)) {
                    // must be either an unknown input type, Scalar or Enum
                    if (getScalar(fdReturnType) != null || isEnumClass(fdReturnType)) {
                        setUnresolvedTypes.add(fdReturnType);
                    } else {
                        // must be a type, create a new input Type but do not add it to
                        // the schema if it already exists
                        SchemaInputType newInputType = generateType(fdReturnType)
                                .createInputType("Input");
                        if (!schema.containsInputTypeWithName(newInputType.getName())) {
                            schema.addInputType(newInputType);
                            setInputTypes.add(newInputType);
                            LOGGER.info("Adding new input type " + newInputType.getName());
                        } else {
                            LOGGER.info("Ignoring: " + newInputType.getName());
                        }
                        fdi.setReturnType(newInputType.getName());
                    }
                }
            }
        }
    }

    /**
     * Add the given {@link SchemaType} to the {@link Schema}.
     *
     * @param schema the {@link Schema} to add to
     * @throws IntrospectionException if issues with introspection
     * @throws ClassNotFoundException if class not found
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
            if (!ID.equals(returnType) && returnType.equals(fd.getReturnType()) && !setUnresolvedTypes.contains(returnType)) {
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
                                                             false,
                                                             method.getArrayLevels());
        fd.setDataFetcher(dataFetcher);
        return fd;
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
            boolean isQuery = m.getAnnotation(Query.class) != null;
            boolean isMutation = m.getAnnotation(Mutation.class) != null;
            boolean hasSourceAnnotation = Arrays.stream(m.getParameters()).anyMatch(p -> p.getAnnotation(Source.class) != null);
            if (isMutation && isQuery) {
                throw new RuntimeException("A class may not have both a Query and Mutation annotation");
            }
            if (isQuery || isMutation || hasSourceAnnotation) {
                LOGGER.info("Processing Query or Mutation " + m.getName());
                DiscoveredMethod discoveredMethod = generateDiscoveredMethod(m, clazz, null);
                discoveredMethod.setMethodType(isQuery || hasSourceAnnotation ? QUERY_TYPE : MUTATION_TYPE);
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
                PropertyDescriptor propertyDescriptor = optionalPd.get();
                try {
                    // check to see if field is annotated with @Ignore or @JsonbTransient
                    Field field = clazz.getDeclaredField(propertyDescriptor.getName());
                    if (field != null
                            && (
                            field.getAnnotation(Ignore.class) != null
                                    || field.getAnnotation(JsonbTransient.class) != null)) {
                        continue;
                    }
                } catch (NoSuchFieldException e) {
                    // ignore check if no field exists
                }

                // this is a getter method, include it here
                DiscoveredMethod discoveredMethod = generateDiscoveredMethod(m, clazz, propertyDescriptor);
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

        if (name.startsWith(IS) || name.startsWith(GET) || name.startsWith(SET)) {
            // this is a getter method
            String prefix = null;
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
        if ("void".equals(returnClazzName)) {
            String message = "void is not a valid return type for a Query or Mutation for method "
                    + method.getName() + " on class " + clazz.getName();
            LOGGER.warning(message);
            throw new RuntimeException(message);
        }

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
                Class<?> paramType = parameter.getType();

                ReturnType returnType = getReturnType(paramType, genericParameterTypes[i++]);
                if (parameter.getAnnotation(Id.class) != null) {
                    if (!isValidIDType(paramType)) {
                        throw new RuntimeException("A class of type " + paramType + " is not allowed to be an @Id");
                    }
                    returnType.setReturnClass(ID);
                }

                SchemaArgument argument = new SchemaArgument(parameterName, returnType.getReturnClass(), false, null, paramType);

                Source sourceAnnotation = parameter.getAnnotation(Source.class);
                if (sourceAnnotation != null) {
                    discoveredMethod.setSource(returnType.getReturnClass());
                    discoveredMethod.setQueryAnnotated(method.getAnnotation(Query.class) != null);
                    argument.setSourceArgument(true);
                }

                discoveredMethod.addArgument(argument);
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

        discoveredMethod.setArrayLevels(realReturnType.getArrayLevels());

        return discoveredMethod;
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
        SchemaGeneratorHelper.RootTypeResult rootTypeResult = null;
        String returnClazzName = returnClazz.getName();
        boolean isCollection = Collection.class.isAssignableFrom(returnClazz);
        boolean isMap = Map.class.isAssignableFrom(returnClazz);
        // deal with Collection or Map
        if (isCollection || isMap) {
            if (isCollection) {
                actualReturnType.setCollectionType(returnClazzName);
            }

            actualReturnType.setMap(isMap);
            // index is 0 for Collection and 1 for Map which assumes
            // we are not interested in the map K, just the map V
            rootTypeResult = getRootTypeName(genericReturnType, isCollection ? 0 : 1);
            String rootType = rootTypeResult.getRootTypeName();

            // set the initial number of array levels to the levels of the root type
            int arrayLevels = rootTypeResult.getLevels();

            if (isArrayType(rootType)) {
                actualReturnType.setReturnClass(getRootArrayClass(rootType));
                arrayLevels += getArrayLevels(rootType);
            } else {
                actualReturnType.setReturnClass(rootType);
            }
            actualReturnType.setArrayLevels(arrayLevels);
        } else if (!returnClazzName.isEmpty() && returnClazzName.startsWith("[")) {
            // return type is array of either primitives or Objects/Interface/Enum.
            actualReturnType.setArrayType(true);
            actualReturnType.setArrayLevels(getArrayLevels(returnClazzName));
            actualReturnType.setReturnClass(getRootArrayClass(returnClazzName));
        } else {
            actualReturnType.setReturnClass(returnClazzName);
        }
        return actualReturnType;
    }

    /**
     * Defines discovered methods for a class.
     */
    public static class DiscoveredMethod {

        /**
         * Indicates query Type.
         */
        public static final int QUERY_TYPE = 0;

        /**
         * Indicates write method.
         */
        public static final int MUTATION_TYPE = 1;

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
         * Number of levels in the Array.
         */
        private int arrayLevels = 0;

        /**
         * The source on which the method should be added.
         */
        private String source;

        /**
         * Indicates if the method containing the {@link Source} annotation was also annotated with the {@link Query} annotation.
         * If true, then this indicates that a top level query shoudl also be created as well as the field in the type.
         */
        private boolean isQueryAnnotated = false;

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
         * Return the number of levels in the Array.
         *
         * @return Return the number of levels in the Array
         */
        public int getArrayLevels() {
            return arrayLevels;
        }

        /**
         * Sets the number of levels in the Array.
         *
         * @param arrayLevels the number of levels in the Array
         */
        public void setArrayLevels(int arrayLevels) {
            this.arrayLevels = arrayLevels;
        }

        /**
         * Add a {@link SchemaArgument}.
         *
         * @param argument a {@link SchemaArgument}
         */
        public void addArgument(SchemaArgument argument) {
            listArguments.add(argument);
        }

        /**
         * Return the source on which the method should be added.
         *
         * @return source on which the method should be added
         */
        public String getSource() {
            return source;
        }

        /**
         * Set the source on which the method should be added.
         *
         * @param source source on which the method should be added
         */
        public void setSource(String source) {
            this.source = source;
        }

        /**
         * Indicates if the method containing the {@link Source} annotation was also annotated with the {@link Query} annotation.
         *
         * @return true if the {@Link Query} annotation was present
         */
        public boolean isQueryAnnotated() {
            return isQueryAnnotated;
        }

        /**
         * Set if the method containing the {@link Source} annotation was * also annotated with the {@link Query} annotation.
         *
         * @param queryAnnotated true if the {@Link Query} annotation was present
         */
        public void setQueryAnnotated(boolean queryAnnotated) {
            isQueryAnnotated = queryAnnotated;
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
                    + ", arrayLevels=" + arrayLevels
                    + ", source=" + source
                    + ", isQueryAnnotated=" + isQueryAnnotated
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
                    && arrayLevels == that.arrayLevels
                    && Objects.equals(name, that.name)
                    && Objects.equals(returnType, that.returnType)
                    && Objects.equals(source, that.source)
                    && Objects.equals(isQueryAnnotated, that.isQueryAnnotated)
                    && Objects.equals(method, that.method)
                    && Objects.equals(collectionType, that.collectionType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, returnType, methodType, method, arrayLevels, isQueryAnnotated,
                                collectionType, isArrayReturnType, isMap, source);
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
         * Number of levels in the Array.
         */
        private int arrayLevels = 0;

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

        /**
         * Return the level of arrays or 0 if not an array.
         *
         * @return the level of arrays
         */
        public int getArrayLevels() {
            return arrayLevels;
        }

        /**
         * Set the level of arrays or 0 if not an array.
         *
         * @param arrayLevels the level of arrays or 0 if not an array
         */
        public void setArrayLevels(int arrayLevels) {
            this.arrayLevels = arrayLevels;
        }
    }
}
