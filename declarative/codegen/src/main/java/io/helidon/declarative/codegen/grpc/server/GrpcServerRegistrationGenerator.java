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

import java.util.Iterator;
import java.util.List;

import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.declarative.codegen.DeclarativeTypes;
import io.helidon.declarative.codegen.grpc.GrpcProtoDescriptor;
import io.helidon.declarative.codegen.grpc.GrpcProtoDescriptors;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.ServiceCodegenTypes;

import static io.helidon.codegen.CodegenUtil.toConstantName;
import static io.helidon.declarative.codegen.DeclarativeTypes.SINGLETON_ANNOTATION;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.AUDIT_SEVERITY;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.GRPC_ENTRY_POINTS;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.GRPC_ROUTE_REGISTRATION;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.GRPC_SECURITY;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.GRPC_SERVICE_DESCRIPTOR;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.GRPC_STREAMS;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.PROTO_FILE_DESCRIPTOR;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.PROTO_MESSAGE_DESCRIPTOR;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.SECURITY_DENY_ALL;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.SECURITY_LEVEL;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.STREAM_OBSERVER;

class GrpcServerRegistrationGenerator {
    private static final String AUDITED_DEFAULT_EVENT_TYPE = "request";
    private static final TypeName LIST_OF_ANNOTATIONS = TypeName.builder(TypeNames.LIST)
            .addTypeArgument(TypeNames.ANNOTATION)
            .build();

    private GrpcServerRegistrationGenerator() {
    }

    static void generate(RegistryRoundContext roundContext, RegistryCodegenContext ctx, GrpcEndpoint endpoint) {
        TypeName endpointType = endpoint.type().typeName();
        String classNameBase = endpointType.classNameWithEnclosingNames().replace('.', '_');
        TypeName generatedType = TypeName.builder()
                .packageName(endpointType.packageName())
                .className(classNameBase + "__GrpcRegistration")
                .build();
        TypeName endpointSupplier = TypeName.builder(TypeNames.SUPPLIER)
                .addTypeArgument(endpointType)
                .build();
        boolean singleton = endpoint.type().hasAnnotation(ServiceCodegenTypes.SERVICE_ANNOTATION_SINGLETON);
        TypeName endpointFieldType = singleton ? endpointType : endpointSupplier;

        ClassModel.Builder classModel = ClassModel.builder()
                .copyright(CodegenUtil.copyright(GrpcServerExtension.GENERATOR, endpointType, generatedType))
                .addAnnotation(CodegenUtil.generatedAnnotation(GrpcServerExtension.GENERATOR,
                                                               endpointType,
                                                               generatedType,
                                                               "1",
                                                               ""))
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .type(generatedType)
                .addAnnotation(SINGLETON_ANNOTATION)
                .addAnnotation(DeclarativeTypes.SUPPRESS_API)
                .addInterface(GRPC_ROUTE_REGISTRATION);

        classModel.addField(field -> field
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .type(endpointFieldType)
                .name("endpoint"));
        classModel.addField(field -> field
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .type(GRPC_SERVICE_DESCRIPTOR)
                .name("descriptor"));
        addClassAnnotationsField(classModel, endpoint);
        addMethodInfoFields(classModel, endpoint);
        classModel.addConstructor(constructor(ctx, endpoint, endpointFieldType));
        addDescriptorMethod(classModel);
        endpoint.listener().ifPresent(listener -> {
            classModel.addMethod(socket -> socket
                    .accessModifier(AccessModifier.PUBLIC)
                    .returnType(TypeNames.STRING)
                    .name("socket")
                    .addAnnotation(Annotations.OVERRIDE)
                    .addContent("return ")
                    .addContentLiteral(listener)
                    .addContentLine(";"));
            classModel.addMethod(socketRequired -> socketRequired
                    .accessModifier(AccessModifier.PUBLIC)
                    .returnType(TypeNames.PRIMITIVE_BOOLEAN)
                    .name("socketRequired")
                    .addAnnotation(Annotations.OVERRIDE)
                    .addContentLine("return true;"));
        });
        addProtoMethod(classModel, endpoint, singleton);
        GrpcProtoDescriptors.addRuntimeValidationMethod(classModel,
                                                        PROTO_FILE_DESCRIPTOR,
                                                        PROTO_MESSAGE_DESCRIPTOR);
        GrpcProtoDescriptors.addRuntimeMethodTypeMethod(classModel);
        addToStringMethod(classModel, endpoint);
        addHandlerMethods(classModel, endpoint, singleton);

        roundContext.addGeneratedType(generatedType, classModel, endpointType, endpoint.type().originatingElementValue());
    }

