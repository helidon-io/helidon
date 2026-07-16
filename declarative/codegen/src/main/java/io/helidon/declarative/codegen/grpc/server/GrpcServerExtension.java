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
import java.util.HashMap;
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
import io.helidon.declarative.codegen.grpc.GrpcMethodHierarchy;
import io.helidon.declarative.codegen.grpc.GrpcProtoDescriptor;
import io.helidon.declarative.codegen.grpc.GrpcProtoDescriptors;
import io.helidon.declarative.codegen.grpc.GrpcProtoDescriptors.MethodType;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;

import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.GRPC_METHOD;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.GRPC_PROTO;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.GRPC_PROTO_DESCRIPTOR;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.GRPC_SERVICE;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.PROTO_FILE_DESCRIPTOR;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.PROTO_MESSAGE_DESCRIPTOR;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.RPC_SERVER_ENDPOINT;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.RPC_SERVER_LISTENER;
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
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.SERVICE_PER_REQUEST;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.STREAM_OBSERVER;
import static java.util.function.Predicate.not;

class GrpcServerExtension implements RegistryCodegenExtension {
    static final TypeName GENERATOR = TypeName.create(GrpcServerExtension.class);
    private static final String AUDITED_DEFAULT_MESSAGE_FORMAT = "%3$s %1$s \"%2$s\" %5$s %6$s requested by %4$s";
    private static final String AUDITED_DEFAULT_OK_SEVERITY = "SUCCESS";
    private static final String AUDITED_DEFAULT_ERROR_SEVERITY = "FAILURE";
    private static final TypeName ITERABLE = TypeName.create("java.lang.Iterable");
    private static final TypeName STREAM = TypeName.create("java.util.stream.Stream");

    private final RegistryCodegenContext ctx;

