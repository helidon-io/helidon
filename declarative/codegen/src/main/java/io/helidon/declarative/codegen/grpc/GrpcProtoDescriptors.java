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

package io.helidon.declarative.codegen.grpc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.TypeHierarchy;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ElementSignature;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.codegen.RegistryCodegenContext;

/**
 * Utilities for declarative gRPC proto descriptors.
 */
public final class GrpcProtoDescriptors {
    private static final TypeName PROTO_MESSAGE = TypeName.create("com.google.protobuf.Message");

    private GrpcProtoDescriptors() {
    }

    /**
     * Find and validate the proto descriptor source declared by a declarative gRPC type.
     *
     * @param ctx codegen context
     * @param typeInfo type to inspect
     * @param typeAnnotations annotations on the type
     * @param protoAnnotation {@code @Grpc.Proto} type
     * @param protoDescriptorAnnotation {@code @Grpc.ProtoDescriptor} type
     * @param grpcMethodAnnotation gRPC method meta-annotation type
     * @param protoFileDescriptor protobuf file descriptor type
     * @param declarationType user-facing declaration type
     * @return proto descriptor source
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static GrpcProtoDescriptor find(RegistryCodegenContext ctx,
                                           TypeInfo typeInfo,
                                           List<Annotation> typeAnnotations,
                                           TypeName protoAnnotation,
                                           TypeName protoDescriptorAnnotation,
                                           TypeName grpcMethodAnnotation,
                                           TypeName protoFileDescriptor,
                                           String declarationType) {
        Optional<Annotation> maybeProtoDescriptor = Annotations.findFirst(protoDescriptorAnnotation, typeAnnotations);
        Optional<GrpcProtoDescriptor> maybeProtoMethod = findProtoMethod(ctx,
                                                                         typeInfo,
                                                                         maybeProtoDescriptor.isPresent(),
                                                                         protoAnnotation,
                                                                         grpcMethodAnnotation,
                                                                         protoFileDescriptor,
                                                                         declarationType);
        if (maybeProtoDescriptor.isPresent() == maybeProtoMethod.isPresent()) {
            throw invalidSource(typeInfo, declarationType);
        }
        if (maybeProtoDescriptor.isEmpty()) {
            return maybeProtoMethod.orElseThrow();
        }

        TypeName descriptorType = maybeProtoDescriptor.orElseThrow().typeValue()
                .orElseThrow(() -> new CodegenException("@Grpc.ProtoDescriptor on "
                                                                + typeInfo.typeName().fqName()
                                                                + " must define a descriptor type.",
                                                        typeInfo.originatingElementValue()));
        validateDescriptorType(ctx, typeInfo, descriptorType, protoFileDescriptor);
        return GrpcProtoDescriptor.descriptorType(descriptorType);
    }

    /**
     * Validate that a declared request or response type is a generated protobuf message type.
     *
     * @param ctx codegen context
     * @param declaration declarative gRPC declaration
     * @param messageType request or response type
     * @param protoMessageDescriptor protobuf message descriptor type
     * @param role request or response
     */
    public static void validateMessageType(RegistryCodegenContext ctx,
                                           TypeInfo declaration,
                                           TypeName messageType,
                                           TypeName protoMessageDescriptor,
                                           String role) {
        TypeInfo messageTypeInfo = ctx.typeInfo(messageType)
                .orElseThrow(() -> invalidMessageType(declaration, messageType, protoMessageDescriptor, role));
        List<TypedElementInfo> descriptorMethods = messageTypeInfo.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(it -> "getDescriptor".equals(it.elementName()))
                .toList();
        if (descriptorMethods.size() != 1) {
            throw invalidMessageType(declaration, messageType, protoMessageDescriptor, role);
        }
        TypedElementInfo descriptorMethod = descriptorMethods.getFirst();
        if (!ElementInfoPredicates.isPublic(descriptorMethod)
                || !ElementInfoPredicates.isStatic(descriptorMethod)
                || !descriptorMethod.parameterArguments().isEmpty()
                || !descriptorMethod.typeName().equals(protoMessageDescriptor)) {
            throw invalidMessageType(declaration, messageType, protoMessageDescriptor, role);
        }
        List<TypedElementInfo> defaultInstanceMethods = messageTypeInfo.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(it -> "getDefaultInstance".equals(it.elementName()))
                .toList();
        if (messageTypeInfo.findInHierarchy(PROTO_MESSAGE).isEmpty() || defaultInstanceMethods.size() != 1) {
            throw invalidMessageType(declaration, messageType, protoMessageDescriptor, role);
        }
        TypedElementInfo defaultInstanceMethod = defaultInstanceMethods.getFirst();
        if (!ElementInfoPredicates.isPublic(defaultInstanceMethod)
                || !ElementInfoPredicates.isStatic(defaultInstanceMethod)
                || !defaultInstanceMethod.parameterArguments().isEmpty()
                || !defaultInstanceMethod.typeName().equals(messageType)) {
            throw invalidMessageType(declaration, messageType, protoMessageDescriptor, role);
        }
    }