    private static Constructor.Builder constructor(RegistryCodegenContext ctx,
                                                   GrpcEndpoint endpoint,
                                                   TypeName endpointFieldType) {
        TypeName endpointType = endpoint.type().typeName();
        TypeName descriptorType = ctx.descriptorType(endpointType);
        var constructor = Constructor.builder()
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addParameter(endpointFieldType, "endpoint")
                .addParameter(GRPC_ENTRY_POINTS, "entryPoints")
                .addContentLine("this.endpoint = endpoint;")
                .addContent("var descriptor = ")
                .addContent(descriptorType)
                .addContentLine(".INSTANCE;")
                .addContentLine("var annotations = CLASS_ANNOTATIONS;")
                .addContentLine("var declarative__proto = proto();")
                .addContent("var builder = ")
                .addContent(GRPC_SERVICE_DESCRIPTOR)
                .addContent(".builder(")
                .addContent(endpointType)
                .addContent(".class, ")
                .addContentLiteral(endpoint.serviceName())
                .addContentLine(");");

        addSecurity(endpoint.security(), constructor, endpointType, null, GrpcSecurityDefinition.empty());
        if (!endpoint.security().isEmpty()) {
            constructor.addContentLine(".configure(builder);");
        }

        for (GrpcMethod method : endpoint.methods()) {
            boolean clientStreaming = method.methodType().clientStreaming();
            boolean serverStreaming = method.methodType().serverStreaming();
            String registrationMethod = method.methodType().registrationMethod();
            String constant = toConstantName("METHOD_" + method.uniqueName());
            constructor.addContent("validateProtoMethod(declarative__proto, ")
                    .addContentLiteral(endpoint.serviceName())
                    .addContent(", ")
                    .addContentLiteral(method.grpcName())
                    .addContent(", ")
                    .addContent(String.valueOf(clientStreaming))
                    .addContent(", ")
                    .addContent(String.valueOf(serverStreaming))
                    .addContent(", ")
                    .addContentLiteral(method.requestType().fqName())
                    .addContent(", ")
                    .addContent(method.requestType())
                    .addContent(".getDescriptor(), ")
                    .addContentLiteral(method.responseType().fqName())
                    .addContent(", ")
                    .addContent(method.responseType())
                    .addContent(".getDescriptor()")
                    .addContentLine(");")
                    .addContent("builder.")
                    .addContent(registrationMethod)
                    .addContent("(")
                    .addContentLiteral(method.grpcName())
                    .addContent(", this::")
                    .addContent(method.uniqueName())
                    .addContentLine(", rules -> {")
                    .increaseContentPadding()
                    .addContent("rules.requestType(")
                    .addContent(method.requestType())
                    .addContent(".class).responseType(")
                    .addContent(method.responseType())
                    .addContentLine(".class);")
                    .addContentLine("if (entryPoints.hasInterceptors()) {")
                    .increaseContentPadding()
                    .addContentLine("rules.intercept(")
                    .increaseContentPadding()
                    .addContentLine("entryPoints.interceptor(")
                    .increaseContentPadding()
                    .increaseContentPadding()
                    .addContentLine("descriptor,")
                    .addContentLine("descriptor.qualifiers(),")
                    .addContentLine("annotations,")
                    .addContent(constant)
                    .addContentLine("));")
                    .decreaseContentPadding()
                    .decreaseContentPadding()
                    .decreaseContentPadding()
                    .addContentLine("}");
            addSecurity(method.security(),
                        constructor,
                        endpointType,
                        method,
                        endpoint.security());
            if (!method.security().isEmpty()) {
                constructor.addContentLine(".configure(rules);");
            }
            constructor.decreaseContentPadding()
                    .addContentLine("});");
        }
        return constructor.addContentLine("this.descriptor = builder.proto(declarative__proto).build();");
    }

