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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.helidon.builder.api.Prototype;
import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Generated;
import io.helidon.common.GenericType;
import io.helidon.common.types.Annotation;
import io.helidon.config.NamedService;
import io.helidon.grpc.api.Grpc;
import io.helidon.security.SecurityLevel;
import io.helidon.security.abac.role.RoleValidator;
import io.helidon.security.annotations.Authenticated;
import io.helidon.security.annotations.Audited;
import io.helidon.security.annotations.Authorized;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.Interception;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.validation.Validation;
import io.helidon.webserver.grpc.GrpcEntryPoint;
import io.helidon.webserver.grpc.GrpcRouteRegistration;
import io.helidon.webserver.grpc.GrpcServiceDescriptor;
import io.helidon.webserver.grpc.RpcServer;
import io.helidon.webserver.grpc.security.GrpcSecurity;

import com.google.protobuf.Descriptors;
import io.grpc.MethodDescriptor;
import io.grpc.ServerInterceptor;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class GrpcServerCodegenTest {
    private static final String PROTO_DESCRIPTOR_SOURCE = """
            package com.example;

            class DeclarativeGrpcProto {
                public static com.google.protobuf.Descriptors.FileDescriptor getDescriptor() {
                    return null;
                }
            }
            """;
    private static final List<Class<?>> CLASSPATH = List.of(
            Annotation.class,
            Dependency.class,
            Descriptors.Descriptor.class,
            Descriptors.FileDescriptor.class,
            Generated.class,
            GenericType.class,
            Grpc.class,
            GrpcSecurity.class,
            GrpcEntryPoint.class,
            GrpcRouteRegistration.class,
            GrpcServiceDescriptor.class,
            RpcServer.class,
            Authenticated.class,
            Audited.class,
            Authorized.class,
            MethodDescriptor.class,
            NamedService.class,
            Prototype.class,
            RoleValidator.class,
            RolesAllowed.class,
            DenyAll.class,
            PermitAll.class,
            SecurityLevel.class,
            ServerInterceptor.class,
            Service.class,
            ServiceDescriptor.class,
            StreamObserver.class,
            Interception.class,
            Validation.class
    );

    @Test
    void generatedServerRegistrationUsesGrpcDescriptors() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/grpc-server"))
                .addSource("GreetingGrpc.java", """
                        package com.example;

                        import java.util.stream.Stream;

                        import com.google.protobuf.Descriptors;

                        import io.grpc.stub.StreamObserver;
                        import io.helidon.grpc.api.Grpc;
                        import io.helidon.webserver.grpc.RpcServer;
                        import io.helidon.security.abac.role.RoleValidator;
                        import io.helidon.security.annotations.Authenticated;
                        import io.helidon.security.annotations.Audited;
                        import io.helidon.security.annotations.Authorized;
                        import io.helidon.service.registry.Service;
                        import jakarta.annotation.security.DenyAll;
                        import jakarta.annotation.security.PermitAll;
                        import jakarta.annotation.security.RolesAllowed;

                        @RpcServer.Endpoint
                        @RpcServer.Listener("admin")
                        @Grpc.GrpcService("Greeting")
                        @Grpc.ProtoDescriptor(DeclarativeGrpcProto.class)
                        @Service.Singleton
                        class GreetingGrpc implements SecuredGreetingGrpc {
                            @Grpc.Unary("SayHello")
                            @Authenticated
                            GreetingReply sayHello(GreetingRequest request) {
                                return GreetingReply.getDefaultInstance();
                            }

                            @Grpc.Unary("ValidatedHello")
                            GreetingReply validatedHello(@ValidGreetingRequest GreetingRequest request) {
                                return GreetingReply.getDefaultInstance();
                            }

                            @Grpc.Unary("InterceptedHello")
                            @InterceptedGreeting
                            GreetingReply interceptedHello(GreetingRequest request) {
                                return GreetingReply.getDefaultInstance();
                            }

                            @Grpc.Unary("AuthorizeHello")
                            @Authorized
                            GreetingReply authorizeHello(GreetingRequest request) {
                                return GreetingReply.getDefaultInstance();
                            }

                            @Grpc.Unary("AdminHello")
                            @RolesAllowed("admin")
                            GreetingReply adminHello(GreetingRequest request) {
                                return GreetingReply.getDefaultInstance();
                            }

                            @Grpc.Unary("PermitAllHello")
                            @PermitAll
                            GreetingReply permitAllHello(GreetingRequest request) {
                                return GreetingReply.getDefaultInstance();
                            }

                            @Grpc.Unary("DenyAllHello")
                            @DenyAll
                            GreetingReply denyAllHello(GreetingRequest request) {
                                return GreetingReply.getDefaultInstance();
                            }

                            @Grpc.Unary("RoleValidatorHello")
                            @RoleValidator.Roles("admin")
                            GreetingReply roleValidatorHello(GreetingRequest request) {
                                return GreetingReply.getDefaultInstance();
                            }

                            @Grpc.Unary("AuditedHello")
                            @Audited(value = "declarativeGrpc",
                                     okSeverity = io.helidon.security.AuditEvent.AuditSeverity.INFO,
                                     errorSeverity = io.helidon.security.AuditEvent.AuditSeverity.ERROR)
                            GreetingReply auditedHello(GreetingRequest request) {
                                return GreetingReply.getDefaultInstance();
                            }

                            @Grpc.Unary("InheritedAuditedHello")
                            @Audited
                            GreetingReply inheritedAuditedHello(GreetingRequest request) {
                                return GreetingReply.getDefaultInstance();
                            }

                            @Grpc.ServerStreaming("StreamHello")
                            Stream<GreetingReply> streamHello(GreetingRequest request) {
                                return Stream.empty();
                            }

                            @Grpc.ClientStreaming("CollectHello")
                            GreetingReply collectHello(Stream<GreetingRequest> requests) {
                                return GreetingReply.getDefaultInstance();
                            }

                            @Grpc.Bidirectional("ChatHello")
                            Stream<GreetingReply> chatHello(Stream<GreetingRequest> requests) {
                                return Stream.empty();
                            }

                            @Grpc.Bidirectional("ChatObserverHello")
                            StreamObserver<GreetingRequest> chatObserverHello(StreamObserver<GreetingReply> responseObserver) {
                                return null;
                            }
                        }
                        """)
                .addSource("SecuredGreetingGrpc.java", """
                        package com.example;

                        import io.helidon.security.annotations.Authenticated;
                        import io.helidon.security.annotations.Audited;

                        @Authenticated
                        @Audited("classAudit")
                        interface SecuredGreetingGrpc {
                        }
                        """)
                .addSource("DefaultAuditedGrpc.java", """
                        package com.example;

                        import io.helidon.grpc.api.Grpc;
                        import io.helidon.security.annotations.Audited;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.grpc.RpcServer;

                        @RpcServer.Endpoint
                        @Grpc.GrpcService("DefaultAudited")
                        @Grpc.ProtoDescriptor(DeclarativeGrpcProto.class)
                        @Service.Singleton
                        class DefaultAuditedGrpc {
                            @Grpc.Unary("DefaultAuditedHello")
                            @Audited
                            GreetingReply defaultAuditedHello(GreetingRequest request) {
                                return GreetingReply.getDefaultInstance();
                            }
                        }
                        """)
                .addSource("GreetingRequest.java", """
                        package com.example;

                        abstract class GreetingRequest extends com.google.protobuf.GeneratedMessageV3 {
                            public static com.google.protobuf.Descriptors.Descriptor getDescriptor() {
                                return null;
                            }

                            public static GreetingRequest getDefaultInstance() {
                                return null;
                            }
                        }
                        """)
                .addSource("GreetingReply.java", """
                        package com.example;

                        abstract class GreetingReply extends com.google.protobuf.GeneratedMessageV3 {
                            public static com.google.protobuf.Descriptors.Descriptor getDescriptor() {
                                return null;
                            }

                            public static GreetingReply getDefaultInstance() {
                                return null;
                            }
                        }
                        """)
                .addSource("DeclarativeGrpcProto.java", """
                        package com.example;

                        class DeclarativeGrpcProto {
                            public static com.google.protobuf.Descriptors.FileDescriptor getDescriptor() {
                                return null;
                            }
                        }
                        """)
                .addSource("ValidGreetingRequest.java", """
                        package com.example;

                        import java.lang.annotation.ElementType;
                        import java.lang.annotation.Target;

                        import io.helidon.validation.Validation;

                        @Validation.Constraint
                        @Target(ElementType.PARAMETER)
                        @interface ValidGreetingRequest {
                        }
                        """)
                .addSource("InterceptedGreeting.java", """
                        package com.example;

                        import java.lang.annotation.ElementType;
                        import java.lang.annotation.Retention;
                        import java.lang.annotation.RetentionPolicy;
                        import java.lang.annotation.Target;

                        import io.helidon.service.registry.Interception;

                        @Interception.Intercepted
                        @Retention(RetentionPolicy.CLASS)
                        @Target(ElementType.METHOD)
                        @interface InterceptedGreeting {
                        }
                        """)
                .addSource("Main.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;

                        @Service.GenerateBinding
                        class Main {
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));

        Path generatedRegistration = result.sourceOutput().resolve("com/example/GreetingGrpc__GrpcRegistration.java");
        assertThat("Generated source should exist: " + generatedRegistration, Files.exists(generatedRegistration), is(true));

        String registration = Files.readString(generatedRegistration, StandardCharsets.UTF_8);
        assertThat(registration, containsString("implements GrpcRouteRegistration"));
        assertThat(registration, containsString("private final GreetingGrpc endpoint;"));
        assertThat(registration, containsString("public String socket()"));
        assertThat(registration, containsString("return \"admin\";"));
        assertThat(registration, containsString("public boolean socketRequired()"));
        assertThat(registration, containsString("return true;"));
        assertThat(registration, containsString("GrpcServiceDescriptor.builder(GreetingGrpc.class, \"Greeting\")"));
        assertThat(registration, containsString("private static final List<Annotation> CLASS_ANNOTATIONS"));
        assertThat(registration, containsString("var annotations = CLASS_ANNOTATIONS;"));
        assertThat(registration, containsString("var declarative__proto = proto();"));
        assertThat(registration, containsString(".proto(declarative__proto)"));
        assertThat(registration, containsString("GreetingRequest.getDescriptor()"));
        assertThat(registration, containsString("GreetingReply.getDescriptor()"));
        assertThat(registration, containsString("private static void validateProtoMethod"));
        assertThat(registration, containsString("method.isClientStreaming() != clientStreaming"));
        assertThat(registration, containsString("method.isServerStreaming() != serverStreaming"));
        assertThat(registration, containsString("private static String methodType"));
        assertThat(registration, containsString(".unary(\"SayHello\", this::sayHello"));
        assertThat(registration, containsString(".unary(\"ValidatedHello\", this::validatedHello"));
        assertThat(registration, containsString(".unary(\"InterceptedHello\", this::interceptedHello"));
        assertThat(registration, containsString(".serverStreaming(\"StreamHello\", this::streamHello"));
        assertThat(registration, containsString(".clientStreaming(\"CollectHello\", this::collectHello"));
        assertThat(registration, containsString(".bidirectional(\"ChatHello\", this::chatHello"));
        assertThat(registration, containsString(".bidirectional(\"ChatObserverHello\", this::chatObserverHello"));
        assertThat(registration, containsString("if (entryPoints.hasInterceptors()) {"));
        assertThat(registration, containsString("entryPoints.interceptor("));
        assertThat(registration,
                   containsString("GrpcSecurity.enforce().authenticate().authenticationOptional(false)"
                                          + ".clearAuthenticator().securityLevel("));
        assertThat(registration, containsString(".classAnnotations(CLASS_ANNOTATIONS).build()).configure(builder);"));
        assertThat(registration,
                   containsString("GrpcSecurity.enforce().authorize().clearAuthorizer().securityLevel("));
        assertThat(registration, containsString("GrpcSecurity.enforce().rolesAllowed(\"admin\").securityLevel("));
        assertThat(registration,
                   containsString("GrpcSecurity.enforce().skipAuthentication().skipAuthorization()"
                                          + ".clearRolesAllowed().securityLevel("));
        assertThat(registration, containsString("GrpcSecurity.enforce().authenticate().authorize().securityLevel("));
        assertThat(registration, containsString(".auditEventType(\"classAudit\")"));
        assertThat(registration, containsString("GrpcSecurity.enforce().audit().auditEventType(\"declarativeGrpc\")"));
        assertThat(registration, not(containsString(".auditEventType(\"request\")")));
        assertThat(registration, not(containsString(".auditMessageFormat(")));
        assertThat(registration, containsString(".auditOkSeverity(AuditEvent.AuditSeverity.INFO)"));
        assertThat(registration, containsString(".auditErrorSeverity(AuditEvent.AuditSeverity.ERROR)"));
        assertThat(registration, containsString("SecurityLevel.builder().type(GreetingGrpc.class)"
                                                        + ".classAnnotations(CLASS_ANNOTATIONS)"));
        assertThat(registration, containsString(".methodName(\"sayHello\")"
                                                        + ".methodAnnotations(METHOD_SAY_HELLO.annotations()).build()"));
        assertThat(registration, containsString("private static final TypedElementInfo METHOD_SAY_HELLO"));
        assertThat(registration, containsString("private static final TypedElementInfo METHOD_VALIDATED_HELLO"));
        assertThat(registration, containsString("private static final TypedElementInfo METHOD_INTERCEPTED_HELLO"));
        assertThat(registration, containsString("private static final TypedElementInfo METHOD_AUTHORIZE_HELLO"));
        assertThat(registration, containsString("private static final TypedElementInfo METHOD_ADMIN_HELLO"));
        assertThat(registration, containsString("private static final TypedElementInfo METHOD_PERMIT_ALL_HELLO"));
        assertThat(registration, containsString("private static final TypedElementInfo METHOD_DENY_ALL_HELLO"));
        assertThat(registration, containsString("private static final TypedElementInfo METHOD_ROLE_VALIDATOR_HELLO"));
        assertThat(registration, containsString("private static final TypedElementInfo METHOD_AUDITED_HELLO"));
        assertThat(registration, containsString("private static final TypedElementInfo METHOD_INHERITED_AUDITED_HELLO"));
        assertThat(registration, containsString("private static final TypedElementInfo METHOD_STREAM_HELLO"));
        assertThat(registration, containsString("private static final TypedElementInfo METHOD_COLLECT_HELLO"));
        assertThat(registration, containsString("private static final TypedElementInfo METHOD_CHAT_HELLO"));
        assertThat(registration, containsString("private static final TypedElementInfo METHOD_CHAT_OBSERVER_HELLO"));
        assertThat(registration, containsString("METHOD_VALIDATED_HELLO));"));
        assertThat(registration, containsString("METHOD_INTERCEPTED_HELLO));"));
        assertThat(registration, containsString("responseObserver.onNext(endpoint.sayHello(request));"));

        Path generatedDefaultAuditedRegistration = result.sourceOutput()
                .resolve("com/example/DefaultAuditedGrpc__GrpcRegistration.java");
        assertThat("Generated source should exist: " + generatedDefaultAuditedRegistration,
                   Files.exists(generatedDefaultAuditedRegistration),
                   is(true));
        String defaultAuditedRegistration = Files.readString(generatedDefaultAuditedRegistration, StandardCharsets.UTF_8);
        assertThat(defaultAuditedRegistration,
                   containsString("GrpcSecurity.enforce().audit().auditEventType(\"request\")"));
        assertThat(registration, containsString("responseObserver.onNext(endpoint.validatedHello(request));"));
        assertThat(registration, containsString("responseObserver.onNext(endpoint.interceptedHello(request));"));
        assertThat(registration, containsString("GrpcStreams.serverStreaming(() -> endpoint.streamHello(request), "
                                                        + "responseObserver);"));
        assertThat(registration, containsString("GrpcStreams.clientStreaming(requests -> endpoint.collectHello(requests), "
                                                        + "responseObserver);"));
        assertThat(registration, containsString("GrpcStreams.bidirectional(requests -> endpoint.chatHello(requests), "
                                                        + "responseObserver);"));
        assertThat(registration, containsString("return endpoint.chatObserverHello(responseObserver);"));

        Path validationInterceptor = result.sourceOutput().resolve("com/example/GreetingGrpc__ValidationInterceptor_0.java");
        assertThat("Generated source should exist: " + validationInterceptor, Files.exists(validationInterceptor), is(true));

        String validation = Files.readString(validationInterceptor, StandardCharsets.UTF_8);
        assertThat(validation, containsString("ValidationContext.create(GreetingGrpc.class"));
        assertThat(validation, containsString("validatedHello(com.example.GreetingRequest)"));
        assertThat(validation, containsString("ConstraintViolation.Location.PARAMETER, \"request\""));
        assertThat(validation, containsString("validation__ctx.check(constraintValidator, request);"));

        Path intercepted = result.sourceOutput().resolve("com/example/GreetingGrpc__Intercepted.java");
        assertThat("Generated source should exist: " + intercepted, Files.exists(intercepted), is(true));

        String interceptedContent = Files.readString(intercepted, StandardCharsets.UTF_8);
        assertThat(interceptedContent, containsString("super.interceptedHello"));
        assertThat(interceptedContent, containsString("interceptedHello_"));

        Path registryMetadata = result.classOutput().resolve("META-INF/helidon/unnamed/com.example/service-registry.json");
        assertThat("Generated registry metadata should exist: " + registryMetadata, Files.exists(registryMetadata), is(true));

        String metadata = Files.readString(registryMetadata, StandardCharsets.UTF_8);
        assertThat(metadata, containsString("\"descriptor\":\"com.example.GreetingGrpc__GrpcRegistration__ServiceDescriptor\""));
        assertThat(metadata, containsString("\"io.helidon.webserver.grpc.GrpcRouteRegistration\""));
    }

    @Test
    void grpcServiceRequiresProtoDescriptorSource() {
        var result = compileGrpcService("grpc-server-missing-proto",
                                        "",
                                        "",
                                        "",
                                        """
                                                @Grpc.Unary("SayHello")
                                                GreetingReply sayHello(GreetingRequest request) {
                                                    return GreetingReply.getDefaultInstance();
                                                }
                                                """);

        assertCompilationFails(result,
                               "Declarative gRPC service com.example.GreetingGrpc must declare exactly one proto descriptor "
                                       + "source: either @Grpc.ProtoDescriptor on the type or one @Grpc.Proto method.");
    }

    @Test
    void grpcServiceUsesProtoDescriptorType() throws IOException {
        var result = compileGrpcService("grpc-server-proto-descriptor-type",
                                        "",
                                        "@Grpc.ProtoDescriptor(DeclarativeGrpcProto.class)",
                                        "",
                                        """
                                                @Grpc.Unary("SayHello")
                                                GreetingReply sayHello(GreetingRequest request) {
                                                    return GreetingReply.getDefaultInstance();
                                                }
                                                """,
                                        """
                                                package com.example;

                                                import com.google.protobuf.Descriptors;

                                                class DeclarativeGrpcProto {
                                                    public static Descriptors.FileDescriptor getDescriptor() {
                                                        return null;
                                                    }
                                                }
                                                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));

        Path generatedRegistration = result.sourceOutput().resolve("com/example/GreetingGrpc__GrpcRegistration.java");
        assertThat("Generated source should exist: " + generatedRegistration, Files.exists(generatedRegistration), is(true));

        String registration = Files.readString(generatedRegistration, StandardCharsets.UTF_8);
        assertThat(registration, containsString("return DeclarativeGrpcProto.getDescriptor();"));
    }

    @Test
    void grpcServiceUsesInstanceProtoMethod() throws IOException {
        var result = compileGrpcService("grpc-server-instance-proto-method",
                                        "",
                                        "",
                                        "",
                                        """
                                                @Grpc.Proto
                                                Descriptors.FileDescriptor proto() {
                                                    return null;
                                                }

                                                @Grpc.Unary("SayHello")
                                                GreetingReply sayHello(GreetingRequest request) {
                                                    return GreetingReply.getDefaultInstance();
                                                }
                                                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));

        Path generatedRegistration = result.sourceOutput().resolve("com/example/GreetingGrpc__GrpcRegistration.java");
        assertThat("Generated source should exist: " + generatedRegistration, Files.exists(generatedRegistration), is(true));

        String registration = Files.readString(generatedRegistration, StandardCharsets.UTF_8);
        assertThat(registration, containsString("return endpoint.proto();"));
    }

    @Test
    void grpcServiceUsesStaticProtoMethod() throws IOException {
        var result = compileGrpcService("grpc-server-static-proto-method",
                                        "",
                                        "",
                                        "",
                                        """
                                                @Grpc.Proto
                                                static Descriptors.FileDescriptor proto() {
                                                    return null;
                                                }

                                                @Grpc.Unary("SayHello")
                                                GreetingReply sayHello(GreetingRequest request) {
                                                    return GreetingReply.getDefaultInstance();
                                                }
                                                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));

        Path generatedRegistration = result.sourceOutput().resolve("com/example/GreetingGrpc__GrpcRegistration.java");
        assertThat("Generated source should exist: " + generatedRegistration, Files.exists(generatedRegistration), is(true));

        String registration = Files.readString(generatedRegistration, StandardCharsets.UTF_8);
        assertThat(registration, containsString("return GreetingGrpc.proto();"));
    }

    @Test
    void grpcServiceUsesInheritedProtoMethod() throws IOException {
        var result = compileGrpcService("grpc-server-inherited-proto-method",
                                        """
                                                class GreetingBase {
                                                    @Grpc.Proto
                                                    protected Descriptors.FileDescriptor proto() {
                                                        return null;
                                                    }
                                                }
                                                """,
                                        "",
                                        "extends GreetingBase",
                                        """
                                                @Grpc.Unary("SayHello")
                                                GreetingReply sayHello(GreetingRequest request) {
                                                    return GreetingReply.getDefaultInstance();
                                                }
                                                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));

        Path generatedRegistration = result.sourceOutput().resolve("com/example/GreetingGrpc__GrpcRegistration.java");
        String registration = Files.readString(generatedRegistration, StandardCharsets.UTF_8);
        assertThat(registration, containsString("return endpoint.proto();"));
    }

    @Test
    void grpcServiceRejectsProtoDescriptorAndProtoMethod() {
        var result = compileGrpcService("grpc-server-proto-descriptor-conflict",
                                        "",
                                        "@Grpc.ProtoDescriptor(DeclarativeGrpcProto.class)",
                                        "",
                                        """
                                                @Grpc.Proto
                                                Descriptors.FileDescriptor proto() {
                                                    return null;
                                                }

                                                @Grpc.Unary("SayHello")
                                                GreetingReply sayHello(GreetingRequest request) {
                                                    return GreetingReply.getDefaultInstance();
                                                }
                                                """,
                                        """
                                                package com.example;

                                                import com.google.protobuf.Descriptors;

                                                class DeclarativeGrpcProto {
                                                    public static Descriptors.FileDescriptor getDescriptor() {
                                                        return null;
                                                    }
                                                }
                                                """);

        assertCompilationFails(result,
                               "Declarative gRPC service com.example.GreetingGrpc must declare exactly one proto descriptor "
                                       + "source: either @Grpc.ProtoDescriptor on the type or one @Grpc.Proto method.");
    }

    @Test
    void grpcServiceCountsPrivateProtoMethodAsConflictingSource() {
        var result = compileGrpcService("grpc-server-private-proto-conflict",
                                        "",
                                        "@Grpc.ProtoDescriptor(DeclarativeGrpcProto.class)",
                                        "",
                                        """
                                                @Grpc.Proto
                                                private Descriptors.FileDescriptor proto() {
                                                    return null;
                                                }

                                                @Grpc.Unary("SayHello")
                                                GreetingReply sayHello(GreetingRequest request) {
                                                    return GreetingReply.getDefaultInstance();
                                                }
                                                """,
                                        PROTO_DESCRIPTOR_SOURCE);

        assertCompilationFails(result,
                               "Declarative gRPC service com.example.GreetingGrpc must declare exactly one proto descriptor "
                                       + "source: either @Grpc.ProtoDescriptor on the type or one @Grpc.Proto method.");
    }

    @Test
    void grpcServiceRejectsInvalidProtoDescriptorType() {
        var result = compileGrpcService("grpc-server-bad-proto-descriptor-type",
                                        "",
                                        "@Grpc.ProtoDescriptor(DeclarativeGrpcProto.class)",
                                        "",
                                        """
                                                @Grpc.Unary("SayHello")
                                                GreetingReply sayHello(GreetingRequest request) {
                                                    return GreetingReply.getDefaultInstance();
                                                }
                                                """,
                                        """
                                                package com.example;

                                                class DeclarativeGrpcProto {
                                                    static String getDescriptor() {
                                                        return "";
                                                    }
                                                }
                                                """);

        assertCompilationFails(result,
                               "@Grpc.ProtoDescriptor on com.example.GreetingGrpc must reference a type with a public static "
                                       + "no-argument getDescriptor() method returning "
                                       + "com.google.protobuf.Descriptors.FileDescriptor: com.example.DeclarativeGrpcProto");
    }

    @Test
    void grpcServiceRequiresServiceName() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/grpc-server-default-service-name"))
                .addSource("Greeting.java", """
                        package com.example;

                        import com.google.protobuf.Descriptors;

                        import io.helidon.grpc.api.Grpc;
                        import io.helidon.webserver.grpc.RpcServer;
                        import io.helidon.service.registry.Service;

                        @RpcServer.Endpoint
                        @Grpc.GrpcService
                        @Grpc.ProtoDescriptor(DeclarativeGrpcProto.class)
                        @Service.Singleton
                        class Greeting {
                            @Grpc.Unary("SayHello")
                            GreetingReply sayHello(GreetingRequest request) {
                                return GreetingReply.getDefaultInstance();
                            }
                        }
                        """)
                .addSource("GreetingRequest.java", """
                        package com.example;

                        abstract class GreetingRequest extends com.google.protobuf.GeneratedMessageV3 {
                            public static com.google.protobuf.Descriptors.Descriptor getDescriptor() {
                                return null;
                            }

                            public static GreetingRequest getDefaultInstance() {
                                return null;
                            }
                        }
                        """)
                .addSource("GreetingReply.java", """
                        package com.example;

                        abstract class GreetingReply extends com.google.protobuf.GeneratedMessageV3 {
                            public static com.google.protobuf.Descriptors.Descriptor getDescriptor() {
                                return null;
                            }

                            public static GreetingReply getDefaultInstance() {
                                return null;
                            }
                        }
                        """)
                .addSource("DeclarativeGrpcProto.java", PROTO_DESCRIPTOR_SOURCE)
                .addSource("Main.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;

                        @Service.GenerateBinding
                        class Main {
                        }
                        """)
                .build()
                .compile();

        assertCompilationFails(result,
                               "Declarative gRPC service com.example.Greeting must define a non-blank "
                                       + "@Grpc.GrpcService value.");
    }

    @Test
    void grpcServiceRejectsMultipleProtoMethods() {
        var result = compileGrpcService("grpc-server-multiple-proto",
                                        "",
                                        "",
                                        "",
                                        """
                                                @Grpc.Proto
                                                Descriptors.FileDescriptor proto() {
                                                    return null;
                                                }

                                                @Grpc.Proto
                                                Descriptors.FileDescriptor duplicateProto() {
                                                    return null;
                                                }

                                                @Grpc.Unary("SayHello")
                                                GreetingReply sayHello(GreetingRequest request) {
                                                    return GreetingReply.getDefaultInstance();
                                                }
                                                """);

        assertCompilationFails(result,
                               "Declarative gRPC service com.example.GreetingGrpc must declare exactly one proto descriptor "
                                       + "source: either @Grpc.ProtoDescriptor on the type or one @Grpc.Proto method.");
    }

    @Test
    void grpcServiceRequiresGrpcMethod() {
        var result = compileGrpcService("grpc-server-no-methods", "");

        assertCompilationFails(result,
                               "Declarative gRPC service com.example.GreetingGrpc must declare at least one gRPC method.");
    }

    @Test
    void grpcServiceUsesMethodNameFromMetaAnnotation() throws IOException {
        var result = compileGrpcService("grpc-server-meta-annotation-name",
                                        "",
                                        "@Grpc.ProtoDescriptor(DeclarativeGrpcProto.class)",
                                        "",
                                        """
                                                @SayHello
                                                GreetingReply greet(GreetingRequest request) {
                                                    return GreetingReply.getDefaultInstance();
                                                }
                                                """,
                                        PROTO_DESCRIPTOR_SOURCE,
                                        """
                                                package com.example;

                                                import java.lang.annotation.ElementType;
                                                import java.lang.annotation.Retention;
                                                import java.lang.annotation.RetentionPolicy;
                                                import java.lang.annotation.Target;

                                                import io.grpc.MethodDescriptor;
                                                import io.helidon.grpc.api.Grpc;

                                                @Target(ElementType.METHOD)
                                                @Retention(RetentionPolicy.RUNTIME)
                                                @Grpc.GrpcMethod(value = MethodDescriptor.MethodType.UNARY,
                                                                 name = "SayHello")
                                                @interface SayHello {
                                                }
                                                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));

        Path generatedRegistration = result.sourceOutput().resolve("com/example/GreetingGrpc__GrpcRegistration.java");
        String registration = Files.readString(generatedRegistration, StandardCharsets.UTF_8);
        assertThat(registration, containsString(".unary(\"SayHello\", this::greet"));
    }

    @Test
    void grpcServiceDiscoversTransitiveSuperclassMethod() throws IOException {
        var result = compileGrpcService("grpc-server-transitive-superclass-method",
                                        """
                                                class GreetingBase {
                                                    @Grpc.Unary("SayHello")
                                                    GreetingReply sayHello(GreetingRequest request) {
                                                        return GreetingReply.getDefaultInstance();
                                                    }
                                                }

                                                class GreetingMiddle extends GreetingBase {
                                                }
                                                """,
                                        "extends GreetingMiddle",
                                        "");

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));

        Path generatedRegistration = result.sourceOutput().resolve("com/example/GreetingGrpc__GrpcRegistration.java");
        String registration = Files.readString(generatedRegistration, StandardCharsets.UTF_8);
        assertThat(registration, containsString(".unary(\"SayHello\", this::sayHello"));
        assertThat(registration, containsString("responseObserver.onNext(endpoint.sayHello(request));"));
    }

    @Test
    void grpcServiceIgnoresInaccessibleCrossPackageProtectedMethod() throws IOException {
        var result = compileGrpcService("grpc-server-cross-package-protected-method",
                                        "",
                                        "@Grpc.ProtoDescriptor(DeclarativeGrpcProto.class)",
                                        "extends com.example.base.Extra1",
                                        """
                                                @Grpc.Unary("SayHello")
                                                GreetingReply sayHello(GreetingRequest request) {
                                                    return GreetingReply.getDefaultInstance();
                                                }
                                                """,
                                        PROTO_DESCRIPTOR_SOURCE,
                                        """
                                                package com.example.base;

                                                import com.google.protobuf.Empty;
                                                import io.helidon.grpc.api.Grpc;

                                                public class Extra1 {
                                                    @Grpc.Unary("Hidden")
                                                    protected Empty hidden(Empty request) {
                                                        return request;
                                                    }
                                                }
                                                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));

        Path generatedRegistration = result.sourceOutput().resolve("com/example/GreetingGrpc__GrpcRegistration.java");
        String registration = Files.readString(generatedRegistration, StandardCharsets.UTF_8);
        assertThat(registration, containsString(".unary(\"SayHello\", this::sayHello"));
        assertThat(registration, not(containsString("Hidden")));
    }

    @Test
    void grpcProtoMethodRequiresFileDescriptorWithoutParameters() {
        var result = compileGrpcService("grpc-server-bad-proto",
                                        "",
                                        "",
                                        "",
                                        """
                                                @Grpc.Proto
                                                String proto(GreetingRequest request) {
                                                    return "";
                                                }

                                                @Grpc.Unary("SayHello")
                                                GreetingReply sayHello(GreetingRequest request) {
                                                    return GreetingReply.getDefaultInstance();
                                                }
                                                """);

        assertCompilationFails(result,
                               "@Grpc.Proto method on com.example.GreetingGrpc must return "
                                       + "com.google.protobuf.Descriptors.FileDescriptor and have no parameters.");
    }

    @Test
    void grpcProtoMethodRejectsCheckedException() {
        var result = compileGrpcService("grpc-server-proto-checked-exception",
                                        "",
                                        "",
                                        "",
                                        """
                                                @Grpc.Proto
                                                Descriptors.FileDescriptor proto() throws IOException {
                                                    return null;
                                                }

                                                @Grpc.Unary("SayHello")
                                                GreetingReply sayHello(GreetingRequest request) {
                                                    return GreetingReply.getDefaultInstance();
                                                }
                                                """);

        assertCompilationFails(result,
                               "@Grpc.Proto method on com.example.GreetingGrpc must not declare checked exceptions.");
    }

    @Test
    void grpcMethodMustNotDeclareCheckedException() {
        var result = compileGrpcService("grpc-server-checked-exception", """
                @Grpc.Unary("SayHello")
                GreetingReply sayHello(GreetingRequest request) throws IOException {
                    return GreetingReply.getDefaultInstance();
                }
                """);

        assertCompilationFails(result,
                               "Declarative gRPC server methods must not declare checked exceptions: "
                                       + "com.example.GreetingGrpc.sayHello()");
    }

    @Test
    void grpcUnaryMethodRejectsUnsupportedSignature() {
        var result = compileGrpcService("grpc-server-bad-unary-signature", """
                @Grpc.Unary("SayHello")
                void sayHello(GreetingRequest request) {
                }
                """);

        assertCompilationFails(result,
                               "Unsupported declarative gRPC method signature for "
                                       + "com.example.GreetingGrpc.sayHello()");
    }

    @Test
    void grpcClientStreamingMethodRejectsUnarySignature() {
        var result = compileGrpcService("grpc-server-bad-client-streaming-signature", """
                @Grpc.ClientStreaming("CollectHello")
                GreetingReply collectHello(GreetingRequest request) {
                    return GreetingReply.getDefaultInstance();
                }
                """);

        assertCompilationFails(result,
                               "Unsupported declarative gRPC method signature for "
                                       + "com.example.GreetingGrpc.collectHello()");
    }

    @Test
    void grpcServerStreamingMethodRejectsRequestStreamingSignature() {
        var result = compileGrpcService("grpc-server-bad-server-streaming-signature", """
                @Grpc.ServerStreaming("StreamHello")
                StreamObserver<GreetingRequest> streamHello(StreamObserver<GreetingReply> responseObserver) {
                    return null;
                }
                """);

        assertCompilationFails(result,
                               "Unsupported declarative gRPC method signature for "
                                       + "com.example.GreetingGrpc.streamHello()");
    }

    @Test
    void grpcServerStreamingMethodRejectsIterableResponse() {
        var result = compileGrpcService("grpc-server-iterable-server-streaming", """
                @Grpc.ServerStreaming("StreamHello")
                Iterable<GreetingReply> streamHello(GreetingRequest request) {
                    return null;
                }
                """);

        assertCompilationFails(result,
                               "Unsupported declarative gRPC method signature for "
                                       + "com.example.GreetingGrpc.streamHello()");
    }

    @Test
    void grpcServiceRejectsDuplicateWireMethodNames() {
        var result = compileGrpcService("grpc-server-duplicate-wire-method", """
                @Grpc.Unary("SayHello")
                GreetingReply sayHello(GreetingRequest request) {
                    return GreetingReply.getDefaultInstance();
                }

                @Grpc.Unary("SayHello")
                GreetingReply sayHelloAgain(GreetingRequest request) {
                    return GreetingReply.getDefaultInstance();
                }
                """);

        assertCompilationFails(result,
                               "Declarative gRPC service com.example.GreetingGrpc maps both sayHello() and sayHelloAgain() "
                                       + "to wire method SayHello.");
    }

    @Test
    void grpcMethodRejectsMultipleMethodAnnotations() {
        var result = compileGrpcService("grpc-server-multiple-method-annotations", """
                @Grpc.Unary("SayHello")
                @Grpc.ServerStreaming("StreamHello")
                void sayHello(GreetingRequest request, StreamObserver<GreetingReply> responseObserver) {
                }
                """);

        assertCompilationFails(result,
                               "Declarative gRPC server method com.example.GreetingGrpc.sayHello() "
                                       + "must declare exactly one gRPC method annotation.");
    }

    @Test
    void grpcMethodRejectsConflictingInheritedMethodAnnotations() {
        var result = compileGrpcService("grpc-server-inherited-multiple-method-annotations",
                                        "",
                                        "@Grpc.ProtoDescriptor(DeclarativeGrpcProto.class)",
                                        "implements GreetingContract",
                                        """
                                                @Override
                                                @Grpc.ServerStreaming("SayHello")
                                                public void sayHello(GreetingRequest request,
                                                                     StreamObserver<GreetingReply> responseObserver) {
                                                }
                                                """,
                                        """
                                                package com.example;

                                                import io.grpc.stub.StreamObserver;
                                                import io.helidon.grpc.api.Grpc;

                                                interface GreetingContract {
                                                    @Grpc.Unary("SayHello")
                                                    void sayHello(GreetingRequest request,
                                                                  StreamObserver<GreetingReply> responseObserver);
                                                }
                                                """,
                                        PROTO_DESCRIPTOR_SOURCE);

        assertCompilationFails(result,
                               "Declarative gRPC server method com.example.GreetingGrpc.sayHello() "
                                       + "must declare exactly one gRPC method annotation.");
    }

    @Test
    void grpcProtoMethodRejectsMethodAnnotation() {
        var result = compileGrpcService("grpc-server-proto-method-annotation",
                                        "",
                                        "",
                                        "",
                                        """
                                                @Grpc.Proto
                                                @Grpc.Unary("Proto")
                                                Descriptors.FileDescriptor proto() {
                                                    return null;
                                                }

                                                @Grpc.Unary("SayHello")
                                                GreetingReply sayHello(GreetingRequest request) {
                                                    return GreetingReply.getDefaultInstance();
                                                }
                                                """);

        assertCompilationFails(result,
                               "@Grpc.Proto method on com.example.GreetingGrpc must not declare a gRPC method annotation.");
    }

    @Test
    void grpcMethodRejectsUnsupportedMethodType() {
        var result = compileGrpcService("grpc-server-unsupported-method-type", """
                @Grpc.GrpcMethod(MethodDescriptor.MethodType.UNKNOWN)
                GreetingReply unknown(GreetingRequest request) {
                    return GreetingReply.getDefaultInstance();
                }
                """);

        assertCompilationFails(result,
                                       "Declarative gRPC server supports only unary, server streaming, client streaming, "
                                               + "and bidirectional streaming methods in this version. Method unknown() uses UNKNOWN.");
    }

    @Test
    void grpcMethodRejectsExplicitAuthorization() {
        var result = compileGrpcService("grpc-server-explicit-authorized", """
                @Grpc.Unary("SayHello")
                @Authorized(explicit = true)
                GreetingReply sayHello(GreetingRequest request) {
                    return GreetingReply.getDefaultInstance();
                }
                """);

        assertCompilationFails(result,
                               "Declarative gRPC does not support @Authorized(explicit = true): "
                                       + "com.example.GreetingGrpc.sayHello()");
    }

    @Test
    void grpcMethodSecurityAnnotationsReplaceClassDefaults() throws IOException {
        var result = compileGrpcService("grpc-server-method-security-overrides",
                                        "import io.helidon.security.annotations.Authenticated;",
                                        """
                                                @Grpc.ProtoDescriptor(DeclarativeGrpcProto.class)
                                                @Authenticated(optional = true, provider = "class-authenticator")
                                                @Authorized(provider = "class-authorizer")
                                                """,
                                        "",
                                        """
                                                @Grpc.Unary("SayHello")
                                                @Authenticated
                                                @Authorized
                                                GreetingReply sayHello(GreetingRequest request) {
                                                    return GreetingReply.getDefaultInstance();
                                                }
                                                """,
                                        PROTO_DESCRIPTOR_SOURCE);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));

        Path generatedRegistration = result.sourceOutput().resolve("com/example/GreetingGrpc__GrpcRegistration.java");
        String registration = Files.readString(generatedRegistration, StandardCharsets.UTF_8);
        assertThat(registration,
                   containsString(".authenticate().authenticationOptional(true)"
                                          + ".authenticator(\"class-authenticator\")"));
        assertThat(registration, containsString(".authorize().authorizer(\"class-authorizer\")"));
        assertThat(registration,
                   containsString(".authenticate().authenticationOptional(false).clearAuthenticator()"
                                          + ".authorize().clearAuthorizer()"));
    }

    @Test
    void grpcClassWithEmptyRolesAllowedDeniesAll() throws IOException {
        var result = compileGrpcService("grpc-server-class-empty-roles",
                                        "import jakarta.annotation.security.RolesAllowed;",
                                        """
                                                @Grpc.ProtoDescriptor(DeclarativeGrpcProto.class)
                                                @RolesAllowed({})
                                                """,
                                        "",
                                        """
                                                @Grpc.Unary("SayHello")
                                                GreetingReply sayHello(GreetingRequest request) {
                                                    return GreetingReply.getDefaultInstance();
                                                }
                                                """,
                                        PROTO_DESCRIPTOR_SOURCE);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));

        Path generatedRegistration = result.sourceOutput().resolve("com/example/GreetingGrpc__GrpcRegistration.java");
        String registration = Files.readString(generatedRegistration, StandardCharsets.UTF_8);
        assertThat(registration,
                   containsString(".classAnnotations(CLASS_ANNOTATIONS)"
                                          + ".addClassAnnotation(Annotation.create(DenyAll.class))"));
    }

    @Test
    void grpcMethodWithEmptyRolesAllowedDeniesAll() throws IOException {
        var result = compileGrpcService("grpc-server-method-empty-roles",
                                        "import jakarta.annotation.security.RolesAllowed;",
                                        "@Grpc.ProtoDescriptor(DeclarativeGrpcProto.class)",
                                        "",
                                        """
                                                @Grpc.Unary("SayHello")
                                                @RolesAllowed({})
                                                GreetingReply sayHello(GreetingRequest request) {
                                                    return GreetingReply.getDefaultInstance();
                                                }
                                                """,
                                        PROTO_DESCRIPTOR_SOURCE);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));

        Path generatedRegistration = result.sourceOutput().resolve("com/example/GreetingGrpc__GrpcRegistration.java");
        String registration = Files.readString(generatedRegistration, StandardCharsets.UTF_8);
        assertThat(registration,
                   containsString(".annotations())"
                                          + ".addMethodAnnotation(Annotation.create(DenyAll.class))"));
    }

    private static TestCompiler.Result compileGrpcService(String workDir, String serviceBody) {
        return compileGrpcService(workDir,
                                  "",
                                  "@Grpc.ProtoDescriptor(DeclarativeGrpcProto.class)",
                                  "",
                                  serviceBody,
                                  PROTO_DESCRIPTOR_SOURCE);
    }

    private static TestCompiler.Result compileGrpcService(String workDir,
                                                          String supportingTypes,
                                                          String serviceDeclaration,
                                                          String serviceBody) {
        return compileGrpcService(workDir,
                                  supportingTypes,
                                  "@Grpc.ProtoDescriptor(DeclarativeGrpcProto.class)",
                                  serviceDeclaration,
                                  serviceBody,
                                  PROTO_DESCRIPTOR_SOURCE);
    }

    private static TestCompiler.Result compileGrpcService(String workDir,
                                                          String imports,
                                                          String typeAnnotation,
                                                          String serviceDeclaration,
                                                          String serviceBody,
                                                          String... extraSources) {
        TestCompiler.Builder builder = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/" + workDir))
                .addSource("GreetingGrpc.java", """
                        package com.example;

                        import com.google.protobuf.Descriptors;

                        import java.io.IOException;

                        import io.grpc.MethodDescriptor;
                        import io.grpc.stub.StreamObserver;
                        import io.helidon.grpc.api.Grpc;
                        import io.helidon.webserver.grpc.RpcServer;
                        import io.helidon.security.annotations.Authorized;
                        import io.helidon.service.registry.Service;

                        %s

                        %s
                        @RpcServer.Endpoint
                        @Grpc.GrpcService("Greeting")
                        @Service.Singleton
                        class GreetingGrpc %s {
                        %s
                        }
                        """.formatted(imports, typeAnnotation, serviceDeclaration, serviceBody.indent(4)))
                .addSource("GreetingRequest.java", """
                        package com.example;

                        abstract class GreetingRequest extends com.google.protobuf.GeneratedMessageV3 {
                            public static com.google.protobuf.Descriptors.Descriptor getDescriptor() {
                                return null;
                            }

                            public static GreetingRequest getDefaultInstance() {
                                return null;
                            }
                        }
                        """)
                .addSource("GreetingReply.java", """
                        package com.example;

                        abstract class GreetingReply extends com.google.protobuf.GeneratedMessageV3 {
                            public static com.google.protobuf.Descriptors.Descriptor getDescriptor() {
                                return null;
                            }

                            public static GreetingReply getDefaultInstance() {
                                return null;
                            }
                        }
                        """)
                .addSource("Main.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;

                        @Service.GenerateBinding
                        class Main {
                        }
                        """);
        for (int i = 0; i < extraSources.length; i++) {
            builder.addSource("Extra" + i + ".java", extraSources[i]);
        }
        return builder.build().compile();
    }

    private static void assertCompilationFails(TestCompiler.Result result, String... diagnosticParts) {
        String diagnostics = String.join("\n", result.diagnostics());
        assertThat("Build should fail", result.success(), is(false));
        assertThat(diagnostics, containsString("error:"));
        for (String diagnosticPart : diagnosticParts) {
            assertThat(diagnostics, containsString(diagnosticPart));
        }
    }
}