    /**
     * Add the generated runtime proto method validation.
     *
     * @param classModel generated class model
     * @param protoFileDescriptor protobuf file descriptor type
     * @param protoMessageDescriptor protobuf message descriptor type
     */
    public static void addRuntimeValidationMethod(ClassModel.Builder classModel,
                                                  TypeName protoFileDescriptor,
                                                  TypeName protoMessageDescriptor) {
        classModel.addMethod(method -> method
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .returnType(TypeNames.PRIMITIVE_VOID)
                .name("validateProtoMethod")
                .addParameter(protoFileDescriptor, "proto")
                .addParameter(TypeNames.STRING, "serviceName")
                .addParameter(TypeNames.STRING, "methodName")
                .addParameter(TypeNames.PRIMITIVE_BOOLEAN, "clientStreaming")
                .addParameter(TypeNames.PRIMITIVE_BOOLEAN, "serverStreaming")
                .addParameter(TypeNames.STRING, "requestJavaType")
                .addParameter(protoMessageDescriptor, "requestType")
                .addParameter(TypeNames.STRING, "responseJavaType")
                .addParameter(protoMessageDescriptor, "responseType")
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
                .addContentLine("if (!service.getFullName().equals(serviceName)) {")
                .increaseContentPadding()
                .addContentLine("throw new IllegalArgumentException(\"Declarative gRPC service name \" + serviceName")
                .increaseContentPadding()
                .addContentLine("+ \" must match proto service \" + service.getFullName() + \".\");")
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
                .addContentLine("}")
                .addContentLine("if (!method.getInputType().equals(requestType)) {")
                .increaseContentPadding()
                .addContentLine("throw new IllegalArgumentException(\"Declarative gRPC request type \" + requestJavaType")
                .increaseContentPadding()
                .addContentLine("+ \" for \" + serviceName + \"/\" + methodName")
                .addContentLine("+ \" declares proto message \" + requestType.getFullName()")
                .addContentLine("+ \" but the method expects \" + method.getInputType().getFullName() + \".\");")
                .decreaseContentPadding()
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("if (!method.getOutputType().equals(responseType)) {")
                .increaseContentPadding()
                .addContentLine("throw new IllegalArgumentException(\"Declarative gRPC response type \" + responseJavaType")
                .increaseContentPadding()
                .addContentLine("+ \" for \" + serviceName + \"/\" + methodName")
                .addContentLine("+ \" declares proto message \" + responseType.getFullName()")
                .addContentLine("+ \" but the method expects \" + method.getOutputType().getFullName() + \".\");")
                .decreaseContentPadding()
                .decreaseContentPadding()
                .addContentLine("}"));
    }