    private static void addMethodInfoFields(ClassModel.Builder classModel, GrpcEndpoint endpoint) {
        for (GrpcMethod grpcMethod : endpoint.methods()) {
            String constant = toConstantName("METHOD_" + grpcMethod.uniqueName());
            classModel.addField(field -> field
                    .accessModifier(AccessModifier.PRIVATE)
                    .isStatic(true)
                    .isFinal(true)
                    .type(TypeNames.TYPED_ELEMENT_INFO)
                    .name(constant)
                    .addContentCreate(grpcMethod.method()));
        }
    }

    private static void addSecurity(GrpcSecurityDefinition security,
                                    Constructor.Builder constructor,
                                    TypeName endpointType,
                                    GrpcMethod grpcMethod,
                                    GrpcSecurityDefinition inheritedSecurity) {
        if (security.isEmpty()) {
            return;
        }
        constructor.addContent(GRPC_SECURITY)
                .addContent(".enforce()");
        security.authenticate().ifPresent(authenticate -> constructor.addContent(authenticate
                                                                                         ? ".authenticate()"
                                                                                         : ".skipAuthentication()"));
        security.authenticationOptional().ifPresent(optional -> constructor.addContent(".authenticationOptional(")
                .addContent(optional.toString())
                .addContent(")"));
        security.authenticator().ifPresent(authenticator -> constructor.addContent(".authenticator(")
                .addContentLiteral(authenticator)
                .addContent(")"));
        if (security.clearAuthenticator()) {
            constructor.addContent(".clearAuthenticator()");
        }
        security.authorize().ifPresent(authorize -> constructor.addContent(authorize
                                                                                   ? ".authorize()"
                                                                                   : ".skipAuthorization()"));
        security.authorizer().ifPresent(authorizer -> constructor.addContent(".authorizer(")
                .addContentLiteral(authorizer)
                .addContent(")"));
        if (security.clearAuthorizer()) {
            constructor.addContent(".clearAuthorizer()");
        }
        if (!security.rolesAllowed().isEmpty()) {
            constructor.addContent(".rolesAllowed(");
            String separator = "";
            for (String role : security.rolesAllowed()) {
                constructor.addContent(separator)
                        .addContentLiteral(role);
                separator = ", ";
            }
            constructor.addContent(")");
        } else if (security.clearRolesAllowed()) {
            constructor.addContent(".clearRolesAllowed()");
        }
        security.audit().ifPresent(audit -> constructor.addContent(audit ? ".audit()" : ".skipAudit()"));
        security.auditEventType()
                .filter(eventType -> !AUDITED_DEFAULT_EVENT_TYPE.equals(eventType)
                        || inheritedSecurity.auditEventType()
                        .filter(inherited -> !AUDITED_DEFAULT_EVENT_TYPE.equals(inherited))
                        .isEmpty())
                .ifPresent(eventType -> constructor.addContent(".auditEventType(")
                        .addContentLiteral(eventType)
                        .addContent(")"));
        security.auditMessageFormat().ifPresent(messageFormat -> constructor.addContent(".auditMessageFormat(")
                .addContentLiteral(messageFormat)
                .addContent(")"));
        security.auditOkSeverity().ifPresent(severity -> constructor.addContent(".auditOkSeverity(")
                .addContent(AUDIT_SEVERITY)
                .addContent(".")
                .addContent(severity)
                .addContent(")"));
        security.auditErrorSeverity().ifPresent(severity -> constructor.addContent(".auditErrorSeverity(")
                .addContent(AUDIT_SEVERITY)
                .addContent(".")
                .addContent(severity)
                .addContent(")"));
        if (security.securityLevel()) {
            addSecurityLevel(constructor,
                             endpointType,
                             grpcMethod,
                             grpcMethod == null
                                     ? security.syntheticDenyAll()
                                     : inheritedSecurity.syntheticDenyAll(),
                             security.syntheticDenyAll());
        }
    }

