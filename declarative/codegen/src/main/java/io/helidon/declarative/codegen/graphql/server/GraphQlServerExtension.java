/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.declarative.codegen.graphql.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.TypeHierarchy;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.declarative.codegen.DeclarativeTypes;
import io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.Argument;
import io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.EndpointDeclaration;
import io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.EnumSchemaType;
import io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.EnumValue;
import io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.GraphQlEndpoint;
import io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.GraphQlGroup;
import io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.GroupKey;
import io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.InputSchemaField;
import io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.InputSchemaFieldType;
import io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.InputSchemaType;
import io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.ObjectSchemaType;
import io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.Operation;
import io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.OperationKind;
import io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.ResolverParameter;
import io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.ResolverParameterKind;
import io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.Resolvers;
import io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.ScalarSchemaType;
import io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.SchemaField;
import io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.SchemaType;
import io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.SourceParameter;
import io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.ValueSchemaType;
import io.helidon.declarative.codegen.graphql.server.spi.GraphQlParameterCodegenProvider;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.ServiceCodegenTypes;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;

import static io.helidon.codegen.CodegenUtil.toConstantName;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerAnnotations.hasNonNull;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerAnnotations.requestMetadataAnnotations;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerAnnotations.validateAutomaticFieldAnnotations;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.COMMON_CONTEXT;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.COMMON_TYPE_NAME;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.DATA_FETCHING_ENVIRONMENT;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_ARGUMENT;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_DEFAULT_VALUE;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_DESCRIPTION;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_ENTITY;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_ENTRY_POINTS;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_EXECUTION_CONTEXT;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_IGNORE;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_MUTATION;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_NAME;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_NON_NULL;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_QUERY;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_SCALAR;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_SCALAR_SPI;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_SCHEMA;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_SERVER_CONTEXT;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_SERVER_ENDPOINT;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_SERVER_FIELD;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_SERVER_LISTENER;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_SERVER_SCHEMA_URI;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_SERVER_SOURCE;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_SERVICE;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.HTTP_ENTRY_POINTS;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.INVOCATION_HANDLER;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.RUNTIME_WIRING;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.SCHEMA_GENERATOR;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.SCHEMA_PARSER;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.SECURITY_CONTEXT;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.SERVER_HTTP_FEATURE;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.SERVER_HTTP_ROUTING_BUILDER;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.TYPE_DEFINITION_REGISTRY;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerInputValues.addEnumInputMethods;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerInputValues.addInputObjectMethods;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerInputValues.addListInputMethods;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerInputValues.addScalarInputValueMethod;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerInputValues.enumInputTypes;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerInputValues.listInputTypes;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerInputValues.valueExpression;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerScalarMethods.addScalarMethods;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.appendArguments;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.appendEnum;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.appendInput;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.appendObject;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.appendScalar;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerTypes.propertyName;
import static java.util.function.Predicate.not;

class GraphQlServerExtension implements RegistryCodegenExtension {
    private static final TypeName GENERATOR = TypeName.create(GraphQlServerExtension.class);
    private static final String DEFAULT_LISTENER = "@default";
    private static final String DEFAULT_CONTEXT = "/graphql";
    private static final String DEFAULT_SCHEMA_URI = "/schema.graphql";
    private static final Set<String> RESERVED_TYPE_NAMES = Set.of("Query",
                                                                  "Mutation",
                                                                  "String",
                                                                  "Int",
                                                                  "Float",
                                                                  "Boolean",
                                                                  "ID");
    private static final Set<String> RESERVED_ENUM_VALUE_NAMES = Set.of("true", "false", "null");
    private static final TypeName LIST_OF_GRAPHQL_SCALARS = TypeName.builder(TypeNames.LIST)
            .addTypeArgument(GRAPHQL_SCALAR_SPI)
            .build();
    private static final TypeName LIST_OF_ANNOTATIONS = TypeName.builder(TypeNames.LIST)
            .addTypeArgument(TypeName.create(Annotation.class))
            .build();

    private final RegistryCodegenContext ctx;
    private final List<GraphQlParameterCodegenProvider> paramProviders;

    GraphQlServerExtension(RegistryCodegenContext ctx) {
        this.ctx = ctx;
        this.paramProviders = loadParamProviders(GraphQlServerExtension.class.getClassLoader());
    }

    static List<GraphQlParameterCodegenProvider> loadParamProviders(ClassLoader classLoader) {
        return HelidonServiceLoader.builder(ServiceLoader.load(GraphQlParameterCodegenProvider.class, classLoader))
                .build()
                .asList();
    }

    @Override
    public void process(RegistryRoundContext roundContext) {
        Collection<TypeInfo> endpoints = roundContext.annotatedTypes(GRAPHQL_SERVER_ENDPOINT);
        List<TypeInfo> sortedEndpoints = endpoints.stream()
                .sorted((first, second) -> first.typeName().fqName().compareTo(second.typeName().fqName()))
                .toList();
        Map<GroupKey, List<EndpointDeclaration>> groups = new LinkedHashMap<>();

        for (TypeInfo endpoint : sortedEndpoints) {
            EndpointDeclaration declaration = toEndpointDeclaration(endpoint);
            groups.computeIfAbsent(declaration.groupKey(), it -> new ArrayList<>())
                    .add(declaration);
        }

        for (List<EndpointDeclaration> groupDeclarations : groups.values()) {
            process(roundContext, toGroup(roundContext, groupDeclarations));
        }
    }

    private EndpointDeclaration toEndpointDeclaration(TypeInfo typeInfo) {
        if (typeInfo.kind() == ElementKind.INTERFACE) {
            throw new CodegenException("Interfaces should not be annotated with " + GRAPHQL_SERVER_ENDPOINT.fqName(),
                                       typeInfo.originatingElementValue());
        }

        Set<Annotation> typeAnnotations = new HashSet<>(TypeHierarchy.hierarchyAnnotations(ctx, typeInfo));
        String listener = Annotations.findFirst(GRAPHQL_SERVER_LISTENER, typeAnnotations)
                .flatMap(Annotation::stringValue)
                .filter(not(String::isBlank))
                .orElse(DEFAULT_LISTENER);
        String context = Annotations.findFirst(GRAPHQL_SERVER_CONTEXT, typeAnnotations)
                .flatMap(Annotation::stringValue)
                .filter(not(String::isBlank))
                .map(GraphQlServerExtension::normalizePath)
                .orElse(DEFAULT_CONTEXT);
        String schemaUri = Annotations.findFirst(GRAPHQL_SERVER_SCHEMA_URI, typeAnnotations)
                .flatMap(Annotation::stringValue)
                .filter(not(String::isBlank))
                .map(GraphQlServerExtension::normalizePath)
                .orElse(DEFAULT_SCHEMA_URI);

        return new EndpointDeclaration(typeInfo,
                                        typeAnnotations,
                                        new GroupKey(listener, context),
                                        schemaUri);
    }

