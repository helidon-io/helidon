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

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.MethodDescriptor;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
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

import javax.enterprise.inject.spi.CDI;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetcherFactories;
import graphql.schema.GraphQLScalarType;
import graphql.schema.PropertyDataFetcher;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Id;
import org.eclipse.microprofile.graphql.Input;
import org.eclipse.microprofile.graphql.Interface;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.NonNull;
import org.eclipse.microprofile.graphql.Query;
import org.eclipse.microprofile.graphql.Source;
import org.eclipse.microprofile.graphql.Type;

import static io.helidon.microprofile.graphql.server.CustomScalars.CUSTOM_DATE_SCALAR;
import static io.helidon.microprofile.graphql.server.CustomScalars.CUSTOM_DATE_TIME_SCALAR;
import static io.helidon.microprofile.graphql.server.CustomScalars.CUSTOM_OFFSET_DATE_TIME_SCALAR;
import static io.helidon.microprofile.graphql.server.CustomScalars.CUSTOM_TIME_SCALAR;
import static io.helidon.microprofile.graphql.server.CustomScalars.CUSTOM_ZONED_DATE_TIME_SCALAR;
import static io.helidon.microprofile.graphql.server.FormattingHelper.DATE;
import static io.helidon.microprofile.graphql.server.FormattingHelper.NO_FORMATTING;
import static io.helidon.microprofile.graphql.server.FormattingHelper.NUMBER;
import static io.helidon.microprofile.graphql.server.FormattingHelper.formatDate;
import static io.helidon.microprofile.graphql.server.FormattingHelper.formatNumber;
import static io.helidon.microprofile.graphql.server.FormattingHelper.getCorrectDateFormatter;
import static io.helidon.microprofile.graphql.server.FormattingHelper.getCorrectNumberFormat;
import static io.helidon.microprofile.graphql.server.FormattingHelper.getFormattingAnnotation;
import static io.helidon.microprofile.graphql.server.FormattingHelper.isJsonbAnnotationPresent;
import static io.helidon.microprofile.graphql.server.SchemaGenerator.DiscoveredMethod.MUTATION_TYPE;
import static io.helidon.microprofile.graphql.server.SchemaGenerator.DiscoveredMethod.QUERY_TYPE;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.DATETIME_SCALAR;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.DATE_SCALAR;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.FORMATTED_DATETIME_SCALAR;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.FORMATTED_DATE_SCALAR;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.FORMATTED_OFFSET_DATETIME_SCALAR;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.FORMATTED_TIME_SCALAR;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.FORMATTED_ZONED_DATETIME_SCALAR;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.ID;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.STRING;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.TIME_SCALAR;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.checkScalars;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.ensureFormat;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.ensureRuntimeException;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.ensureValidName;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getAnnotationValue;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getArrayLevels;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getDefaultValueAnnotationValue;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getDescription;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getFieldAnnotations;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getFieldName;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getGraphQLType;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getMethodAnnotations;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getMethodName;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getParameterAnnotations;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getRootArrayClass;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getSafeClass;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getScalar;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getSimpleName;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getTypeName;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.isArrayType;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.isDateTimeScalar;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.isEnumClass;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.isGraphQLType;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.isPrimitive;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.isValidIDType;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.shouldIgnoreField;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.shouldIgnoreMethod;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.stripMethodName;

/**
 * Various utilities for generating {@link Schema}s from classes.
 */
public class SchemaGenerator {

    /**
     * Constant "is".
     */
    protected static final String IS = "is";

    /**
     * Constant "get".
     */
    protected static final String GET = "get";

    /**
     * Constant "set".
     */
    protected static final String SET = "set";

    /**
     * Class name for {@link NonNull}.
     */
    protected static final String NON_NULL_CLASS = NonNull.class.getName();

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
     * A {@link Context} to be passed to execution.
     */
    private Context context;

