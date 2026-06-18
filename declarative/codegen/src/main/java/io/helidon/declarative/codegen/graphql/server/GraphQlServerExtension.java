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
import java.util.List;
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
            process(roundContext, toEndpoint(endpoint));
        }
    }

    private GraphQlEndpoint toEndpoint(TypeInfo typeInfo) {
        if (typeInfo.kind() == ElementKind.INTERFACE) {
            throw new CodegenException("Interfaces should not be annotated with " + GRAPHQL_SERVER_ENDPOINT.fqName(),
                                       typeInfo.originatingElementValue());
        }

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
                .forEach(method -> addOperation(typeInfo, method, queries, mutations, queryNames, mutationNames));

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
                                   List.copyOf(queries),
                                   List.copyOf(mutations));
    }

    private void addOperation(TypeInfo endpoint,
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

        if (graphQlType(method.typeName()).isEmpty()) {
            throw unsupportedType("Return type", endpoint, method, method.typeName(), method.originatingElementValue());
        }

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
        if (graphQlType(parameter.typeName()).isEmpty()) {
            throw unsupportedType("Argument type", endpoint, method, parameter.typeName(), parameter.originatingElementValue());
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
        throw unsupportedType("Argument type", null, null, type, argument.parameter().originatingElementValue());
    }

    private String schema(GraphQlEndpoint endpoint) {
        StringBuilder result = new StringBuilder();
        result.append("schema {\n")
                .append("  query: Query\n");
        if (!endpoint.mutations().isEmpty()) {
            result.append("  mutation: Mutation\n");
        }
        result.append("}\n\n");

        appendType(result, "Query", endpoint.queries());
        if (!endpoint.mutations().isEmpty()) {
            result.append('\n');
            appendType(result, "Mutation", endpoint.mutations());
        }
        return result.toString();
    }

    private void appendType(StringBuilder result, String typeName, List<Operation> operations) {
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
                            .append(schemaType(argument.parameter().typeName(), argument.nonNull()));
                    argument.defaultValue().ifPresent(value -> result.append(" = ").append(value));
                }
                result.append(')');
            }
            result.append(": ")
                    .append(schemaType(operation.method().typeName(),
                                       operation.method().typeName().primitive()
                                               || hasNonNull(operation.annotations())))
                    .append('\n');
        }
        result.append("}\n");
    }

    private static boolean hasNonNull(List<Annotation> annotations) {
        return Annotations.findFirst(GRAPHQL_NON_NULL, annotations).isPresent();
    }

    private static String graphQlName(Set<Annotation> annotations, String defaultName) {
        return Annotations.findFirst(GRAPHQL_NAME, annotations)
                .flatMap(Annotation::stringValue)
                .filter(not(String::isBlank))
                .orElse(defaultName);
    }

    private static String schemaType(TypeName type, boolean nonNull) {
        String graphQlType = graphQlType(type).orElseThrow();
        return nonNull ? graphQlType + "!" : graphQlType;
    }

    private static Optional<String> graphQlType(TypeName type) {
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

    private static CodegenException unsupportedType(String subject,
                                                    TypeInfo endpoint,
                                                    TypedElementInfo method,
                                                    TypeName type,
                                                    Object originatingElement) {
        StringBuilder message = new StringBuilder(subject)
                .append(" ")
                .append(type.fqName())
                .append(" is not supported by the initial declarative GraphQL generator");
        if (endpoint != null && method != null) {
            message.append(" for ")
                    .append(endpoint.typeName().fqName())
                    .append(".")
                    .append(method.elementName());
        }
        message.append(". Supported scalar types are String, int/Integer, double/Double, and boolean/Boolean.");
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
}
