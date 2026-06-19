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
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;

import static io.helidon.declarative.codegen.DeclarativeTypes.CONFIG;
import static io.helidon.declarative.codegen.DeclarativeTypes.SINGLETON_ANNOTATION;
import static io.helidon.declarative.codegen.grpc.client.GrpcClientTypes.GRPC_CLIENT;
import static io.helidon.declarative.codegen.grpc.client.GrpcClientTypes.GRPC_CLIENT_ENDPOINT;
import static io.helidon.declarative.codegen.grpc.client.GrpcClientTypes.GRPC_CLIENT_METHOD_DESCRIPTOR;
import static io.helidon.declarative.codegen.grpc.client.GrpcClientTypes.GRPC_CLIENT_QUALIFIER_INSTANCE;
import static io.helidon.declarative.codegen.grpc.client.GrpcClientTypes.GRPC_METHOD;
import static io.helidon.declarative.codegen.grpc.client.GrpcClientTypes.GRPC_SERVICE;
import static io.helidon.declarative.codegen.grpc.client.GrpcClientTypes.GRPC_SERVICE_CLIENT;
import static io.helidon.declarative.codegen.grpc.client.GrpcClientTypes.GRPC_SERVICE_DESCRIPTOR;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_NAMED;
import static java.util.function.Predicate.not;

class GrpcClientExtension implements RegistryCodegenExtension {
    static final TypeName GENERATOR = TypeName.create(GrpcClientExtension.class);

    private static final TypeName ITERATOR = TypeName.create("java.util.Iterator");
    private static final TypeName STREAM_OBSERVER = TypeName.create("io.grpc.stub.StreamObserver");

    private final RegistryCodegenContext ctx;

    GrpcClientExtension(RegistryCodegenContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void process(RegistryRoundContext roundContext) {
        Collection<TypeInfo> clientApis = roundContext.annotatedTypes(GRPC_CLIENT_ENDPOINT);

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
                    .addAnnotation(GRPC_CLIENT_QUALIFIER_INSTANCE);

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

            roundContext.addGeneratedType(generatedType, classModel, type.typeName(), type.originatingElementValue());
        }
    }

    private GrpcEndpoint toEndpoint(TypeInfo typeInfo) {
        if (typeInfo.kind() != ElementKind.INTERFACE) {
            throw new CodegenException("Types annotated with "
                                               + GRPC_CLIENT_ENDPOINT.classNameWithEnclosingNames()
                                               + " must be interfaces. This type is: " + typeInfo.kind(),
                                       typeInfo.originatingElementValue());
        }

        Set<Annotation> typeAnnotations = new HashSet<>(TypeHierarchy.hierarchyAnnotations(ctx, typeInfo));
        Annotation endpointAnnotation = Annotations.findFirst(GRPC_CLIENT_ENDPOINT, typeAnnotations)
                .orElseThrow(() -> new CodegenException("Missing " + GRPC_CLIENT_ENDPOINT.fqName(),
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
        return new GrpcEndpoint(typeInfo, uri, serviceName, configKey, clientName, List.copyOf(methods));
    }

    private Optional<GrpcMethod> toClientMethod(TypeInfo typeInfo, TypedElementInfo method) {
        List<Annotation> grpcMethodAnnotations = explicitGrpcMethodAnnotations(method);
        if (grpcMethodAnnotations.size() > 1) {
            throw new CodegenException("Declarative gRPC client method "
                                               + typeInfo.typeName().fqName() + "." + method.elementName()
                                               + "() must declare exactly one gRPC method annotation.",
                                       method.originatingElementValue());
        }

        List<Annotation> annotations = TypeHierarchy.hierarchyAnnotations(ctx, typeInfo, method);
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
                && isSingleGeneric(returnType, ITERATOR)) {
            invocation = GrpcMethod.Invocation.SERVER_STREAMING_ITERATOR;
            requestType = parameters.getFirst().typeName();
            responseType = returnType.typeArguments().getFirst();
            methodParameters = List.of(new MethodParameter(requestType, "request"));
        } else if (methodType == GrpcMethodType.CLIENT_STREAMING
                && parameters.size() == 1
                && isSingleGeneric(parameters.getFirst().typeName(), ITERATOR)
                && !isStreamingContainer(returnType)
                && !voidReturn) {
            invocation = GrpcMethod.Invocation.CLIENT_STREAMING_ITERATOR;
            requestType = parameters.getFirst().typeName().typeArguments().getFirst();
            responseType = returnType;
            methodParameters = List.of(new MethodParameter(parameters.getFirst().typeName(), "requests"));
        } else if (methodType == GrpcMethodType.BIDI_STREAMING
                && parameters.size() == 1
                && isSingleGeneric(parameters.getFirst().typeName(), ITERATOR)
                && isSingleGeneric(returnType, ITERATOR)) {
            invocation = GrpcMethod.Invocation.BIDI_ITERATOR;
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
                                               + "must be Iterator<Res> method(Req) or "
                                               + "void method(Req, StreamObserver<Res>); client streaming methods "
                                               + "must be Res method(Iterator<Req>) or StreamObserver<Req> "
                                               + "method(StreamObserver<Res>); bidirectional streaming methods "
                                               + "must be Iterator<Res> method(Iterator<Req>) or "
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
        return genericType.equals(ITERATOR) || genericType.equals(STREAM_OBSERVER);
    }

    private void constructorBody(Constructor.Builder constructor, GrpcEndpoint endpoint) {
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
                .addContentLine("if (uri.startsWith(\"http://\")) {")
                .increaseContentPadding()
                .addContentLine("declarative__clientBuilder.tls(it -> it.enabled(false));")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("declarative__client = declarative__clientBuilder.build();")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("this.serviceClient = declarative__client.serviceClient(declarative__descriptor);");
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

    private static List<Annotation> explicitGrpcMethodAnnotations(TypedElementInfo method) {
        return method.annotations()
                .stream()
                .filter(it -> it.typeName().equals(GRPC_METHOD) || it.hasMetaAnnotation(GRPC_METHOD))
                .toList();
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
            SERVER_STREAMING_ITERATOR("serverStreaming", "serverStream", true),
            SERVER_STREAMING_OBSERVER("serverStreaming", "serverStream", false),
            CLIENT_STREAMING_ITERATOR("clientStreaming", "clientStream", true),
            CLIENT_STREAMING_OBSERVER("clientStreaming", "clientStream", true),
            BIDI_ITERATOR("bidirectional", "bidi", true),
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
