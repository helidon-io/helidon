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

import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.declarative.codegen.DeclarativeTypes;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.ServiceCodegenTypes;

import static io.helidon.codegen.CodegenUtil.toConstantName;
import static io.helidon.declarative.codegen.DeclarativeTypes.SINGLETON_ANNOTATION;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.GRPC_ENTRY_POINTS;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.GRPC_ROUTE_REGISTRATION;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.GRPC_SECURITY;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.GRPC_SERVICE_DESCRIPTOR;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.PROTO_FILE_DESCRIPTOR;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.SECURITY_LEVEL;
import static io.helidon.declarative.codegen.grpc.server.GrpcServerTypes.STREAM_OBSERVER;

class GrpcServerRegistrationGenerator {
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
        addMethodInfoFields(classModel, endpoint);
        classModel.addConstructor(constructor(ctx, endpoint, endpointFieldType));
        addDescriptorMethod(classModel);
        addProtoMethod(classModel, endpoint, singleton);
        addValidateProtoMethod(classModel);
        addMethodTypeMethod(classModel);
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
                .addContent("var annotations = ")
                .addContent(descriptorType)
                .addContentLine(".ANNOTATIONS;")
                .addContentLine("var declarative__proto = proto();")
                .addContent("var builder = ")
                .addContent(GRPC_SERVICE_DESCRIPTOR)
                .addContent(".builder(")
                .addContent(endpointType)
                .addContent(".class, ")
                .addContentLiteral(endpoint.serviceName())
                .addContentLine(")")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContentLine(".proto(declarative__proto);")
                .decreaseContentPadding()
                .decreaseContentPadding();

        addSecurity(endpoint.security(), constructor, endpointType, descriptorType, null);
        if (!endpoint.security().isEmpty()) {
            constructor.addContentLine(".configure(builder);");
        }