    private static String normalizePath(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    private GraphQlGroup toGroup(RegistryRoundContext roundContext, List<EndpointDeclaration> declarations) {
        validateGroupPackage(declarations);
        validateGroupRequestMetadata(declarations);
        String schemaUri = schemaUri(declarations);
        SchemaTypes schemaTypes = new SchemaTypes(roundContext);
        Resolvers resolvers = new Resolvers(new ArrayList<>(),
                                            new ArrayList<>(),
                                            new ArrayList<>(),
                                            new HashSet<>(),
                                            new HashSet<>());
        List<GraphQlEndpoint> endpoints = declarations.stream()
                .map(declaration -> toEndpoint(schemaTypes, resolvers, declaration))
                .toList();

        if (resolvers.queries().isEmpty()) {
            throw new CodegenException("Declarative GraphQL endpoint group for "
                                               + groupDescription(declarations.getFirst().groupKey())
                                               + " must define at least one @GraphQl.Query method.",
                                       declarations.getFirst().typeInfo().originatingElementValue());
        }
        schemaTypes.validate();

        return new GraphQlGroup(declarations.getFirst().groupKey(), schemaUri, schemaTypes, endpoints);
    }

    private static void validateGroupPackage(List<EndpointDeclaration> declarations) {
        Set<String> packages = new HashSet<>();
        for (EndpointDeclaration declaration : declarations) {
            packages.add(declaration.typeInfo().typeName().packageName());
        }
        if (packages.size() <= 1) {
            return;
        }

        throw new CodegenException("Declarative GraphQL endpoint group for "
                                           + groupDescription(declarations.getFirst().groupKey())
                                           + " contains endpoints in multiple packages. Grouped endpoints must be in the "
                                           + "same package so generated resolver code can access package-private endpoint "
                                           + "and schema types.",
                                   declarations.getFirst().typeInfo().originatingElementValue());
    }

    private static void validateGroupRequestMetadata(List<EndpointDeclaration> declarations) {
        if (declarations.size() <= 1) {
            return;
        }

        List<Annotation> expected = requestMetadataAnnotations(declarations.getFirst().annotations());
        for (EndpointDeclaration declaration : declarations) {
            List<Annotation> actual = requestMetadataAnnotations(declaration.annotations());
            if (!expected.equals(actual)) {
                throw new CodegenException("Declarative GraphQL endpoint group for "
                                                   + groupDescription(declaration.groupKey())
                                                   + " contains different endpoint-level request metadata annotations. "
                                                   + "Grouped endpoints share one HTTP route and one HTTP entry point, "
                                                   + "so non-route endpoint annotations must be declared consistently on "
                                                   + "every endpoint in the group.",
                                           declaration.typeInfo().originatingElementValue());
            }
        }
    }

    private static String schemaUri(List<EndpointDeclaration> declarations) {
        String schemaUri = declarations.getFirst().schemaUri();
        for (EndpointDeclaration declaration : declarations) {
            if (!schemaUri.equals(declaration.schemaUri())) {
                throw new CodegenException("Conflicting GraphQL schema URI in endpoint group for "
                                                   + groupDescription(declaration.groupKey()) + ": '"
                                                   + schemaUri + "' and '" + declaration.schemaUri() + "'.",
                                           declaration.typeInfo().originatingElementValue());
            }
        }
        return schemaUri;
    }

    private static String groupDescription(GroupKey key) {
        return "listener '" + key.listener() + "' and context '" + key.context() + "'";
    }

    private GraphQlEndpoint toEndpoint(SchemaTypes schemaTypes,
                                       Resolvers resolvers,
                                       EndpointDeclaration declaration) {
        TypeInfo typeInfo = declaration.typeInfo();
        List<Operation> queries = new ArrayList<>();
        List<Operation> mutations = new ArrayList<>();
        List<Operation> fieldResolvers = new ArrayList<>();
        Resolvers endpointResolvers = new Resolvers(queries,
                                                    mutations,
                                                    fieldResolvers,
                                                    resolvers.queryNames(),
                                                    resolvers.mutationNames());

        typeInfo.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(not(ElementInfoPredicates::isPrivate))
                .filter(not(ElementInfoPredicates::isStatic))
                .forEach(method -> addResolver(schemaTypes, typeInfo, method, endpointResolvers));

        resolvers.queries().addAll(queries);
        resolvers.mutations().addAll(mutations);
        resolvers.fieldResolvers().addAll(fieldResolvers);
        return new GraphQlEndpoint(typeInfo,
                                   declaration.annotations(),
                                   List.copyOf(queries),
                                   List.copyOf(mutations),
                                   List.copyOf(fieldResolvers));
    }

    private void addResolver(SchemaTypes schemaTypes,
                             TypeInfo endpoint,
                             TypedElementInfo method,
                             Resolvers resolvers) {

        Set<Annotation> annotations = new HashSet<>(TypeHierarchy.hierarchyAnnotations(ctx, endpoint, method));
        if (Annotations.findFirst(GRAPHQL_IGNORE, annotations).isPresent()) {
            return;
        }

        Optional<Annotation> field = Annotations.findFirst(GRAPHQL_SERVER_FIELD, annotations);
        Optional<Annotation> query = Annotations.findFirst(GRAPHQL_QUERY, annotations);
        Optional<Annotation> mutation = Annotations.findFirst(GRAPHQL_MUTATION, annotations);
        int resolverAnnotations = (field.isPresent() ? 1 : 0) + (query.isPresent() ? 1 : 0) + (mutation.isPresent() ? 1 : 0);
        if (resolverAnnotations > 1) {
            throw new CodegenException("GraphQL resolver method can only use one of @GraphQl.Query, @GraphQl.Mutation, "
                                               + "and @GraphQlServer.Field: "
                                               + endpoint.typeName().fqName() + "." + method.elementName(),
                                       method.originatingElementValue());
        }
        if (resolverAnnotations == 0) {
            return;
        }
        if (field.isPresent()) {
            addFieldResolver(schemaTypes, endpoint, method, annotations, field.orElseThrow(), resolvers.fieldResolvers());
            return;
        }

        OperationKind kind = query.isPresent() ? OperationKind.QUERY : OperationKind.MUTATION;
        String graphQlName = graphQlName(annotations, method.elementName(), method.originatingElementValue());
        Set<String> names = kind == OperationKind.QUERY ? resolvers.queryNames() : resolvers.mutationNames();
        if (!names.add(graphQlName)) {
            throw new CodegenException("Duplicate GraphQL " + kind.label() + " field '" + graphQlName + "' in endpoint "
                                               + endpoint.typeName().fqName(),
                                       method.originatingElementValue());
        }

        schemaTypes.outputType(method.typeName(),
                               method.typeName().primitive() || hasNonNull(annotations),
                               method.originatingElementValue());

        List<ResolverParameter> parameters = resolverParameters(schemaTypes, endpoint, method, kind, Optional.empty());
        validateArgumentNames(endpoint, method, kind, graphQlName, parameters);

        Operation operation = new Operation(kind,
                                            endpoint,
                                            method,
                                            List.copyOf(annotations),
                                            graphQlName,
                                            resolverMethodName(endpoint, method),
                                            ctx.uniqueName(endpoint, method),
                                            parameters);
        if (kind == OperationKind.QUERY) {
            resolvers.queries().add(operation);
        } else {
            resolvers.mutations().add(operation);
        }
    }

    private void addFieldResolver(SchemaTypes schemaTypes,
                                  TypeInfo endpoint,
                                  TypedElementInfo method,
                                  Set<Annotation> annotations,
                                  Annotation fieldAnnotation,
                                  List<Operation> fieldResolvers) {

        SourceParameter source = sourceParameter(schemaTypes.roundContext, endpoint, method);
        List<ResolverParameter> parameters = resolverParameters(schemaTypes,
                                                                endpoint,
                                                                method,
                                                                OperationKind.FIELD,
                                                                Optional.of(source));
        String graphQlName = fieldName(endpoint, method, annotations, fieldAnnotation);
        String schemaType = schemaTypes.outputType(method.typeName(),
                                                   method.typeName().primitive() || hasNonNull(annotations),
                                                   method.originatingElementValue());
        validateArgumentNames(endpoint, method, OperationKind.FIELD, graphQlName, parameters);

        Operation operation = new Operation(OperationKind.FIELD,
                                            endpoint,
                                            method,
                                            List.copyOf(annotations),
                                            graphQlName,
                                            resolverMethodName(endpoint, method),
                                            ctx.uniqueName(endpoint, method),
                                            parameters);
        schemaTypes.addFieldResolver(source.typeInfo(), operation, schemaType);
        fieldResolvers.add(operation);
    }

    private List<ResolverParameter> resolverParameters(SchemaTypes schemaTypes,
                                                       TypeInfo endpoint,
                                                       TypedElementInfo method,
                                                       OperationKind kind,
                                                       Optional<SourceParameter> source) {
        List<ResolverParameter> result = new ArrayList<>();
        List<TypedElementInfo> parameters = method.parameterArguments();
        for (int i = 0; i < parameters.size(); i++) {
            TypedElementInfo parameter = parameters.get(i);
            Set<Annotation> parameterAnnotations = new HashSet<>(TypeHierarchy.hierarchyAnnotations(ctx,
                                                                                                     endpoint,
                                                                                                     method,
                                                                                                     parameter,
                                                                                                     i));
            GraphQlParameterContext parameterContext = parameterContext(endpoint,
                                                                        method,
                                                                        parameter,
                                                                        i,
                                                                        parameterAnnotations,
                                                                        kind);
            if (source.isPresent() && source.orElseThrow().index() == i) {
                result.add(new ResolverParameter(parameter,
                                                Optional.empty(),
                                                Optional.of(source.orElseThrow().typeInfo()),
                                                Optional.empty(),
                                                parameterContext,
                                                ResolverParameterKind.SOURCE));
                continue;
            }

            Optional<ResolverParameterKind> specialParameter = specialParameterKind(parameter.typeName());
            if (specialParameter.isPresent()) {
                result.add(new ResolverParameter(parameter,
                                                Optional.empty(),
                                                Optional.empty(),
                                                Optional.empty(),
                                                parameterContext,
                                                specialParameter.orElseThrow()));
                continue;
            }

            Optional<GraphQlParameterCodegenProvider> parameterProvider = parameterProvider(parameterContext);
            if (Annotations.findFirst(GRAPHQL_ARGUMENT, parameterAnnotations).isEmpty()
                    && parameterProvider.isPresent()) {
                result.add(new ResolverParameter(parameter,
                                                Optional.empty(),
                                                Optional.empty(),
                                                parameterProvider,
                                                parameterContext,
                                                ResolverParameterKind.CUSTOM));
                continue;
            }

            result.add(new ResolverParameter(parameter,
                                            Optional.of(toArgument(schemaTypes,
                                                                  endpoint,
                                                                  method,
                                                                  parameter,
                                                                  parameterAnnotations)),
                                            Optional.empty(),
                                            Optional.empty(),
                                            parameterContext,
                                            ResolverParameterKind.ARGUMENT));
        }
        return List.copyOf(result);
    }

    private static void validateArgumentNames(TypeInfo endpoint,
                                              TypedElementInfo method,
                                              OperationKind kind,
                                              String fieldName,
                                              List<ResolverParameter> parameters) {
        Map<String, Argument> arguments = new LinkedHashMap<>();
        for (ResolverParameter parameter : parameters) {
            if (parameter.argument().isEmpty()) {
                continue;
            }

            Argument argument = parameter.argument().orElseThrow();
            Argument previous = arguments.putIfAbsent(argument.graphQlName(), argument);
            if (previous != null) {
                throw new CodegenException("Duplicate GraphQL argument '" + argument.graphQlName() + "' for "
                                                   + kind.label() + " field '" + fieldName + "' in resolver "
                                                   + endpoint.typeName().fqName() + "." + method.elementName(),
                                           argument.parameter().originatingElementValue());
            }
        }
    }

    private GraphQlParameterContext parameterContext(TypeInfo endpoint,
                                                     TypedElementInfo method,
                                                     TypedElementInfo parameter,
                                                     int paramIndex,
                                                     Set<Annotation> annotations,
                                                     OperationKind kind) {
        return new GraphQlParameterContextImpl(Set.copyOf(annotations),
                                               parameter.typeName(),
                                               endpoint.typeName(),
                                               method.elementName(),
                                               ctx.uniqueName(endpoint, method),
                                               parameter.elementName(),
                                               paramIndex,
                                               resolverKind(kind));
    }

    private static GraphQlResolverKind resolverKind(OperationKind kind) {
        return switch (kind) {
        case QUERY -> GraphQlResolverKind.QUERY;
        case MUTATION -> GraphQlResolverKind.MUTATION;
        case FIELD -> GraphQlResolverKind.FIELD;
        };
    }

    private Optional<GraphQlParameterCodegenProvider> parameterProvider(GraphQlParameterContext parameterContext) {
        for (GraphQlParameterCodegenProvider provider : paramProviders) {
            try {
                if (provider.supports(parameterContext)) {
                    return Optional.of(provider);
                }
            } catch (Exception e) {
                throw new CodegenException("Failed to process GraphQL resolver parameter '"
                                                   + parameterContext.parameterType().resolvedName()
                                                   + " " + parameterContext.paramName()
                                                   + "' that is " + (parameterContext.paramIndex() + 1)
                                                   + " parameter of method "
                                                   + parameterContext.endpointType().fqName()
                                                   + "." + parameterContext.methodName()
                                                   + ", as the parameter handler ("
                                                   + provider.getClass().getName()
                                                   + ") threw an exception: "
                                                   + e.getMessage(),
                                           e);
            }
        }
        return Optional.empty();
    }

    private SourceParameter sourceParameter(RegistryRoundContext roundContext, TypeInfo endpoint, TypedElementInfo method) {
        List<SourceParameter> annotated = new ArrayList<>();
        List<SourceParameter> inferred = new ArrayList<>();
        List<TypedElementInfo> parameters = method.parameterArguments();

        for (int i = 0; i < parameters.size(); i++) {
            TypedElementInfo parameter = parameters.get(i);
            Set<Annotation> annotations = new HashSet<>(TypeHierarchy.hierarchyAnnotations(ctx,
                                                                                           endpoint,
                                                                                           method,
                                                                                           parameter,
                                                                                           i));
            if (Annotations.findFirst(GRAPHQL_SERVER_SOURCE, annotations).isPresent()
                    && Annotations.findFirst(GRAPHQL_ARGUMENT, annotations).isPresent()) {
                throw new CodegenException("@GraphQlServer.Source and @GraphQl.Argument cannot be combined on "
                                                   + endpoint.typeName().fqName() + "." + method.elementName(),
                                           parameter.originatingElementValue());
            }

            if (Annotations.findFirst(GRAPHQL_ARGUMENT, annotations).isPresent()) {
                continue;
            }
            if (Annotations.findFirst(GRAPHQL_SERVER_SOURCE, annotations).isEmpty()
                    && parameterProvider(parameterContext(endpoint,
                                                          method,
                                                          parameter,
                                                          i,
                                                          annotations,
                                                          OperationKind.FIELD)).isPresent()) {
                continue;
            }

            Optional<SourceParameter> source = sourceParameter(roundContext, endpoint, method, parameter, i);
            if (Annotations.findFirst(GRAPHQL_SERVER_SOURCE, annotations).isPresent()) {
                annotated.add(source.orElseThrow(() -> unsupportedSourceType(endpoint, method, parameter)));
            } else if (source.isPresent()
                    && Annotations.findFirst(GRAPHQL_ARGUMENT, annotations).isEmpty()
                    && specialParameterKind(parameter.typeName()).isEmpty()) {
                inferred.add(source.orElseThrow());
            }
        }

        if (annotated.size() > 1) {
            throw new CodegenException("GraphQL child field resolver " + endpoint.typeName().fqName() + "."
                                               + method.elementName()
                                               + " must not declare more than one @GraphQlServer.Source parameter.",
                                       method.originatingElementValue());
        }
        if (annotated.size() == 1) {
            return annotated.getFirst();
        }
        if (inferred.size() == 1) {
            return inferred.getFirst();
        }
        throw new CodegenException("GraphQL child field resolver " + endpoint.typeName().fqName() + "."
                                           + method.elementName()
                                           + " must declare exactly one source parameter. Annotate the source parameter "
                                           + "with @GraphQlServer.Source when it cannot be inferred.",
                                   method.originatingElementValue());
    }

    private Optional<SourceParameter> sourceParameter(RegistryRoundContext roundContext,
                                                      TypeInfo endpoint,
                                                      TypedElementInfo method,
                                                      TypedElementInfo parameter,
                                                      int index) {
        if (isScalarType(roundContext, parameter.typeName())) {
            return Optional.empty();
        }
        if (specialParameterKind(parameter.typeName()).isPresent()) {
            return Optional.empty();
        }

        TypeInfo typeInfo = roundContext.typeInfo(parameter.typeName().boxed())
                .or(() -> roundContext.typeInfo(parameter.typeName()))
                .orElseThrow(() -> unsupportedSourceType(endpoint, method, parameter));
        if (typeInfo.kind() == ElementKind.RECORD
                || typeInfo.kind() == ElementKind.CLASS
                || typeInfo.kind() == ElementKind.INTERFACE) {
            return Optional.of(new SourceParameter(index, parameter, typeInfo));
        }
        throw unsupportedSourceType(endpoint, method, parameter);
    }

    private static Optional<ResolverParameterKind> specialParameterKind(TypeName type) {
        TypeName boxed = type.boxed();
        if (boxed.equals(DATA_FETCHING_ENVIRONMENT)) {
            return Optional.of(ResolverParameterKind.ENVIRONMENT);
        }
        if (boxed.equals(COMMON_CONTEXT)) {
            return Optional.of(ResolverParameterKind.HELIDON_CONTEXT);
        }
        if (boxed.equals(GRAPHQL_EXECUTION_CONTEXT)) {
            return Optional.of(ResolverParameterKind.EXECUTION_CONTEXT);
        }
        if (boxed.equals(SECURITY_CONTEXT)) {
            return Optional.of(ResolverParameterKind.SECURITY_CONTEXT);
        }
        return Optional.empty();
    }

    private static String fieldName(TypeInfo endpoint,
                                    TypedElementInfo method,
                                    Set<Annotation> annotations,
                                    Annotation fieldAnnotation) {
        Optional<String> fieldName = fieldAnnotation.stringValue()
                .filter(not(String::isBlank));
        Optional<String> graphQlName = Annotations.findFirst(GRAPHQL_NAME, annotations)
                .flatMap(Annotation::stringValue)
                .filter(not(String::isBlank));
        if (fieldName.isPresent() && graphQlName.isPresent() && !fieldName.orElseThrow().equals(graphQlName.orElseThrow())) {
            throw new CodegenException("@GraphQlServer.Field value and @GraphQl.Name cannot declare different GraphQL "
                                               + "field names on " + endpoint.typeName().fqName() + "."
                                               + method.elementName(),
                                       method.originatingElementValue());
        }
        return validateGraphQlName(fieldName.or(() -> graphQlName).orElse(method.elementName()),
                                   method.originatingElementValue());
    }

    private String resolverMethodName(TypeInfo endpoint, TypedElementInfo method) {
        return endpoint.typeName().classNameWithEnclosingNames().replace('.', '_')
                + "_" + ctx.uniqueName(endpoint, method);
    }

    private Argument toArgument(SchemaTypes schemaTypes,
                                TypeInfo endpoint,
                                TypedElementInfo method,
                                TypedElementInfo parameter,
                                Set<Annotation> annotations) {
        ValueSchemaType valueType = schemaTypes.inputValueType(parameter.typeName(),
                                                               endpoint,
                                                               method,
                                                               parameter.originatingElementValue());
        String graphQlName = Annotations.findFirst(GRAPHQL_ARGUMENT, annotations)
                .flatMap(Annotation::stringValue)
                .filter(not(String::isBlank))
                .map(it -> validateGraphQlName(it, parameter.originatingElementValue()))
                .orElseGet(() -> graphQlName(annotations, parameter.elementName(), parameter.originatingElementValue()));
        Optional<String> defaultValue = Annotations.findFirst(GRAPHQL_DEFAULT_VALUE, annotations)
                .flatMap(Annotation::stringValue)
                .filter(not(String::isBlank));
        boolean nonNull = parameter.typeName().primitive()
                || Annotations.findFirst(GRAPHQL_NON_NULL, annotations).isPresent();
        String schemaType = nonNull ? valueType.graphQlName() + "!" : valueType.graphQlName();
        return new Argument(parameter,
                            graphQlName,
                            List.copyOf(annotations),
                            defaultValue,
                            nonNull,
                            schemaType,
                            valueType);
    }

    private void process(RegistryRoundContext roundContext, GraphQlGroup group) {
        TypeInfo type = group.primaryEndpoint().typeInfo();
        TypeName endpointTypeName = type.typeName();
        String className = featureClassName(group);
        TypeName generatedType = TypeName.builder()
                .packageName(endpointTypeName.packageName())
                .className(className)
                .build();

        ClassModel.Builder classModel = ClassModel.builder()
                .copyright(CodegenUtil.copyright(GENERATOR, endpointTypeName, generatedType))
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR, endpointTypeName, generatedType, "1", ""))
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .type(generatedType)
                .addAnnotation(DeclarativeTypes.SINGLETON_ANNOTATION)
                .addAnnotation(DeclarativeTypes.SUPPRESS_API)
                .addInterface(SERVER_HTTP_FEATURE);

