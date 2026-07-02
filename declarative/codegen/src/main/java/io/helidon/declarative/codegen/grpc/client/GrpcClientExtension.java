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

package io.helidon.declarative.codegen.grpc.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.TypeHierarchy;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.codegen.classmodel.Parameter;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.declarative.codegen.DeclarativeTypes;
import io.helidon.declarative.codegen.DeclarativeUtils;
import io.helidon.declarative.codegen.DelcarativeConfigSupport;
import io.helidon.declarative.codegen.grpc.GrpcMethodHierarchy;
import io.helidon.declarative.codegen.grpc.GrpcProtoDescriptor;
import io.helidon.declarative.codegen.grpc.GrpcProtoDescriptors;
import io.helidon.declarative.codegen.grpc.GrpcProtoDescriptors.MethodType;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;

import static io.helidon.declarative.codegen.DeclarativeTypes.CONFIG;
import static io.helidon.declarative.codegen.DeclarativeTypes.SINGLETON_ANNOTATION;
import static io.helidon.declarative.codegen.grpc.client.GrpcClientTypes.GRPC_CLIENT;
import static io.helidon.declarative.codegen.grpc.client.GrpcClientTypes.GRPC_CLIENT_METHOD_DESCRIPTOR;
import static io.helidon.declarative.codegen.grpc.client.GrpcClientTypes.GRPC_METHOD;
import static io.helidon.declarative.codegen.grpc.client.GrpcClientTypes.GRPC_PROTO;
import static io.helidon.declarative.codegen.grpc.client.GrpcClientTypes.GRPC_PROTO_DESCRIPTOR;
import static io.helidon.declarative.codegen.grpc.client.GrpcClientTypes.GRPC_SERVICE;
import static io.helidon.declarative.codegen.grpc.client.GrpcClientTypes.GRPC_SERVICE_CLIENT;
import static io.helidon.declarative.codegen.grpc.client.GrpcClientTypes.GRPC_SERVICE_DESCRIPTOR;
import static io.helidon.declarative.codegen.grpc.client.GrpcClientTypes.PROTO_FILE_DESCRIPTOR;
import static io.helidon.declarative.codegen.grpc.client.GrpcClientTypes.PROTO_MESSAGE_DESCRIPTOR;
import static io.helidon.declarative.codegen.grpc.client.GrpcClientTypes.RPC_CLIENT_ENDPOINT;
import static io.helidon.declarative.codegen.grpc.client.GrpcClientTypes.RPC_CLIENT_QUALIFIER_INSTANCE;
import static io.helidon.declarative.codegen.grpc.client.GrpcClientTypes.SERVICE_INSTANCE;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_NAMED;
import static java.util.function.Predicate.not;

class GrpcClientExtension implements RegistryCodegenExtension {
    static final TypeName GENERATOR = TypeName.create(GrpcClientExtension.class);

    private static final TypeName ITERATOR = TypeName.create("java.util.Iterator");
    private static final TypeName ITERABLE = TypeName.create("java.lang.Iterable");
    private static final TypeName STREAM = TypeName.create("java.util.stream.Stream");
    private static final TypeName STREAM_OBSERVER = TypeName.create("io.grpc.stub.StreamObserver");

    private final RegistryCodegenContext ctx;

