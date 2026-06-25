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
import java.util.List;
import java.util.Optional;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.TypeHierarchy;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.codegen.RegistryCodegenContext;

/**
 * Utilities for declarative gRPC proto descriptors.
 */
public final class GrpcProtoDescriptors {
    private GrpcProtoDescriptors() {
    }

    /**
     * Find and validate the proto descriptor source declared by a type.
     *
     * @param ctx codegen context
     * @param typeInfo type to inspect
     * @param typeAnnotations annotations on the type
     * @param protoAnnotation {@code @Grpc.Proto} type
     * @param protoDescriptorAnnotation {@code @Grpc.ProtoDescriptor} type
     * @param grpcMethodAnnotation gRPC method meta-annotation type
     * @param protoFileDescriptor protobuf file descriptor type
     * @param declarationType user-facing declaration type
     * @return proto descriptor source, or empty if none is declared
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static Optional<GrpcProtoDescriptor> find(RegistryCodegenContext ctx,
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
                                                                         protoAnnotation,
                                                                         grpcMethodAnnotation,
                                                                         protoFileDescriptor,
                                                                         declarationType);

        if (maybeProtoDescriptor.isPresent() && maybeProtoMethod.isPresent()) {
            throw exactlyOne(typeInfo, declarationType);
        }
        if (maybeProtoDescriptor.isEmpty()) {
            return maybeProtoMethod;
        }

        TypeName descriptorType = maybeProtoDescriptor.get()
                .typeValue()
                .orElseThrow(() -> new CodegenException("@Grpc.ProtoDescriptor on "
                                                                + typeInfo.typeName().fqName()
                                                                + " must define a descriptor type.",
                                                        typeInfo.originatingElementValue()));
        validateDescriptorType(ctx, typeInfo, descriptorType, protoFileDescriptor);
        return Optional.of(GrpcProtoDescriptor.descriptorType(descriptorType));
    }

    /**
     * Create an exception for missing or duplicate proto descriptor sources.
     *
     * @param typeInfo type with the invalid declaration
     * @param declarationType user-facing declaration type
     * @return codegen exception
     */
    public static CodegenException exactlyOne(TypeInfo typeInfo, String declarationType) {
        return new CodegenException("Declarative gRPC "
                                            + declarationType
                                            + " "
                                            + typeInfo.typeName().fqName()
                                            + " must declare exactly one proto descriptor source: either "
                                            + "@Grpc.ProtoDescriptor on the type or one @Grpc.Proto method.",
                                    typeInfo.originatingElementValue());
    }

    private static Optional<GrpcProtoDescriptor> findProtoMethod(RegistryCodegenContext ctx,
                                                                TypeInfo typeInfo,
                                                                TypeName protoAnnotation,
                                                                TypeName grpcMethodAnnotation,
                                                                TypeName protoFileDescriptor,
                                                                String declarationType) {
        List<TypedElementInfo> protoMethods = new ArrayList<>();
        for (TypedElementInfo method : typeInfo.elementInfo()) {
            if (!ElementInfoPredicates.isMethod(method) || ElementInfoPredicates.isPrivate(method)) {
                continue;
            }
            List<Annotation> annotations = TypeHierarchy.hierarchyAnnotations(ctx, typeInfo, method);
            if (Annotations.findFirst(protoAnnotation, annotations).isEmpty()) {
                continue;
            }
            protoMethods.add(method);
        }
        if (protoMethods.isEmpty()) {
            return Optional.empty();
        }
        if (protoMethods.size() > 1) {
            throw exactlyOne(typeInfo, declarationType);
        }
        TypedElementInfo method = protoMethods.getFirst();
        List<Annotation> annotations = TypeHierarchy.hierarchyAnnotations(ctx, typeInfo, method);
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
        List<TypedElementInfo> descriptorMethods = new ArrayList<>();
        for (TypedElementInfo method : descriptorTypeInfo.elementInfo()) {
            if (!ElementInfoPredicates.isMethod(method) || !"getDescriptor".equals(method.elementName())) {
                continue;
            }
            descriptorMethods.add(method);
        }
        if (descriptorMethods.size() != 1) {
            throw invalidDescriptorType(typeInfo, descriptorType, protoFileDescriptor);
        }
        TypedElementInfo method = descriptorMethods.getFirst();
        if (!ElementInfoPredicates.isStatic(method)
                || ElementInfoPredicates.isPrivate(method)
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
                                            + " must reference a type with a static no-argument getDescriptor() "
                                            + "method returning "
                                            + protoFileDescriptor.fqName()
                                            + ": "
                                            + descriptorType.fqName(),
                                    typeInfo.originatingElementValue());
    }
}