        Map<TypeName, String> endpointFields = endpointFields(group);
        for (GraphQlEndpoint endpoint : group.endpoints()) {
            classModel.addField(field -> field
                    .accessModifier(AccessModifier.PRIVATE)
                    .isFinal(true)
                    .type(endpoint.typeInfo().typeName())
                    .name(endpointFields.get(endpoint.typeInfo().typeName())));
        }
        classModel.addField(field -> field
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .type(GRAPHQL_ENTRY_POINTS)
                .name("entryPoints"));
        classModel.addField(field -> field
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .type(HTTP_ENTRY_POINTS)
                .name("httpEntryPoints"));
        classModel.addField(field -> field
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .type(LIST_OF_GRAPHQL_SCALARS)
                .name("scalars"));

        Constructor.Builder constructor = Constructor.builder()
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_INJECT));
        for (GraphQlEndpoint endpoint : group.endpoints()) {
            String fieldName = endpointFields.get(endpoint.typeInfo().typeName());
            constructor.addParameter(param -> param
                            .type(endpoint.typeInfo().typeName())
                            .name(fieldName))
                    .addContent("this.")
                    .addContent(fieldName)
                    .addContent(" = ")
                    .addContent(fieldName)
                    .addContentLine(";");
        }
        constructor.addParameter(param -> param
                        .type(GRAPHQL_ENTRY_POINTS)
                        .name("entryPoints"))
                .addParameter(param -> param
                        .type(HTTP_ENTRY_POINTS)
                        .name("httpEntryPoints"))
                .addParameter(param -> param
                        .type(LIST_OF_GRAPHQL_SCALARS)
                        .name("scalars"))
                .addContentLine("this.entryPoints = entryPoints;")
                .addContentLine("this.httpEntryPoints = httpEntryPoints;")
                .addContentLine("this.scalars = scalars;");
        classModel.addConstructor(constructor);

        addSetupMethod(classModel, group);
        addSocketMethods(classModel, group);
        addSchemaMethod(classModel, group);
        addInvocationHandlerMethod(classModel);
        addRuntimeWiringMethod(classModel, group);
        addRequestAnnotationsMethod(classModel);
        addScalarMethods(classModel);
        addContextParameterMethods(classModel, group.operations());
        addScalarInputValueMethod(classModel);
        addEnumInputMethods(classModel, enumInputTypes(group.operations(), group.schemaTypes().inputTypes()));
        addListInputMethods(classModel, listInputTypes(group.operations(), group.schemaTypes().inputTypes()));
        addInputObjectMethods(classModel, group.schemaTypes().inputTypes());
        addResolverMethods(classModel, group, endpointFields);

        roundContext.addGeneratedType(generatedType, classModel, endpointTypeName, type.originatingElementValue());
    }

    private static String featureClassName(GraphQlGroup group) {
        if (group.endpoints().size() == 1) {
            TypeName endpointTypeName = group.primaryEndpoint().typeInfo().typeName();
            return endpointTypeName.classNameWithEnclosingNames().replace('.', '_') + "__GraphQlFeature";
        }
        return "GraphQl_" + identifierPart(group.key().listener()) + "_" + identifierPart(group.key().context())
                + "__GraphQlFeature";
    }

    private static String identifierPart(String value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isJavaIdentifierPart(ch)) {
                result.append(ch);
            } else {
                result.append('_')
                        .append(Integer.toHexString(ch))
                        .append('_');
            }
        }
        return result.isEmpty() ? "root" : result.toString();
    }

    private Map<TypeName, String> endpointFields(GraphQlGroup group) {
        Map<TypeName, String> result = new LinkedHashMap<>();
        int index = 0;
        for (GraphQlEndpoint endpoint : group.endpoints()) {
            result.put(endpoint.typeInfo().typeName(), "endpoint_" + index);
            index++;
        }
        return result;
    }

    private void addSetupMethod(ClassModel.Builder classModel, GraphQlGroup group) {
        TypeName endpointDescriptorType = ctx.descriptorType(group.primaryEndpoint().typeInfo().typeName());
        classModel.addMethod(setup -> setup
                .accessModifier(AccessModifier.PUBLIC)
                .addAnnotation(Annotations.OVERRIDE)
                .name("setup")
                .addParameter(routing -> routing
                        .name("routing")
                        .type(SERVER_HTTP_ROUTING_BUILDER))
                .addContent("var descriptor = ")
                .addContent(endpointDescriptorType)
                .addContentLine(".INSTANCE;")
                .addContent("var annotations = requestAnnotations(")
                .addContent(endpointDescriptorType)
                .addContentLine(".ANNOTATIONS);")
                .addContent("routing.register(")
                .addContent(GRAPHQL_SERVICE)
                .addContentLine(".builder()")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContent(".webContext(")
                .addContentLiteral(group.key().context())
                .addContentLine(")")
                .addContent(".schemaUri(")
                .addContentLiteral(group.schemaUri())
                .addContentLine(")")
                .addContentLine(".httpEntryPoints(httpEntryPoints, descriptor, descriptor.qualifiers(), annotations)")
                .addContentLine(".invocationHandler(invocationHandler())")
                .addContentLine(".build());")
                .decreaseContentPadding()
                .decreaseContentPadding());
    }

    private void addRequestAnnotationsMethod(ClassModel.Builder classModel) {
        classModel.addMethod(method -> method
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .returnType(LIST_OF_ANNOTATIONS)
                .name("requestAnnotations")
                .addParameter(annotations -> annotations
                        .type(LIST_OF_ANNOTATIONS)
                        .name("annotations"))
                .addContent("var routeAnnotations = ")
                .addContent(TypeNames.SET)
                .addContentLine(".of(")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContent(COMMON_TYPE_NAME)
                .addContent(".create(")
                .addContentLiteral(GRAPHQL_SERVER_ENDPOINT.fqName())
                .addContentLine("),")
                .addContent(COMMON_TYPE_NAME)
                .addContent(".create(")
                .addContentLiteral(GRAPHQL_SERVER_LISTENER.fqName())
                .addContentLine("),")
                .addContent(COMMON_TYPE_NAME)
                .addContent(".create(")
                .addContentLiteral(GRAPHQL_SERVER_CONTEXT.fqName())
                .addContentLine("),")
                .addContent(COMMON_TYPE_NAME)
                .addContent(".create(")
                .addContentLiteral(GRAPHQL_SERVER_SCHEMA_URI.fqName())
                .addContentLine("));")
                .decreaseContentPadding()
                .decreaseContentPadding()
                .addContentLine("return annotations.stream()")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContentLine(".filter(annotation -> !routeAnnotations.contains(annotation.typeName()))")
                .addContentLine(".toList();")
                .decreaseContentPadding()
                .decreaseContentPadding());
    }

    private void addSocketMethods(ClassModel.Builder classModel, GraphQlGroup group) {
        if (DEFAULT_LISTENER.equals(group.key().listener())) {
            return;
        }

        classModel.addMethod(socket -> socket
                .accessModifier(AccessModifier.PUBLIC)
                .returnType(TypeNames.STRING)
                .name("socket")
                .addAnnotation(Annotations.OVERRIDE)
                .addContent("return ")
                .addContentLiteral(group.key().listener())
                .addContentLine(";"));
        classModel.addMethod(socket -> socket
                .accessModifier(AccessModifier.PUBLIC)
                .returnType(TypeNames.PRIMITIVE_BOOLEAN)
                .name("socketRequired")
                .addAnnotation(Annotations.OVERRIDE)
                .addContentLine("return true;"));
    }

    private void addSchemaMethod(ClassModel.Builder classModel, GraphQlGroup group) {
        classModel.addMethod(method -> method
                .accessModifier(AccessModifier.PRIVATE)
                .returnType(GRAPHQL_SCHEMA)
                .name("schema")
                .addContent("String schema = ")
                .addContentLiteral(schema(group))
                .addContentLine(";")
                .addContent(TYPE_DEFINITION_REGISTRY)
                .addContent(" typeDefinitionRegistry = new ")
                .addContent(SCHEMA_PARSER)
                .addContentLine("().parse(schema);")
                .addContent(RUNTIME_WIRING)
                .addContentLine(" runtimeWiring = runtimeWiring();")
                .addContent("return new ")
                .addContent(SCHEMA_GENERATOR)
                .addContentLine("().makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);"));
    }

    private void addInvocationHandlerMethod(ClassModel.Builder classModel) {
        classModel.addMethod(method -> method
                .accessModifier(AccessModifier.PRIVATE)
                .returnType(INVOCATION_HANDLER)
                .name("invocationHandler")
                .addContent("return ")
                .addContent(INVOCATION_HANDLER)
                .addContentLine(".create(schema());"));
    }

    private void addRuntimeWiringMethod(ClassModel.Builder classModel, GraphQlGroup group) {
        classModel.addMethod(method -> {
            method.accessModifier(AccessModifier.PRIVATE)
                    .returnType(RUNTIME_WIRING)
                    .name("runtimeWiring")
                    .addContent("var builder = ")
                    .addContent(RUNTIME_WIRING)
                    .addContentLine(".newRuntimeWiring();");

            addScalarWiring(method, group.schemaTypes().scalarTypes());
            addEnumWiring(method, group.schemaTypes().enumTypes());
            addDataFetcherVariables(method, group);
            addWiring(method, "Query", group.queries());
            addWiring(method, "Mutation", group.mutations());
            addObjectWiring(method, group.schemaTypes().objectTypes());

            method.addContentLine("return builder.build();");
        });
    }

    private void addScalarWiring(io.helidon.codegen.classmodel.Method.Builder method, List<ScalarSchemaType> scalarTypes) {
        for (ScalarSchemaType scalarType : scalarTypes) {
            method.addContent("builder.scalar(graphQlScalar(")
                    .addContentLiteral(scalarType.graphQlName())
                    .addContent(", ")
                    .addContent(scalarType.javaType())
                    .addContentLine(".class));");
        }
    }

    private void addEnumWiring(io.helidon.codegen.classmodel.Method.Builder method, List<EnumSchemaType> enumTypes) {
        for (EnumSchemaType enumType : enumTypes) {
            method.addContent("builder.type(")
                    .addContentLiteral(enumType.graphQlName())
                    .addContentLine(", type -> type")
                    .increaseContentPadding()
                    .increaseContentPadding()
                    .addContentLine(".enumValues(enumName -> switch (enumName) {")
                    .increaseContentPadding();
            for (EnumValue value : enumType.values()) {
                method.addContent("case ")
                        .addContentLiteral(value.graphQlName())
                        .addContent(" -> ")
                        .addContent(enumType.javaType())
                        .addContent(".")
                        .addContent(value.javaName())
                        .addContentLine(";");
            }
            method.addContent("default -> throw new IllegalArgumentException(\"Unsupported GraphQL enum value \" + enumName")
                    .addContent(" + \" for ")
                    .addContent(enumType.graphQlName())
                    .addContentLine("\");")
                    .decreaseContentPadding()
                    .addContentLine("}));")
                    .decreaseContentPadding()
                    .decreaseContentPadding();
        }
    }

    private void addDataFetcherVariables(io.helidon.codegen.classmodel.Method.Builder method, GraphQlGroup group) {
        List<Operation> operations = group.operations();
        if (operations.isEmpty()) {
            return;
        }

        Map<TypeName, String> descriptorVariables = new LinkedHashMap<>();
        Map<TypeName, String> annotationsVariables = new LinkedHashMap<>();
        int endpointIndex = 0;
        for (GraphQlEndpoint endpoint : group.endpoints()) {
            TypeName endpointType = endpoint.typeInfo().typeName();
            TypeName descriptorType = ctx.descriptorType(endpointType);
            String descriptorName = "descriptor_" + endpointIndex;
            String annotationsName = "annotations_" + endpointIndex;
            descriptorVariables.put(endpointType, descriptorName);
            annotationsVariables.put(endpointType, annotationsName);
            method.addContent("var ")
                    .addContent(descriptorName)
                    .addContent(" = ")
                    .addContent(descriptorType)
                    .addContentLine(".INSTANCE;");
            method.addContent("var ")
                    .addContent(annotationsName)
                    .addContent(" = ")
                    .addContent(descriptorType)
                    .addContentLine(".ANNOTATIONS;");
            endpointIndex++;
        }

        for (Operation operation : operations) {
            TypeName endpointType = operation.endpoint().typeName();
            TypeName descriptorType = ctx.descriptorType(endpointType);
            method.addContent("var fetcher_")
                    .addContent(operation.uniqueName())
                    .addContentLine(" = entryPoints.dataFetcher(")
                    .increaseContentPadding()
                    .increaseContentPadding()
                    .addContent(descriptorVariables.get(endpointType))
                    .addContentLine(",")
                    .addContent(descriptorVariables.get(endpointType))
                    .addContentLine(".qualifiers(),")
                    .addContent(annotationsVariables.get(endpointType))
                    .addContentLine(",")
                    .addContent(descriptorType)
                    .addContent(".")
                    .addContent(toConstantName("METHOD_" + operation.descriptorMethodName()))
                    .addContentLine(",")
                    .addContent("this::")
                    .addContent(operation.uniqueName())
                    .addContentLine(");")
                    .decreaseContentPadding()
                    .decreaseContentPadding();
        }
    }

    private void addWiring(io.helidon.codegen.classmodel.Method.Builder method,
                           String graphQlType,
                           List<Operation> operations) {
        if (operations.isEmpty()) {
            return;
        }

        method.addContent("builder.type(")
                .addContentLiteral(graphQlType)
                .addContentLine(", type -> type")
                .increaseContentPadding()
                .increaseContentPadding();
        for (Operation operation : operations) {
            method.addContent(".dataFetcher(")
                    .addContentLiteral(operation.graphQlName())
                    .addContent(", fetcher_")
                    .addContent(operation.uniqueName())
                    .addContentLine(")");
        }
        method.addContentLine(");")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }

    private void addObjectWiring(io.helidon.codegen.classmodel.Method.Builder method, List<ObjectSchemaType> objectTypes) {
        for (ObjectSchemaType objectType : objectTypes) {
            method.addContent("builder.type(")
                    .addContentLiteral(objectType.graphQlName())
                    .addContentLine(", type -> type")
                    .increaseContentPadding()
                    .increaseContentPadding();
            for (SchemaField field : objectType.fields()) {
                method.addContent(".dataFetcher(")
                        .addContentLiteral(field.graphQlName());
                if (field.resolver().isPresent()) {
                    method.addContent(", fetcher_")
                            .addContent(field.resolver().orElseThrow())
                            .addContentLine(")");
                } else {
                    method.addContent(", environment -> ((")
                            .addContent(objectType.javaType())
                            .addContent(") environment.getSource()).")
                            .addContent(field.accessor().orElseThrow())
                            .addContentLine(")");
                }
            }
            method.addContentLine(");")
                    .decreaseContentPadding()
                    .decreaseContentPadding();
        }
    }

    private void addResolverMethods(ClassModel.Builder classModel,
                                    GraphQlGroup group,
                                    Map<TypeName, String> endpointFields) {
        group.operations()
                .forEach(operation -> classModel.addMethod(method -> method
                        .accessModifier(AccessModifier.PRIVATE)
                        .returnType(TypeNames.OBJECT)
                        .name(operation.uniqueName())
                        .addParameter(env -> env
                                .type(DATA_FETCHING_ENVIRONMENT)
                                .name("environment"))
                        .addThrows(TypeName.create(Exception.class))
                        .update(it -> resolverBody(classModel, it, operation, endpointFields))));
    }

    private void resolverBody(ClassModel.Builder classModel,
                              io.helidon.codegen.classmodel.Method.Builder method,
                              Operation operation,
                              Map<TypeName, String> endpointFields) {
        method.addContent("return this.")
                .addContent(endpointFields.get(operation.endpoint().typeName()))
                .addContent(".")
                .addContent(operation.method().elementName())
                .addContent("(");
        List<ResolverParameter> parameters = operation.parameters();
        if (parameters.isEmpty()) {
            method.addContentLine(");");
            return;
        }
        if (parameters.size() == 1) {
            resolverParameterValue(classModel, method, parameters.getFirst());
            method.addContentLine(");");
            return;
        }

        method.addContentLine()
                .increaseContentPadding()
                .increaseContentPadding();
        for (int i = 0; i < parameters.size(); i++) {
            resolverParameterValue(classModel, method, parameters.get(i));
            if (i + 1 == parameters.size()) {
                method.addContentLine(");");
            } else {
                method.addContentLine(",");
            }
        }
        method.decreaseContentPadding()
                .decreaseContentPadding();
    }

    private void resolverParameterValue(ClassModel.Builder classModel,
                                        io.helidon.codegen.classmodel.Method.Builder method,
                                        ResolverParameter parameter) {
        switch (parameter.kind()) {
        case SOURCE:
            method.addContent("((")
                    .addContent(parameter.sourceType().orElseThrow().typeName())
                    .addContent(") environment.getSource())");
            return;
        case ENVIRONMENT:
            method.addContent("environment");
            return;
        case HELIDON_CONTEXT:
            method.addContent("helidonContext(environment)");
            return;
        case EXECUTION_CONTEXT:
            method.addContent("graphQlExecutionContext(environment)");
            return;
        case SECURITY_CONTEXT:
            method.addContent("securityContext(environment)");
            return;
        case ARGUMENT:
            argumentValue(method, parameter.argument().orElseThrow());
            return;
        case CUSTOM:
            try {
                parameter.provider()
                        .orElseThrow()
                        .codegen(new GraphQlParameterCodegenContextImpl(parameter.context(),
                                                                        classModel,
                                                                        method,
                                                                        "environment"));
                return;
            } catch (Exception e) {
                throw new CodegenException("Failed to code generate GraphQL resolver parameter '"
                                                   + parameter.context().parameterType().resolvedName()
                                                   + " " + parameter.context().paramName()
                                                   + "' that is " + (parameter.context().paramIndex() + 1)
                                                   + " parameter of method "
                                                   + parameter.context().endpointType().fqName()
                                                   + "." + parameter.context().methodName()
                                                   + ", as the parameter handler ("
                                                   + parameter.provider().orElseThrow().getClass().getName()
                                                   + ") threw an exception: "
                                                   + e.getMessage(),
                                           e);
            }
        default:
            throw new IllegalStateException("Unsupported GraphQL resolver parameter kind: " + parameter.kind());
        }
    }

    private void argumentValue(io.helidon.codegen.classmodel.Method.Builder method, Argument argument) {
        valueExpression(method,
                        argument.valueType(),
                        "environment.getArgument(\"" + argument.graphQlName() + "\")");
    }

    private void addContextParameterMethods(ClassModel.Builder classModel, List<Operation> operations) {
        boolean usesHelidonContext = usesParameter(operations, ResolverParameterKind.HELIDON_CONTEXT)
                || usesParameter(operations, ResolverParameterKind.SECURITY_CONTEXT);
        boolean usesExecutionContext = usesParameter(operations, ResolverParameterKind.EXECUTION_CONTEXT);
        boolean usesSecurityContext = usesParameter(operations, ResolverParameterKind.SECURITY_CONTEXT);

        if (usesHelidonContext) {
            classModel.addMethod(method -> method
                    .accessModifier(AccessModifier.PRIVATE)
                    .isStatic(true)
                    .returnType(COMMON_CONTEXT)
                    .name("helidonContext")
                    .addParameter(param -> param
                            .type(DATA_FETCHING_ENVIRONMENT)
                            .name("environment"))
                    .addContent(COMMON_CONTEXT)
                    .addContent(" context = environment.getGraphQlContext().get(")
                    .addContent(GRAPHQL_EXECUTION_CONTEXT)
                    .addContentLine(".HELIDON_CONTEXT_KEY);")
                    .addContentLine("if (context == null) {")
                    .increaseContentPadding()
                    .addContentLine("throw new IllegalStateException(\"Missing Helidon context for GraphQL resolver.\");")
                    .decreaseContentPadding()
                    .addContentLine("}")
                    .addContentLine("return context;"));
        }

        if (usesExecutionContext) {
            classModel.addMethod(method -> method
                    .accessModifier(AccessModifier.PRIVATE)
                    .isStatic(true)
                    .returnType(GRAPHQL_EXECUTION_CONTEXT)
                    .name("graphQlExecutionContext")
                    .addParameter(param -> param
                            .type(DATA_FETCHING_ENVIRONMENT)
                            .name("environment"))
                    .addContent(GRAPHQL_EXECUTION_CONTEXT)
                    .addContent(" context = environment.getGraphQlContext().get(")
                    .addContent(GRAPHQL_EXECUTION_CONTEXT)
                    .addContentLine(".EXECUTION_CONTEXT_KEY);")
                    .addContentLine("if (context == null) {")
                    .increaseContentPadding()
                    .addContentLine("throw new IllegalStateException(\"Missing GraphQL execution context for resolver.\");")
                    .decreaseContentPadding()
                    .addContentLine("}")
                    .addContentLine("return context;"));
        }

        if (usesSecurityContext) {
            classModel.addMethod(method -> method
                    .accessModifier(AccessModifier.PRIVATE)
                    .isStatic(true)
                    .returnType(SECURITY_CONTEXT)
                    .name("securityContext")
                    .addParameter(param -> param
                            .type(DATA_FETCHING_ENVIRONMENT)
                            .name("environment"))
                    .addContentLine("return helidonContext(environment)")
                    .increaseContentPadding()
                    .addContent(".get(")
                    .addContent(SECURITY_CONTEXT)
                    .addContentLine(".class)")
                    .addContentLine(".orElseThrow(() -> new IllegalStateException(")
                    .increaseContentPadding()
                    .addContentLine("\"Missing security context for GraphQL resolver.\"));")
                    .decreaseContentPadding()
                    .decreaseContentPadding());
        }
    }

    private static boolean usesParameter(List<Operation> operations, ResolverParameterKind kind) {
        return operations.stream()
                .flatMap(operation -> operation.parameters().stream())
                .anyMatch(parameter -> parameter.kind() == kind);
    }

    private String schema(GraphQlGroup group) {
        StringBuilder result = new StringBuilder();
        result.append("schema {\n")
                .append("  query: Query\n");
        if (!group.mutations().isEmpty()) {
            result.append("  mutation: Mutation\n");
        }
        result.append("}\n\n");

        appendType(result, "Query", group.queries(), group.schemaTypes());
        if (!group.mutations().isEmpty()) {
            result.append('\n');
            appendType(result, "Mutation", group.mutations(), group.schemaTypes());
        }
        group.schemaTypes().appendTo(result);
        return result.toString();
    }

    private void appendType(StringBuilder result, String typeName, List<Operation> operations, SchemaTypes schemaTypes) {
        result.append("type ")
                .append(typeName)
                .append(" {\n");
        for (Operation operation : operations) {
            result.append("  ")
                    .append(operation.graphQlName());
            appendArguments(result, operation.arguments());
            result.append(": ")
                    .append(schemaTypes.outputType(operation.method().typeName(),
                                                   operation.method().typeName().primitive()
                                                           || hasNonNull(operation.annotations()),
                                                   operation.method().originatingElementValue()))
                    .append('\n');
        }
        result.append("}\n");
    }

    private static String graphQlName(Set<Annotation> annotations, String defaultName, Object originatingElement) {
        String name = Annotations.findFirst(GRAPHQL_NAME, annotations)
                .flatMap(Annotation::stringValue)
                .filter(not(String::isBlank))
                .orElse(defaultName);
        return validateGraphQlName(name, originatingElement);
    }

    private static String validateGraphQlName(String name, Object originatingElement) {
        if (!isGraphQlName(name)) {
            throw new CodegenException("GraphQL name '" + name + "' must match [_A-Za-z][_0-9A-Za-z]*.",
                                       originatingElement);
        }
        if (name.startsWith("__")) {
            throw new CodegenException("GraphQL name '" + name
                                               + "' must not start with '__'. GraphQL reserves names beginning with '__'.",
                                       originatingElement);
        }
        return name;
    }

    private static void validateEnumValueName(String name, Object originatingElement) {
        if (RESERVED_ENUM_VALUE_NAMES.contains(name)) {
            throw new CodegenException("GraphQL enum value '" + name
                                               + "' must not be true, false, or null.",
                                       originatingElement);
        }
    }

    private static boolean isGraphQlName(String name) {
        if (name.isEmpty() || !isGraphQlNameStart(name.charAt(0))) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            if (!isGraphQlNamePart(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isGraphQlNameStart(char ch) {
        return ch == '_' || (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z');
    }

    private static boolean isGraphQlNamePart(char ch) {
        return isGraphQlNameStart(ch) || (ch >= '0' && ch <= '9');
    }

    private static Optional<String> builtinScalarGraphQlType(TypeName type) {
        TypeName boxed = type.boxed();
        if (boxed.equals(TypeNames.STRING)) {
            return Optional.of("String");
        }
        if (boxed.equals(TypeNames.BOXED_INT)) {
            return Optional.of("Int");
        }
        if (boxed.equals(TypeNames.BOXED_DOUBLE)) {
            return Optional.of("Float");
        }
        if (boxed.equals(TypeNames.BOXED_BOOLEAN)) {
            return Optional.of("Boolean");
        }
        return Optional.empty();
    }

    private static boolean isScalarType(RegistryRoundContext roundContext, TypeName type) {
        return builtinScalarGraphQlType(type).isPresent()
                || roundContext.typeInfo(type.boxed())
                        .or(() -> roundContext.typeInfo(type))
                        .filter(it -> it.findAnnotation(GRAPHQL_SCALAR).isPresent())
                        .isPresent();
    }

    final class SchemaTypes {
        private final RegistryRoundContext roundContext;
        private final Map<TypeName, SchemaType> types = new LinkedHashMap<>();
        private final Map<TypeName, InputSchemaType> inputTypes = new LinkedHashMap<>();
        private final Map<String, TypeName> graphQlNames = new LinkedHashMap<>();
        private final Set<TypeName> visiting = new HashSet<>();
        private final Set<TypeName> visitingInputs = new HashSet<>();

        private SchemaTypes(RegistryRoundContext roundContext) {
            this.roundContext = roundContext;
        }

        private String outputType(TypeName type, boolean nonNull, Object originatingElement) {
            String graphQlType = outputType(type, originatingElement);
            return nonNull ? graphQlType + "!" : graphQlType;
        }

        private List<ObjectSchemaType> objectTypes() {
            return types.values()
                    .stream()
                    .filter(ObjectSchemaType.class::isInstance)
                    .map(ObjectSchemaType.class::cast)
                    .toList();
        }

        private List<ScalarSchemaType> scalarTypes() {
            return types.values()
                    .stream()
                    .filter(ScalarSchemaType.class::isInstance)
                    .map(ScalarSchemaType.class::cast)
                    .toList();
        }

        private List<EnumSchemaType> enumTypes() {
            return types.values()
                    .stream()
                    .filter(EnumSchemaType.class::isInstance)
                    .map(EnumSchemaType.class::cast)
                    .toList();
        }

        private List<InputSchemaType> inputTypes() {
            return List.copyOf(inputTypes.values());
        }

        private void validate() {
            for (SchemaType schemaType : types.values()) {
                if (schemaType instanceof ObjectSchemaType objectType && objectType.fields().isEmpty()) {
                    throw new CodegenException("GraphQL object type " + objectType.javaType().fqName()
                                                       + " does not declare any readable fields.",
                                               objectType.originatingElement());
                }
            }
            for (InputSchemaType inputType : inputTypes.values()) {
                if (inputType.fields().isEmpty()) {
                    throw new CodegenException("GraphQL input type " + inputType.javaType().fqName()
                                                       + " does not declare any input fields.",
                                               inputType.originatingElement());
                }
            }
        }

        private void appendTo(StringBuilder result) {
            for (SchemaType type : types.values()) {
                result.append('\n');
                if (type instanceof ObjectSchemaType objectType) {
                    appendObject(result, objectType);
                } else if (type instanceof EnumSchemaType enumType) {
                    appendEnum(result, enumType);
                } else if (type instanceof ScalarSchemaType scalarType) {
                    appendScalar(result, scalarType);
                }
            }
            for (InputSchemaType inputType : inputTypes.values()) {
                result.append('\n');
                appendInput(result, inputType);
            }
        }

        private String outputType(TypeName type, Object originatingElement) {
            if (type.isList()) {
                TypeName elementType = listElementType(type, originatingElement);
                return "[" + outputType(elementType,
                                         elementType.primitive() || hasNonNull(elementType),
                                         originatingElement) + "]";
            }

            Optional<String> scalar = scalarType(type, originatingElement);
            if (scalar.isPresent()) {
                return scalar.get();
            }

            TypeInfo typeInfo = roundContext.typeInfo(type.boxed())
                    .or(() -> roundContext.typeInfo(type))
                    .orElseThrow(() -> unsupportedOutputType(type, originatingElement));

            if (typeInfo.kind() == ElementKind.ENUM) {
                return enumType(typeInfo).graphQlName();
            }
            if (typeInfo.kind() == ElementKind.RECORD
                    || typeInfo.kind() == ElementKind.CLASS
                    || typeInfo.kind() == ElementKind.INTERFACE) {
                return objectType(typeInfo);
            }
            throw unsupportedOutputType(type, originatingElement);
        }

        private ValueSchemaType inputValueType(TypeName type,
                                               TypeInfo endpoint,
                                               TypedElementInfo method,
                                               Object originatingElement) {
            if (type.isList()) {
                TypeName elementType = listElementType(type, originatingElement);
                ValueSchemaType elementSchemaType = inputValueType(elementType, endpoint, method, originatingElement);
                String elementGraphQlName = elementType.primitive() || hasNonNull(elementType)
                        ? elementSchemaType.graphQlName() + "!"
                        : elementSchemaType.graphQlName();
                return new ValueSchemaType(type,
                                           "[" + elementGraphQlName + "]",
                                           Optional.empty(),
                                           Optional.empty(),
                                           Optional.of(elementSchemaType));
            }

            Optional<String> scalar = scalarType(type, originatingElement);
            if (scalar.isPresent()) {
                return new ValueSchemaType(type.boxed(),
                                           scalar.orElseThrow(),
                                           Optional.empty(),
                                           Optional.empty(),
                                           Optional.empty());
            }

            TypeInfo typeInfo = roundContext.typeInfo(type.boxed())
                    .or(() -> roundContext.typeInfo(type))
                    .orElseThrow(() -> unsupportedArgumentType(endpoint, method, type, originatingElement));
            if (typeInfo.kind() == ElementKind.ENUM) {
                EnumSchemaType enumType = enumType(typeInfo);
                return new ValueSchemaType(typeInfo.typeName(),
                                           enumType.graphQlName(),
                                           Optional.of(enumType),
                                           Optional.empty(),
                                           Optional.empty());
            }
            if (typeInfo.kind() == ElementKind.RECORD) {
                InputSchemaType inputType = inputType(typeInfo);
                return new ValueSchemaType(typeInfo.typeName(),
                                           inputType.graphQlName(),
                                           Optional.empty(),
                                           Optional.of(inputType),
                                           Optional.empty());
            }
            throw unsupportedInputType(typeInfo, originatingElement);
        }

        private InputSchemaType inputType(TypeInfo typeInfo) {
            TypeName typeName = typeInfo.typeName();
            InputSchemaType existing = inputTypes.get(typeName);
            if (existing != null) {
                return existing;
            }

            requireEntity(typeInfo);
            Set<Annotation> annotations = new HashSet<>(TypeHierarchy.hierarchyAnnotations(ctx, typeInfo));
            String graphQlName = inputTypeName(typeInfo, annotations);
            reserveTypeName(typeInfo, graphQlName);
            InputSchemaType placeholder = new InputSchemaType(typeName,
                                                              graphQlName,
                                                              description(annotations),
                                                              List.of(),
                                                              typeInfo.originatingElementValue());
            inputTypes.put(typeName, placeholder);

            if (!visitingInputs.add(typeName)) {
                return placeholder;
            }
            try {
                InputSchemaType result = new InputSchemaType(typeName,
                                                             graphQlName,
                                                             description(annotations),
                                                             inputFields(typeInfo),
                                                             typeInfo.originatingElementValue());
                inputTypes.put(typeName, result);
                return result;
            } finally {
                visitingInputs.remove(typeName);
            }
        }

        private Optional<String> scalarType(TypeName type, Object originatingElement) {
            Optional<String> builtin = builtinScalarGraphQlType(type);
            if (builtin.isPresent()) {
                return builtin;
            }

            Optional<TypeInfo> typeInfo = roundContext.typeInfo(type.boxed())
                    .or(() -> roundContext.typeInfo(type));
            if (typeInfo.isPresent() && typeInfo.orElseThrow().findAnnotation(GRAPHQL_SCALAR).isPresent()) {
                return Optional.of(scalarType(typeInfo.orElseThrow()));
            }
            return Optional.empty();
        }

        private String scalarType(TypeInfo typeInfo) {
            TypeName typeName = typeInfo.typeName();
            SchemaType existing = types.get(typeName);
            if (existing != null) {
                return existing.graphQlName();
            }

            Set<Annotation> annotations = new HashSet<>(TypeHierarchy.hierarchyAnnotations(ctx, typeInfo));
            String graphQlName = scalarName(typeInfo, annotations);
            reserveTypeName(typeInfo, graphQlName);
            types.put(typeName,
                      new ScalarSchemaType(typeName,
                                           graphQlName,
                                           description(annotations),
                                           typeInfo.originatingElementValue()));
            return graphQlName;
        }

        private EnumSchemaType enumType(TypeInfo typeInfo) {
            TypeName typeName = typeInfo.typeName();
            SchemaType existing = types.get(typeName);
            if (existing != null) {
                return (EnumSchemaType) existing;
            }

            requireEntity(typeInfo);
            Set<Annotation> annotations = new HashSet<>(TypeHierarchy.hierarchyAnnotations(ctx, typeInfo));
            String graphQlName = typeName(typeInfo, annotations);
            reserveTypeName(typeInfo, graphQlName);
            List<EnumValue> values = enumValues(typeInfo);
            if (values.isEmpty()) {
                throw new CodegenException("GraphQL enum type " + typeName.fqName()
                                                   + " does not declare any enum constants.",
                                           typeInfo.originatingElementValue());
            }
            EnumSchemaType enumType = new EnumSchemaType(typeName, graphQlName, description(annotations), values);
            types.put(typeName, enumType);
            return enumType;
        }

        private String objectType(TypeInfo typeInfo) {
            TypeName typeName = typeInfo.typeName();
            SchemaType existing = types.get(typeName);
            if (existing != null) {
                return existing.graphQlName();
            }

            requireEntity(typeInfo);
            Set<Annotation> annotations = new HashSet<>(TypeHierarchy.hierarchyAnnotations(ctx, typeInfo));
            String graphQlName = typeName(typeInfo, annotations);
            reserveTypeName(typeInfo, graphQlName);
            types.put(typeName,
                      new ObjectSchemaType(typeName,
                                           graphQlName,
                                           description(annotations),
                                           List.of(),
                                           typeInfo.originatingElementValue()));

            if (!visiting.add(typeName)) {
                return graphQlName;
            }
            try {
                List<SchemaField> fields = objectFields(typeInfo);
                types.put(typeName,
                          new ObjectSchemaType(typeName,
                                               graphQlName,
                                               description(annotations),
                                               fields,
                                               typeInfo.originatingElementValue()));
            } finally {
                visiting.remove(typeName);
            }
            return graphQlName;
        }

        private void addFieldResolver(TypeInfo sourceType, Operation operation, String schemaType) {
            TypeName typeName = sourceType.typeName();
            objectType(sourceType);
            ObjectSchemaType objectType = (ObjectSchemaType) types.get(typeName);
            Map<String, SchemaField> fields = new LinkedHashMap<>();
            for (SchemaField field : objectType.fields()) {
                fields.put(field.graphQlName(), field);
            }
            putField(sourceType,
                     fields,
                     new SchemaField(operation.graphQlName(),
                                     schemaType,
                                     description(new HashSet<>(operation.annotations())),
                                     operation.arguments(),
                                     Optional.empty(),
                                     Optional.of(operation.uniqueName())),
                     operation.method().originatingElementValue());
            types.put(typeName, new ObjectSchemaType(typeName,
                                                     objectType.graphQlName(),
                                                     objectType.description(),
                                                     List.copyOf(fields.values()),
                                                     objectType.originatingElement()));
        }

        private List<EnumValue> enumValues(TypeInfo typeInfo) {
            Map<String, EnumValue> values = new LinkedHashMap<>();
            typeInfo.elementInfo()
                    .stream()
                    .filter(it -> it.kind() == ElementKind.ENUM_CONSTANT)
                    .forEach(it -> {
                        EnumValue value = enumValue(it);
                        EnumValue previous = values.putIfAbsent(value.graphQlName(), value);
                        if (previous != null) {
                            throw new CodegenException("Duplicate GraphQL enum value '" + value.graphQlName()
                                                               + "' in enum " + typeInfo.typeName().fqName(),
                                                       it.originatingElementValue());
                        }
                    });
            return List.copyOf(values.values());
        }

        private EnumValue enumValue(TypedElementInfo enumConstant) {
            Set<Annotation> annotations = new HashSet<>(enumConstant.annotations());
            String graphQlName = graphQlName(annotations,
                                             enumConstant.elementName(),
                                             enumConstant.originatingElementValue());
            validateEnumValueName(graphQlName, enumConstant.originatingElementValue());
            return new EnumValue(enumConstant.elementName(), graphQlName, description(annotations));
        }

        private List<SchemaField> objectFields(TypeInfo typeInfo) {
            Map<String, SchemaField> fields = new LinkedHashMap<>();

            if (typeInfo.kind() == ElementKind.RECORD) {
                typeInfo.elementInfo()
                        .stream()
                        .filter(it -> it.kind() == ElementKind.RECORD_COMPONENT)
                        .forEach(it -> addField(typeInfo, fields, it, it.elementName(), it.elementName() + "()"));
                return List.copyOf(fields.values());
            }

            typeInfo.elementInfo()
                    .stream()
                    .filter(it -> it.kind() == ElementKind.FIELD)
                    .filter(it -> it.accessModifier() == AccessModifier.PUBLIC)
                    .filter(not(ElementInfoPredicates::isStatic))
                    .forEach(it -> addField(typeInfo, fields, it, it.elementName(), it.elementName()));

            typeInfo.elementInfo()
                    .stream()
                    .filter(ElementInfoPredicates::isMethod)
                    .filter(it -> it.accessModifier() == AccessModifier.PUBLIC)
                    .filter(not(ElementInfoPredicates::isStatic))
                    .filter(it -> it.parameterArguments().isEmpty())
                    .forEach(it -> propertyName(it).ifPresent(name -> addField(typeInfo,
                                                                                fields,
                                                                                it,
                                                                                name,
                                                                                it.elementName() + "()")));

            return List.copyOf(fields.values());
        }

        private List<InputSchemaField> inputFields(TypeInfo typeInfo) {
            Map<String, InputSchemaField> fields = new LinkedHashMap<>();
            typeInfo.elementInfo()
                    .stream()
                    .filter(it -> it.kind() == ElementKind.RECORD_COMPONENT)
                    .forEach(it -> addInputField(typeInfo, fields, it, it.elementName()));
            return List.copyOf(fields.values());
        }

        private void addField(TypeInfo typeInfo,
                              Map<String, SchemaField> fields,
                              TypedElementInfo element,
                              String defaultName,
                              String accessor) {
            Set<Annotation> annotations = new HashSet<>(TypeHierarchy.hierarchyAnnotations(ctx, typeInfo, element));
            if (Annotations.findFirst(GRAPHQL_IGNORE, annotations).isPresent()) {
                return;
            }
            validateAutomaticFieldAnnotations(typeInfo, element, annotations);
            String graphQlName = graphQlName(annotations, defaultName, element.originatingElementValue());
            String schemaType = outputType(element.typeName(),
                                           element.typeName().primitive() || hasNonNull(annotations),
                                           element.originatingElementValue());
            SchemaField previous = fields.putIfAbsent(graphQlName,
                                                      new SchemaField(graphQlName,
                                                                      schemaType,
                                                                      description(annotations),
                                                                      List.of(),
                                                                      Optional.of(accessor),
                                                                      Optional.empty()));
            if (previous != null) {
                throw new CodegenException("Duplicate GraphQL field '" + graphQlName + "' in type "
                                                   + typeInfo.typeName().fqName(),
                                           element.originatingElementValue());
            }
        }

        private void addInputField(TypeInfo typeInfo,
                                   Map<String, InputSchemaField> fields,
                                   TypedElementInfo element,
                                   String defaultName) {
            Set<Annotation> annotations = new HashSet<>(element.annotations());
            if (Annotations.findFirst(GRAPHQL_IGNORE, annotations).isPresent()) {
                throw new CodegenException("@GraphQl.Ignore cannot be used on GraphQL input record component "
                                                   + typeInfo.typeName().fqName() + "." + element.elementName()
                                                   + ". Input records must provide every constructor component.",
                                           element.originatingElementValue());
            }
            if (Annotations.findFirst(GRAPHQL_DEFAULT_VALUE, annotations).isPresent()) {
                throw new CodegenException("@GraphQl.DefaultValue cannot be used on GraphQL input record component "
                                                   + typeInfo.typeName().fqName() + "." + element.elementName()
                                                   + ". Default values are supported on resolver arguments only.",
                                           element.originatingElementValue());
            }
            String graphQlName = graphQlName(annotations, defaultName, element.originatingElementValue());
            InputSchemaFieldType fieldType = inputFieldType(typeInfo, element);
            String schemaType = element.typeName().primitive() || hasNonNull(annotations)
                    ? fieldType.graphQlName() + "!"
                    : fieldType.graphQlName();
            InputSchemaField previous = fields.putIfAbsent(graphQlName,
                                                           new InputSchemaField(element,
                                                                                graphQlName,
                                                                                schemaType,
                                                                                description(annotations),
                                                                                fieldType.valueType()));
            if (previous != null) {
                throw new CodegenException("Duplicate GraphQL input field '" + graphQlName + "' in type "
                                                   + typeInfo.typeName().fqName(),
                                           element.originatingElementValue());
            }
        }

        private InputSchemaFieldType inputFieldType(TypeInfo inputType, TypedElementInfo element) {
            try {
                ValueSchemaType valueType = inputValueType(element.typeName(),
                                                           null,
                                                           null,
                                                           element.originatingElementValue());
                return new InputSchemaFieldType(valueType.graphQlName(), valueType);
            } catch (CodegenException e) {
                if (listShapeError(e)) {
                    throw e;
                }
                throw unsupportedInputFieldType(inputType, element);
            }
        }

        private void putField(TypeInfo typeInfo,
                              Map<String, SchemaField> fields,
                              SchemaField field,
                              Object originatingElement) {
            SchemaField previous = fields.putIfAbsent(field.graphQlName(), field);
            if (previous != null) {
                throw new CodegenException("Duplicate GraphQL field '" + field.graphQlName() + "' in type "
                                                   + typeInfo.typeName().fqName(),
                                           originatingElement);
            }
        }

        private void reserveTypeName(TypeInfo typeInfo, String graphQlName) {
            if (RESERVED_TYPE_NAMES.contains(graphQlName)) {
                throw new CodegenException("GraphQL type " + typeInfo.typeName().fqName()
                                                   + " cannot use reserved type name '" + graphQlName + "'.",
                                           typeInfo.originatingElementValue());
            }
            TypeName previous = graphQlNames.putIfAbsent(graphQlName, typeInfo.typeName());
            if (previous != null) {
                throw new CodegenException("Duplicate GraphQL type name '" + graphQlName + "' for "
                                                   + previous.fqName() + " and " + typeInfo.typeName().fqName()
                                                   + ". GraphQL output and input types share one schema namespace.",
                                           typeInfo.originatingElementValue());
            }
        }
    }

    private static String typeName(TypeInfo typeInfo, Set<Annotation> annotations) {
        return graphQlName(annotations, typeInfo.typeName().className(), typeInfo.originatingElementValue());
    }

    private static String inputTypeName(TypeInfo typeInfo, Set<Annotation> annotations) {
        String baseName = typeName(typeInfo, annotations);
        return baseName.endsWith("Input") ? baseName : baseName + "Input";
    }

    private static TypeName listElementType(TypeName type, Object originatingElement) {
        if (type.typeArguments().size() != 1) {
            throw new CodegenException("GraphQL list type " + type.resolvedName()
                                               + " must declare exactly one type argument.",
                                       originatingElement);
        }
        TypeName elementType = type.typeArguments().getFirst();
        if (elementType.wildcard() || elementType.generic()) {
            throw new CodegenException("GraphQL list type " + type.resolvedName()
                                               + " must use a concrete element type.",
                                       originatingElement);
        }
        return elementType;
    }

    private static boolean listShapeError(CodegenException e) {
        return e.getMessage().startsWith("GraphQL list type ");
    }

    private static String scalarName(TypeInfo typeInfo, Set<Annotation> annotations) {
        Optional<String> scalarName = Annotations.findFirst(GRAPHQL_SCALAR, annotations)
                .flatMap(Annotation::stringValue)
                .filter(not(String::isBlank));
        Optional<String> graphQlName = Annotations.findFirst(GRAPHQL_NAME, annotations)
                .flatMap(Annotation::stringValue)
                .filter(not(String::isBlank));
        if (scalarName.isPresent() && graphQlName.isPresent() && !scalarName.orElseThrow().equals(graphQlName.orElseThrow())) {
            throw new CodegenException("@GraphQl.Scalar value and @GraphQl.Name cannot declare different GraphQL type names "
                                               + "on " + typeInfo.typeName().fqName(),
                                       typeInfo.originatingElementValue());
        }
        return validateGraphQlName(scalarName.or(() -> graphQlName).orElse(typeInfo.typeName().className()),
                                   typeInfo.originatingElementValue());
    }

    private static Optional<String> description(Set<Annotation> annotations) {
        return Annotations.findFirst(GRAPHQL_DESCRIPTION, annotations)
                .flatMap(Annotation::stringValue)
                .filter(not(String::isBlank));
    }

    private static void requireEntity(TypeInfo typeInfo) {
        if (typeInfo.findAnnotation(GRAPHQL_ENTITY).isEmpty()) {
            throw new CodegenException("GraphQL schema type " + typeInfo.typeName().fqName()
                                               + " must be annotated with @GraphQl.Entity before SDL is generated for it.",
                                       typeInfo.originatingElementValue());
        }
    }

    private CodegenException unsupportedOutputType(TypeName type, Object originatingElement) {
        return new CodegenException("Return or field type " + type.fqName()
                                            + " is not supported by the declarative GraphQL generator. Supported output types "
                                            + "are String, int/Integer, double/Double, boolean/Boolean, List<T>, Java types "
                                            + "annotated with @GraphQl.Scalar, and Java enums/classes/records/interfaces "
                                            + "annotated with @GraphQl.Entity.",
                                    originatingElement);
    }

    private static CodegenException unsupportedArgumentType(TypeInfo endpoint,
                                                           TypedElementInfo method,
                                                           TypeName type,
                                                           Object originatingElement) {
        StringBuilder message = new StringBuilder("Argument type ")
                .append(type.fqName())
                .append(" is not supported by the initial declarative GraphQL generator");
        if (endpoint != null && method != null) {
            message.append(" for ")
                    .append(endpoint.typeName().fqName())
                    .append(".")
                    .append(method.elementName());
        }
        message.append(". Supported argument types are String, int/Integer, double/Double, boolean/Boolean, List<T>, Java "
                               + "enums annotated with @GraphQl.Entity, Java types annotated with @GraphQl.Scalar, "
                               + "plus Java records annotated with "
                               + "@GraphQl.Entity for GraphQL input objects.");
        return new CodegenException(message.toString(), originatingElement);
    }

    private static CodegenException unsupportedInputType(TypeInfo typeInfo, Object originatingElement) {
        return new CodegenException("GraphQL input type " + typeInfo.typeName().fqName()
                                            + " is not supported by the declarative GraphQL generator. Input objects "
                                            + "must be Java records annotated with @GraphQl.Entity.",
                                    originatingElement);
    }

    private static CodegenException unsupportedInputFieldType(TypeInfo inputType, TypedElementInfo field) {
        return new CodegenException("GraphQL input field type " + field.typeName().fqName()
                                            + " is not supported by the declarative GraphQL generator for "
                                            + inputType.typeName().fqName() + "." + field.elementName()
                                            + ". Supported input field types are String, int/Integer, double/Double, "
                                            + "boolean/Boolean, List<T>, Java "
                                            + "enums annotated with @GraphQl.Entity, Java types annotated with "
                                            + "@GraphQl.Scalar, and nested Java records annotated "
                                            + "with @GraphQl.Entity.",
                                    field.originatingElementValue());
    }

    private static CodegenException unsupportedSourceType(TypeInfo endpoint,
                                                         TypedElementInfo method,
                                                         TypedElementInfo parameter) {
        return new CodegenException("Source parameter type " + parameter.typeName().fqName()
                                            + " is not supported for GraphQL child field resolver "
                                            + endpoint.typeName().fqName() + "." + method.elementName()
                                            + ". Source parameters must be Java classes, records, or interfaces "
                                            + "annotated with @GraphQl.Entity.",
                                    parameter.originatingElementValue());
    }

}
