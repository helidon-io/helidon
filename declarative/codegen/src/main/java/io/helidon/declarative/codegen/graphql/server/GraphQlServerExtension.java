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
import java.util.Set;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.TypeHierarchy;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.declarative.codegen.DeclarativeTypes;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.ServiceCodegenTypes;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;

import static io.helidon.codegen.CodegenUtil.toConstantName;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.DATA_FETCHING_ENVIRONMENT;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_ARGUMENT;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_DEFAULT_VALUE;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_DESCRIPTION;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_ENTITY;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_ENTRY_POINTS;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_IGNORE;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_MUTATION;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_NAME;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_NON_NULL;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_QUERY;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_SCHEMA;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_SERVER_CONTEXT;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_SERVER_ENDPOINT;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_SERVER_FIELD;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_SERVER_LISTENER;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_SERVER_SCHEMA_URI;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.GRAPHQL_SERVICE;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.INVOCATION_HANDLER;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.RUNTIME_WIRING;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.SCHEMA_GENERATOR;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.SCHEMA_PARSER;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.SERVER_HTTP_FEATURE;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.SERVER_HTTP_ROUTING_BUILDER;
import static io.helidon.declarative.codegen.graphql.server.GraphQlServerCodegenTypes.TYPE_DEFINITION_REGISTRY;
import static java.util.function.Predicate.not;

class GraphQlServerExtension implements RegistryCodegenExtension {
    private static final TypeName GENERATOR = TypeName.create(GraphQlServerExtension.class);
    private static final String DEFAULT_CONTEXT = "/graphql";
    private static final String DEFAULT_SCHEMA_URI = "/schema.graphql";

    private final RegistryCodegenContext ctx;