    private static void addSecurityLevel(Constructor.Builder constructor,
                                         TypeName endpointType,
                                         GrpcMethod grpcMethod,
                                         boolean syntheticClassDenyAll,
                                         boolean syntheticMethodDenyAll) {
        constructor.addContent(".securityLevel(")
                .addContent(SECURITY_LEVEL)
                .addContent(".builder().type(")
                .addContent(endpointType)
                .addContent(".class).classAnnotations(CLASS_ANNOTATIONS)");
        if (syntheticClassDenyAll) {
            addDenyAllAnnotation(constructor, ".addClassAnnotation(");
        }
        if (grpcMethod != null) {
            constructor.addContent(".methodName(")
                    .addContentLiteral(grpcMethod.method().elementName())
                    .addContent(").methodAnnotations(")
                    .addContent(toConstantName("METHOD_" + grpcMethod.uniqueName()))
                    .addContent(".annotations())");
            if (syntheticMethodDenyAll) {
                addDenyAllAnnotation(constructor, ".addMethodAnnotation(");
            }
        }
        constructor.addContent(".build())");
    }

    private static void addDenyAllAnnotation(Constructor.Builder constructor, String method) {
        constructor.addContent(method)
                .addContent(TypeNames.ANNOTATION)
                .addContent(".create(")
                .addContent(SECURITY_DENY_ALL)
                .addContent(".class))");
    }

    private static void addClassAnnotationsField(ClassModel.Builder classModel, GrpcEndpoint endpoint) {
        classModel.addField(field -> field
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .isFinal(true)
                .type(LIST_OF_ANNOTATIONS)
                .name("CLASS_ANNOTATIONS")
                .addContent(List.class)
                .addContent(".of(")
                .update(it -> {
                    Iterator<Annotation> iterator = endpoint.annotations().iterator();
                    while (iterator.hasNext()) {
                        it.addContentCreate(iterator.next());
                        if (iterator.hasNext()) {
                            it.addContent(", ");
                        }
                    }
                })
                .addContent(")"));
    }

    private static void addDescriptorMethod(ClassModel.Builder classModel) {
        classModel.addMethod(descriptor -> descriptor
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(AccessModifier.PUBLIC)
                .returnType(GRPC_SERVICE_DESCRIPTOR)
                .name("descriptor")
                .addContentLine("return descriptor;"));
    }

    private static void addProtoMethod(ClassModel.Builder classModel, GrpcEndpoint endpoint, boolean singleton) {
        GrpcProtoDescriptor protoDescriptor = endpoint.protoDescriptor();
        classModel.addMethod(proto -> {
            proto.accessModifier(AccessModifier.PRIVATE)
                    .returnType(PROTO_FILE_DESCRIPTOR)
                    .name("proto");
            if (protoDescriptor.descriptorType().isPresent()) {
                proto.addContent("return ")
                        .addContent(protoDescriptor.descriptorType().orElseThrow())
                        .addContentLine(".getDescriptor();");
            } else if (protoDescriptor.isStatic()) {
                TypedElementInfo method = protoDescriptor.method().orElseThrow();
                proto.addContent("return ")
                        .addContent(method.enclosingType().orElse(endpoint.type().typeName()))
                        .addContent(".")
                        .addContent(method.elementName())
                        .addContentLine("();");
            } else {
                proto.addContent(singleton ? "return endpoint." : "return endpoint.get().")
                        .addContent(protoDescriptor.method().orElseThrow().elementName())
                        .addContentLine("();");
            }
        });
    }