    GrpcServerExtension(RegistryCodegenContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void process(RegistryRoundContext roundContext) {
        Collection<TypeInfo> serverEndpoints = roundContext.annotatedTypes(RPC_SERVER_ENDPOINT);

        for (TypeInfo serverEndpoint : serverEndpoints) {
            if (serverEndpoint.kind() == ElementKind.INTERFACE) {
                throw new CodegenException("Types annotated with "
                                                   + RPC_SERVER_ENDPOINT.classNameWithEnclosingNames()
                                                   + " must be classes. This type is: " + serverEndpoint.kind(),
                                           serverEndpoint.originatingElementValue());
            }
            GrpcServerRegistrationGenerator.generate(roundContext, ctx, toEndpoint(serverEndpoint));
        }
    }

    private GrpcEndpoint toEndpoint(TypeInfo serverEndpoint) {
        TypeName endpointType = serverEndpoint.typeName();
        if (serverEndpoint.hasAnnotation(SERVICE_PER_REQUEST)) {
            throw new CodegenException("Declarative gRPC service "
                                               + endpointType.fqName()
                                               + " cannot use @Service.PerRequest because gRPC calls do not participate in "
                                               + "WebServer HTTP request scope. Use @Service.Singleton or @Service.PerLookup.",
                                       serverEndpoint.originatingElementValue());
        }
        List<Annotation> typeAnnotations = effectiveSecurityAnnotations(TypeHierarchy.hierarchyAnnotations(ctx,
                                                                                                            serverEndpoint),
                                                                        serverEndpoint.annotations());
        GrpcProtoDescriptor protoDescriptor = GrpcProtoDescriptors.find(ctx,
                                                                        serverEndpoint,
                                                                        typeAnnotations,
                                                                        GRPC_PROTO,
                                                                        GRPC_PROTO_DESCRIPTOR,
                                                                        GRPC_METHOD,
                                                                        PROTO_FILE_DESCRIPTOR,
                                                                        "service");
        List<GrpcMethod> methods = new ArrayList<>();
        for (TypedElementInfo method : GrpcMethodHierarchy.methods(serverEndpoint)) {
            toGrpcMethod(serverEndpoint, method).ifPresent(methods::add);
        }
        if (methods.isEmpty()) {
            throw new CodegenException("Declarative gRPC service "
                                               + endpointType.fqName()
                                               + " must declare at least one gRPC method.",
                                       serverEndpoint.originatingElementValue());
        }
        var methodsByGrpcName = new HashMap<String, GrpcMethod>();
        for (GrpcMethod method : methods) {
            GrpcMethod previous = methodsByGrpcName.putIfAbsent(method.grpcName(), method);
            if (previous != null) {
                throw new CodegenException("Declarative gRPC service "
                                                   + endpointType.fqName()
                                                   + " maps both "
                                                   + previous.method().elementName()
                                                   + "() and "
                                                   + method.method().elementName()
                                                   + "() to wire method "
                                                   + method.grpcName()
                                                   + ".",
                                           method.method().originatingElementValue());
            }
        }
        Annotation serviceAnnotation = Annotations.findFirst(GRPC_SERVICE, typeAnnotations)
                .orElseThrow(() -> new CodegenException("Declarative gRPC service "
                                                                + endpointType.fqName()
                                                                + " must declare @Grpc.GrpcService.",
                                                        serverEndpoint.originatingElementValue()));
        return new GrpcEndpoint(serverEndpoint,
                                List.copyOf(typeAnnotations),
                                serviceAnnotation.stringValue()
                                        .filter(not(String::isBlank))
                                        .orElseThrow(() -> new CodegenException("Declarative gRPC service "
                                                                + endpointType.fqName()
                                                                + " must define a non-blank @Grpc.GrpcService value.",
                                                        serverEndpoint.originatingElementValue())),
                                Annotations.findFirst(RPC_SERVER_LISTENER, typeAnnotations)
                                        .flatMap(Annotation::stringValue)
                                        .filter(not(String::isBlank)),
                                protoDescriptor,
                                security(typeAnnotations,
                                         serverEndpoint.annotations(),
                                         serverEndpoint.typeName().fqName(),
                                         serverEndpoint.originatingElementValue()),
                                List.copyOf(methods));
    }

    private Optional<GrpcMethod> toGrpcMethod(TypeInfo serverEndpoint, TypedElementInfo method) {
        if (!ElementInfoPredicates.isMethod(method)
                || ElementInfoPredicates.isPrivate(method)
                || ElementInfoPredicates.isStatic(method)) {
            return Optional.empty();
        }

        List<Annotation> annotations = effectiveSecurityAnnotations(TypeHierarchy.hierarchyAnnotations(ctx,
                                                                                                        serverEndpoint,
                                                                                                        method),
                                                                    method.annotations());
        List<Annotation> grpcMethodAnnotations = grpcMethodAnnotations(method, annotations);
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

        MethodType methodType = GrpcProtoDescriptors.methodType(method, maybeGrpcMethod.get(), "server");
        GrpcMethod.Invocation invocation;
        TypeName requestType;
        TypeName responseType;
        List<TypedElementInfo> parameters = method.parameterArguments();
        if (methodType == MethodType.UNARY
                && parameters.size() == 1
                && !method.typeName().boxed().equals(TypeNames.BOXED_VOID)) {
            invocation = GrpcMethod.Invocation.UNARY_RETURN;
            requestType = parameters.getFirst().typeName();
            responseType = method.typeName();
        } else if ((methodType == MethodType.UNARY || methodType == MethodType.SERVER_STREAMING)
                && parameters.size() == 2
                && method.typeName().boxed().equals(TypeNames.BOXED_VOID)
                && parameters.get(1).typeName().genericTypeName().equals(STREAM_OBSERVER)
                && parameters.get(1).typeName().typeArguments().size() == 1) {
            invocation = GrpcMethod.Invocation.OBSERVER;
            requestType = parameters.getFirst().typeName();
            responseType = parameters.get(1).typeName().typeArguments().getFirst();
        } else if (methodType == MethodType.SERVER_STREAMING
                && parameters.size() == 1
                && !isStreamingContainer(parameters.getFirst().typeName())
                && isSingleGeneric(method.typeName(), STREAM)) {
            invocation = GrpcMethod.Invocation.SERVER_STREAMING_STREAM;
            requestType = parameters.getFirst().typeName();
            responseType = method.typeName().typeArguments().getFirst();
        } else if (methodType == MethodType.CLIENT_STREAMING
                && parameters.size() == 1
                && isSingleGeneric(parameters.getFirst().typeName(), STREAM)
                && !isStreamingContainer(method.typeName())
                && !method.typeName().boxed().equals(TypeNames.BOXED_VOID)) {
            invocation = GrpcMethod.Invocation.CLIENT_STREAMING_STREAM;
            requestType = parameters.getFirst().typeName().typeArguments().getFirst();
            responseType = method.typeName();
        } else if (methodType == MethodType.BIDI_STREAMING
                && parameters.size() == 1
                && isSingleGeneric(parameters.getFirst().typeName(), STREAM)
                && isSingleGeneric(method.typeName(), STREAM)) {
            invocation = GrpcMethod.Invocation.BIDI_STREAM;
            requestType = parameters.getFirst().typeName().typeArguments().getFirst();
            responseType = method.typeName().typeArguments().getFirst();
        } else if (methodType == MethodType.BIDI_STREAMING
                && parameters.size() == 1
                && parameters.getFirst().typeName().genericTypeName().equals(STREAM_OBSERVER)
                && parameters.getFirst().typeName().typeArguments().size() == 1
                && method.typeName().genericTypeName().equals(STREAM_OBSERVER)
                && method.typeName().typeArguments().size() == 1) {
            invocation = GrpcMethod.Invocation.BIDI_OBSERVER;
            requestType = method.typeName().typeArguments().getFirst();
            responseType = parameters.getFirst().typeName().typeArguments().getFirst();
        } else {
            throw new CodegenException("Unsupported declarative gRPC method signature for "
                                               + serverEndpoint.typeName().fqName() + "." + method.elementName()
                                               + "(). Unary methods must be Res method(Req) or "
                                               + "void method(Req, StreamObserver<Res>); server streaming "
                                               + "methods must be Stream<Res> method(Req) or "
                                               + "void method(Req, StreamObserver<Res>); client streaming methods "
                                               + "must be Res method(Stream<Req>); bidirectional streaming methods must be "
                                               + "Stream<Res> method(Stream<Req>) or "
                                               + "StreamObserver<Req> method(StreamObserver<Res>).",
                                       method.originatingElementValue());
        }
        GrpcProtoDescriptors.validateMessageType(ctx,
                                                  serverEndpoint,
                                                  requestType,
                                                  PROTO_MESSAGE_DESCRIPTOR,
                                                  "request");
        GrpcProtoDescriptors.validateMessageType(ctx,
                                                  serverEndpoint,
                                                  responseType,
                                                  PROTO_MESSAGE_DESCRIPTOR,
                                                  "response");
        TypedElementInfo effectiveMethod = TypedElementInfo.builder(method)
                .annotations(annotations)
                .inheritedAnnotations(List.of())
                .build();
        return Optional.of(new GrpcMethod(effectiveMethod,
                                          methodName(method, annotations),
                                          ctx.uniqueName(serverEndpoint, method),
                                          methodType,
                                          invocation,
                                          requestType,
                                          responseType,
                                          security(annotations,
                                                   method.annotations(),
                                                   serverEndpoint.typeName().fqName() + "." + method.elementName() + "()",
                                                   method.originatingElementValue())));
    }

    private static boolean isSingleGeneric(TypeName type, TypeName expectedGenericType) {
        return type.genericTypeName().equals(expectedGenericType) && type.typeArguments().size() == 1;
    }

    private static boolean isStreamingContainer(TypeName type) {
        TypeName genericType = type.genericTypeName();
        return genericType.equals(ITERABLE) || genericType.equals(STREAM) || genericType.equals(STREAM_OBSERVER);
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

    private static List<Annotation> effectiveSecurityAnnotations(List<Annotation> annotations,
                                                                 List<Annotation> directAnnotations) {
        boolean hasDirectPermitAll = Annotations.findFirst(SECURITY_PERMIT_ALL, directAnnotations).isPresent()
                || Annotations.findFirst(SECURITY_ROLE_PERMIT_ALL, directAnnotations).isPresent();
        if (hasDirectPermitAll) {
            return annotations;
        }

        boolean hasDirectAbacAnnotation = directAnnotations.stream()
                .anyMatch(it -> it.typeName().equals(SECURITY_ROLES)
                        || it.typeName().equals(SECURITY_ROLES_CONTAINER)
                        || it.hasMetaAnnotation(SECURITY_ABAC_ANNOTATION));
        if (!hasDirectAbacAnnotation) {
            return annotations;
        }

        return annotations.stream()
                .filter(it -> !it.typeName().equals(SECURITY_PERMIT_ALL))
                .filter(it -> !it.typeName().equals(SECURITY_ROLE_PERMIT_ALL))
                .toList();
    }

    private static GrpcSecurityDefinition security(List<Annotation> annotations,
                                                   List<Annotation> directAnnotations,
                                                   String target,
                                                   Object originatingElement) {
        Optional<Annotation> authenticated = Annotations.findFirst(SECURITY_AUTHENTICATED, annotations);
        Optional<Annotation> authorized = Annotations.findFirst(SECURITY_AUTHORIZED, annotations);
        Optional<Annotation> audited = Annotations.findFirst(SECURITY_AUDITED, annotations);
        Optional<Annotation> denyAll = Annotations.findFirst(SECURITY_DENY_ALL, annotations);
        Optional<Annotation> permitAll = Annotations.findFirst(SECURITY_PERMIT_ALL, annotations);
        Optional<Annotation> rolePermitAll = Annotations.findFirst(SECURITY_ROLE_PERMIT_ALL, annotations);
        Set<String> rolesAllowed = new LinkedHashSet<>();
        boolean hasRolesAllowed = false;
        boolean hasRoleValidatorRoles = false;
        for (Annotation annotation : annotations) {
            if (annotation.typeName().equals(SECURITY_ROLES_ALLOWED)) {
                hasRolesAllowed = true;
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
        boolean hasDirectAuthenticated = Annotations.findFirst(SECURITY_AUTHENTICATED, directAnnotations).isPresent();
        boolean hasDirectAuthorized = Annotations.findFirst(SECURITY_AUTHORIZED, directAnnotations).isPresent();
        boolean hasDirectPermitAll = Annotations.findFirst(SECURITY_PERMIT_ALL, directAnnotations).isPresent()
                || Annotations.findFirst(SECURITY_ROLE_PERMIT_ALL, directAnnotations).isPresent();
        boolean syntheticDenyAll = hasRolesAllowed && rolesAllowed.isEmpty();
        boolean hasDenyAll = denyAll.isPresent() || syntheticDenyAll;
        boolean securityLevel = authenticated.isPresent()
                || authorized.isPresent()
                || audited.isPresent()
                || hasDenyAll
                || hasPermitAll
                || hasRoleValidatorRoles
                || hasAbacAnnotation
                || hasRolesAllowed;

        if (!securityLevel) {
            return GrpcSecurityDefinition.empty();
        }

        Optional<Boolean> authenticate = authenticated.flatMap(Annotation::booleanValue);
        Optional<Boolean> authorize = authorized.flatMap(Annotation::booleanValue);
        Optional<String> authenticator = authenticated
                .flatMap(it -> it.stringValue("provider"))
                .filter(not(String::isBlank));
        Optional<String> authorizer = authorized
                .flatMap(it -> it.stringValue("provider"))
                .filter(not(String::isBlank));

        if (hasDenyAll) {
            authenticate = Optional.of(false);
            authorize = Optional.of(true);
        } else if (hasPermitAll) {
            if (hasDirectPermitAll || !hasDirectAuthenticated) {
                authenticate = Optional.of(false);
            }
            if (hasDirectPermitAll || !hasDirectAuthorized) {
                authorize = Optional.of(false);
            }
        } else if (hasRoleValidatorRoles || hasAbacAnnotation) {
            if (authenticate.isEmpty()) {
                authenticate = Optional.of(true);
            }
            if (authorize.isEmpty()) {
                authorize = Optional.of(true);
            }
        }

        return new GrpcSecurityDefinition(authenticate,
                                          authenticated.map(it -> it.booleanValue("optional").orElse(false)),
                                          authenticator,
                                          authenticated.isPresent() && authenticator.isEmpty(),
                                          authorize,
                                          authorizer,
                                          authorized.isPresent() && authorizer.isEmpty(),
                                          List.copyOf(rolesAllowed),
                                          hasDenyAll || hasPermitAll || hasRoleValidatorRoles,
                                          audited.map(_ -> true),
                                          audited.flatMap(Annotation::stringValue).filter(it -> !it.isBlank()),
                                          audited.flatMap(it -> it.stringValue("messageFormat"))
                                                  .filter(not(AUDITED_DEFAULT_MESSAGE_FORMAT::equals)),
                                          audited.flatMap(it -> it.stringValue("okSeverity"))
                                                  .filter(not(AUDITED_DEFAULT_OK_SEVERITY::equals)),
                                          audited.flatMap(it -> it.stringValue("errorSeverity"))
                                                  .filter(not(AUDITED_DEFAULT_ERROR_SEVERITY::equals)),
                                          syntheticDenyAll,
                                          true);
    }
}