    GrpcClientExtension(RegistryCodegenContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void process(RegistryRoundContext roundContext) {
        Collection<TypeInfo> clientApis = roundContext.annotatedTypes(RPC_CLIENT_ENDPOINT);

        List<GrpcEndpoint> endpoints = clientApis.stream()
                .map(this::toEndpoint)
                .collect(Collectors.toUnmodifiableList());

        for (GrpcEndpoint endpoint : endpoints) {
            TypeInfo type = endpoint.type();
            String classNameBase = type.typeName().classNameWithEnclosingNames().replace('.', '_');
            TypeName generatedType = TypeName.builder()
                    .packageName(type.typeName().packageName())
                    .className(classNameBase + "__GrpcClient")
                    .build();

            var classModel = ClassModel.builder()
                    .copyright(CodegenUtil.copyright(GENERATOR, type.typeName(), generatedType))
                    .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR, type.typeName(), generatedType, "1", ""))
                    .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                    .type(generatedType)
                    .addInterface(type.typeName())
                    .addAnnotation(SINGLETON_ANNOTATION)
                    .addAnnotation(DeclarativeTypes.SUPPRESS_API)
                    .addAnnotation(RPC_CLIENT_QUALIFIER_INSTANCE);

            classModel.addField(client -> client
                    .accessModifier(AccessModifier.PRIVATE)
                    .isFinal(true)
                    .type(GRPC_SERVICE_CLIENT)
                    .name("serviceClient"));

            for (GrpcMethod method : endpoint.methods()) {
                classModel.addMethod(clientMethod -> {
                    clientMethod.addAnnotation(Annotations.OVERRIDE)
                            .accessModifier(AccessModifier.PUBLIC)
                            .name(method.method().elementName())
                            .returnType(method.method().typeName());
                    for (MethodParameter parameter : method.parameters()) {
                        clientMethod.addParameter(it -> it
                                .name(parameter.name())
                                .type(parameter.type()));
                    }
                    for (MethodParameter parameter : method.parameters()) {
                        clientMethod.addContent("java.util.Objects.requireNonNull(")
                                .addContent(parameter.name())
                                .addContent(", ")
                                .addContentLiteral(parameter.name())
                                .addContentLine(");");
                    }

                    switch (method.invocation()) {
                    case SERVER_STREAMING_STREAM -> clientMethod.addContent("return serviceClient.serverStreaming(")
                            .addContentLiteral(method.methodName())
                            .addContentLine(", request);");
                    case CLIENT_STREAMING_STREAM -> clientMethod.addContent("return serviceClient.clientStreaming(")
                            .addContentLiteral(method.methodName())
                            .addContentLine(", requests);");
                    case BIDI_STREAM -> clientMethod.addContent("return serviceClient.bidirectional(")
                            .addContentLiteral(method.methodName())
                            .addContentLine(", requests);");
                    default -> {
                        if (method.invocation().returnsValue()) {
                            clientMethod.addContent("return ");
                        }
                        clientMethod.addContent("serviceClient.")
                                .addContent(method.invocation().clientMethodName())
                                .addContent("(")
                                .addContentLiteral(method.methodName());
                        for (MethodParameter parameter : method.parameters()) {
                            clientMethod.addContent(", ")
                                    .addContent(parameter.name());
                        }
                        clientMethod.addContentLine(");");
                    }
                    }
                });
            }

            classModel.addConstructor(Constructor.builder()
                                              .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                                              .addParameter(config -> config
                                                      .name("config")
                                                      .type(CONFIG))
                                              .addParameter(registryClient -> registryClient
                                                      .name(endpoint.clientName().isPresent()
                                                                    ? "registryClient"
                                                                    : "registryClients")
                                                      .update(it -> registryClientParameter(it, endpoint)))
                                              .update(it -> constructorBody(it, endpoint)));
            GrpcProtoDescriptors.addRuntimeValidationMethod(classModel,
                                                            PROTO_FILE_DESCRIPTOR,
                                                            PROTO_MESSAGE_DESCRIPTOR);
            GrpcProtoDescriptors.addRuntimeMethodTypeMethod(classModel);

            roundContext.addGeneratedType(generatedType, classModel, type.typeName(), type.originatingElementValue());
        }
    }

    private GrpcEndpoint toEndpoint(TypeInfo typeInfo) {
        if (typeInfo.kind() != ElementKind.INTERFACE) {
            throw new CodegenException("Types annotated with "
                                               + RPC_CLIENT_ENDPOINT.classNameWithEnclosingNames()
                                               + " must be interfaces. This type is: " + typeInfo.kind(),
                                       typeInfo.originatingElementValue());
        }

        Set<Annotation> typeAnnotations = new HashSet<>(TypeHierarchy.hierarchyAnnotations(ctx, typeInfo));
        Annotation endpointAnnotation = Annotations.findFirst(RPC_CLIENT_ENDPOINT, typeAnnotations)
                .orElseThrow(() -> new CodegenException("Missing " + RPC_CLIENT_ENDPOINT.fqName(),
                                                        typeInfo.originatingElementValue()));
        String uri = endpointAnnotation.stringValue()
                .filter(not(String::isBlank))
                .orElseThrow(() -> new CodegenException("Declarative gRPC client "
                                                                + typeInfo.typeName().fqName()
                                                                + " must define a non-blank endpoint URI.",
                                                        typeInfo.originatingElementValue()));
        Optional<String> clientName = endpointAnnotation.stringValue("clientName")
                .filter(not(String::isBlank));
        String configKey = endpointAnnotation.stringValue("configKey")
                .filter(not(String::isBlank))
                .orElseGet(() -> typeInfo.typeName().fqName());
        String serviceName = Annotations.findFirst(GRPC_SERVICE, typeAnnotations)
                .flatMap(Annotation::stringValue)
                .filter(not(String::isBlank))
                .orElseThrow(() -> new CodegenException("Declarative gRPC client "
                                                                + typeInfo.typeName().fqName()
                                                                + " must define a non-blank @Grpc.GrpcService value.",
                                                        typeInfo.originatingElementValue()));
        GrpcProtoDescriptor protoDescriptor = GrpcProtoDescriptors.find(ctx,
                                                                        typeInfo,
                                                                        List.copyOf(typeAnnotations),
                                                                        GRPC_PROTO,
                                                                        GRPC_PROTO_DESCRIPTOR,
                                                                        GRPC_METHOD,
                                                                        PROTO_FILE_DESCRIPTOR,
                                                                        "client");
        protoDescriptor.method()
                .filter(_ -> !protoDescriptor.isStatic())
                .filter(not(ElementInfoPredicates::isDefault))
                .ifPresent(method -> {
                    throw new CodegenException("Non-static @Grpc.Proto method on declarative gRPC client "
                                                       + typeInfo.typeName().fqName()
                                                       + " must be a default interface method.",
                                               method.originatingElementValue());
                });

        List<GrpcMethod> methods = new ArrayList<>();
        GrpcMethodHierarchy.methods(typeInfo)
                .stream()
                .filter(ElementInfoPredicates::isAbstract)
                .filter(not(ElementInfoPredicates::isPrivate))
                .filter(not(ElementInfoPredicates::isStatic))
                .filter(not(ElementInfoPredicates::isDefault))
                .forEach(method -> toClientMethod(typeInfo, method).ifPresent(methods::add));
        if (methods.isEmpty()) {
            throw new CodegenException("Declarative gRPC client "
                                               + typeInfo.typeName().fqName()
                                               + " must declare at least one gRPC method.",
                                       typeInfo.originatingElementValue());
        }
        var methodsByGrpcName = new LinkedHashMap<String, GrpcMethod>();
        for (GrpcMethod method : methods) {
            GrpcMethod previous = methodsByGrpcName.putIfAbsent(method.methodName(), method);
            if (previous != null) {
                throw new CodegenException("Declarative gRPC client "
                                                   + typeInfo.typeName().fqName()
                                                   + " maps both "
                                                   + previous.method().elementName()
                                                   + "() and "
                                                   + method.method().elementName()
                                                   + "() to wire method "
                                                   + method.methodName()
                                                   + ".",
                                           method.method().originatingElementValue());
            }
        }
        return new GrpcEndpoint(typeInfo,
                                uri,
                                serviceName,
                                configKey,
                                clientName,
                                protoDescriptor,
                                List.copyOf(methods));
    }

    private Optional<GrpcMethod> toClientMethod(TypeInfo typeInfo, TypedElementInfo method) {
        List<Annotation> annotations = TypeHierarchy.hierarchyAnnotations(ctx, typeInfo, method);
        List<Annotation> grpcMethodAnnotations = grpcMethodAnnotations(method, annotations);
        if (grpcMethodAnnotations.size() > 1) {
            throw new CodegenException("Declarative gRPC client method "
                                               + typeInfo.typeName().fqName() + "." + method.elementName()
                                               + "() must declare exactly one gRPC method annotation.",
                                       method.originatingElementValue());
        }

        Optional<Annotation> maybeGrpcMethod = DeclarativeUtils.findMetaAnnotated(annotations, GRPC_METHOD);
        if (maybeGrpcMethod.isEmpty()) {
            throw new CodegenException("Declarative gRPC client method "
                                               + typeInfo.typeName().fqName() + "." + method.elementName()
                                               + "() must declare a gRPC method annotation.",
                                       method.originatingElementValue());
        }
        if (!method.throwsChecked().isEmpty()) {
            throw new CodegenException("Declarative gRPC client methods must not declare checked exceptions: "
                                               + typeInfo.typeName().fqName() + "." + method.elementName() + "()",
                                       method.originatingElementValue());
        }

        MethodType methodType = GrpcProtoDescriptors.methodType(method, maybeGrpcMethod.get(), "client");
        List<TypedElementInfo> parameters = method.parameterArguments();
        TypeName returnType = method.typeName();
        boolean voidReturn = returnType.boxed().equals(TypeNames.BOXED_VOID);
        GrpcMethod.Invocation invocation;
        TypeName requestType;
        TypeName responseType;
        List<MethodParameter> methodParameters;
        if (methodType == MethodType.UNARY
                && parameters.size() == 1
                && !voidReturn
                && !isStreamingContainer(parameters.getFirst().typeName())
                && !isStreamingContainer(returnType)) {
            invocation = GrpcMethod.Invocation.UNARY_RETURN;
            requestType = parameters.getFirst().typeName();
            responseType = returnType;
            methodParameters = List.of(new MethodParameter(requestType, "request"));
        } else if ((methodType == MethodType.UNARY || methodType == MethodType.SERVER_STREAMING)
                && parameters.size() == 2
                && voidReturn
                && !isStreamingContainer(parameters.getFirst().typeName())
                && isSingleGeneric(parameters.get(1).typeName(), STREAM_OBSERVER)) {
            invocation = methodType == MethodType.UNARY
                    ? GrpcMethod.Invocation.UNARY_OBSERVER
                    : GrpcMethod.Invocation.SERVER_STREAMING_OBSERVER;
            requestType = parameters.getFirst().typeName();
            responseType = parameters.get(1).typeName().typeArguments().getFirst();
            methodParameters = List.of(new MethodParameter(requestType, "request"),
                                       new MethodParameter(parameters.get(1).typeName(), "responseObserver"));
        } else if (methodType == MethodType.SERVER_STREAMING
                && parameters.size() == 1
                && !isStreamingContainer(parameters.getFirst().typeName())
                && isSingleGeneric(returnType, STREAM)) {
            invocation = GrpcMethod.Invocation.SERVER_STREAMING_STREAM;
            requestType = parameters.getFirst().typeName();
            responseType = returnType.typeArguments().getFirst();
            methodParameters = List.of(new MethodParameter(requestType, "request"));
        } else if (methodType == MethodType.CLIENT_STREAMING
                && parameters.size() == 1
                && isSingleGeneric(parameters.getFirst().typeName(), STREAM)
                && !isStreamingContainer(returnType)
                && !voidReturn) {
            invocation = GrpcMethod.Invocation.CLIENT_STREAMING_STREAM;
            requestType = parameters.getFirst().typeName().typeArguments().getFirst();
            responseType = returnType;
            methodParameters = List.of(new MethodParameter(parameters.getFirst().typeName(), "requests"));
        } else if (methodType == MethodType.BIDI_STREAMING
                && parameters.size() == 1
                && isSingleGeneric(parameters.getFirst().typeName(), STREAM)
                && isSingleGeneric(returnType, STREAM)) {
            invocation = GrpcMethod.Invocation.BIDI_STREAM;
            requestType = parameters.getFirst().typeName().typeArguments().getFirst();
            responseType = returnType.typeArguments().getFirst();
            methodParameters = List.of(new MethodParameter(parameters.getFirst().typeName(), "requests"));
        } else if ((methodType == MethodType.CLIENT_STREAMING || methodType == MethodType.BIDI_STREAMING)
                && parameters.size() == 1
                && isSingleGeneric(parameters.getFirst().typeName(), STREAM_OBSERVER)
                && isSingleGeneric(returnType, STREAM_OBSERVER)) {
            invocation = methodType == MethodType.CLIENT_STREAMING
                    ? GrpcMethod.Invocation.CLIENT_STREAMING_OBSERVER
                    : GrpcMethod.Invocation.BIDI_OBSERVER;
            requestType = returnType.typeArguments().getFirst();
            responseType = parameters.getFirst().typeName().typeArguments().getFirst();
            methodParameters = List.of(new MethodParameter(parameters.getFirst().typeName(), "responseObserver"));
        } else {
            throw new CodegenException("Unsupported declarative gRPC client method signature for "
                                               + typeInfo.typeName().fqName() + "." + method.elementName()
                                               + "(). Unary methods must be Res method(Req) or "
                                               + "void method(Req, StreamObserver<Res>); server streaming methods "
                                               + "must be Stream<Res> method(Req) or "
                                               + "void method(Req, StreamObserver<Res>); client streaming methods "
                                               + "must be Res method(Stream<Req>) or "
                                               + "StreamObserver<Req> method(StreamObserver<Res>); bidirectional streaming "
                                               + "methods must be Stream<Res> method(Stream<Req>) or "
                                               + "StreamObserver<Req> method(StreamObserver<Res>).",
                                       method.originatingElementValue());
        }
        GrpcProtoDescriptors.validateMessageType(ctx,
                                                  typeInfo,
                                                  requestType,
                                                  PROTO_MESSAGE_DESCRIPTOR,
                                                  "request");
        GrpcProtoDescriptors.validateMessageType(ctx,
                                                  typeInfo,
                                                  responseType,
                                                  PROTO_MESSAGE_DESCRIPTOR,
                                                  "response");
        return Optional.of(new GrpcMethod(method,
                                          methodName(method, annotations),
                                          methodType,
                                          invocation,
                                          requestType,
                                          responseType,
                                          methodParameters));
    }

    private static boolean isSingleGeneric(TypeName type, TypeName expectedGenericType) {
        return type.genericTypeName().equals(expectedGenericType) && type.typeArguments().size() == 1;
    }

    private static boolean isStreamingContainer(TypeName type) {
        TypeName genericType = type.genericTypeName();
        return genericType.equals(ITERATOR)
                || genericType.equals(ITERABLE)
                || genericType.equals(STREAM)
                || genericType.equals(STREAM_OBSERVER);
    }

    private void constructorBody(Constructor.Builder constructor, GrpcEndpoint endpoint) {
        GrpcProtoDescriptor protoDescriptor = endpoint.protoDescriptor();
        constructor.addContent("var declarative__proto = ");
        if (protoDescriptor.descriptorType().isPresent()) {
            constructor.addContent(protoDescriptor.descriptorType().orElseThrow())
                    .addContentLine(".getDescriptor();");
        } else if (protoDescriptor.isStatic()) {
            TypedElementInfo method = protoDescriptor.method().orElseThrow();
            constructor.addContent(method.enclosingType().orElse(endpoint.type().typeName()))
                    .addContent(".")
                    .addContent(method.elementName())
                    .addContentLine("();");
        } else {
            constructor.addContent("this.")
                    .addContent(protoDescriptor.method().orElseThrow().elementName())
                    .addContentLine("();");
        }
        for (GrpcMethod method : endpoint.methods()) {
            constructor.addContent("validateProtoMethod(declarative__proto, ")
                    .addContentLiteral(endpoint.serviceName())
                    .addContent(", ")
                    .addContentLiteral(method.methodName())
                    .addContent(", ")
                    .addContent(String.valueOf(method.methodType().clientStreaming()))
                    .addContent(", ")
                    .addContent(String.valueOf(method.methodType().serverStreaming()))
                    .addContent(", ")
                    .addContentLiteral(method.requestType().fqName())
                    .addContent(", ")
                    .addContent(method.requestType())
                    .addContent(".getDescriptor(), ")
                    .addContentLiteral(method.responseType().fqName())
                    .addContent(", ")
                    .addContent(method.responseType())
                    .addContent(".getDescriptor()")
                    .addContentLine(");");
        }
        constructor.addContent("var declarative__descriptor = ")
                .addContent(GRPC_SERVICE_DESCRIPTOR)
                .addContentLine(".builder()")
                .increaseContentPadding()
                .addContent(".serviceName(")
                .addContentLiteral(endpoint.serviceName())
                .addContentLine(")");
        for (GrpcMethod method : endpoint.methods()) {
            constructor.addContent(".putMethod(")
                    .addContentLiteral(method.methodName())
                    .addContent(", ")
                    .addContent(GRPC_CLIENT_METHOD_DESCRIPTOR)
                    .addContent(".")
                    .addContent(method.methodType().registrationMethod())
                    .addContent("(")
                    .addContentLiteral(endpoint.serviceName())
                    .addContent(", ")
                    .addContentLiteral(method.methodName())
                    .addContentLine(")")
                    .increaseContentPadding()
                    .addContent(".requestType(")
                    .addContent(method.requestType())
                    .addContentLine(".class)")
                    .addContent(".responseType(")
                    .addContent(method.responseType())
                    .addContentLine(".class)")
                    .addContentLine(".build())")
                    .decreaseContentPadding();
        }
        constructor.addContentLine(".build();")
                .decreaseContentPadding()
                .addContent("var declarative__clientConfig = config.get(")
                .addContentLiteral(endpoint.configKey())
                .addContentLine(").get(\"client\");")
                .addContent(GRPC_CLIENT)
                .addContentLine(" declarative__client = null;")
                .addContentLine("if (!declarative__clientConfig.exists()) {")
                .increaseContentPadding()
                .update(it -> {
                    if (endpoint.clientName().isPresent()) {
                        it.addContentLine("declarative__client = registryClient.get().orElse(null);");
                    } else {
                        it.addContentLine("declarative__client = registryClients.get().stream()")
                                .increaseContentPadding()
                                .addContentLine(".filter(instance -> instance.qualifiers().stream()")
                                .increaseContentPadding()
                                .addContent(".noneMatch(qualifier -> qualifier.typeName().equals(")
                                .addContent(SERVICE_ANNOTATION_NAMED)
                                .addContentLine(".TYPE)))")
                                .decreaseContentPadding()
                                .addContentLine(".findFirst()")
                                .addContent(".map(")
                                .addContent(SERVICE_INSTANCE)
                                .addContentLine("::get)")
                                .addContentLine(".orElse(null);")
                                .decreaseContentPadding();
                    }
                })
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("if (declarative__client == null) {")
                .increaseContentPadding()
                .update(it -> DelcarativeConfigSupport.assignResolveExpression(it, "config", "uri", endpoint.uri()))
                .addContent("var declarative__clientBuilder = ")
                .addContent(GRPC_CLIENT)
                .addContentLine(".builder();")
                .addContentLine("if (declarative__clientConfig.exists()) {")
                .increaseContentPadding()
                .addContentLine("declarative__clientBuilder.config(declarative__clientConfig);")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("declarative__clientBuilder.baseUri(uri);")
                .addContentLine("declarative__client = declarative__clientBuilder.build();")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("this.serviceClient = declarative__client.serviceClient(declarative__descriptor);");
    }

    private void registryClientParameter(Parameter.Builder param, GrpcEndpoint endpoint) {
        if (endpoint.clientName().isPresent()) {
            param.addAnnotation(Annotation.create(SERVICE_ANNOTATION_NAMED, endpoint.clientName().get()));
            TypeName optionalGrpcClient = TypeName.builder()
                    .from(TypeNames.OPTIONAL)
                    .addTypeArgument(GRPC_CLIENT)
                    .build();
            param.type(TypeName.builder()
                               .from(TypeNames.SUPPLIER)
                               .addTypeArgument(optionalGrpcClient)
                               .build());
        } else {
            TypeName serviceInstance = TypeName.builder()
                    .from(SERVICE_INSTANCE)
                    .addTypeArgument(GRPC_CLIENT)
                    .build();
            TypeName registryClients = TypeName.builder()
                    .from(TypeNames.LIST)
                    .addTypeArgument(serviceInstance)
                    .build();
            param.type(TypeName.builder()
                               .from(TypeNames.SUPPLIER)
                               .addTypeArgument(registryClients)
                               .build());
        }
    }

    private static List<Annotation> grpcMethodAnnotations(TypedElementInfo method, List<Annotation> annotations) {
        List<Annotation> result = new ArrayList<>();
        method.annotations()
                .stream()
                .filter(it -> it.typeName().equals(GRPC_METHOD))
                .forEach(result::add);
        annotations
                .stream()
                .filter(it -> !it.typeName().equals(GRPC_METHOD))
                .filter(it -> it.hasMetaAnnotation(GRPC_METHOD))
                .forEach(result::add);
        return result;
    }

    private static String methodName(TypedElementInfo method, List<Annotation> annotations) {
        Annotation concreteAnnotation = annotations.stream()
                .filter(it -> it.typeName().equals(GRPC_METHOD) || it.hasMetaAnnotation(GRPC_METHOD))
                .findFirst()
                .orElseThrow();
        String name = concreteAnnotation.typeName().equals(GRPC_METHOD)
                ? concreteAnnotation.stringValue("name").orElse("")
                : concreteAnnotation.stringValue().orElse("");
        if (name.isBlank() && !concreteAnnotation.typeName().equals(GRPC_METHOD)) {
            name = Annotations.findFirst(GRPC_METHOD, concreteAnnotation.metaAnnotations())
                    .flatMap(it -> it.stringValue("name"))
                    .orElse("");
        }
        return name.isBlank() ? method.elementName() : name;
    }

    private record GrpcEndpoint(TypeInfo type,
                                String uri,
                                String serviceName,
                                String configKey,
                                Optional<String> clientName,
                                GrpcProtoDescriptor protoDescriptor,
                                List<GrpcMethod> methods) {
    }

    private record GrpcMethod(TypedElementInfo method,
                              String methodName,
                              MethodType methodType,
                              Invocation invocation,
                              TypeName requestType,
                              TypeName responseType,
                              List<MethodParameter> parameters) {
        private enum Invocation {
            UNARY_RETURN("unary", true),
            UNARY_OBSERVER("unary", false),
            SERVER_STREAMING_STREAM("serverStreaming", true),
            SERVER_STREAMING_OBSERVER("serverStream", false),
            CLIENT_STREAMING_STREAM("clientStreaming", true),
            CLIENT_STREAMING_OBSERVER("clientStream", true),
            BIDI_STREAM("bidirectional", true),
            BIDI_OBSERVER("bidi", true);

            private final String clientMethodName;
            private final boolean returnsValue;

            Invocation(String clientMethodName, boolean returnsValue) {
                this.clientMethodName = clientMethodName;
                this.returnsValue = returnsValue;
            }

            private String clientMethodName() {
                return clientMethodName;
            }

            private boolean returnsValue() {
                return returnsValue;
            }
        }
    }

    private record MethodParameter(TypeName type, String name) {
    }

}