    /**
     * Add the generated runtime method cardinality formatter.
     *
     * @param classModel generated class model
     */
    public static void addRuntimeMethodTypeMethod(ClassModel.Builder classModel) {
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

    /**
     * Read and validate a declarative gRPC method cardinality.
     *
     * @param method annotated method
     * @param grpcMethod gRPC method annotation
     * @param declarationType client or server
     * @return method cardinality
     */
    public static MethodType methodType(TypedElementInfo method, Annotation grpcMethod, String declarationType) {
        String methodType = grpcMethod.stringValue()
                .orElseThrow(() -> new CodegenException("gRPC method annotation is missing method type",
                                                         method.originatingElementValue()));
        int lastDot = methodType.lastIndexOf('.');
        if (lastDot != -1) {
            methodType = methodType.substring(lastDot + 1);
        }
        try {
            return MethodType.valueOf(methodType);
        } catch (IllegalArgumentException e) {
            throw new CodegenException("Declarative gRPC " + declarationType + " supports only unary, server streaming, "
                                               + "client streaming, and bidirectional streaming methods in this version. "
                                               + "Method " + method.elementName() + "() uses " + methodType + ".",
                                       method.originatingElementValue());
        }
    }

    /**
     * Declarative gRPC method cardinality shared by client and server code generation.
     */
    public enum MethodType {
        /** Unary call. */
        UNARY(false, false, "unary"),
        /** Server-streaming call. */
        SERVER_STREAMING(false, true, "serverStreaming"),
        /** Client-streaming call. */
        CLIENT_STREAMING(true, false, "clientStreaming"),
        /** Bidirectional-streaming call. */
        BIDI_STREAMING(true, true, "bidirectional");

        private final boolean clientStreaming;
        private final boolean serverStreaming;
        private final String registrationMethod;

        MethodType(boolean clientStreaming, boolean serverStreaming, String registrationMethod) {
            this.clientStreaming = clientStreaming;
            this.serverStreaming = serverStreaming;
            this.registrationMethod = registrationMethod;
        }

        /**
         * Whether the request is streaming.
         *
         * @return whether the request is streaming
         */
        public boolean clientStreaming() {
            return clientStreaming;
        }

        /**
         * Whether the response is streaming.
         *
         * @return whether the response is streaming
         */
        public boolean serverStreaming() {
            return serverStreaming;
        }

        /**
         * Generated routing registration method.
         *
         * @return registration method
         */
        public String registrationMethod() {
            return registrationMethod;
        }
    }

    private static Optional<GrpcProtoDescriptor> findProtoMethod(RegistryCodegenContext ctx,
                                                                TypeInfo typeInfo,
                                                                boolean hasProtoDescriptor,
                                                                TypeName protoAnnotation,
                                                                TypeName grpcMethodAnnotation,
                                                                TypeName protoFileDescriptor,
                                                                String declarationType) {
        Map<ElementSignature, ProtoMethod> protoMethods = new LinkedHashMap<>();
        Set<ElementSignature> declaredSignatures = new HashSet<>();
        Set<ElementSignature> classSignatures = new HashSet<>();
        for (TypedElementInfo method : typeInfo.elementInfo()) {
            if (!ElementInfoPredicates.isMethod(method)) {
                continue;
            }
            if (!ElementInfoPredicates.isPrivate(method)) {
                classSignatures.add(method.signature());
                if (!ElementInfoPredicates.isStatic(method)) {
                    declaredSignatures.add(method.signature());
                }
            }
            List<Annotation> annotations = TypeHierarchy.hierarchyAnnotations(ctx, typeInfo, method);
            if (Annotations.findFirst(protoAnnotation, annotations).isPresent()) {
                protoMethods.put(method.signature(), new ProtoMethod(typeInfo, method));
            }
        }
        List<TypeInfo> inheritedInterfaces = new ArrayList<>(typeInfo.interfaceTypeInfo());
        Optional<TypeInfo> maybeSuperType = typeInfo.superTypeInfo();
        while (maybeSuperType.isPresent()) {
            TypeInfo superType = maybeSuperType.orElseThrow();
            for (TypedElementInfo method : superType.elementInfo()) {
                if (!ElementInfoPredicates.isMethod(method)
                        || ElementInfoPredicates.isPrivate(method)
                        || method.accessModifier() != AccessModifier.PUBLIC
                                && !superType.typeName().packageName().equals(typeInfo.typeName().packageName())
                        || !classSignatures.add(method.signature())) {
                    continue;
                }
                if (!ElementInfoPredicates.isStatic(method)) {
                    declaredSignatures.add(method.signature());
                }
                List<Annotation> annotations = TypeHierarchy.hierarchyAnnotations(ctx, superType, method);
                if (Annotations.findFirst(protoAnnotation, annotations).isPresent()) {
                    protoMethods.put(method.signature(), new ProtoMethod(superType, method));
                }
            }
            inheritedInterfaces.addAll(superType.interfaceTypeInfo());
            maybeSuperType = superType.superTypeInfo();
        }
        collectInheritedProtoMethods(ctx,
                                     inheritedInterfaces,
                                     protoAnnotation,
                                     new HashSet<>(),
                                     declaredSignatures,
                                     protoMethods);
        if (protoMethods.isEmpty()) {
            return Optional.empty();
        }
        if (protoMethods.size() > 1 || hasProtoDescriptor) {
            throw invalidSource(typeInfo, declarationType);
        }

        ProtoMethod protoMethod = protoMethods.values().iterator().next();
        TypedElementInfo method = protoMethod.method();
        if (ElementInfoPredicates.isPrivate(method)) {
            throw new CodegenException("@Grpc.Proto method on "
                                               + typeInfo.typeName().fqName()
                                               + " must not be private.",
                                       method.originatingElementValue());
        }
        if (!method.throwsChecked().isEmpty()) {
            throw new CodegenException("@Grpc.Proto method on "
                                               + typeInfo.typeName().fqName()
                                               + " must not declare checked exceptions.",
                                       method.originatingElementValue());
        }
        List<Annotation> annotations = TypeHierarchy.hierarchyAnnotations(ctx, protoMethod.declaringType(), method);
        boolean hasGrpcMethodAnnotation = annotations.stream()
                .anyMatch(it -> it.typeName().equals(grpcMethodAnnotation) || it.hasMetaAnnotation(grpcMethodAnnotation));
        if (hasGrpcMethodAnnotation) {
            throw new CodegenException("@Grpc.Proto method on "
                                               + typeInfo.typeName().fqName()
                                               + " must not declare a gRPC method annotation.",
                                       method.originatingElementValue());
        }
        if (!method.parameterArguments().isEmpty() || !method.typeName().equals(protoFileDescriptor)) {
            throw new CodegenException("@Grpc.Proto method on "
                                               + typeInfo.typeName().fqName()
                                               + " must return "
                                               + protoFileDescriptor.fqName()
                                               + " and have no parameters.",
                                       method.originatingElementValue());
        }
        return Optional.of(GrpcProtoDescriptor.method(method, ElementInfoPredicates.isStatic(method)));
    }

    private static void collectInheritedProtoMethods(RegistryCodegenContext ctx,
                                                     List<TypeInfo> interfaces,
                                                     TypeName protoAnnotation,
                                                     Set<TypeName> processedTypes,
                                                     Set<ElementSignature> declaredSignatures,
                                                     Map<ElementSignature, ProtoMethod> protoMethods) {
        for (TypeInfo interfaceInfo : interfaces) {
            if (!processedTypes.add(interfaceInfo.typeName())) {
                continue;
            }
            for (TypedElementInfo method : interfaceInfo.elementInfo()) {
                if (!ElementInfoPredicates.isMethod(method)
                        || ElementInfoPredicates.isStatic(method)
                        || ElementInfoPredicates.isPrivate(method)
                        || declaredSignatures.contains(method.signature())) {
                    continue;
                }
                List<Annotation> annotations = TypeHierarchy.hierarchyAnnotations(ctx, interfaceInfo, method);
                if (Annotations.findFirst(protoAnnotation, annotations).isPresent()) {
                    ProtoMethod candidate = new ProtoMethod(interfaceInfo, method);
                    ProtoMethod current = protoMethods.get(method.signature());
                    if (current == null
                            || interfaceInfo.findInHierarchy(current.declaringType().typeName()).isPresent()) {
                        protoMethods.put(method.signature(), candidate);
                    }
                }
            }
            collectInheritedProtoMethods(ctx,
                                         interfaceInfo.interfaceTypeInfo(),
                                         protoAnnotation,
                                         processedTypes,
                                         declaredSignatures,
                                         protoMethods);
        }
    }

    private static CodegenException invalidSource(TypeInfo typeInfo, String declarationType) {
        return new CodegenException("Declarative gRPC "
                                            + declarationType
                                            + " "
                                            + typeInfo.typeName().fqName()
                                            + " must declare exactly one proto descriptor source: either "
                                            + "@Grpc.ProtoDescriptor on the type or one @Grpc.Proto method.",
                                    typeInfo.originatingElementValue());
    }

    private static void validateDescriptorType(RegistryCodegenContext ctx,
                                               TypeInfo typeInfo,
                                               TypeName descriptorType,
                                               TypeName protoFileDescriptor) {
        TypeInfo descriptorTypeInfo = ctx.typeInfo(descriptorType)
                .orElseThrow(() -> new CodegenException("@Grpc.ProtoDescriptor on "
                                                                + typeInfo.typeName().fqName()
                                                                + " references "
                                                                + descriptorType.fqName()
                                                                + ", but that type is not available to code generation.",
                                                        typeInfo.originatingElementValue()));
        List<TypedElementInfo> descriptorMethods = descriptorTypeInfo.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(it -> "getDescriptor".equals(it.elementName()))
                .toList();
        if (descriptorMethods.size() != 1) {
            throw invalidDescriptorType(typeInfo, descriptorType, protoFileDescriptor);
        }
        TypedElementInfo method = descriptorMethods.getFirst();
        if (!ElementInfoPredicates.isPublic(method)
                || !ElementInfoPredicates.isStatic(method)
                || !method.parameterArguments().isEmpty()
                || !method.typeName().equals(protoFileDescriptor)) {
            throw invalidDescriptorType(typeInfo, descriptorType, protoFileDescriptor);
        }
    }

    private static CodegenException invalidDescriptorType(TypeInfo typeInfo,
                                                          TypeName descriptorType,
                                                          TypeName protoFileDescriptor) {
        return new CodegenException("@Grpc.ProtoDescriptor on "
                                            + typeInfo.typeName().fqName()
                                            + " must reference a type with a public static no-argument getDescriptor() "
                                            + "method returning "
                                            + protoFileDescriptor.fqName()
                                            + ": "
                                            + descriptorType.fqName(),
                                    typeInfo.originatingElementValue());
    }

    private static CodegenException invalidMessageType(TypeInfo declaration,
                                                       TypeName messageType,
                                                       TypeName protoMessageDescriptor,
                                                       String role) {
        return new CodegenException("Declarative gRPC "
                                            + role
                                            + " type "
                                            + messageType.fqName()
                                            + " on "
                                            + declaration.typeName().fqName()
                                            + " must declare a public static no-argument getDescriptor() method returning "
                                            + protoMessageDescriptor.fqName()
                                            + ", implement com.google.protobuf.Message, and declare a public static "
                                            + "no-argument getDefaultInstance() method returning "
                                            + messageType.fqName()
                                            + ".",
                                    declaration.originatingElementValue());
    }

    private record ProtoMethod(TypeInfo declaringType, TypedElementInfo method) {
    }
}
