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
import static org.hamcrest.MatcherAssert.assertThat;

class GrpcServerCodegenTest {
    private static final List<Class<?>> CLASSPATH = List.of(
            Annotation.class,
            Dependency.class,
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

                        import java.util.List;
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
                        @Service.Singleton
                        class GreetingGrpc implements SecuredGreetingGrpc {
                            @Grpc.Proto
                            Descriptors.FileDescriptor proto() {
                                return null;
                            }

                            @Grpc.Unary("SayHello")
                            @Authenticated
                            GreetingReply sayHello(GreetingRequest request) {
                                return new GreetingReply();
                            }

                            @Grpc.Unary("ValidatedHello")
                            GreetingReply validatedHello(@ValidGreetingRequest GreetingRequest request) {
                                return new GreetingReply();
                            }

                            @Grpc.Unary("InterceptedHello")
                            @InterceptedGreeting
                            GreetingReply interceptedHello(GreetingRequest request) {
                                return new GreetingReply();
                            }

                            @Grpc.Unary("AuthorizeHello")
                            @Authorized
                            GreetingReply authorizeHello(GreetingRequest request) {
                                return new GreetingReply();
                            }

                            @Grpc.Unary("AdminHello")
                            @RolesAllowed("admin")
                            GreetingReply adminHello(GreetingRequest request) {
                                return new GreetingReply();
                            }

                            @Grpc.Unary("PermitAllHello")
                            @PermitAll
                            GreetingReply permitAllHello(GreetingRequest request) {
                                return new GreetingReply();
                            }

                            @Grpc.Unary("DenyAllHello")
                            @DenyAll
                            GreetingReply denyAllHello(GreetingRequest request) {
                                return new GreetingReply();
                            }

                            @Grpc.Unary("RoleValidatorHello")
                            @RoleValidator.Roles("admin")
                            GreetingReply roleValidatorHello(GreetingRequest request) {
                                return new GreetingReply();
                            }

                            @Grpc.Unary("AuditedHello")
                            @Audited("declarativeGrpc")
                            GreetingReply auditedHello(GreetingRequest request) {
                                return new GreetingReply();
                            }

                            @Grpc.ServerStreaming("StreamHello")
                            Stream<GreetingReply> streamHello(GreetingRequest request) {
                                return Stream.empty();
                            }

                            @Grpc.ServerStreaming("IterableHello")
                            Iterable<GreetingReply> iterableHello(GreetingRequest request) {
                                return List.of();
                            }

                            @Grpc.ClientStreaming("CollectHello")
                            GreetingReply collectHello(Stream<GreetingRequest> requests) {
                                return new GreetingReply();
                            }

                            @Grpc.ClientStreaming("CollectIterableHello")
                            GreetingReply collectIterableHello(Iterable<GreetingRequest> requests) {
                                return new GreetingReply();
                            }

                            @Grpc.Bidirectional("ChatHello")
                            Stream<GreetingReply> chatHello(Stream<GreetingRequest> requests) {
                                return Stream.empty();
                            }

                            @Grpc.Bidirectional("ChatIterableHello")
                            Iterable<GreetingReply> chatIterableHello(Iterable<GreetingRequest> requests) {
                                return List.of();
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

                        @Authenticated
                        interface SecuredGreetingGrpc {
                        }
                        """)
                .addSource("GreetingRequest.java", """
                        package com.example;

                        class GreetingRequest {
                        }
                        """)
                .addSource("GreetingReply.java", """
                        package com.example;

                        class GreetingReply {
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
        assertThat(registration, containsString("validateProtoMethod(declarative__proto, \"Greeting\", \"SayHello\", "
                                                        + "false, false);"));
        assertThat(registration, containsString("validateProtoMethod(declarative__proto, \"Greeting\", \"StreamHello\", "
                                                        + "false, true);"));
        assertThat(registration, containsString("validateProtoMethod(declarative__proto, \"Greeting\", \"IterableHello\", "
                                                        + "false, true);"));
        assertThat(registration, containsString("validateProtoMethod(declarative__proto, \"Greeting\", \"CollectHello\", "
                                                        + "true, false);"));
        assertThat(registration, containsString("validateProtoMethod(declarative__proto, \"Greeting\", "
                                                        + "\"CollectIterableHello\", true, false);"));
        assertThat(registration, containsString("validateProtoMethod(declarative__proto, \"Greeting\", \"ChatHello\", "
                                                        + "true, true);"));
        assertThat(registration, containsString("validateProtoMethod(declarative__proto, \"Greeting\", "
                                                        + "\"ChatIterableHello\", true, true);"));
        assertThat(registration, containsString("validateProtoMethod(declarative__proto, \"Greeting\", "
                                                        + "\"ChatObserverHello\", true, true);"));
        assertThat(registration, containsString("private static void validateProtoMethod"));
        assertThat(registration, containsString("method.isClientStreaming() != clientStreaming"));
        assertThat(registration, containsString("method.isServerStreaming() != serverStreaming"));
        assertThat(registration, containsString("private static String methodType"));
        assertThat(registration, containsString(".unary(\"SayHello\", this::sayHello"));
        assertThat(registration, containsString(".unary(\"ValidatedHello\", this::validatedHello"));
        assertThat(registration, containsString(".unary(\"InterceptedHello\", this::interceptedHello"));
        assertThat(registration, containsString(".serverStreaming(\"StreamHello\", this::streamHello"));
        assertThat(registration, containsString(".serverStreaming(\"IterableHello\", this::iterableHello"));
        assertThat(registration, containsString(".clientStreaming(\"CollectHello\", this::collectHello"));
        assertThat(registration, containsString(".clientStreaming(\"CollectIterableHello\", this::collectIterableHello"));
        assertThat(registration, containsString(".bidirectional(\"ChatHello\", this::chatHello"));
        assertThat(registration, containsString(".bidirectional(\"ChatIterableHello\", this::chatIterableHello"));
        assertThat(registration, containsString(".bidirectional(\"ChatObserverHello\", this::chatObserverHello"));
        assertThat(registration, containsString("if (entryPoints.hasInterceptors()) {"));
        assertThat(registration, containsString("entryPoints.interceptor("));
        assertThat(registration, containsString("GrpcSecurity.enforce().authenticate().securityLevel("));
        assertThat(registration, containsString(".classAnnotations(CLASS_ANNOTATIONS).build()).configure(builder);"));
        assertThat(registration, containsString("GrpcSecurity.enforce().authorize().securityLevel("));
        assertThat(registration, containsString("GrpcSecurity.enforce().rolesAllowed(\"admin\").securityLevel("));
        assertThat(registration, containsString("GrpcSecurity.enforce().skipAuthentication().skipAuthorization().securityLevel("));
        assertThat(registration, containsString("GrpcSecurity.enforce().authenticate().authorize().securityLevel("));
        assertThat(registration, containsString("GrpcSecurity.enforce().audit().auditEventType(\"declarativeGrpc\")"));
        assertThat(registration, containsString(".auditMessageFormat(\"%3$s %1$s \\\"%2$s\\\" %5$s %6$s "
                                                        + "requested by %4$s\")"));
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
        assertThat(registration, containsString("private static final TypedElementInfo METHOD_STREAM_HELLO"));
        assertThat(registration, containsString("private static final TypedElementInfo METHOD_ITERABLE_HELLO"));
        assertThat(registration, containsString("private static final TypedElementInfo METHOD_COLLECT_HELLO"));
        assertThat(registration, containsString("private static final TypedElementInfo METHOD_COLLECT_ITERABLE_HELLO"));
        assertThat(registration, containsString("private static final TypedElementInfo METHOD_CHAT_HELLO"));
        assertThat(registration, containsString("private static final TypedElementInfo METHOD_CHAT_ITERABLE_HELLO"));
        assertThat(registration, containsString("private static final TypedElementInfo METHOD_CHAT_OBSERVER_HELLO"));
        assertThat(registration, containsString("METHOD_VALIDATED_HELLO));"));
        assertThat(registration, containsString("METHOD_INTERCEPTED_HELLO));"));
        assertThat(registration, containsString("responseObserver.onNext(endpoint.sayHello(request));"));
        assertThat(registration, containsString("responseObserver.onNext(endpoint.validatedHello(request));"));
        assertThat(registration, containsString("responseObserver.onNext(endpoint.interceptedHello(request));"));
        assertThat(registration, containsString("GrpcStreams.serverStreaming(endpoint.streamHello(request), responseObserver);"));
        assertThat(registration, containsString("GrpcStreams.serverStreaming(endpoint.iterableHello(request), responseObserver);"));
        assertThat(registration, containsString("GrpcStreams.clientStreamingStream(requests -> endpoint.collectHello(requests), "
                                                        + "responseObserver);"));
        assertThat(registration, containsString("GrpcStreams.clientStreaming(requests -> endpoint.collectIterableHello(requests), "
                                                        + "responseObserver);"));
        assertThat(registration, containsString("GrpcStreams.bidirectionalStream(requests -> endpoint.chatHello(requests), "
                                                        + "responseObserver);"));
        assertThat(registration, containsString("GrpcStreams.bidirectional(requests -> endpoint.chatIterableHello(requests), "
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
    void grpcServiceRequiresProtoMethod() {
        var result = compileGrpcService("grpc-server-missing-proto", """
                @Grpc.Unary("SayHello")
                GreetingReply sayHello(GreetingRequest request) {
                    return new GreetingReply();
                }
                """);

        assertCompilationFails(result,
                               "Declarative gRPC service com.example.GreetingGrpc must declare exactly one proto descriptor source: "
                                       + "either @Grpc.ProtoDescriptor on the type or one @Grpc.Proto method.");
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
                                                    return new GreetingReply();
                                                }
                                                """,
                                        """
                                                package com.example;

                                                import com.google.protobuf.Descriptors;

                                                class DeclarativeGrpcProto {
                                                    static Descriptors.FileDescriptor getDescriptor() {
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
                                                    return new GreetingReply();
                                                }
                                                """,
                                        """
                                                package com.example;

                                                import com.google.protobuf.Descriptors;

                                                class DeclarativeGrpcProto {
                                                    static Descriptors.FileDescriptor getDescriptor() {
                                                        return null;
                                                    }
                                                }
                                                """);

        assertCompilationFails(result,
                               "Declarative gRPC service com.example.GreetingGrpc must declare exactly one proto descriptor source: "
                                       + "either @Grpc.ProtoDescriptor on the type or one @Grpc.Proto method.");
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
                                                    return new GreetingReply();
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
                               "@Grpc.ProtoDescriptor on com.example.GreetingGrpc must reference a type with a static "
                                       + "no-argument getDescriptor() method returning "
                                       + "com.google.protobuf.Descriptors.FileDescriptor: com.example.DeclarativeGrpcProto");
    }

    @Test
    void grpcServiceUsesClassNameWhenServiceNameIsBlank() throws IOException {
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
                        @Service.Singleton
                        class Greeting {
                            @Grpc.Proto
                            Descriptors.FileDescriptor proto() {
                                return null;
                            }

                            @Grpc.Unary("SayHello")
                            GreetingReply sayHello(GreetingRequest request) {
                                return new GreetingReply();
                            }
                        }
                        """)
                .addSource("GreetingRequest.java", """
                        package com.example;

                        class GreetingRequest {
                        }
                        """)
                .addSource("GreetingReply.java", """
                        package com.example;

                        class GreetingReply {
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

        Path generatedRegistration = result.sourceOutput().resolve("com/example/Greeting__GrpcRegistration.java");
        assertThat("Generated source should exist: " + generatedRegistration, Files.exists(generatedRegistration), is(true));

        String registration = Files.readString(generatedRegistration, StandardCharsets.UTF_8);
        assertThat(registration, containsString("GrpcServiceDescriptor.builder(Greeting.class, \"Greeting\")"));
        assertThat(registration, containsString("validateProtoMethod(declarative__proto, \"Greeting\", \"SayHello\", "
                                                        + "false, false);"));
    }

    @Test
    void grpcServiceRejectsMultipleProtoMethods() {
        var result = compileGrpcService("grpc-server-multiple-proto", """
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
                    return new GreetingReply();
                }
                """);

        assertCompilationFails(result,
                               "Declarative gRPC service com.example.GreetingGrpc "
                                       + "must declare exactly one proto descriptor source: either "
                                       + "@Grpc.ProtoDescriptor on the type or one @Grpc.Proto method.");
    }

    @Test
    void grpcServiceRequiresGrpcMethod() {
        var result = compileGrpcService("grpc-server-no-methods", """
                @Grpc.Proto
                Descriptors.FileDescriptor proto() {
                    return null;
                }
                """);

        assertCompilationFails(result,
                               "Declarative gRPC service com.example.GreetingGrpc must declare at least one gRPC method.");
    }

    @Test
    void grpcProtoMethodRequiresFileDescriptorWithoutParameters() {
        var result = compileGrpcService("grpc-server-bad-proto", """
                @Grpc.Proto
                String proto(GreetingRequest request) {
                    return "";
                }

                @Grpc.Unary("SayHello")
                GreetingReply sayHello(GreetingRequest request) {
                    return new GreetingReply();
                }
                """);

        assertCompilationFails(result,
                               "@Grpc.Proto method on com.example.GreetingGrpc must return "
                                       + "com.google.protobuf.Descriptors.FileDescriptor and have no parameters.");
    }

    @Test
    void grpcMethodMustNotDeclareCheckedException() {
        var result = compileGrpcService("grpc-server-checked-exception", """
                @Grpc.Proto
                Descriptors.FileDescriptor proto() {
                    return null;
                }

                @Grpc.Unary("SayHello")
                GreetingReply sayHello(GreetingRequest request) throws IOException {
                    return new GreetingReply();
                }
                """);

        assertCompilationFails(result,
                               "Declarative gRPC server methods must not declare checked exceptions: "
                                       + "com.example.GreetingGrpc.sayHello()");
    }

    @Test
    void grpcUnaryMethodRejectsUnsupportedSignature() {
        var result = compileGrpcService("grpc-server-bad-unary-signature", """
                @Grpc.Proto
                Descriptors.FileDescriptor proto() {
                    return null;
                }

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
                @Grpc.Proto
                Descriptors.FileDescriptor proto() {
                    return null;
                }

                @Grpc.ClientStreaming("CollectHello")
                GreetingReply collectHello(GreetingRequest request) {
                    return new GreetingReply();
                }
                """);

        assertCompilationFails(result,
                               "Unsupported declarative gRPC method signature for "
                                       + "com.example.GreetingGrpc.collectHello()");
    }

    @Test
    void grpcServerStreamingMethodRejectsRequestStreamingSignature() {
        var result = compileGrpcService("grpc-server-bad-server-streaming-signature", """
                @Grpc.Proto
                Descriptors.FileDescriptor proto() {
                    return null;
                }

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
    void grpcMethodRejectsMultipleMethodAnnotations() {
        var result = compileGrpcService("grpc-server-multiple-method-annotations", """
                @Grpc.Proto
                Descriptors.FileDescriptor proto() {
                    return null;
                }

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
                                        "",
                                        "implements GreetingContract",
                                        """
                                                @Grpc.Proto
                                                Descriptors.FileDescriptor proto() {
                                                    return null;
                                                }

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
                                                """);

        assertCompilationFails(result,
                               "Declarative gRPC server method com.example.GreetingGrpc.sayHello() "
                                       + "must declare exactly one gRPC method annotation.");
    }

    @Test
    void grpcProtoMethodRejectsMethodAnnotation() {
        var result = compileGrpcService("grpc-server-proto-method-annotation", """
                @Grpc.Proto
                @Grpc.Unary("Proto")
                Descriptors.FileDescriptor proto() {
                    return null;
                }

                @Grpc.Unary("SayHello")
                GreetingReply sayHello(GreetingRequest request) {
                    return new GreetingReply();
                }
                """);

        assertCompilationFails(result,
                               "@Grpc.Proto method on com.example.GreetingGrpc "
                                       + "must not declare a gRPC method annotation.");
    }

    @Test
    void grpcMethodRejectsUnsupportedMethodType() {
        var result = compileGrpcService("grpc-server-unsupported-method-type", """
                @Grpc.Proto
                Descriptors.FileDescriptor proto() {
                    return null;
                }

                @Grpc.GrpcMethod(MethodDescriptor.MethodType.UNKNOWN)
                GreetingReply unknown(GreetingRequest request) {
                    return new GreetingReply();
                }
                """);

        assertCompilationFails(result,
                                       "Declarative gRPC server supports only unary, server streaming, client streaming, "
                                               + "and bidirectional streaming methods in this version. Method unknown() uses UNKNOWN.");
    }

    @Test
    void grpcMethodRejectsExplicitAuthorization() {
        var result = compileGrpcService("grpc-server-explicit-authorized", """
                @Grpc.Proto
                Descriptors.FileDescriptor proto() {
                    return null;
                }

                @Grpc.Unary("SayHello")
                @Authorized(explicit = true)
                GreetingReply sayHello(GreetingRequest request) {
                    return new GreetingReply();
                }
                """);

        assertCompilationFails(result,
                               "Declarative gRPC does not support @Authorized(explicit = true): "
                                       + "com.example.GreetingGrpc.sayHello()");
    }

    private static TestCompiler.Result compileGrpcService(String workDir, String serviceBody) {
        return compileGrpcService(workDir, "", "", "", serviceBody);
    }

    private static TestCompiler.Result compileGrpcService(String workDir,
                                                          String supportingTypes,
                                                          String serviceDeclaration,
                                                          String serviceBody) {
        return compileGrpcService(workDir, supportingTypes, "", serviceDeclaration, serviceBody);
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

                        class GreetingRequest {
                        }
                        """)
                .addSource("GreetingReply.java", """
                        package com.example;

                        class GreetingReply {
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