    GraphQlServerExtension(RegistryCodegenContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void process(RegistryRoundContext roundContext) {
        Collection<TypeInfo> endpoints = roundContext.annotatedTypes(GRAPHQL_SERVER_ENDPOINT);

        for (TypeInfo endpoint : endpoints) {
            process(roundContext, toEndpoint(roundContext, endpoint));
        }
    }

    private GraphQlEndpoint toEndpoint(RegistryRoundContext roundContext, TypeInfo typeInfo) {
        if (typeInfo.kind() == ElementKind.INTERFACE) {
            throw new CodegenException("Interfaces should not be annotated with " + GRAPHQL_SERVER_ENDPOINT.fqName(),
                                       typeInfo.originatingElementValue());
        }

        SchemaTypes schemaTypes = new SchemaTypes(roundContext);
        Set<Annotation> typeAnnotations = new HashSet<>(TypeHierarchy.hierarchyAnnotations(ctx, typeInfo));
        String listener = Annotations.findFirst(GRAPHQL_SERVER_LISTENER, typeAnnotations)
                .flatMap(Annotation::stringValue)
                .orElse(null);
        String context = Annotations.findFirst(GRAPHQL_SERVER_CONTEXT, typeAnnotations)
                .flatMap(Annotation::stringValue)
                .filter(not(String::isBlank))
                .orElse(DEFAULT_CONTEXT);
        String schemaUri = Annotations.findFirst(GRAPHQL_SERVER_SCHEMA_URI, typeAnnotations)
                .flatMap(Annotation::stringValue)
                .filter(not(String::isBlank))
                .orElse(DEFAULT_SCHEMA_URI);

        List<Operation> queries = new ArrayList<>();
        List<Operation> mutations = new ArrayList<>();
        Set<String> queryNames = new HashSet<>();
        Set<String> mutationNames = new HashSet<>();

        typeInfo.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(not(ElementInfoPredicates::isPrivate))
                .filter(not(ElementInfoPredicates::isStatic))
                .forEach(method -> addOperation(schemaTypes, typeInfo, method, queries, mutations, queryNames, mutationNames));

        if (queries.isEmpty()) {
            throw new CodegenException("Declarative GraphQL endpoint " + typeInfo.typeName().fqName()
                                               + " must define at least one @GraphQl.Query method.",
                                       typeInfo.originatingElementValue());
        }

        return new GraphQlEndpoint(typeInfo,
                                   typeAnnotations,
                                   Optional.ofNullable(listener),
                                   context,
                                   schemaUri,
                                   schemaTypes,
                                   List.copyOf(queries),
                                   List.copyOf(mutations));
    }

    private void addOperation(SchemaTypes schemaTypes,
                              TypeInfo endpoint,
                              TypedElementInfo method,
                              List<Operation> queries,
                              List<Operation> mutations,
                              Set<String> queryNames,
                              Set<String> mutationNames) {

        Set<Annotation> annotations = new HashSet<>(TypeHierarchy.hierarchyAnnotations(ctx, endpoint, method));
        if (Annotations.findFirst(GRAPHQL_IGNORE, annotations).isPresent()) {
            return;
        }
        if (Annotations.findFirst(GRAPHQL_SERVER_FIELD, annotations).isPresent()) {
            throw new CodegenException("Declarative GraphQL child field resolvers are not supported by this generator slice yet: "
                                               + endpoint.typeName().fqName() + "." + method.elementName(),
                                       method.originatingElementValue());
        }

        Optional<Annotation> query = Annotations.findFirst(GRAPHQL_QUERY, annotations);
        Optional<Annotation> mutation = Annotations.findFirst(GRAPHQL_MUTATION, annotations);
        if (query.isPresent() && mutation.isPresent()) {
            throw new CodegenException("GraphQL resolver method cannot be both @GraphQl.Query and @GraphQl.Mutation: "
                                               + endpoint.typeName().fqName() + "." + method.elementName(),
                                       method.originatingElementValue());
        }
        if (query.isEmpty() && mutation.isEmpty()) {
            return;
        }

        OperationKind kind = query.isPresent() ? OperationKind.QUERY : OperationKind.MUTATION;
        String graphQlName = graphQlName(annotations, method.elementName());
        Set<String> names = kind == OperationKind.QUERY ? queryNames : mutationNames;
        if (!names.add(graphQlName)) {
            throw new CodegenException("Duplicate GraphQL " + kind.label() + " field '" + graphQlName + "' in endpoint "
                                               + endpoint.typeName().fqName(),
                                       method.originatingElementValue());
        }

        schemaTypes.outputType(method.typeName(),
                               method.typeName().primitive() || hasNonNull(annotations),
                               method.originatingElementValue());

        var arguments = new ArrayList<Argument>();
        int index = 0;
        for (TypedElementInfo parameter : method.parameterArguments()) {
            Set<Annotation> parameterAnnotations = new HashSet<>(TypeHierarchy.hierarchyAnnotations(ctx,
                                                                                                     endpoint,
                                                                                                     method,
                                                                                                     parameter,
                                                                                                     index));
            arguments.add(toArgument(endpoint, method, parameter, parameterAnnotations));
            index++;
        }

        Operation operation = new Operation(kind,
                                            method,
                                            List.copyOf(annotations),
                                            graphQlName,
                                            ctx.uniqueName(endpoint, method),
                                            List.copyOf(arguments));
        if (kind == OperationKind.QUERY) {
            queries.add(operation);
        } else {
            mutations.add(operation);
        }
    }

    private Argument toArgument(TypeInfo endpoint,
                                TypedElementInfo method,
                                TypedElementInfo parameter,
                                Set<Annotation> annotations) {
        if (scalarGraphQlType(parameter.typeName()).isEmpty()) {
            throw unsupportedArgumentType(endpoint, method, parameter.typeName(), parameter.originatingElementValue());
        }
        String graphQlName = Annotations.findFirst(GRAPHQL_ARGUMENT, annotations)
                .flatMap(Annotation::stringValue)
                .filter(not(String::isBlank))
                .orElseGet(() -> graphQlName(annotations, parameter.elementName()));
        Optional<String> defaultValue = Annotations.findFirst(GRAPHQL_DEFAULT_VALUE, annotations)
                .flatMap(Annotation::stringValue)
                .filter(not(String::isBlank));
        boolean nonNull = parameter.typeName().primitive()
                || Annotations.findFirst(GRAPHQL_NON_NULL, annotations).isPresent();
        return new Argument(parameter, graphQlName, List.copyOf(annotations), defaultValue, nonNull);
    }

    private void process(RegistryRoundContext roundContext, GraphQlEndpoint endpoint) {
        TypeInfo type = endpoint.typeInfo();
        TypeName endpointTypeName = type.typeName();
        String classNameBase = endpointTypeName.classNameWithEnclosingNames().replace('.', '_');
        String className = classNameBase + "__GraphQlFeature";
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

        classModel.addField(field -> field
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .type(endpointTypeName)
                .name("endpoint"));
        classModel.addField(field -> field
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .type(GRAPHQL_ENTRY_POINTS)
                .name("entryPoints"));

        Constructor.Builder constructor = Constructor.builder()
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_INJECT))
                .addParameter(param -> param
                        .type(endpointTypeName)
                        .name("endpoint"))
                .addParameter(param -> param
                        .type(GRAPHQL_ENTRY_POINTS)
                        .name("entryPoints"))
                .addContentLine("this.endpoint = endpoint;")
                .addContentLine("this.entryPoints = entryPoints;");
        classModel.addConstructor(constructor);

