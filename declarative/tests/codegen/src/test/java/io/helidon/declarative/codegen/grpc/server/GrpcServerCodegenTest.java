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
import io.helidon.security.annotations.Authenticated;
import io.helidon.security.annotations.Authorized;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.Interception;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.validation.Validation;
import io.helidon.webserver.grpc.GrpcEntryPoint;
import io.helidon.webserver.grpc.GrpcRouteRegistration;
import io.helidon.webserver.grpc.GrpcServiceDescriptor;
import io.helidon.webserver.grpc.security.GrpcSecurity;

import com.google.protobuf.Descriptors;
import io.grpc.MethodDescriptor;
import io.grpc.ServerInterceptor;
import io.grpc.stub.StreamObserver;
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
            Authenticated.class,
            Authorized.class,
            MethodDescriptor.class,
            NamedService.class,
            Prototype.class,
            RolesAllowed.class,
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

                        import com.google.protobuf.Descriptors;

                        import java.lang.annotation.ElementType;
                        import java.lang.annotation.Retention;
                        import java.lang.annotation.RetentionPolicy;
                        import java.lang.annotation.Target;

                        import io.grpc.stub.StreamObserver;
                        import io.helidon.grpc.api.Grpc;
                        import io.helidon.security.annotations.Authenticated;
                        import io.helidon.security.annotations.Authorized;
                        import io.helidon.service.registry.Interception;
                        import io.helidon.service.registry.Service;
                        import io.helidon.validation.Validation;
                        import jakarta.annotation.security.RolesAllowed;

                        @Grpc.GrpcService("Greeting")
                        @Service.Singleton
                        class GreetingGrpc {
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

                            @Grpc.ServerStreaming("StreamHello")
                            void streamHello(GreetingRequest request, StreamObserver<GreetingReply> responseObserver) {
                            }

                            @Grpc.ClientStreaming("CollectHello")
                            StreamObserver<GreetingRequest> collectHello(StreamObserver<GreetingReply> responseObserver) {
                                return null;
                            }

                            @Grpc.Bidirectional("ChatHello")
                            StreamObserver<GreetingRequest> chatHello(StreamObserver<GreetingReply> responseObserver) {
                                return null;
                            }
                        }

                        class GreetingRequest {
                        }

                        class GreetingReply {
                        }

                        @Validation.Constraint
                        @Target(ElementType.PARAMETER)
                        @interface ValidGreetingRequest {
                        }

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
        assertThat(registration, containsString("GrpcServiceDescriptor.builder(GreetingGrpc.class, \"Greeting\")"));
        assertThat(registration, containsString(".proto(proto())"));
        assertThat(registration, containsString(".unary(\"SayHello\", this::sayHello"));
        assertThat(registration, containsString(".unary(\"ValidatedHello\", this::validatedHello"));
        assertThat(registration, containsString(".unary(\"InterceptedHello\", this::interceptedHello"));
        assertThat(registration, containsString(".serverStreaming(\"StreamHello\", this::streamHello"));
        assertThat(registration, containsString(".clientStreaming(\"CollectHello\", this::collectHello"));
        assertThat(registration, containsString(".bidirectional(\"ChatHello\", this::chatHello"));
        assertThat(registration, containsString("entryPoints.interceptor("));
        assertThat(registration, containsString("GrpcSecurity.enforce().authenticate().configure(rules);"));
        assertThat(registration, containsString("GrpcSecurity.enforce().authorize().configure(rules);"));
        assertThat(registration, containsString("GrpcSecurity.enforce().rolesAllowed(\"admin\").configure(rules);"));
        assertThat(registration, containsString("private static final TypedElementInfo METHOD_SAY_HELLO"));
        assertThat(registration, containsString("private static final TypedElementInfo METHOD_VALIDATED_HELLO"));
        assertThat(registration, containsString("private static final TypedElementInfo METHOD_INTERCEPTED_HELLO"));
        assertThat(registration, containsString("private static final TypedElementInfo METHOD_AUTHORIZE_HELLO"));
        assertThat(registration, containsString("private static final TypedElementInfo METHOD_ADMIN_HELLO"));
        assertThat(registration, containsString("private static final TypedElementInfo METHOD_STREAM_HELLO"));
        assertThat(registration, containsString("private static final TypedElementInfo METHOD_COLLECT_HELLO"));
        assertThat(registration, containsString("private static final TypedElementInfo METHOD_CHAT_HELLO"));
        assertThat(registration, containsString("METHOD_VALIDATED_HELLO));"));
        assertThat(registration, containsString("METHOD_INTERCEPTED_HELLO));"));
        assertThat(registration, containsString("responseObserver.onNext(endpoint.get().sayHello(request));"));
        assertThat(registration, containsString("responseObserver.onNext(endpoint.get().validatedHello(request));"));
        assertThat(registration, containsString("responseObserver.onNext(endpoint.get().interceptedHello(request));"));
        assertThat(registration, containsString("endpoint.get().streamHello(request, responseObserver);"));
        assertThat(registration, containsString("return endpoint.get().collectHello(responseObserver);"));
        assertThat(registration, containsString("return endpoint.get().chatHello(responseObserver);"));

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
}