    /**
     * Construct a {@link SchemaGenerator} instance.
     *
     * @param context the {@link Context} to use
     */
    public SchemaGenerator(Context context) {
        this.context = context;
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
     * Generate a {@link Schema} by scanning all discovered classes using the {@link GraphQLCdiExtension}.
     *
     * @return a {@link Schema}
     */
    @SuppressWarnings("rawtypes")
    public Schema generateSchema() throws IntrospectionException, ClassNotFoundException {
        GraphQLCdiExtension extension = CDI.current()
                .getBeanManager()
                .getExtension(GraphQLCdiExtension.class);
        Class[] classes = extension.collectedApis();
        Class[] schemaProcessors = extension.collectedSchemaProcessors();

        Schema schema = generateSchemaFromClasses(classes);

        for (Class<?> schemaProcessor : schemaProcessors) {
            if (SchemaPreProcessor.class.isAssignableFrom(schemaProcessor)) {
                try {
                    Constructor<?> constructor = schemaProcessor.getDeclaredConstructor();
                    SchemaPreProcessor processor = (SchemaPreProcessor) constructor.newInstance();
                    LOGGER.info("Calling SchemaPreProcessor " + processor);
                    processor.processSchema(schema);
                } catch (Exception e) {
                    LOGGER.warning("Unable to constructor or call SchemaPreProcessor "
                                           + schemaProcessor + ": " + e.getMessage());
                }
            }
        }
        return schema;
    }

    /**
     * Generate a {@link Schema} from a given array of classes.  The classes are checked to see if they contain any of the
     * annotations from the microprofile spec.
     *
     * @param clazzes array of classes to check
     * @return a {@link Schema}
     */
    protected Schema generateSchemaFromClasses(Class<?>... clazzes)
            throws IntrospectionException, ClassNotFoundException {
        Schema schema = new Schema();
        setUnresolvedTypes.clear();
        setAdditionalMethods.clear();

        SchemaType rootQueryType = new SchemaType(schema.getQueryName(), null);
        SchemaType rootMutationType = new SchemaType(schema.getMutationName(), null);

        // process any specific classes with the Input, Type or Interface annotations
        for (Class<?> clazz : clazzes) {
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

                if (typeAnnotation != null && inputAnnotation != null) {
                    ensureRuntimeException(LOGGER, "Class " + clazz.getName() + " has been annotated with"
                            + " both Type and Input");
                }

                if (typeAnnotation != null || interfaceAnnotation != null) {
                    if (interfaceAnnotation != null && !clazz.isInterface()) {
                        ensureRuntimeException(LOGGER, "Class " + clazz.getName() + " has been annotated with"
                                + " @Interface but is not one");
                    }

                    // assuming value for annotation overrides @Name
                    String typeName = getTypeName(clazz, true);
                    SchemaType type = new SchemaType(typeName.isBlank() ? clazz.getSimpleName() : typeName, clazz.getName());
                    type.setIsInterface(clazz.isInterface());
                    type.setDescription(getDescription(clazz.getAnnotation(Description.class)));

                    // add the discovered type
                    addTypeToSchema(schema, type);

                    if (type.isInterface()) {
                        // is an interface so check for any implementors and add them too
                        jandexUtils.getKnownImplementors(clazz.getName()).forEach(c -> setUnresolvedTypes.add(c.getName()));
                    }
                } else if (inputAnnotation != null) {
                    String clazzName = clazz.getName();
                    String simpleName = clazz.getSimpleName();

                    SchemaInputType inputType = generateType(clazzName, true).createInputType("");
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

        // process any additional methods require via the @Source annotation
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

                // check for existing DataFetcher
                fd.setDataFetcher(DataFetcherUtils.newMethodDataFetcher(
                        schema, dm.method.getDeclaringClass(), dm.method,
                        dm.getSource(), fd.getArguments().toArray(new SchemaArgument[0])));
                type.addFieldDefinition(fd);

                // we are creating this as a type so ignore any Input annotation
                String simpleName = getSimpleName(fd.getReturnType(), true);
                String returnType = fd.getReturnType();
                if (!simpleName.equals(returnType)) {
                    updateLongTypes(schema, returnType, simpleName);
                }
            }
        }

        // process default values for dates
        processDefaultDateTimeValues(schema);

        // process the @GraphQLApi annotated classes
        if (rootQueryType.getFieldDefinitions().size() == 0 && rootMutationType.getFieldDefinitions().size() == 0) {
            LOGGER.warning("Unable to find any classes with @GraphQLApi annotation."
                                   + "Unable to build schema");
        }

        return schema;
    }

    /**
     * Process all {@link SchemaFieldDefinition}s and {@link SchemaArgument}s and update the default values for any scalars.
     *
     * @param schema {@link Schema} to update
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void processDefaultDateTimeValues(Schema schema) {
        // concatenate both the SchemaType and SchemaInputType
        Stream streamInputTypes = schema.getInputTypes().stream().map(it -> (SchemaType) it);
        Stream<SchemaType> streamAll = Stream.concat(streamInputTypes, schema.getTypes().stream());
        streamAll.forEach(t -> {
            t.getFieldDefinitions().forEach(fd -> {
                String returnType = fd.getReturnType();
                // only check Date/Time/DateTime scalars that are not Queries or don't have data fetchers
                // as default formatting has already been dealt with
                if (isDateTimeScalar(returnType) && (t.getName().equals(Schema.QUERY) || fd.getDataFetcher() == null)) {
                    String[] existingFormat = fd.getFormat();
                    // check if this type is an array type and if so then get the actual original type
                    Class<?> clazzOriginalType = fd.getOriginalArrayType() != null
                            ? fd.getOriginalArrayType() : fd.getOriginalType();
                    String[] newFormat = ensureFormat(returnType, clazzOriginalType.getName(), existingFormat);
                    if (!Arrays.equals(newFormat, existingFormat) && newFormat.length == 2) {
                        // formats differ so set the new format and DataFetcher
                        fd.setFormat(newFormat);
                        if (fd.getDataFetcher() == null) {
                            // create the raw array to pass to the retrieveFormattingDataFetcher method
                            DataFetcher dataFetcher = retrieveFormattingDataFetcher(
                                    new String[] {DATE, newFormat[0], newFormat[1] },
                                    fd.getName(), clazzOriginalType.getName());
                            fd.setDataFetcher(dataFetcher);
                        }
                        fd.setDefaultFormatApplied(true);
                        SchemaScalar scalar = schema.getScalarByName(fd.getReturnType());
                        GraphQLScalarType newScalarType = null;
                        if (fd.getReturnType().equals(FORMATTED_DATE_SCALAR)) {
                            fd.setReturnType(DATE_SCALAR);
                            newScalarType = CUSTOM_DATE_SCALAR;
                        } else if (fd.getReturnType().equals(FORMATTED_TIME_SCALAR)) {
                            fd.setReturnType(TIME_SCALAR);
                            newScalarType = CUSTOM_TIME_SCALAR;
                        } else if (fd.getReturnType().equals(FORMATTED_DATETIME_SCALAR)) {
                            fd.setReturnType(DATETIME_SCALAR);
                            newScalarType = CUSTOM_DATE_TIME_SCALAR;
                        } else if (fd.getReturnType().equals(FORMATTED_OFFSET_DATETIME_SCALAR)) {
                            fd.setReturnType(FORMATTED_OFFSET_DATETIME_SCALAR);
                            newScalarType = CUSTOM_OFFSET_DATE_TIME_SCALAR;
                        } else if (fd.getReturnType().equals(FORMATTED_ZONED_DATETIME_SCALAR)) {
                            fd.setReturnType(FORMATTED_ZONED_DATETIME_SCALAR);
                            newScalarType = CUSTOM_ZONED_DATE_TIME_SCALAR;
                        }

                        // clone the scalar with the new scalar name
                        SchemaScalar newScalar = new SchemaScalar(fd.getReturnType(), scalar.getActualClass(),
                                                                  newScalarType, scalar.getDefaultFormat());
                        if (!schema.containsScalarWithName(newScalar.getName())) {
                            schema.addScalar(newScalar);
                        }
                    }
                }

                // check the SchemaArguments
                fd.getArguments().forEach(a -> {
                    String argumentType = a.getArgumentType();
                    if (isDateTimeScalar(argumentType)) {
                        String[] existingArgFormat = a.getFormat();
                        String[] newArgFormat = ensureFormat(argumentType, a.getOriginalType().getName(), existingArgFormat);
                        if (!Arrays.equals(newArgFormat, existingArgFormat) && newArgFormat.length == 2) {
                            a.setFormat(newArgFormat);
                        }
                    }
                });
            });
        });
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
                String simpleName = getSimpleName(returnType, true);

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
                        SchemaType newType = generateType(returnType, false);

                        // update any return types to the discovered scalars
                        checkScalars(schema, newType);
                        schema.addType(newType);
                    }
                    // need to update any FieldDefinitions that contained the original "long" type of c
                    updateLongTypes(schema, returnType, simpleName);
                }
            } catch (Exception e) {
                ensureRuntimeException(LOGGER, "Cannot get GraphQL type for " + returnType, e);
            }
        }
    }

    /**
     * Generate a {@link SchemaType} from a given class.
     *
     * @param realReturnType the class to generate type from
     * @param isInputType    indicates if the type is an input type and if not the Input annotation will be ignored
     * @return a {@link SchemaType}
     * @throws IntrospectionException
     * @throws ClassNotFoundException
     */
    private SchemaType generateType(String realReturnType, boolean isInputType)
            throws IntrospectionException, ClassNotFoundException {

        // if isInputType=false then we ignore the name annotation in case
        // an annotated input type was also used as a return type
        String simpleName = getSimpleName(realReturnType, !isInputType);
        SchemaType type = new SchemaType(simpleName, realReturnType);
        type.setDescription(getDescription(Class.forName(realReturnType).getAnnotation(Description.class)));

        for (Map.Entry<String, DiscoveredMethod> entry : retrieveGetterBeanMethods(Class.forName(realReturnType), isInputType)
                .entrySet()) {
            DiscoveredMethod discoveredMethod = entry.getValue();
            String valueTypeName = discoveredMethod.getReturnType();
            SchemaFieldDefinition fd = newFieldDefinition(discoveredMethod, null);
            type.addFieldDefinition(fd);

            if (!ID.equals(valueTypeName) && valueTypeName.equals(fd.getReturnType())) {
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

        for (Map.Entry<String, DiscoveredMethod> entry : retrieveAllAnnotatedBeanMethods(clazz).entrySet()) {
            DiscoveredMethod discoveredMethod = entry.getValue();
            Method method = discoveredMethod.getMethod();

            SchemaFieldDefinition fd = null;

            // only include discovered methods in the original type where either the source is null
            // or the source is not null and it has a query annotation
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
                        SchemaInputType inputType = generateType(returnType, true).createInputType("");
                        // if the name of the InputType was not changed then append "Input"
                        if (inputType.getName().equals(Class.forName(returnType).getSimpleName())) {
                            inputType.setName(inputType.getName() + "Input");
                        }

                        if (!schema.containsInputTypeWithName(inputType.getName())) {
                            schema.addInputType(inputType);
                            checkInputType(schema, inputType);
                        }
                        a.setArgumentType(inputType.getName());
                    }

                    // in either case, get the argument format

                }
                if (fd != null) {
                    fd.addArgument(a);
                }
            }

            if (fd != null) {
                DataFetcher dataFetcher = null;
                String[] format = discoveredMethod.getFormat();
                SchemaScalar dateScalar = getScalar(discoveredMethod.getReturnType());

                // if the type is a Date/Time/DateTime scalar and there is currently no format,
                // then use the default format if there is one
                if ((dateScalar != null && isDateTimeScalar(dateScalar.getName()) && isFormatEmpty(format))) {
                    Class<?> originalType = fd.isArrayReturnType() ? fd.getOriginalArrayType() : fd.getOriginalType();
                    String[] newFormat = ensureFormat(dateScalar.getName(),
                                                      originalType.getName(), new String[2]);
                    if (newFormat.length == 2) {
                        format = new String[] {DATE, newFormat[0], newFormat[1] };
                    }
                }
                if (!isFormatEmpty(format)) {
                    // a format exists on the method return type so format it after returning the value
                    final String graphQLType = getGraphQLType(fd.getReturnType());
                    final DataFetcher methodDataFetcher = DataFetcherUtils.newMethodDataFetcher(schema, clazz, method, null,
                                                                                                fd.getArguments().toArray(
                                                                                                        new SchemaArgument[0]));
                    final String[] newFormat = new String[] {format[0], format[1], format[2] };

                    if (dateScalar != null && isDateTimeScalar(dateScalar.getName())) {
                        dataFetcher = DataFetcherFactories.wrapDataFetcher(methodDataFetcher,
                                                                           (e, v) -> {
                                                                               DateTimeFormatter dateTimeFormatter =
                                                                                       getCorrectDateFormatter(
                                                                                               graphQLType, newFormat[2],
                                                                                               newFormat[1]);
                                                                               return formatDate(v, dateTimeFormatter);
                                                                           });
                    } else {
                        dataFetcher = DataFetcherFactories.wrapDataFetcher(methodDataFetcher,
                                                                           (e, v) -> {
                                                                               NumberFormat numberFormat = getCorrectNumberFormat(
                                                                                       graphQLType, newFormat[2], newFormat[1]);
                                                                               boolean isScalar = SchemaGeneratorHelper.getScalar(
                                                                                       discoveredMethod.getReturnType()) != null;
                                                                               return formatNumber(v, isScalar, numberFormat);
                                                                           });
                        fd.setReturnType(STRING);
                    }
                } else {
                    // no formatting, just call the method
                    dataFetcher = DataFetcherUtils.newMethodDataFetcher(schema, clazz, method, null,
                                                                        fd.getArguments().toArray(new SchemaArgument[0]));
                }
                fd.setDataFetcher(dataFetcher);
                fd.setDescription(discoveredMethod.getDescription());

                schemaType.addFieldDefinition(fd);

                // check for scalar return type
                checkScalars(schema, schemaType);

                String returnType = discoveredMethod.getReturnType();
                // check to see if this is a known type
                if (returnType.equals(fd.getReturnType()) && !setUnresolvedTypes.contains(returnType)
                        && !ID.equals(returnType)) {
                    // value class was unchanged meaning we need to resolve
                    setUnresolvedTypes.add(returnType);
                }
            }
        }
    }

