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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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
import io.helidon.declarative.codegen.grpc.GrpcProtoDescriptor;
import io.helidon.declarative.codegen.grpc.GrpcProtoDescriptors;
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
import static io.helidon.declarative.codegen.grpc.client.GrpcClientTypes.RPC_CLIENT_ENDPOINT;
import static io.helidon.declarative.codegen.grpc.client.GrpcClientTypes.RPC_CLIENT_QUALIFIER_INSTANCE;
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
                    case SERVER_STREAMING_ITERABLE -> clientMethod.addContent("return () -> serviceClient.serverStream(")
                            .addContentLiteral(method.methodName())
                            .addContentLine(", request);");
                    case SERVER_STREAMING_STREAM -> clientMethod.addContent("return ")
                            .addContent(StreamSupport.class)
                            .addContent(".stream(")
                            .addContent(Spliterators.class)
                            .addContent(".spliteratorUnknownSize(serviceClient.serverStream(")
                            .addContentLiteral(method.methodName())
                            .addContentLine(", request), 0), false);");
                    case CLIENT_STREAMING_ITERABLE -> clientMethod.addContent("return serviceClient.clientStream(")
                            .addContentLiteral(method.methodName())
                            .addContentLine(", requests.iterator());");
                    case CLIENT_STREAMING_STREAM -> clientMethod.addContentLine("try (requests) {")
                            .increaseContentPadding()
                            .addContent("return serviceClient.clientStream(")
                            .addContentLiteral(method.methodName())
                            .addContentLine(", requests.iterator());")
                            .decreaseContentPadding()
                            .addContentLine("}");
                    case BIDI_ITERABLE -> clientMethod.addContent("return () -> serviceClient.bidi(")
                            .addContentLiteral(method.methodName())
                            .addContentLine(", requests.iterator());");
                    case BIDI_STREAM -> clientMethod.addContent("return serviceClient.bidiStream(")
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
                                                      .name("registryClient")
                                                      .update(it -> registryClientParameter(it, endpoint)))
                                              .update(it -> constructorBody(it, endpoint)));
            addProtoMethod(classModel, endpoint);
            addValidateProtoMethod(classModel);
            addMethodTypeMethod(classModel);

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
                                                                        "client")
                .orElseThrow(() -> GrpcProtoDescriptors.exactlyOne(typeInfo, "client"));

        Map<MethodSignature, MethodOrigin> discoveredMethods = new LinkedHashMap<>();
        typeInfo.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(ElementInfoPredicates::isAbstract)
                .filter(not(ElementInfoPredicates::isPrivate))
                .filter(not(ElementInfoPredicates::isStatic))
                .filter(not(ElementInfoPredicates::isDefault))
                .forEach(it -> discoveredMethods.put(MethodSignature.create(it), new MethodOrigin(typeInfo, it)));
        typeInfo.interfaceTypeInfo()
                .forEach(iface -> iface.elementInfo()
                        .stream()
                        .filter(ElementInfoPredicates::isMethod)
                        .filter(ElementInfoPredicates::isAbstract)
                        .filter(not(ElementInfoPredicates::isPrivate))
                        .filter(not(ElementInfoPredicates::isStatic))
                        .filter(not(ElementInfoPredicates::isDefault))
                        .forEach(it -> discoveredMethods.putIfAbsent(MethodSignature.create(it),
                                                                     new MethodOrigin(iface, it))));

        List<GrpcMethod> methods = new ArrayList<>();
        discoveredMethods.forEach((sig, origin) -> toClientMethod(origin.type(), origin.method()).ifPresent(methods::add));
        if (methods.isEmpty()) {
            throw new CodegenException("Declarative gRPC client "
                                               + typeInfo.typeName().fqName()
                                               + " must declare at least one gRPC method.",
                                       typeInfo.originatingElementValue());
        }
        return new GrpcEndpoint(typeInfo, uri, serviceName, configKey, clientName, protoDescriptor, List.copyOf(methods));
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

        GrpcMethodType methodType = methodType(method, maybeGrpcMethod.get());
        List<TypedElementInfo> parameters = method.parameterArguments();
        TypeName returnType = method.typeName();
        boolean voidReturn = returnType.boxed().equals(TypeNames.BOXED_VOID);
        GrpcMethod.Invocation invocation;
        TypeName requestType;
        TypeName responseType;
        List<MethodParameter> methodParameters;
        if (methodType == GrpcMethodType.UNARY
                && parameters.size() == 1
                && !voidReturn
                && !isStreamingContainer(parameters.getFirst().typeName())
                && !isStreamingContainer(returnType)) {
            invocation = GrpcMethod.Invocation.UNARY_RETURN;
            requestType = parameters.getFirst().typeName();
            responseType = returnType;
            methodParameters = List.of(new MethodParameter(requestType, "request"));
        } else if ((methodType == GrpcMethodType.UNARY || methodType == GrpcMethodType.SERVER_STREAMING)
                && parameters.size() == 2
                && voidReturn
                && !isStreamingContainer(parameters.getFirst().typeName())
                && isSingleGeneric(parameters.get(1).typeName(), STREAM_OBSERVER)) {
            invocation = methodType == GrpcMethodType.UNARY
                    ? GrpcMethod.Invocation.UNARY_OBSERVER
                    : GrpcMethod.Invocation.SERVER_STREAMING_OBSERVER;
            requestType = parameters.getFirst().typeName();
            responseType = parameters.get(1).typeName().typeArguments().getFirst();
            methodParameters = List.of(new MethodParameter(requestType, "request"),
                                       new MethodParameter(parameters.get(1).typeName(), "responseObserver"));
        } else if (methodType == GrpcMethodType.SERVER_STREAMING
                && parameters.size() == 1
                && !isStreamingContainer(parameters.getFirst().typeName())
                && isSingleGeneric(returnType, ITERABLE)) {
            invocation = GrpcMethod.Invocation.SERVER_STREAMING_ITERABLE;
            requestType = parameters.getFirst().typeName();
            responseType = returnType.typeArguments().getFirst();
            methodParameters = List.of(new MethodParameter(requestType, "request"));
        } else if (methodType == GrpcMethodType.SERVER_STREAMING
                && parameters.size() == 1
                && !isStreamingContainer(parameters.getFirst().typeName())
                && isSingleGeneric(returnType, STREAM)) {
            invocation = GrpcMethod.Invocation.SERVER_STREAMING_STREAM;
            requestType = parameters.getFirst().typeName();
            responseType = returnType.typeArguments().getFirst();
            methodParameters = List.of(new MethodParameter(requestType, "request"));
        } else if (methodType == GrpcMethodType.CLIENT_STREAMING
                && parameters.size() == 1
                && isSingleGeneric(parameters.getFirst().typeName(), ITERABLE)
                && !isStreamingContainer(returnType)
                && !voidReturn) {
            invocation = GrpcMethod.Invocation.CLIENT_STREAMING_ITERABLE;
            requestType = parameters.getFirst().typeName().typeArguments().getFirst();
            responseType = returnType;
            methodParameters = List.of(new MethodParameter(parameters.getFirst().typeName(), "requests"));
        } else if (methodType == GrpcMethodType.CLIENT_STREAMING
                && parameters.size() == 1
                && isSingleGeneric(parameters.getFirst().typeName(), STREAM)
                && !isStreamingContainer(returnType)
                && !voidReturn) {
            invocation = GrpcMethod.Invocation.CLIENT_STREAMING_STREAM;
            requestType = parameters.getFirst().typeName().typeArguments().getFirst();
            responseType = returnType;
            methodParameters = List.of(new MethodParameter(parameters.getFirst().typeName(), "requests"));
        } else if (methodType == GrpcMethodType.BIDI_STREAMING
                && parameters.size() == 1
                && isSingleGeneric(parameters.getFirst().typeName(), ITERABLE)
                && isSingleGeneric(returnType, ITERABLE)) {
            invocation = GrpcMethod.Invocation.BIDI_ITERABLE;
            requestType = parameters.getFirst().typeName().typeArguments().getFirst();
            responseType = returnType.typeArguments().getFirst();
            methodParameters = List.of(new MethodParameter(parameters.getFirst().typeName(), "requests"));
        } else if (methodType == GrpcMethodType.BIDI_STREAMING
                && parameters.size() == 1
                && isSingleGeneric(parameters.getFirst().typeName(), STREAM)
                && isSingleGeneric(returnType, STREAM)) {
            invocation = GrpcMethod.Invocation.BIDI_STREAM;
            requestType = parameters.getFirst().typeName().typeArguments().getFirst();
            responseType = returnType.typeArguments().getFirst();
            methodParameters = List.of(new MethodParameter(parameters.getFirst().typeName(), "requests"));
        } else if ((methodType == GrpcMethodType.CLIENT_STREAMING || methodType == GrpcMethodType.BIDI_STREAMING)
                && parameters.size() == 1
                && isSingleGeneric(parameters.getFirst().typeName(), STREAM_OBSERVER)
                && isSingleGeneric(returnType, STREAM_OBSERVER)) {
            invocation = methodType == GrpcMethodType.CLIENT_STREAMING
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
                                               + "must be Iterable<Res> method(Req), Stream<Res> method(Req), or "
                                               + "void method(Req, StreamObserver<Res>); client streaming methods "
                                               + "must be Res method(Iterable<Req>), Res method(Stream<Req>), or "
                                               + "StreamObserver<Req> method(StreamObserver<Res>); bidirectional streaming "
                                               + "methods must be Iterable<Res> method(Iterable<Req>), "
                                               + "Stream<Res> method(Stream<Req>), or "
                                               + "StreamObserver<Req> method(StreamObserver<Res>).",
                                       method.originatingElementValue());
        }
        return Optional.of(new GrpcMethod(method,
                                          methodName(method, annotations),
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
        constructor.addContentLine("var declarative__proto = proto();");
        for (GrpcMethod method : endpoint.methods()) {
            boolean clientStreaming = method.invocation() == GrpcMethod.Invocation.CLIENT_STREAMING_ITERABLE
                    || method.invocation() == GrpcMethod.Invocation.CLIENT_STREAMING_STREAM
                    || method.invocation() == GrpcMethod.Invocation.CLIENT_STREAMING_OBSERVER
                    || method.invocation() == GrpcMethod.Invocation.BIDI_ITERABLE
                    || method.invocation() == GrpcMethod.Invocation.BIDI_STREAM
                    || method.invocation() == GrpcMethod.Invocation.BIDI_OBSERVER;
            boolean serverStreaming = method.invocation() == GrpcMethod.Invocation.SERVER_STREAMING_ITERABLE
                    || method.invocation() == GrpcMethod.Invocation.SERVER_STREAMING_STREAM
                    || method.invocation() == GrpcMethod.Invocation.SERVER_STREAMING_OBSERVER
                    || method.invocation() == GrpcMethod.Invocation.BIDI_ITERABLE
                    || method.invocation() == GrpcMethod.Invocation.BIDI_STREAM
                    || method.invocation() == GrpcMethod.Invocation.BIDI_OBSERVER;
            constructor.addContent("validateProtoMethod(declarative__proto, ")
                    .addContentLiteral(endpoint.serviceName())
                    .addContent(", ")
                    .addContentLiteral(method.methodName())
                    .addContent(", ")
                    .addContent(String.valueOf(clientStreaming))
                    .addContent(", ")
                    .addContent(String.valueOf(serverStreaming))
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
                    .addContent(method.invocation().descriptorFactory())
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
                .update(it -> DelcarativeConfigSupport.assignResolveExpression(it, "config", "uri", endpoint.uri()))
                .addContent("var declarative__clientConfig = config.get(")
                .addContentLiteral(endpoint.configKey())
                .addContentLine(").get(\"client\");")
                .addContent(GRPC_CLIENT)
                .addContentLine(" declarative__client = null;")
                .addContentLine("if (!declarative__clientConfig.exists()) {")
                .increaseContentPadding()
                .addContentLine("declarative__client = registryClient.get().orElse(null);")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("if (declarative__client == null) {")
                .increaseContentPadding()
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

    private static void addProtoMethod(ClassModel.Builder classModel, GrpcEndpoint endpoint) {
        TypeName endpointType = endpoint.type().typeName();
        GrpcProtoDescriptor protoDescriptor = endpoint.protoDescriptor();
        classModel.addMethod(proto -> {
            proto.accessModifier(AccessModifier.PRIVATE)
                    .returnType(PROTO_FILE_DESCRIPTOR)
                    .name("proto");
            if (protoDescriptor.descriptorType().isPresent()) {
                proto.addContent("return ")
                        .addContent(protoDescriptor.descriptorType().orElseThrow())
                        .addContentLine(".getDescriptor();");
            } else {
                proto.addContent("return ")
                        .addContent(endpointType)
                        .addContent(".")
                        .addContent(protoDescriptor.method().orElseThrow().elementName())
                        .addContentLine("();");
            }
        });
    }

    private static void addValidateProtoMethod(ClassModel.Builder classModel) {
        classModel.addMethod(method -> method
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .returnType(TypeNames.PRIMITIVE_VOID)
                .name("validateProtoMethod")
                .addParameter(PROTO_FILE_DESCRIPTOR, "proto")
                .addParameter(TypeNames.STRING, "serviceName")
                .addParameter(TypeNames.STRING, "methodName")
                .addParameter(TypeNames.PRIMITIVE_BOOLEAN, "clientStreaming")
                .addParameter(TypeNames.PRIMITIVE_BOOLEAN, "serverStreaming")
                .addContentLine("var protoPackage = proto.getPackage();")
                .addContentLine("var protoServiceName = serviceName;")
                .addContentLine("if (!protoPackage.isEmpty() && protoServiceName.startsWith(protoPackage + \".\")) {")
                .increaseContentPadding()
                .addContentLine("protoServiceName = protoServiceName.substring(protoPackage.length() + 1);")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("var service = proto.findServiceByName(protoServiceName);")
                .addContentLine("if (service == null) {")
                .increaseContentPadding()
                .addContentLine("throw new IllegalArgumentException(\"Unable to find gRPC service \" + serviceName")
                .increaseContentPadding()
                .addContentLine("+ \" in proto descriptor.\");")
                .decreaseContentPadding()
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("var method = service.findMethodByName(methodName);")
                .addContentLine("if (method == null) {")
                .increaseContentPadding()
                .addContentLine("throw new IllegalArgumentException(\"Unable to find gRPC method \" + serviceName")
                .increaseContentPadding()
                .addContentLine("+ \"/\" + methodName + \" in proto descriptor.\");")
                .decreaseContentPadding()
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("if (method.isClientStreaming() != clientStreaming")
                .increaseContentPadding()
                .addContentLine("|| method.isServerStreaming() != serverStreaming) {")
                .decreaseContentPadding()
                .increaseContentPadding()
                .addContentLine("throw new IllegalArgumentException(\"Declarative gRPC method \" + serviceName")
                .increaseContentPadding()
                .addContentLine("+ \"/\" + methodName + \" is configured as \"")
                .addContentLine("+ methodType(clientStreaming, serverStreaming)")
                .addContentLine("+ \" but proto declares \"")
                .addContentLine("+ methodType(method.isClientStreaming(), method.isServerStreaming()) + \".\");")
                .decreaseContentPadding()
                .decreaseContentPadding()
                .addContentLine("}"));
    }

    private static void addMethodTypeMethod(ClassModel.Builder classModel) {
        classModel.addMethod(method -> method
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .returnType(TypeNames.STRING)
                .name("methodType")
                .addParameter(TypeNames.PRIMITIVE_BOOLEAN, "clientStreaming")
                .addParameter(TypeNames.PRIMITIVE_BOOLEAN, "serverStreaming")
                .addContentLine("if (clientStreaming && serverStreaming) {")
                .increaseContentPadding()
                .addContentLine("return \"BIDI_STREAMING\";")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("if (clientStreaming) {")
                .increaseContentPadding()
                .addContentLine("return \"CLIENT_STREAMING\";")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("if (serverStreaming) {")
                .increaseContentPadding()
                .addContentLine("return \"SERVER_STREAMING\";")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("return \"UNARY\";"));
    }

    private void registryClientParameter(Parameter.Builder param, GrpcEndpoint endpoint) {
        if (endpoint.clientName().isPresent()) {
            param.addAnnotation(Annotation.create(SERVICE_ANNOTATION_NAMED, endpoint.clientName().get()));
        }
        TypeName optionalGrpcClient = TypeName.builder()
                .from(TypeNames.OPTIONAL)
                .addTypeArgument(GRPC_CLIENT)
                .build();
        param.type(TypeName.builder()
                           .from(TypeNames.SUPPLIER)
                           .addTypeArgument(optionalGrpcClient)
                           .build());
    }

    private GrpcMethodType methodType(TypedElementInfo method, Annotation grpcMethod) {
        String methodType = grpcMethod.stringValue()
                .orElseThrow(() -> new CodegenException("gRPC method annotation is missing method type",
                                                         method.originatingElementValue()));
        int lastDot = methodType.lastIndexOf('.');
        if (lastDot != -1) {
            methodType = methodType.substring(lastDot + 1);
        }
        return switch (methodType) {
        case "UNARY" -> GrpcMethodType.UNARY;
        case "SERVER_STREAMING" -> GrpcMethodType.SERVER_STREAMING;
        case "CLIENT_STREAMING" -> GrpcMethodType.CLIENT_STREAMING;
        case "BIDI_STREAMING" -> GrpcMethodType.BIDI_STREAMING;
        default -> throw new CodegenException("Declarative gRPC client supports only unary, server streaming, client "
                                                      + "streaming, and bidirectional streaming methods "
                                                      + "in this version. Method " + method.elementName()
                                                      + "() uses " + methodType + ".",
                                              method.originatingElementValue());
        };
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
                              Invocation invocation,
                              TypeName requestType,
                              TypeName responseType,
                              List<MethodParameter> parameters) {
        private enum Invocation {
            UNARY_RETURN("unary", "unary", true),
            UNARY_OBSERVER("unary", "unary", false),
            SERVER_STREAMING_ITERABLE("serverStreaming", "serverStream", true),
            SERVER_STREAMING_STREAM("serverStreaming", "serverStream", true),
            SERVER_STREAMING_OBSERVER("serverStreaming", "serverStream", false),
            CLIENT_STREAMING_ITERABLE("clientStreaming", "clientStream", true),
            CLIENT_STREAMING_STREAM("clientStreaming", "clientStream", true),
            CLIENT_STREAMING_OBSERVER("clientStreaming", "clientStream", true),
            BIDI_ITERABLE("bidirectional", "bidi", true),
            BIDI_STREAM("bidirectional", "bidiStream", true),
            BIDI_OBSERVER("bidirectional", "bidi", true);

            private final String descriptorFactory;
            private final String clientMethodName;
            private final boolean returnsValue;

            Invocation(String descriptorFactory, String clientMethodName, boolean returnsValue) {
                this.descriptorFactory = descriptorFactory;
                this.clientMethodName = clientMethodName;
                this.returnsValue = returnsValue;
            }

            private String descriptorFactory() {
                return descriptorFactory;
            }

            private String clientMethodName() {
                return clientMethodName;
            }

            private boolean returnsValue() {
                return returnsValue;
            }
        }
    }

    private enum GrpcMethodType {
        UNARY,
        SERVER_STREAMING,
        CLIENT_STREAMING,
        BIDI_STREAMING
    }

    private record MethodParameter(TypeName type, String name) {
    }

    private record MethodSignature(String name, List<TypeName> parameterTypes) {
        public static MethodSignature create(TypedElementInfo element) {
            return new MethodSignature(element.elementName(),
                                       element.parameterArguments()
                                               .stream()
                                               .map(TypedElementInfo::typeName)
                                               .toList());
        }
    }

    private record MethodOrigin(TypeInfo type, TypedElementInfo method) {
    }
}
