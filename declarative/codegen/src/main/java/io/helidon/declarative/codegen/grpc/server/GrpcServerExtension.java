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

package io.helidon.declarative.codegen.grpc.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.TypeHierarchy;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.declarative.codegen.DeclarativeUtils;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;

import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.GRPC_METHOD;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.GRPC_PROTO;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.GRPC_SERVICE;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.PROTO_FILE_DESCRIPTOR;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.SECURITY_ABAC_ANNOTATION;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.SECURITY_AUDITED;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.SECURITY_AUTHENTICATED;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.SECURITY_AUTHORIZED;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.SECURITY_DENY_ALL;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.SECURITY_PERMIT_ALL;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.SECURITY_ROLES;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.SECURITY_ROLES_ALLOWED;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.SECURITY_ROLES_CONTAINER;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.SECURITY_ROLE_PERMIT_ALL;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.STREAM_OBSERVER;

class GrpcServerExtension implements RegistryCodegenExtension {
    static final TypeName GENERATOR = TypeName.create(GrpcServerExtension.class);

    private final RegistryCodegenContext ctx;

    GrpcServerExtension(RegistryCodegenContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void process(RegistryRoundContext roundContext) {
        Collection<TypeInfo> serverEndpoints = roundContext.annotatedTypes(GRPC_SERVICE);

        for (TypeInfo serverEndpoint : serverEndpoints) {
            if (serverEndpoint.kind() == ElementKind.INTERFACE) {
                continue;
            }
            GrpcServerRegistrationGenerator.generate(roundContext, ctx, toEndpoint(serverEndpoint));
        }
    }

    private GrpcEndpoint toEndpoint(TypeInfo serverEndpoint) {
        TypeName endpointType = serverEndpoint.typeName();
        List<GrpcMethod> methods = new ArrayList<>();
        for (TypedElementInfo method : serverEndpoint.elementInfo()) {
            toGrpcMethod(serverEndpoint, method).ifPresent(methods::add);
        }
        if (methods.isEmpty()) {
            throw new CodegenException("Declarative gRPC service "
                                               + endpointType.fqName()
                                               + " must declare at least one gRPC method.",
                                       serverEndpoint.originatingElementValue());
        }

        GrpcProtoMethod protoMethod = findProtoMethod(serverEndpoint)
                .orElseThrow(() -> new CodegenException("Declarative gRPC service "
                                                                + endpointType.fqName()
                                                                + " must declare a @Grpc.Proto method returning "
                                                                + PROTO_FILE_DESCRIPTOR.fqName() + ".",
                                                        serverEndpoint.originatingElementValue()));
        List<Annotation> typeAnnotations = TypeHierarchy.hierarchyAnnotations(ctx, serverEndpoint);
        Annotation serviceAnnotation = serverEndpoint.findAnnotation(GRPC_SERVICE)
                .orElseThrow(() -> new CodegenException("Missing " + GRPC_SERVICE.fqName(),
                                                        serverEndpoint.originatingElementValue()));
        return new GrpcEndpoint(serverEndpoint,
                                serviceAnnotation.stringValue().orElse(""),
                                protoMethod,
                                security(typeAnnotations,
                                         serverEndpoint.typeName().fqName(),
                                         serverEndpoint.originatingElementValue()),
                                List.copyOf(methods));
    }

    private Optional<GrpcProtoMethod> findProtoMethod(TypeInfo serverEndpoint) {
        List<TypedElementInfo> protoMethods = new ArrayList<>();
        for (TypedElementInfo method : serverEndpoint.elementInfo()) {
            if (!ElementInfoPredicates.isMethod(method) || ElementInfoPredicates.isPrivate(method)) {
                continue;
            }
            List<Annotation> annotations = TypeHierarchy.hierarchyAnnotations(ctx, serverEndpoint, method);
            if (Annotations.findFirst(GRPC_PROTO, annotations).isEmpty()) {
                continue;
            }
            protoMethods.add(method);
        }
        if (protoMethods.isEmpty()) {
            return Optional.empty();
        }
        if (protoMethods.size() > 1) {
            throw new CodegenException("Declarative gRPC service "
                                               + serverEndpoint.typeName().fqName()
                                               + " must declare exactly one @Grpc.Proto method.",
                                       serverEndpoint.originatingElementValue());
        }
        TypedElementInfo method = protoMethods.getFirst();
        if (!explicitGrpcMethodAnnotations(method).isEmpty()) {
            throw new CodegenException("@Grpc.Proto method on "
                                               + serverEndpoint.typeName().fqName()
                                               + " must not declare a gRPC method annotation.",
                                       method.originatingElementValue());
        }
        if (!method.parameterArguments().isEmpty() || !method.typeName().equals(PROTO_FILE_DESCRIPTOR)) {
            throw new CodegenException("@Grpc.Proto method on "
                                               + serverEndpoint.typeName().fqName()
                                               + " must return "
                                               + PROTO_FILE_DESCRIPTOR.fqName()
                                               + " and have no parameters.",
                                       method.originatingElementValue());
        }
        return Optional.of(new GrpcProtoMethod(method, ElementInfoPredicates.isStatic(method)));
    }

    private Optional<GrpcMethod> toGrpcMethod(TypeInfo serverEndpoint, TypedElementInfo method) {
        if (!ElementInfoPredicates.isMethod(method)
                || ElementInfoPredicates.isPrivate(method)
                || ElementInfoPredicates.isStatic(method)) {
            return Optional.empty();
        }

        List<Annotation> annotations = TypeHierarchy.hierarchyAnnotations(ctx, serverEndpoint, method);
        if (Annotations.findFirst(GRPC_PROTO, annotations).isPresent()) {
            return Optional.empty();
        }

        List<Annotation> grpcMethodAnnotations = explicitGrpcMethodAnnotations(method);
        if (grpcMethodAnnotations.size() > 1) {
            throw new CodegenException("Declarative gRPC server method "
                                               + serverEndpoint.typeName().fqName() + "." + method.elementName()
                                               + "() must declare exactly one gRPC method annotation.",
                                       method.originatingElementValue());
        }
        Optional<Annotation> maybeGrpcMethod = DeclarativeUtils.findMetaAnnotated(annotations, GRPC_METHOD);
        if (maybeGrpcMethod.isEmpty()) {
            return Optional.empty();
        }
        if (!method.throwsChecked().isEmpty()) {
            throw new CodegenException("Declarative gRPC server methods must not declare checked exceptions: "
                                               + serverEndpoint.typeName().fqName() + "." + method.elementName()
                                               + "()",
                                       method.originatingElementValue());
        }

        String methodType = methodType(method, maybeGrpcMethod.get());
        GrpcMethod.Invocation invocation;
        TypeName requestType;
        TypeName responseType;
        List<TypedElementInfo> parameters = method.parameterArguments();
        if ("UNARY".equals(methodType)
                && parameters.size() == 1
                && !method.typeName().boxed().equals(TypeNames.BOXED_VOID)) {
            invocation = GrpcMethod.Invocation.UNARY_RETURN;
            requestType = parameters.getFirst().typeName();
            responseType = method.typeName();
        } else if (("UNARY".equals(methodType) || "SERVER_STREAMING".equals(methodType))
                && parameters.size() == 2
                && method.typeName().boxed().equals(TypeNames.BOXED_VOID)
                && parameters.get(1).typeName().genericTypeName().equals(STREAM_OBSERVER)
                && parameters.get(1).typeName().typeArguments().size() == 1) {
            invocation = GrpcMethod.Invocation.OBSERVER;
            requestType = parameters.getFirst().typeName();
            responseType = parameters.get(1).typeName().typeArguments().getFirst();
        } else if (("CLIENT_STREAMING".equals(methodType) || "BIDI_STREAMING".equals(methodType))
                && parameters.size() == 1
                && parameters.getFirst().typeName().genericTypeName().equals(STREAM_OBSERVER)
                && parameters.getFirst().typeName().typeArguments().size() == 1
                && method.typeName().genericTypeName().equals(STREAM_OBSERVER)
                && method.typeName().typeArguments().size() == 1) {
            invocation = GrpcMethod.Invocation.REQUEST_STREAMING;
            requestType = method.typeName().typeArguments().getFirst();
            responseType = parameters.getFirst().typeName().typeArguments().getFirst();
        } else {
            throw new CodegenException("Unsupported declarative gRPC method signature for "
                                               + serverEndpoint.typeName().fqName() + "." + method.elementName()
                                               + "(). Unary methods must be Res method(Req) or "
                                               + "void method(Req, StreamObserver<Res>); server streaming "
                                               + "methods must be void method(Req, StreamObserver<Res>); client and "
                                               + "bidirectional streaming methods must be StreamObserver<Req> "
                                               + "method(StreamObserver<Res>).",
                                       method.originatingElementValue());
        }
        return Optional.of(new GrpcMethod(method,
                                          methodName(method, annotations),
                                          ctx.uniqueName(serverEndpoint, method),
                                          methodType,
                                          invocation,
                                          requestType,
                                          responseType,
                                          security(annotations,
                                                   serverEndpoint.typeName().fqName() + "." + method.elementName() + "()",
                                                   method.originatingElementValue())));
    }

    private String methodType(TypedElementInfo method, Annotation grpcMethod) {
        String methodType = grpcMethod.stringValue()
                .orElseThrow(() -> new CodegenException("gRPC method annotation is missing method type",
                                                         method.originatingElementValue()));
        int lastDot = methodType.lastIndexOf('.');
        if (lastDot != -1) {
            methodType = methodType.substring(lastDot + 1);
        }
        if (!"UNARY".equals(methodType)
                && !"SERVER_STREAMING".equals(methodType)
                && !"CLIENT_STREAMING".equals(methodType)
                && !"BIDI_STREAMING".equals(methodType)) {
            throw new CodegenException("Declarative gRPC server supports only unary, server streaming, client "
                                               + "streaming, and bidirectional streaming methods "
                                               + "in this version. Method " + method.elementName()
                                               + "() uses " + methodType + ".",
                                       method.originatingElementValue());
        }
        return methodType;
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

    private static GrpcSecurityDefinition security(List<Annotation> annotations, String target, Object originatingElement) {
        Optional<Annotation> authenticated = Annotations.findFirst(SECURITY_AUTHENTICATED, annotations);
        Optional<Annotation> authorized = Annotations.findFirst(SECURITY_AUTHORIZED, annotations);
        Optional<Annotation> audited = Annotations.findFirst(SECURITY_AUDITED, annotations);
        Optional<Annotation> denyAll = Annotations.findFirst(SECURITY_DENY_ALL, annotations);
        Optional<Annotation> permitAll = Annotations.findFirst(SECURITY_PERMIT_ALL, annotations);
        Optional<Annotation> rolePermitAll = Annotations.findFirst(SECURITY_ROLE_PERMIT_ALL, annotations);
        Set<String> rolesAllowed = new LinkedHashSet<>();
        boolean hasRoleValidatorRoles = false;
        for (Annotation annotation : annotations) {
            if (annotation.typeName().equals(SECURITY_ROLES_ALLOWED)) {
                rolesAllowed.addAll(annotation.stringValues().orElseGet(List::of));
            } else if (annotation.typeName().equals(SECURITY_ROLES)
                    || annotation.typeName().equals(SECURITY_ROLES_CONTAINER)) {
                hasRoleValidatorRoles = true;
            }
        }
        boolean hasAbacAnnotation = annotations.stream()
                .anyMatch(it -> it.hasMetaAnnotation(SECURITY_ABAC_ANNOTATION));

        if (authorized.flatMap(it -> it.booleanValue("explicit")).orElse(false)) {
            throw new CodegenException("Declarative gRPC does not support @Authorized(explicit = true): " + target,
                                       originatingElement);
        }

        boolean hasPermitAll = permitAll.isPresent() || rolePermitAll.isPresent();
        boolean hasDenyAll = denyAll.isPresent();
        boolean securityLevel = authenticated.isPresent()
                || authorized.isPresent()
                || audited.isPresent()
                || hasDenyAll
                || hasPermitAll
                || hasRoleValidatorRoles
                || hasAbacAnnotation
                || !rolesAllowed.isEmpty();

        if (!securityLevel) {
            return GrpcSecurityDefinition.empty();
        }

        Optional<Boolean> authenticate = authenticated.flatMap(Annotation::booleanValue);
        Optional<Boolean> authorize = authorized.flatMap(Annotation::booleanValue);

        if (hasDenyAll || hasPermitAll) {
            authenticate = Optional.of(false);
            authorize = Optional.of(true);
        } else if (hasRoleValidatorRoles || hasAbacAnnotation) {
            if (authenticate.isEmpty()) {
                authenticate = Optional.of(true);
            }
            if (authorize.isEmpty()) {
                authorize = Optional.of(true);
            }
        }

        return new GrpcSecurityDefinition(authenticate,
                                          authenticated.flatMap(it -> it.booleanValue("optional")).orElse(false),
                                          authenticated.flatMap(it -> it.stringValue("provider")).filter(it -> !it.isBlank()),
                                          authorize,
                                          authorized.flatMap(it -> it.stringValue("provider")).filter(it -> !it.isBlank()),
                                          List.copyOf(rolesAllowed),
                                          audited.map(_ -> true),
                                          audited.flatMap(Annotation::stringValue).filter(it -> !it.isBlank()),
                                          audited.flatMap(it -> it.stringValue("messageFormat")).filter(it -> !it.isBlank()),
                                          true);
    }
}