    private static void addToStringMethod(ClassModel.Builder classModel, GrpcEndpoint endpoint) {
        classModel.addMethod(toString -> toString
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(AccessModifier.PUBLIC)
                .returnType(TypeNames.STRING)
                .name("toString")
                .addContent("return ")
                .addContentLiteral("gRPC route registration for "
                                           + endpoint.type().typeName().className()
                                           + "(" + endpoint.serviceName() + ")")
                .addContentLine(";"));
    }

    private static void addHandlerMethods(ClassModel.Builder classModel, GrpcEndpoint endpoint, boolean singleton) {
        String endpointAccessor = singleton ? "endpoint." : "endpoint.get().";
        for (GrpcMethod grpcMethod : endpoint.methods()) {
            TypeName responseObserver = TypeName.builder(STREAM_OBSERVER)
                    .addTypeArgument(grpcMethod.responseType())
                    .build();
            if (grpcMethod.invocation() == GrpcMethod.Invocation.CLIENT_STREAMING_STREAM
                    || grpcMethod.invocation() == GrpcMethod.Invocation.BIDI_STREAM
                    || grpcMethod.invocation() == GrpcMethod.Invocation.BIDI_OBSERVER) {
                TypeName requestObserver = TypeName.builder(STREAM_OBSERVER)
                        .addTypeArgument(grpcMethod.requestType())
                        .build();
                classModel.addMethod(method -> method
                        .accessModifier(AccessModifier.PRIVATE)
                        .returnType(requestObserver)
                        .name(grpcMethod.uniqueName())
                        .addParameter(responseObserver, "responseObserver")
                        .update(it -> {
                            switch (grpcMethod.invocation()) {
                            case CLIENT_STREAMING_STREAM -> it.addContent("return ")
                                    .addContent(GRPC_STREAMS)
                                    .addContent(".clientStreaming(requests -> ")
                                    .addContent(endpointAccessor)
                                    .addContent(grpcMethod.method().elementName())
                                    .addContentLine("(requests), responseObserver);");
                            case BIDI_STREAM -> it.addContent("return ")
                                    .addContent(GRPC_STREAMS)
                                    .addContent(".bidirectional(requests -> ")
                                    .addContent(endpointAccessor)
                                    .addContent(grpcMethod.method().elementName())
                                    .addContentLine("(requests), responseObserver);");
                            case BIDI_OBSERVER -> it.addContent("return " + endpointAccessor)
                                    .addContent(grpcMethod.method().elementName())
                                    .addContentLine("(responseObserver);");
                            default -> throw new IllegalStateException("Unexpected invocation: " + grpcMethod.invocation());
                            }
                        }));
                continue;
            }
            classModel.addMethod(method -> {
                method.accessModifier(AccessModifier.PRIVATE)
                        .returnType(TypeNames.PRIMITIVE_VOID)
                        .name(grpcMethod.uniqueName())
                        .addParameter(grpcMethod.requestType(), "request")
                        .addParameter(responseObserver, "responseObserver");
                if (grpcMethod.invocation() == GrpcMethod.Invocation.UNARY_RETURN) {
                    method.addContent("responseObserver.onNext(" + endpointAccessor)
                            .addContent(grpcMethod.method().elementName())
                            .addContentLine("(request));")
                            .addContentLine("responseObserver.onCompleted();");
                } else if (grpcMethod.invocation() == GrpcMethod.Invocation.SERVER_STREAMING_STREAM) {
                    method.addContent(GRPC_STREAMS)
                            .addContent(".serverStreaming(() -> ")
                            .addContent(endpointAccessor)
                            .addContent(grpcMethod.method().elementName())
                            .addContentLine("(request), responseObserver);");
                } else {
                    method.addContent(endpointAccessor)
                            .addContent(grpcMethod.method().elementName())
                            .addContentLine("(request, responseObserver);");
                }
            });
        }
    }
}