        addSetupMethod(classModel, endpoint);
        addSocketMethods(classModel, endpoint);
        addSchemaMethod(classModel, endpoint);
        addInvocationHandlerMethod(classModel);
        addRuntimeWiringMethod(classModel, endpoint, ctx.descriptorType(endpointTypeName));
        addResolverMethods(classModel, endpoint);

        roundContext.addGeneratedType(generatedType, classModel, endpointTypeName, type.originatingElementValue());
    }

    private void addSetupMethod(ClassModel.Builder classModel, GraphQlEndpoint endpoint) {
        classModel.addMethod(setup -> setup
                .accessModifier(AccessModifier.PUBLIC)
                .addAnnotation(Annotations.OVERRIDE)
                .name("setup")
                .addParameter(routing -> routing
                        .name("routing")
                        .type(SERVER_HTTP_ROUTING_BUILDER))
                .addContent("routing.register(")
                .addContent(GRAPHQL_SERVICE)
                .addContentLine(".builder()")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContent(".webContext(")
                .addContentLiteral(endpoint.context())
                .addContentLine(")")
                .addContent(".schemaUri(")
                .addContentLiteral(endpoint.schemaUri())
                .addContentLine(")")
                .addContentLine(".invocationHandler(invocationHandler())")
                .addContentLine(".build());")
                .decreaseContentPadding()
                .decreaseContentPadding());
    }

    private void addSocketMethods(ClassModel.Builder classModel, GraphQlEndpoint endpoint) {
        endpoint.listener().ifPresent(listener -> {
            classModel.addMethod(socket -> socket
                    .accessModifier(AccessModifier.PUBLIC)
                    .returnType(TypeNames.STRING)
                    .name("socket")
                    .addAnnotation(Annotations.OVERRIDE)
                    .addContent("return ")
                    .addContentLiteral(listener)
                    .addContentLine(";"));
            classModel.addMethod(socket -> socket
                    .accessModifier(AccessModifier.PUBLIC)
                    .returnType(TypeNames.PRIMITIVE_BOOLEAN)
                    .name("socketRequired")
                    .addAnnotation(Annotations.OVERRIDE)
                    .addContentLine("return true;"));
        });
    }

    private void addSchemaMethod(ClassModel.Builder classModel, GraphQlEndpoint endpoint) {
        classModel.addMethod(method -> method
                .accessModifier(AccessModifier.PRIVATE)
                .returnType(GRAPHQL_SCHEMA)
                .name("schema")
                .addContent("String schema = ")
                .addContentLiteral(schema(endpoint))
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

    private void addRuntimeWiringMethod(ClassModel.Builder classModel,
                                        GraphQlEndpoint endpoint,
                                        TypeName descriptorType) {
        classModel.addMethod(method -> {
            method.accessModifier(AccessModifier.PRIVATE)
                    .returnType(RUNTIME_WIRING)
                    .name("runtimeWiring")
                    .addContent("var descriptor = ")
                    .addContent(descriptorType)
                    .addContentLine(".INSTANCE;")
                    .addContent("var annotations = ")
                    .addContent(descriptorType)
                    .addContentLine(".ANNOTATIONS;")
                    .addContent("var builder = ")
                    .addContent(RUNTIME_WIRING)
                    .addContentLine(".newRuntimeWiring();");

            addWiring(method, "Query", endpoint.queries(), descriptorType);
            addWiring(method, "Mutation", endpoint.mutations(), descriptorType);
            addObjectWiring(method, endpoint.schemaTypes().objectTypes());

            method.addContentLine("return builder.build();");
        });
    }

    private void addWiring(io.helidon.codegen.classmodel.Method.Builder method,
                           String graphQlType,
                           List<Operation> operations,
                           TypeName descriptorType) {
        if (operations.isEmpty()) {
            return;
        }

        for (Operation operation : operations) {
            method.addContent("var fetcher_")
                    .addContent(operation.uniqueName())
                    .addContentLine(" = entryPoints.dataFetcher(")
                    .increaseContentPadding()
                    .increaseContentPadding()
                    .addContentLine("descriptor,")
                    .addContentLine("descriptor.qualifiers(),")
                    .addContentLine("annotations,")
                    .addContent(descriptorType)
                    .addContent(".")
                    .addContent(toConstantName("METHOD_" + operation.uniqueName()))
                    .addContentLine(",")
                    .addContent("this::")
                    .addContent(operation.uniqueName())
                    .addContentLine(");")
                    .decreaseContentPadding()
                    .decreaseContentPadding();
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
                        .addContentLiteral(field.graphQlName())
                        .addContent(", environment -> ((")
                        .addContent(objectType.javaType())
                        .addContent(") environment.getSource()).")
                        .addContent(field.accessor())
                        .addContentLine(")");
            }
            method.addContentLine(");")
                    .decreaseContentPadding()
                    .decreaseContentPadding();
        }
    }

    private void addResolverMethods(ClassModel.Builder classModel, GraphQlEndpoint endpoint) {
        endpoint.operations()
                .forEach(operation -> classModel.addMethod(method -> method
                        .accessModifier(AccessModifier.PRIVATE)
                        .returnType(TypeNames.OBJECT)
                        .name(operation.uniqueName())
                        .addParameter(env -> env
                                .type(DATA_FETCHING_ENVIRONMENT)
                                .name("environment"))
                        .addThrows(TypeName.create(Exception.class))
                        .update(it -> resolverBody(it, endpoint, operation))));
    }

    private void resolverBody(io.helidon.codegen.classmodel.Method.Builder method,
                              GraphQlEndpoint endpoint,
                              Operation operation) {
        method.addContent("return this.endpoint.")
                .addContent(operation.method().elementName())
                .addContent("(");
        List<Argument> arguments = operation.arguments();
        if (arguments.isEmpty()) {
            method.addContentLine(");");
            return;
        }
        if (arguments.size() == 1) {
            argumentValue(method, arguments.getFirst());
            method.addContentLine(");");
            return;
        }

        method.addContentLine()
                .increaseContentPadding()
                .increaseContentPadding();
        for (int i = 0; i < arguments.size(); i++) {
            argumentValue(method, arguments.get(i));
            if (i + 1 == arguments.size()) {
                method.addContentLine(");");
            } else {
                method.addContentLine(",");
            }
        }
        method.decreaseContentPadding()
                .decreaseContentPadding();
    }

    private void argumentValue(io.helidon.codegen.classmodel.Method.Builder method, Argument argument) {
        TypeName type = argument.parameter().typeName();
        TypeName boxed = type.boxed();
        if (boxed.equals(TypeNames.BOXED_INT)
                || boxed.equals(TypeNames.BOXED_DOUBLE)
                || boxed.equals(TypeNames.BOXED_BOOLEAN)
                || boxed.equals(TypeNames.STRING)) {
            method.addContent("(")
                    .addContent(boxed)
                    .addContent(") environment.getArgument(")
                    .addContentLiteral(argument.graphQlName())
                    .addContent(")");
            return;
        }
        throw unsupportedArgumentType(null, null, type, argument.parameter().originatingElementValue());
    }

    private String schema(GraphQlEndpoint endpoint) {
        StringBuilder result = new StringBuilder();
        result.append("schema {\n")
                .append("  query: Query\n");
        if (!endpoint.mutations().isEmpty()) {
            result.append("  mutation: Mutation\n");
        }
        result.append("}\n\n");

        appendType(result, "Query", endpoint.queries(), endpoint.schemaTypes());
        if (!endpoint.mutations().isEmpty()) {
            result.append('\n');
            appendType(result, "Mutation", endpoint.mutations(), endpoint.schemaTypes());
        }
        endpoint.schemaTypes().appendTo(result);
        return result.toString();
    }

    private void appendType(StringBuilder result, String typeName, List<Operation> operations, SchemaTypes schemaTypes) {
        result.append("type ")
                .append(typeName)
                .append(" {\n");
        for (Operation operation : operations) {
            result.append("  ")
                    .append(operation.graphQlName());
            if (!operation.arguments().isEmpty()) {
                result.append('(');
                for (int i = 0; i < operation.arguments().size(); i++) {
                    Argument argument = operation.arguments().get(i);
                    if (i > 0) {
                        result.append(", ");
                    }
                    result.append(argument.graphQlName())
                            .append(": ")
                            .append(scalarSchemaType(argument.parameter().typeName(), argument.nonNull()));
                    argument.defaultValue().ifPresent(value -> result.append(" = ").append(value));
                }
                result.append(')');
            }
            result.append(": ")
                    .append(schemaTypes.outputType(operation.method().typeName(),
                                                   operation.method().typeName().primitive()
                                                           || hasNonNull(operation.annotations()),
                                                   operation.method().originatingElementValue()))
                    .append('\n');
        }
        result.append("}\n");
    }

    private static boolean hasNonNull(Collection<Annotation> annotations) {
        return Annotations.findFirst(GRAPHQL_NON_NULL, annotations).isPresent();
    }

    private static String graphQlName(Set<Annotation> annotations, String defaultName) {
        return Annotations.findFirst(GRAPHQL_NAME, annotations)
                .flatMap(Annotation::stringValue)
                .filter(not(String::isBlank))
                .orElse(defaultName);
    }

    private static String scalarSchemaType(TypeName type, boolean nonNull) {
        String graphQlType = scalarGraphQlType(type).orElseThrow();
        return nonNull ? graphQlType + "!" : graphQlType;
    }

    private static Optional<String> scalarGraphQlType(TypeName type) {
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

    private final class SchemaTypes {
        private final RegistryRoundContext roundContext;
        private final Map<TypeName, SchemaType> types = new LinkedHashMap<>();
        private final Map<String, TypeName> graphQlNames = new LinkedHashMap<>();
        private final Set<TypeName> visiting = new HashSet<>();

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

        private void appendTo(StringBuilder result) {
            for (SchemaType type : types.values()) {
                result.append('\n');
                if (type instanceof ObjectSchemaType objectType) {
                    appendObject(result, objectType);
                } else if (type instanceof EnumSchemaType enumType) {
                    appendEnum(result, enumType);
                }
            }
        }

        private String outputType(TypeName type, Object originatingElement) {
            Optional<String> scalar = scalarGraphQlType(type);
            if (scalar.isPresent()) {
                return scalar.get();
            }

            TypeInfo typeInfo = roundContext.typeInfo(type.boxed())
                    .or(() -> roundContext.typeInfo(type))
                    .orElseThrow(() -> unsupportedOutputType(type, originatingElement));

            if (typeInfo.kind() == ElementKind.ENUM) {
                return enumType(typeInfo);
            }
            if (typeInfo.kind() == ElementKind.RECORD
                    || typeInfo.kind() == ElementKind.CLASS
                    || typeInfo.kind() == ElementKind.INTERFACE) {
                return objectType(typeInfo);
            }
            throw unsupportedOutputType(type, originatingElement);
        }

        private String enumType(TypeInfo typeInfo) {
            TypeName typeName = typeInfo.typeName();
            SchemaType existing = types.get(typeName);
            if (existing != null) {
                return existing.graphQlName();
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
            types.put(typeName, new EnumSchemaType(graphQlName, description(annotations), values));
            return graphQlName;
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
            types.put(typeName, new ObjectSchemaType(typeName, graphQlName, description(annotations), List.of()));

            if (!visiting.add(typeName)) {
                return graphQlName;
            }
            try {
                List<SchemaField> fields = objectFields(typeInfo);
                if (fields.isEmpty()) {
                    throw new CodegenException("GraphQL object type " + typeName.fqName()
                                                       + " does not declare any readable fields.",
                                               typeInfo.originatingElementValue());
                }
                types.put(typeName, new ObjectSchemaType(typeName, graphQlName, description(annotations), fields));
            } finally {
                visiting.remove(typeName);
            }
            return graphQlName;
        }

        private List<EnumValue> enumValues(TypeInfo typeInfo) {
            return typeInfo.elementInfo()
                    .stream()
                    .filter(it -> it.kind() == ElementKind.ENUM_CONSTANT)
                    .map(this::enumValue)
                    .toList();
        }

        private EnumValue enumValue(TypedElementInfo enumConstant) {
            Set<Annotation> annotations = new HashSet<>(enumConstant.annotations());
            return new EnumValue(graphQlName(annotations, enumConstant.elementName()), description(annotations));
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

        private void addField(TypeInfo typeInfo,
                              Map<String, SchemaField> fields,
                              TypedElementInfo element,
                              String defaultName,
                              String accessor) {
            Set<Annotation> annotations = new HashSet<>(element.annotations());
            if (Annotations.findFirst(GRAPHQL_IGNORE, annotations).isPresent()) {
                return;
            }
            String graphQlName = graphQlName(annotations, defaultName);
            String schemaType = outputType(element.typeName(),
                                           element.typeName().primitive() || hasNonNull(annotations),
                                           element.originatingElementValue());
            SchemaField previous = fields.putIfAbsent(graphQlName,
                                                      new SchemaField(graphQlName,
                                                                      schemaType,
                                                                      description(annotations),
                                                                      accessor));
            if (previous != null) {
                throw new CodegenException("Duplicate GraphQL field '" + graphQlName + "' in type "
                                                   + typeInfo.typeName().fqName(),
                                           element.originatingElementValue());
            }
        }

        private void reserveTypeName(TypeInfo typeInfo, String graphQlName) {
            if ("Query".equals(graphQlName) || "Mutation".equals(graphQlName)) {
                throw new CodegenException("GraphQL type " + typeInfo.typeName().fqName()
                                                   + " cannot use reserved type name '" + graphQlName + "'.",
                                           typeInfo.originatingElementValue());
            }
            TypeName previous = graphQlNames.putIfAbsent(graphQlName, typeInfo.typeName());
            if (previous != null && !previous.equals(typeInfo.typeName())) {
                throw new CodegenException("Duplicate GraphQL type name '" + graphQlName + "' for "
                                                   + previous.fqName() + " and " + typeInfo.typeName().fqName() + ".",
                                           typeInfo.originatingElementValue());
            }
        }
    }

    private static Optional<String> propertyName(TypedElementInfo method) {
        String methodName = method.elementName();
        if (isPropertyGetter(methodName)) {
            return Optional.of(nameFromPropertyGetter(methodName));
        }
        if (isBooleanPropertyGetter(methodName, method.typeName())) {
            return Optional.of(nameFromBooleanPropertyGetter(methodName));
        }
        return Optional.empty();
    }

    private static boolean isPropertyGetter(String methodName) {
        return methodName.startsWith("get")
                && methodName.length() > 3
                && Character.isUpperCase(methodName.charAt(3))
                && !"getClass".equals(methodName);
    }

    private static boolean isBooleanPropertyGetter(String methodName, TypeName typeName) {
        TypeName boxed = typeName.boxed();
        return methodName.startsWith("is")
                && methodName.length() > 2
                && Character.isUpperCase(methodName.charAt(2))
                && boxed.equals(TypeNames.BOXED_BOOLEAN);
    }

    private static String nameFromPropertyGetter(String methodName) {
        return lowerFirstProperty(methodName.substring(3));
    }

    private static String nameFromBooleanPropertyGetter(String methodName) {
        return lowerFirstProperty(methodName.substring(2));
    }

    private static String lowerFirstProperty(String propertyName) {
        char firstChar = propertyName.charAt(0);
        if (propertyName.length() == 1) {
            return String.valueOf(Character.toLowerCase(firstChar));
        }
        if (!Character.isUpperCase(propertyName.charAt(1))) {
            return Character.toLowerCase(firstChar) + propertyName.substring(1);
        }
        return propertyName;
    }

    private static String typeName(TypeInfo typeInfo, Set<Annotation> annotations) {
        return graphQlName(annotations, typeInfo.typeName().className());
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

    private static void appendObject(StringBuilder result, ObjectSchemaType objectType) {
        appendDescription(result, 0, objectType.description());
        result.append("type ")
                .append(objectType.graphQlName())
                .append(" {\n");
        for (SchemaField field : objectType.fields()) {
            appendDescription(result, 2, field.description());
            result.append("  ")
                    .append(field.graphQlName())
                    .append(": ")
                    .append(field.schemaType())
                    .append('\n');
        }
        result.append("}\n");
    }

    private static void appendEnum(StringBuilder result, EnumSchemaType enumType) {
        appendDescription(result, 0, enumType.description());
        result.append("enum ")
                .append(enumType.graphQlName())
                .append(" {\n");
        for (EnumValue value : enumType.values()) {
            appendDescription(result, 2, value.description());
            result.append("  ")
                    .append(value.graphQlName())
                    .append('\n');
        }
        result.append("}\n");
    }

    private static void appendDescription(StringBuilder result, int indent, Optional<String> description) {
        description.ifPresent(value -> result.append(" ".repeat(indent))
                .append('"')
                .append(escapeDescription(value))
                .append("\"\n"));
    }

    private static String escapeDescription(String description) {
        return description.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }

    private CodegenException unsupportedOutputType(TypeName type, Object originatingElement) {
        return new CodegenException("Return or field type " + type.fqName()
                                            + " is not supported by the declarative GraphQL generator. Supported output types "
                                            + "are String, int/Integer, double/Double, boolean/Boolean, and Java "
                                            + "enums/classes/records/interfaces annotated with @GraphQl.Entity.",
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
        message.append(". Supported argument types are String, int/Integer, double/Double, and boolean/Boolean.");
        return new CodegenException(message.toString(), originatingElement);
    }

    private enum OperationKind {
        QUERY("query"),
        MUTATION("mutation");

        private final String label;

        OperationKind(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    private record GraphQlEndpoint(TypeInfo typeInfo,
                                   Set<Annotation> annotations,
                                   Optional<String> listener,
                                   String context,
                                   String schemaUri,
                                   SchemaTypes schemaTypes,
                                   List<Operation> queries,
                                   List<Operation> mutations) {
        List<Operation> operations() {
            List<Operation> result = new ArrayList<>(queries);
            result.addAll(mutations);
            return result;
        }
    }

    private record Operation(OperationKind kind,
                             TypedElementInfo method,
                             List<Annotation> annotations,
                             String graphQlName,
                             String uniqueName,
                             List<Argument> arguments) {
    }

    private record Argument(TypedElementInfo parameter,
                            String graphQlName,
                            List<Annotation> annotations,
                            Optional<String> defaultValue,
                            boolean nonNull) {
    }

    private interface SchemaType {
        String graphQlName();
    }

    private record ObjectSchemaType(TypeName javaType,
                                    String graphQlName,
                                    Optional<String> description,
                                    List<SchemaField> fields) implements SchemaType {
    }

    private record EnumSchemaType(String graphQlName,
                                  Optional<String> description,
                                  List<EnumValue> values) implements SchemaType {
    }

    private record SchemaField(String graphQlName,
                               String schemaType,
                               Optional<String> description,
                               String accessor) {
    }

    private record EnumValue(String graphQlName,
                             Optional<String> description) {
    }
}