        for (GrpcMethod method : endpoint.methods()) {
            boolean clientStreaming = "CLIENT_STREAMING".equals(method.methodType())
                    || "BIDI_STREAMING".equals(method.methodType());
            boolean serverStreaming = "SERVER_STREAMING".equals(method.methodType())
                    || "BIDI_STREAMING".equals(method.methodType());
            String registrationMethod = switch (method.methodType()) {
            case "UNARY" -> "unary";
            case "SERVER_STREAMING" -> "serverStreaming";
            case "CLIENT_STREAMING" -> "clientStreaming";
            case "BIDI_STREAMING" -> "bidirectional";
            default -> throw new IllegalArgumentException("Unsupported gRPC method type: " + method.methodType());
            };
            String constant = toConstantName("METHOD_" + method.uniqueName());
            constructor.addContent("validateProtoMethod(declarative__proto, ")
                    .addContentLiteral(endpoint.serviceName())
                    .addContent(", ")
                    .addContentLiteral(method.grpcName())
                    .addContent(", ")
                    .addContent(String.valueOf(clientStreaming))
                    .addContent(", ")
                    .addContent(String.valueOf(serverStreaming))
                    .addContentLine(");")
                    .addContent("builder.")
                    .addContent(registrationMethod)
                    .addContent("(")
                    .addContentLiteral(method.grpcName())
                    .addContent(", this::")
                    .addContent(method.uniqueName())
                    .addContentLine(", rules -> {")
                    .increaseContentPadding()
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
            addSecurity(method.security(), constructor, endpointType, descriptorType, method);
            if (!method.security().isEmpty()) {
                constructor.addContentLine(".configure(rules);");
            }
            constructor.decreaseContentPadding()
                    .addContentLine("});");
        }
        return constructor.addContentLine("this.descriptor = builder.build();");
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
                                    TypeName descriptorType,
                                    GrpcMethod grpcMethod) {
        if (security.isEmpty()) {
            return;
        }
        constructor.addContent(GRPC_SECURITY)
                .addContent(".enforce()");
        security.authenticate().ifPresent(authenticate -> constructor.addContent(authenticate
                                                                                         ? ".authenticate()"
                                                                                         : ".skipAuthentication()"));
        if (security.authenticationOptional()) {
            constructor.addContent(".authenticationOptional()");
        }
        security.authenticator().ifPresent(authenticator -> constructor.addContent(".authenticator(")
                .addContentLiteral(authenticator)
                .addContent(")"));
        security.authorize().ifPresent(authorize -> constructor.addContent(authorize
                                                                                   ? ".authorize()"
                                                                                   : ".skipAuthorization()"));
        security.authorizer().ifPresent(authorizer -> constructor.addContent(".authorizer(")
                .addContentLiteral(authorizer)
                .addContent(")"));
        if (!security.rolesAllowed().isEmpty()) {
            constructor.addContent(".rolesAllowed(");
            String separator = "";
            for (String role : security.rolesAllowed()) {
                constructor.addContent(separator)
                        .addContentLiteral(role);
                separator = ", ";
            }
            constructor.addContent(")");
        }
        security.audit().ifPresent(audit -> constructor.addContent(audit ? ".audit()" : ".skipAudit()"));
        security.auditEventType().ifPresent(eventType -> constructor.addContent(".auditEventType(")
                .addContentLiteral(eventType)
                .addContent(")"));
        security.auditMessageFormat().ifPresent(messageFormat -> constructor.addContent(".auditMessageFormat(")
                .addContentLiteral(messageFormat)
                .addContent(")"));
        if (security.securityLevel()) {
            addSecurityLevel(constructor, endpointType, descriptorType, grpcMethod);
        }
    }

    private static void addSecurityLevel(Constructor.Builder constructor,
                                         TypeName endpointType,
                                         TypeName descriptorType,
                                         GrpcMethod grpcMethod) {
        constructor.addContent(".securityLevel(")
                .addContent(SECURITY_LEVEL)
                .addContent(".builder().type(")
                .addContent(endpointType)
                .addContent(".class).classAnnotations(")
                .addContent(descriptorType)
                .addContent(".ANNOTATIONS)");
        if (grpcMethod != null) {
            constructor.addContent(".methodName(")
                    .addContentLiteral(grpcMethod.method().elementName())
                    .addContent(").methodAnnotations(")
                    .addContent(toConstantName("METHOD_" + grpcMethod.uniqueName()))
                    .addContent(".annotations())");
        }
        constructor.addContent(".build())");
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
        TypeName endpointType = endpoint.type().typeName();
        GrpcProtoMethod protoMethod = endpoint.protoMethod();
        classModel.addMethod(proto -> {
            proto.accessModifier(AccessModifier.PRIVATE)
                    .returnType(PROTO_FILE_DESCRIPTOR)
                    .name("proto");
            if (protoMethod.isStatic()) {
                proto.addContent("return ")
                        .addContent(endpointType)
                        .addContent(".")
                        .addContent(protoMethod.method().elementName())
                        .addContentLine("();");
            } else {
                proto.addContent(singleton ? "return endpoint." : "return endpoint.get().")
                        .addContent(protoMethod.method().elementName())
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
            if (grpcMethod.invocation() == GrpcMethod.Invocation.REQUEST_STREAMING) {
                TypeName requestObserver = TypeName.builder(STREAM_OBSERVER)
                        .addTypeArgument(grpcMethod.requestType())
                        .build();
                classModel.addMethod(method -> method
                        .accessModifier(AccessModifier.PRIVATE)
                        .returnType(requestObserver)
                        .name(grpcMethod.uniqueName())
                        .addParameter(responseObserver, "responseObserver")
                        .addContent("return " + endpointAccessor)
                        .addContent(grpcMethod.method().elementName())
                        .addContentLine("(responseObserver);"));
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
                } else {
                    method.addContent(endpointAccessor)
                            .addContent(grpcMethod.method().elementName())
                            .addContentLine("(request, responseObserver);");
                }
            });
        }
    }
}