    /**
     * Returns true if the format is empty or undefined.
     *
     * @param format format to check
     * @return true if the format is empty or undefined
     */
    protected static boolean isFormatEmpty(String[] format) {
        if (format == null || format.length == 0) {
            return true;
        }
        // now check that each value is not null
        for (String entry : format) {
            if (entry == null) {
                return true;
            }
        }
        return false;
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

        while (setInputTypes.size() > 0) {
            SchemaInputType type = setInputTypes.iterator().next();
            setInputTypes.remove(type);

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
                        SchemaInputType newInputType = generateType(fdReturnType, true).createInputType("");

                        // if the name of the InputType was not changed then append "Input"
                        if (newInputType.getName().equals(Class.forName(newInputType.getValueClassName()).getSimpleName())) {
                            newInputType.setName(newInputType.getName() + "Input");
                        }

                        if (!schema.containsInputTypeWithName(newInputType.getName())) {
                            schema.addInputType(newInputType);
                            setInputTypes.add(newInputType);
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
        retrieveGetterBeanMethods(Class.forName(valueClassName), false).forEach((k, v) -> {
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
     * @param discoveredMethod the {@link DiscoveredMethod}
     * @param optionalName     optional name for the field definition
     * @return a {@link SchemaFieldDefinition}
     */
    @SuppressWarnings("rawtypes")
    private SchemaFieldDefinition newFieldDefinition(DiscoveredMethod discoveredMethod, String optionalName) {
        String valueClassName = discoveredMethod.getReturnType();
        String graphQLType = getGraphQLType(valueClassName);
        DataFetcher dataFetcher = null;
        String propertyName = discoveredMethod.getPropertyName();
        String name = discoveredMethod.getName();

        boolean isArrayReturnType = discoveredMethod.isArrayReturnType || discoveredMethod.isCollectionType() || discoveredMethod
                .isMap();

        if (isArrayReturnType) {
            if (discoveredMethod.isMap) {
                // add DataFetcher that will just retrieve the values() from the map
                // dataFetcher = DataFetcherUtils.newMapValuesDataFetcher(fieldName);
            }
        }

        // check for format on the property
        // note: currently the format will be an array of [3] as defined by FormattingHelper.getFormattingAnnotation
        String[] format = discoveredMethod.getFormat();
        if (propertyName != null && !isFormatEmpty(format)) {
            if (!isGraphQLType(valueClassName)) {
                dataFetcher = retrieveFormattingDataFetcher(format, propertyName, graphQLType);

                // if the format is a number format then set teh return type to String
                if (NUMBER.equals(format[0])) {
                    graphQLType = STRING;
                }
            }
        } else {
            // Add a PropertyDataFetcher if the name has been changed via annotation
            if (propertyName != null && !propertyName.equals(name)) {
                dataFetcher = new PropertyDataFetcher(propertyName);
            }
        }

        SchemaFieldDefinition fd = new SchemaFieldDefinition(optionalName != null
                                                                     ? optionalName
                                                                     : discoveredMethod.name,
                                                             graphQLType,
                                                             isArrayReturnType,
                                                             discoveredMethod.isReturnTypeMandatory(),
                                                             discoveredMethod.getArrayLevels());
        fd.setDataFetcher(dataFetcher);
        fd.setOriginalType(discoveredMethod.getMethod().getReturnType());
        fd.setArrayReturnTypeMandatory(discoveredMethod.isArrayReturnTypeMandatory());
        fd.setOriginalArrayType(isArrayReturnType ? discoveredMethod.getOriginalArrayType() : null);

        if (format != null && format.length == 3) {
            fd.setFormat(new String[] {format[1], format[2] });
        }

        fd.setDescription(discoveredMethod.getDescription());
        fd.setJsonbFormat(discoveredMethod.isJsonbFormat);
        fd.setDefaultValue(discoveredMethod.getDefaultValue());
        return fd;
    }

    /**
     * Return the correct formatting {@link DataFetcher} to format the date or number field.
     *
     * @param rawFormat    raw format with [0] = type, [1] = locale, [2] = format
     * @param propertyName property to fetch from
     * @param type         the type of the data - from Class.getName()
     * @return the correct {@link DataFetcher}
     */
    private DataFetcher retrieveFormattingDataFetcher(String[] rawFormat, String propertyName, String type) {
        return NUMBER.equals(rawFormat[0])
                ? new DataFetcherUtils.NumberFormattingDataFetcher(propertyName, type, rawFormat[1], rawFormat[2])
                : new DataFetcherUtils.DateFormattingDataFetcher(propertyName, type, rawFormat[1], rawFormat[2]);
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

        // look through set of additional methods added for Source annotations
        setAdditionalMethods.forEach(m -> {
            m.getArguments().forEach(a -> {
                if (a.getArgumentType().equals(longReturnType)) {
                    a.setArgumentType(shortReturnType);
                }
            });
        });
    }

    /**
     * Return a {@link Map} of all the discovered methods which have the {@link Query} or {@link Mutation} annotations.
     *
     * @param clazz Class to introspect
     * @return a {@link Map} of the methods and return types
     * @throws IntrospectionException if there were errors introspecting classes
     */
    protected Map<String, DiscoveredMethod> retrieveAllAnnotatedBeanMethods(Class<?> clazz)
            throws IntrospectionException, ClassNotFoundException {
        Map<String, DiscoveredMethod> mapDiscoveredMethods = new HashMap<>();
        for (Method m : getAllMethods(clazz)) {
            boolean isQuery = m.getAnnotation(Query.class) != null;
            boolean isMutation = m.getAnnotation(Mutation.class) != null;
            boolean hasSourceAnnotation = Arrays.stream(m.getParameters()).anyMatch(p -> p.getAnnotation(Source.class) != null);
            if (isMutation && isQuery) {
                ensureRuntimeException(LOGGER, "The class " + clazz.getName()
                        + " may not have both a Query and Mutation annotation");
            }
            if (isQuery || isMutation || hasSourceAnnotation) {
                DiscoveredMethod discoveredMethod = generateDiscoveredMethod(m, clazz, null, false, true);
                discoveredMethod.setMethodType(isQuery || hasSourceAnnotation ? QUERY_TYPE : MUTATION_TYPE);
                String name = discoveredMethod.getName();
                if (mapDiscoveredMethods.containsKey(name)) {
                    ensureRuntimeException(LOGGER, "A method named " + name + " already exists on "
                            + "the " + (isMutation ? "mutation" : "query")
                            + " " + discoveredMethod.getMethod().getName());
                }
                mapDiscoveredMethods.put(name, discoveredMethod);
            }
        }
        return mapDiscoveredMethods;
    }

    /**
     * Retrieve only the getter methods for the {@link Class}.
     *
     * @param clazz       the {@link Class} to introspect
     * @param isInputType indicates if this type is an input type
     * @return a {@link Map} of the methods and return types
     * @throws IntrospectionException if there were errors introspecting classes
     */
    protected Map<String, DiscoveredMethod> retrieveGetterBeanMethods(Class<?> clazz,
                                                                      boolean isInputType)
            throws IntrospectionException, ClassNotFoundException {
        Map<String, DiscoveredMethod> mapDiscoveredMethods = new HashMap<>();

        for (Method m : getAllMethods(clazz)) {
            if (m.getName().equals("getClass") || shouldIgnoreMethod(m, isInputType)) {
                continue;
            }

            Optional<PropertyDescriptor> optionalPdReadMethod = Arrays
                    .stream(Introspector.getBeanInfo(clazz).getPropertyDescriptors())
                    .filter(p -> p.getReadMethod() != null && p.getReadMethod().getName().equals(m.getName())).findFirst();

            if (optionalPdReadMethod.isPresent()) {
                PropertyDescriptor propertyDescriptor = optionalPdReadMethod.get();
                Method writeMethod = propertyDescriptor.getWriteMethod();
                boolean ignoreWriteMethod = isInputType && writeMethod != null && shouldIgnoreMethod(writeMethod, true);

                // only include if the field should not be ignored
                if (!shouldIgnoreField(clazz, propertyDescriptor.getName()) && !ignoreWriteMethod) {
                    // this is a getter method, include it here
                    DiscoveredMethod discoveredMethod = generateDiscoveredMethod(m, clazz, propertyDescriptor, isInputType,
                                                                                 false);
                    mapDiscoveredMethods.put(discoveredMethod.getName(), discoveredMethod);
                }
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
    protected List<Method> getAllMethods(Class<?> clazz) throws IntrospectionException {
        return Arrays.asList(Introspector.getBeanInfo(clazz).getMethodDescriptors())
                .stream()
                .map(MethodDescriptor::getMethod)
                .collect(Collectors.toList());
    }

    /**
     * Generate a {@link DiscoveredMethod} from the given arguments.
     *
     * @param method            {@link Method} being introspected
     * @param clazz             {@link Class} being introspected
     * @param pd                {@link PropertyDescriptor} for the property being introspected (may be null if retrieving all
     *                          methods as in the case for a {@link Query} annotation)
     * @param isInputType       indicates if the method is part of an input type
     * @param isQueryOrMutation indicates if this is for a query or mutation
     * @return a {@link DiscoveredMethod}
     */
    private DiscoveredMethod generateDiscoveredMethod(Method method, Class<?> clazz,
                                                      PropertyDescriptor pd, boolean isInputType,
                                                      boolean isQueryOrMutation) throws ClassNotFoundException {
        String[] format = new String[0];
        String description = null;
        boolean isReturnTypeMandatory = false;
        boolean isArrayReturnTypeMandatory = false;
        boolean isJsonbFormat = false;
        String defaultValue = null;

        // retrieve the method name
        String varName = stripMethodName(method, !isQueryOrMutation);

        // check for either Name or JsonbProperty annotations on method or field
        // ensure if this is an input type that the write method is checked
        String annotatedName = getMethodName(isInputType ? pd.getWriteMethod() : method);
        if (annotatedName != null) {
            varName = annotatedName;
        } else if (pd != null) {
            // check the field only if this is a getter
            annotatedName = getFieldName(clazz, pd.getName());
            if (annotatedName != null) {
                varName = annotatedName;
            }
        }

        ensureValidName(LOGGER, varName);

        Class<?> returnClazz = method.getReturnType();
        String returnClazzName = returnClazz.getName();
        if ("void".equals(returnClazzName)) {
            ensureRuntimeException(LOGGER, "void is not a valid return type for a Query or Mutation method "
                    + method.getName() + " on class " + clazz.getName());
        }

        if (pd != null) {
            boolean fieldHasIdAnnotation = false;
            Field field = null;

            try {
                field = clazz.getDeclaredField(pd.getName());
                fieldHasIdAnnotation = field != null && field.getAnnotation(Id.class) != null;
                description = getDescription(field.getAnnotation(Description.class));

                // default values only make sense for input types
                defaultValue = isInputType ? getDefaultValueAnnotationValue(field) : null;
                NonNull nonNullAnnotation = field.getAnnotation(NonNull.class);
                isArrayReturnTypeMandatory = getAnnotationValue(getFieldAnnotations(field, 0), NonNull.class) != null;

                if (isInputType) {
                    Method writeMethod = pd.getWriteMethod();
                    if (writeMethod != null) {
                        // retrieve the setter method and check the description
                        String methodDescription = getDescription(writeMethod.getAnnotation(Description.class));
                        if (methodDescription != null) {
                            description = methodDescription;
                        }
                        String writeMethodDefaultValue = getDefaultValueAnnotationValue(writeMethod);
                        if (writeMethodDefaultValue != null) {
                            defaultValue = writeMethodDefaultValue;
                        }

                        // for an input type the method annotation will override
                        NonNull methodAnnotation = writeMethod.getAnnotation(NonNull.class);
                        if (methodAnnotation != null) {
                            nonNullAnnotation = methodAnnotation;
                        }

                        // the annotation on the set method parameter will override for the input type if it's present
                        boolean isSetArrayMandatory =
                                getAnnotationValue(getParameterAnnotations(writeMethod.getParameters()[0], 0),
                                                   NonNull.class) != null;
                        if (isSetArrayMandatory && !isArrayReturnTypeMandatory) {
                            isArrayReturnTypeMandatory = true;
                        }

                        // if the set method has a format then this should overwrite any formatting
                        // as this is an InputType
                        String[] writeMethodFormat = getFormattingAnnotation(writeMethod);
                        if (writeMethodFormat[0] != null) {
                            format = writeMethodFormat;
                            isJsonbFormat = isJsonbAnnotationPresent(writeMethod);
                        }

                    }
                } else {
                    NonNull methodAnnotation = method.getAnnotation(NonNull.class);
                    if (methodAnnotation != null) {
                        nonNullAnnotation = methodAnnotation;
                    }
                    if (!isArrayReturnTypeMandatory) {
                        isArrayReturnTypeMandatory =
                                getAnnotationValue(getMethodAnnotations(method, 0), NonNull.class) != null;
                    }
                }

                // if the return type is annotated as NonNull or it is a primitive then it is mandatory
                isReturnTypeMandatory = (isPrimitive(returnClazzName) && defaultValue == null)
                        || nonNullAnnotation != null && defaultValue == null;

            } catch (NoSuchFieldException e) {
            }

            if (fieldHasIdAnnotation || method.getAnnotation(Id.class) != null) {
                validateIDClass(returnClazz);
                returnClazzName = ID;
            }

            // check for format on the property but only override if it is null
            if (field != null && (format.length == 0 || format[0] == null)) {
                format = getFormattingAnnotation(field);
                if (isFormatEmpty(format)) {
                    // check to see format of the inner most class. E.g. List<@DateFormat("DD/MM") String>
                    format = FormattingHelper.getFieldFormat(field, 0);
                }
                isJsonbFormat = isJsonbAnnotationPresent(field);
            }
        } else {
            // pd is null which means this is for query or mutation
            defaultValue = getDefaultValueAnnotationValue(method);
            isReturnTypeMandatory = isPrimitive(returnClazzName) && defaultValue == null
                    || method.getAnnotation(NonNull.class) != null && defaultValue == null;
            if (method.getAnnotation(Id.class) != null) {
                validateIDClass(returnClazz);
                returnClazzName = ID;
            }
        }

        // check for method return type number format
        String[] methodFormat = getFormattingAnnotation(method);
        if (methodFormat[0] != null && !isInputType) {
            format = methodFormat;
        }

        DiscoveredMethod discoveredMethod = new DiscoveredMethod();
        discoveredMethod.setName(varName);
        discoveredMethod.setMethod(method);
        discoveredMethod.setFormat(format);
        discoveredMethod.setDefaultValue(defaultValue);
        discoveredMethod.setJsonbFormat(isJsonbFormat);
        discoveredMethod.setPropertyName(pd != null ? pd.getName() : null);

        if (description == null && !isInputType) {
            description = getDescription(method.getAnnotation(Description.class));
        }

        processMethodParameters(method, discoveredMethod, annotatedName);

        // process the return type for the method
        ReturnType realReturnType = getReturnType(returnClazz, method.getGenericReturnType(), -1, method);
        if (realReturnType.getReturnClass() != null && !ID.equals(returnClazzName)) {
            discoveredMethod.setArrayReturnType(realReturnType.isArrayType());
            discoveredMethod.setCollectionType(realReturnType.getCollectionType());
            discoveredMethod.setMap(realReturnType.isMap());
            SchemaScalar dateScalar = getScalar(realReturnType.getReturnClass());
            if (dateScalar != null && isDateTimeScalar(dateScalar.getName())) {
                // only set the original array type if it's a date/time
                discoveredMethod.setOriginalArrayType(Class.forName(realReturnType.returnClass));
            }
            discoveredMethod.setReturnType(realReturnType.getReturnClass());
            if (!isFormatEmpty(realReturnType.getFormat())) {
                discoveredMethod.setFormat(realReturnType.format);
            }
        } else {
            discoveredMethod.setName(varName);
            discoveredMethod.setReturnType(returnClazzName);
            discoveredMethod.setMethod(method);
        }

        discoveredMethod.setArrayLevels(realReturnType.getArrayLevels());
        discoveredMethod.setReturnTypeMandatory(isReturnTypeMandatory);
        discoveredMethod.setArrayReturnTypeMandatory(isArrayReturnTypeMandatory
                                                             || realReturnType.isReturnTypeMandatory && !isInputType);
        discoveredMethod.setDescription(description);

        return discoveredMethod;
    }

    /**
     * Process parameters for the given method.
     *
     * @param method           {@link Method} to process
     * @param discoveredMethod {@link DiscoveredMethod} to update
     * @param annotatedName    annotated name or null
     */
    private void processMethodParameters(Method method, DiscoveredMethod discoveredMethod, String annotatedName) {
        Parameter[] parameters = method.getParameters();
        if (parameters != null && parameters.length > 0) {
            java.lang.reflect.Type[] genericParameterTypes = method.getGenericParameterTypes();
            int i = 0;
            for (Parameter parameter : parameters) {
                boolean isID = false;
                Name paramNameAnnotation = parameter.getAnnotation(Name.class);
                String parameterName = paramNameAnnotation != null
                        && !paramNameAnnotation.value().isBlank()
                        ? paramNameAnnotation.value()
                        : parameter.getName();

                Class<?> paramType = parameter.getType();

                ReturnType returnType = getReturnType(paramType, genericParameterTypes[i], i, method);

                if (parameter.getAnnotation(Id.class) != null) {
                    validateIDClass(returnType.getReturnClass());
                    returnType.setReturnClass(ID);
                    isID = true;
                }

                String argumentDefaultValue = getDefaultValueAnnotationValue(parameter);

                boolean isMandatory =
                        (isPrimitive(paramType) && argumentDefaultValue == null)
                                || (parameter.getAnnotation(NonNull.class) != null && argumentDefaultValue == null);
                SchemaArgument argument =
                        new SchemaArgument(parameterName, returnType.getReturnClass(),
                                           isMandatory, argumentDefaultValue, paramType);
                String argumentDescription = getDescription(parameter.getAnnotation(Description.class));
                argument.setDescription(argumentDescription);

                String[] argumentFormat = getFormattingAnnotation(parameter);
                String[] argumentTypeFormat = FormattingHelper.getMethodParameterFormat(parameter, 0);

                // The argument type format overrides any argument format. E.g. NumberFormat should apply below
                // E.g.  public List<String> getListAsString(@Name("arg1")
                //                                           @JsonbNumberFormat("ignore 00.0000000")
                //                                           List<List<@NumberFormat("value 00.0000000") BigDecimal>> values)
                argumentFormat = !isFormatEmpty(argumentTypeFormat) ? argumentTypeFormat : argumentFormat;

                if (argumentFormat[0] != null) {
                    argument.setFormat(new String[] {argumentFormat[1], argumentFormat[2] });
                    argument.setArgumentType(String.class.getName());
                }

                Source sourceAnnotation = parameter.getAnnotation(Source.class);
                if (sourceAnnotation != null) {
                    // set the method name to the correct property name as it will currently be incorrect
                    discoveredMethod.setName(annotatedName != null ? annotatedName : stripMethodName(method, false));
                    discoveredMethod.setSource(returnType.getReturnClass());
                    discoveredMethod.setQueryAnnotated(method.getAnnotation(Query.class) != null);
                    argument.setSourceArgument(true);
                }

                if (!isID) {
                    SchemaScalar dateScalar = getScalar(returnType.getReturnClass());
                    if (dateScalar != null && isDateTimeScalar(dateScalar.getName())) {
                        // only set the original array type if it's a date/time
                        //   discoveredMethod.setOriginalArrayType(Class.forName(returnType.returnClass));
                    }
                    argument.setArrayReturnTypeMandatory(returnType.isReturnTypeMandatory);
                    argument.setArrayReturnType(returnType.isArrayType);
                    if (returnType.isArrayType) {
                        argument.setOriginalArrayType(getSafeClass(returnType.returnClass));
                    }
                    argument.setArrayLevels(returnType.getArrayLevels());
                }

                discoveredMethod.addArgument(argument);
                i++;
            }
        }
    }

    /**
     * Validate that a {@link Class} annotated with ID is a valid type.
     *
     * @param returnClazz {@link Class} to check
     */
    private static void validateIDClass(Class<?> returnClazz) {
        if (!isValidIDType(returnClazz)) {
            ensureRuntimeException(LOGGER, "A class of type " + returnClazz + " is not allowed to be an @Id");
        }
    }

    /**
     * Validate that a class annotated with ID is a valid type.
     *
     * @param returnClazz class to check
     */
    private static void validateIDClass(String returnClazz) {
        try {
            validateIDClass(Class.forName(returnClazz));
        } catch (ClassNotFoundException e) {
            // ignore
        }
    }

    /**
     * Return the {@link ReturnType} for this return class and method.
     *
     * @param returnClazz       return type
     * @param genericReturnType generic return {@link java.lang.reflect.Type} may be null
     * @param parameterNumber   the parameter number for the parameter
     * @param method            {@link Method} to find parameter for
     * @return a {@link ReturnType}
     */
    protected ReturnType getReturnType(Class<?> returnClazz, java.lang.reflect.Type genericReturnType,
                                       int parameterNumber, Method method) {
        ReturnType actualReturnType = new ReturnType();
        RootTypeResult rootTypeResult;
        String returnClazzName = returnClazz.getName();
        boolean isCollection = Collection.class.isAssignableFrom(returnClazz);
        boolean isMap = Map.class.isAssignableFrom(returnClazz);
        // deal with Collection or Map
        if (isCollection || isMap) {
            if (isCollection) {
                actualReturnType.setCollectionType(returnClazzName);
            }

            actualReturnType.setMap(isMap);
            // index is 0 for Collection and 1 for Map which assumes we are not
            // interested in the map K, just the map V which is what our implementation will do
            rootTypeResult = getRootTypeName(genericReturnType, isCollection ? 0 : 1, parameterNumber, method);
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
            actualReturnType.setReturnTypeMandatory(rootTypeResult.isArrayReturnTypeMandatory());
            actualReturnType.setFormat(rootTypeResult.format);
            actualReturnType.setArrayType(true);
        } else if (!returnClazzName.isEmpty() && returnClazzName.startsWith("[")) {
            // return type is array of either primitives or Objects/Interface/Enum.
            actualReturnType.setArrayType(true);
            actualReturnType.setArrayLevels(getArrayLevels(returnClazzName));
            actualReturnType.setReturnClass(getRootArrayClass(returnClazzName));
        } else {
            // primitive or type
            actualReturnType.setReturnClass(returnClazzName);
        }
        return actualReturnType;
    }

    /**
     * Return the inner most root type such as {@link String} for a List of List of String.
     *
     * @param genericReturnType the {@link java.lang.reflect.Type}
     * @param index             the index to use, either 0 for {@link Collection} or 1 for {@link Map}
     * @param parameterNumber   parameter number or -1 if parameter not being checked
     * @param method            {@link Method} being checked
     * @return the inner most root type
     */
    protected RootTypeResult getRootTypeName(java.lang.reflect.Type genericReturnType, int index,
                                             int parameterNumber, Method method) {
        int level = 1;
        boolean isParameter = parameterNumber != -1;
        String[] format = NO_FORMATTING;

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
            boolean hasAnnotation = false;
            if (isParameter) {
                // check for the NonNull
                Parameter parameter = method.getParameters()[parameterNumber];
                hasAnnotation = getAnnotationValue(getParameterAnnotations(parameter, 0), NonNull.class) != null;
            } else {
                format = FormattingHelper.getMethodFormat(method, 0);
            }

            isReturnTypeMandatory = hasAnnotation || isPrimitive(clazz.getName());
            return new RootTypeResult(((Class<?>) actualTypeArgument).getName(), level, isReturnTypeMandatory, format);
        } else {
            Class<?> clazz = genericReturnType.getClass();
            isReturnTypeMandatory = clazz.getAnnotation(NonNull.class) != null
                    || isPrimitive(clazz.getName());
            return new RootTypeResult(((Class<?>) genericReturnType).getName(), level, isReturnTypeMandatory, format);
        }
    }

    /**
     * Return the {@link JandexUtils} instance.
     *
     * @return the {@link JandexUtils} instance.
     */
    protected JandexUtils getJandexUtils() {
        return jandexUtils;
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
         * Indicates if the return type is a {@link Map}. Note: In the 1.0.1 microprofile spec the behaviour of {@link Map} is
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
         * The property name if the method is a getter.
         */
        private String propertyName;

        /**
         * Indicates if the method containing the {@link Source} annotation was also annotated with the {@link Query} annotation.
         * If true, then this indicates that a top level query should also be created as well as the field in the type.
         */
        private boolean isQueryAnnotated = false;

        /**
         * Defines the format for a number or date.
         */
        private String[] format = new String[0];

        /**
         * A description for a method.
         */
        private String description;

        /**
         * Indicates id the return type is mandatory.
         */
        private boolean isReturnTypeMandatory;

        /**
         * The default value for this discovered method.
         */
        private Object defaultValue;

        /**
         * If the return type is an array then indicates if the value in the array is mandatory.
         */
        private boolean isArrayReturnTypeMandatory;

        /**
         * Original array inner type if it is array type.
         */
        private Class<?> originalArrayType;

        /**
         * Indicates if the format is of type JsonB.
         */
        private boolean isJsonbFormat;

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

        /**
         * Return the format for a number or date.
         *
         * @return the format for a number or date
         */
        public String[] getFormat() {
            return format;
        }

        /**
         * Set the format for a number or date.
         *
         * @param format the format for a number or date
         */
        public void setFormat(String[] format) {
            this.format = format;
        }

        /**
         * Return the property name if the method is a getter.
         *
         * @return property name if the method is a getter
         */
        public String getPropertyName() {
            return propertyName;
        }

        /**
         * Set the property name if the method is a getter.
         *
         * @param propertyName property name
         */
        public void setPropertyName(String propertyName) {
            this.propertyName = propertyName;
        }

        /**
         * Return the description for a method.
         *
         * @return the description for a method
         */
        public String getDescription() {
            return description;
        }

        /**
         * Set the description for a method.
         *
         * @param description the description for a method
         */
        public void setDescription(String description) {
            this.description = description;
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

        /**
         * Return the default value for this method.
         *
         * @return the default value for this method
         */
        public Object getDefaultValue() {
            return defaultValue;
        }

        /**
         * Set the default value for this method.
         *
         * @param defaultValue the default value for this method
         */
        public void setDefaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
        }

        /**
         * Return if the array return type is mandatory.
         *
         * @return if the array return type is mandatory
         */
        public boolean isArrayReturnTypeMandatory() {
            return isArrayReturnTypeMandatory;
        }

        /**
         * Sets if the array return type is mandatory.
         *
         * @param arrayReturnTypeMandatory if the array return type is mandatory
         */
        public void setArrayReturnTypeMandatory(boolean arrayReturnTypeMandatory) {
            isArrayReturnTypeMandatory = arrayReturnTypeMandatory;
        }

        /**
         * Sets the original array type.
         *
         * @param originalArrayType the original array type
         */
        public void setOriginalArrayType(Class<?> originalArrayType) {
            this.originalArrayType = originalArrayType;
        }

        /**
         * Returns the original array type.
         *
         * @return the original array type
         */
        public Class<?> getOriginalArrayType() {
            return originalArrayType;
        }

        /**
         * Set if the format is of type JsonB.
         *
         * @param isJsonbFormat if the format is of type JsonB
         */
        public void setJsonbFormat(boolean isJsonbFormat) {
            this.isJsonbFormat = isJsonbFormat;
        }

        /**
         * Returns true if the format is of type JsonB.
         *
         * @return true if the format is of type JsonB
         */
        public boolean isJsonbFormat() {
            return isJsonbFormat;
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
                    + ", isReturnTypeMandatory=" + isReturnTypeMandatory
                    + ", isArrayReturnTypeMandatory=" + isArrayReturnTypeMandatory
                    + ", description=" + description
                    + ", originalArrayType=" + originalArrayType
                    + ", defaultValue=" + defaultValue
                    + ", isJsonbFormat=" + isJsonbFormat
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
                    && Objects.equals(description, that.description)
                    && Objects.equals(isReturnTypeMandatory, that.isReturnTypeMandatory)
                    && Objects.equals(isArrayReturnTypeMandatory, that.isArrayReturnTypeMandatory)
                    && Objects.equals(defaultValue, that.defaultValue)
                    && Objects.equals(isJsonbFormat, that.isJsonbFormat)
                    && Objects.equals(collectionType, that.collectionType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, returnType, methodType, method, arrayLevels, isQueryAnnotated,
                                collectionType, isArrayReturnType, isMap, source, description,
                                isReturnTypeMandatory, defaultValue, isArrayReturnTypeMandatory, isJsonbFormat);
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
         * Indicates id the return type is mandatory.
         */
        private boolean isReturnTypeMandatory;

        /**
         * The format of the return type if any.
         */
        private String[] format;

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

        /**
         * Return the format of the result class.
         *
         * @return the format of the result class
         */
        public String[] getFormat() {
            return format;
        }

        /**
         * Set the format of the result class.
         *
         * @param format the format of the result class
         */
        public void setFormat(String[] format) {
            this.format = format;
        }
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
         * Indicates if the array return type is mandatory.
         */
        private boolean isArrayReturnTypeMandatory;

        /**
         * The format of the result class.
         */
        private final String[] format;

        /**
         * Construct a root type result.
         *
         * @param rootTypeName               root type of the {@link Collection} or {@link Map}
         * @param isArrayReturnTypeMandatory indicates if the return type is mandatory
         * @param levels                     number of levels in total
         * @param format                     format of the result class
         */
        public RootTypeResult(String rootTypeName, int levels, boolean isArrayReturnTypeMandatory, String[] format) {
            this.rootTypeName = rootTypeName;
            this.levels = levels;
            this.isArrayReturnTypeMandatory = isArrayReturnTypeMandatory;
            this.format = format;
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
        public boolean isArrayReturnTypeMandatory() {
            return isArrayReturnTypeMandatory;
        }

        /**
         * Set if the return type is mandatory.
         *
         * @param arrayReturnTypeMandatory if the return type is mandatory
         */
        public void setArrayReturnTypeMandatory(boolean arrayReturnTypeMandatory) {
            isArrayReturnTypeMandatory = arrayReturnTypeMandatory;
        }

        /**
         * Return the format of the result class.
         *
         * @return the format of the result class
         */
        public String[] getFormat() {
            return format;
        }
    }
}
