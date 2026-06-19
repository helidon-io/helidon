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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.helidon.builder.api.Prototype;
import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Generated;
import io.helidon.common.configurable.Resource;
import io.helidon.common.tls.Tls;
import io.helidon.common.types.Annotation;
import io.helidon.config.Config;
import io.helidon.config.ConfigBuilderSupport;
import io.helidon.grpc.api.Grpc;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.webclient.api.HttpClientConfig;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webclient.grpc.GrpcClientConfig;
import io.helidon.webclient.grpc.GrpcClientMethodDescriptor;
import io.helidon.webclient.grpc.GrpcClientProtocolConfig;
import io.helidon.webclient.grpc.GrpcServiceClient;
import io.helidon.webclient.grpc.GrpcServiceDescriptor;
import io.helidon.webclient.spi.Protocol;

import io.grpc.CallCredentials;
import io.grpc.Channel;
import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class GrpcClientCodegenTest {
    private static final List<Class<?>> CLASSPATH = List.of(
            Annotation.class,
            CallCredentials.class,
            Channel.class,
            ClientInterceptor.class,
            Config.class,
            ConfigBuilderSupport.class,
            Dependency.class,
            Generated.class,
            Grpc.class,
            GrpcClient.class,
            GrpcClientConfig.class,
            GrpcClientMethodDescriptor.class,
            GrpcClientProtocolConfig.class,
            GrpcServiceClient.class,
            GrpcServiceDescriptor.class,
            HttpClientConfig.class,
            MethodDescriptor.class,
            Protocol.class,
            Prototype.class,
            Resource.class,
            Service.class,
            ServiceDescriptor.class,
            StreamObserver.class,
            Tls.class,
            WebClient.class
    );

    @Test
    void generatedClientUsesGrpcServiceClient() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/grpc-client"))
                .addSource("GreetingClient.java", """
                        package com.example;

                        import java.util.Iterator;

                        import io.grpc.stub.StreamObserver;
                        import io.helidon.grpc.api.Grpc;
                        import io.helidon.webclient.grpc.GrpcClient;

                        @GrpcClient.Endpoint(value = "http://localhost:${grpc.port}",
                                             configKey = "grpc.clients.greeting")
                        @Grpc.GrpcService("GreetingService")
                        interface GreetingClient {
                            @Grpc.Unary("Greet")
                            GreetingReply greet(GreetingRequest request);

                            @Grpc.Unary("AsyncGreet")
                            void asyncGreet(GreetingRequest request, StreamObserver<GreetingReply> responseObserver);

                            @Grpc.ServerStreaming("Split")
                            Iterator<GreetingReply> split(GreetingRequest request);

                            @Grpc.ServerStreaming("AsyncSplit")
                            void asyncSplit(GreetingRequest request, StreamObserver<GreetingReply> responseObserver);

                            @Grpc.ClientStreaming("Join")
                            GreetingReply join(Iterator<GreetingRequest> requests);

                            @Grpc.ClientStreaming("AsyncJoin")
                            StreamObserver<GreetingRequest> asyncJoin(StreamObserver<GreetingReply> responseObserver);

                            @Grpc.Bidirectional("Chat")
                            Iterator<GreetingReply> chat(Iterator<GreetingRequest> requests);

                            @Grpc.Bidirectional("AsyncChat")
                            StreamObserver<GreetingRequest> asyncChat(StreamObserver<GreetingReply> responseObserver);
                        }

                        class GreetingRequest {
                        }

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

        Path generatedClient = result.sourceOutput().resolve("com/example/GreetingClient__GrpcClient.java");
        assertThat("Generated source should exist: " + generatedClient, Files.exists(generatedClient), is(true));

        String client = Files.readString(generatedClient, StandardCharsets.UTF_8);
        assertThat(client, containsString("implements GreetingClient"));
        assertThat(client, containsString("@GrpcClient.Client"));
        assertThat(client, containsString("private final GrpcServiceClient serviceClient;"));
        assertThat(client, containsString("GrpcServiceDescriptor.builder()"));
        assertThat(client, containsString(".serviceName(\"GreetingService\")"));
        assertThat(client, containsString("GrpcClientMethodDescriptor.unary(\"GreetingService\", \"Greet\")"));
        assertThat(client, containsString("GrpcClientMethodDescriptor.unary(\"GreetingService\", \"AsyncGreet\")"));
        assertThat(client, containsString("GrpcClientMethodDescriptor.serverStreaming(\"GreetingService\", \"Split\")"));
        assertThat(client, containsString("GrpcClientMethodDescriptor.serverStreaming(\"GreetingService\", \"AsyncSplit\")"));
        assertThat(client, containsString("GrpcClientMethodDescriptor.clientStreaming(\"GreetingService\", \"Join\")"));
        assertThat(client, containsString("GrpcClientMethodDescriptor.clientStreaming(\"GreetingService\", \"AsyncJoin\")"));
        assertThat(client, containsString("GrpcClientMethodDescriptor.bidirectional(\"GreetingService\", \"Chat\")"));
        assertThat(client, containsString("GrpcClientMethodDescriptor.bidirectional(\"GreetingService\", \"AsyncChat\")"));
        assertThat(client, containsString(".requestType(GreetingRequest.class)"));
        assertThat(client, containsString(".responseType(GreetingReply.class)"));
        assertThat(client, containsString("ConfigBuilderSupport.resolveExpression(config, \"http://localhost:${grpc.port}\")"));
        assertThat(client, containsString("var declarative__clientConfig = config.get(\"grpc.clients.greeting\").get(\"client\");"));
        assertThat(client, containsString("GrpcClient declarative__client = null;"));
        assertThat(client, containsString("declarative__client = registryClient.get().orElse(null);"));
        assertThat(client, containsString("declarative__clientBuilder.config(declarative__clientConfig);"));
        assertThat(client, containsString("declarative__clientBuilder.baseUri(uri);"));
        assertThat(client, containsString("uri.regionMatches(true, 0, \"http://\", 0, \"http://\".length())"));
        assertThat(client, containsString("declarative__clientBuilder.tls(it -> it.enabled(false));"));
        assertThat(client, containsString("return serviceClient.unary(\"Greet\", request);"));
        assertThat(client, containsString("serviceClient.unary(\"AsyncGreet\", request, responseObserver);"));
        assertThat(client, containsString("return serviceClient.serverStream(\"Split\", request);"));
        assertThat(client, containsString("serviceClient.serverStream(\"AsyncSplit\", request, responseObserver);"));
        assertThat(client, containsString("return serviceClient.clientStream(\"Join\", requests);"));
        assertThat(client, containsString("return serviceClient.clientStream(\"AsyncJoin\", responseObserver);"));
        assertThat(client, containsString("return serviceClient.bidi(\"Chat\", requests);"));
        assertThat(client, containsString("return serviceClient.bidi(\"AsyncChat\", responseObserver);"));
    }

    @Test
    void grpcClientEndpointMustBeInterface() {
        var result = compileGrpcClient("grpc-client-class-endpoint", """
                @GrpcClient.Endpoint("http://localhost:8080")
                @Grpc.GrpcService("GreetingService")
                class GreetingClient {
                    @Grpc.Unary("Greet")
                    GreetingReply greet(GreetingRequest request) {
                        return new GreetingReply();
                    }
                }
                """);

        assertCompilationFails(result,
                               "Types annotated with GrpcClient.Endpoint must be interfaces. This type is: CLASS");
    }

    @Test
    void grpcClientRequiresServiceName() {
        var result = compileGrpcClient("grpc-client-missing-service", """
                @GrpcClient.Endpoint("http://localhost:8080")
                interface GreetingClient {
                    @Grpc.Unary("Greet")
                    GreetingReply greet(GreetingRequest request);
                }
                """);

        assertCompilationFails(result,
                               "Declarative gRPC client com.example.GreetingClient "
                                       + "must define a non-blank @Grpc.GrpcService value.");
    }

    @Test
    void grpcClientMethodRequiresGrpcAnnotation() {
        var result = compileGrpcClient("grpc-client-missing-method-annotation", """
                @GrpcClient.Endpoint("http://localhost:8080")
                @Grpc.GrpcService("GreetingService")
                interface GreetingClient {
                    GreetingReply greet(GreetingRequest request);
                }
                """);

        assertCompilationFails(result,
                               "Declarative gRPC client method com.example.GreetingClient.greet() "
                                       + "must declare a gRPC method annotation.");
    }

    @Test
    void grpcClientMethodMustNotDeclareCheckedException() {
        var result = compileGrpcClient("grpc-client-checked-exception", """
                @GrpcClient.Endpoint("http://localhost:8080")
                @Grpc.GrpcService("GreetingService")
                interface GreetingClient {
                    @Grpc.Unary("Greet")
                    GreetingReply greet(GreetingRequest request) throws IOException;
                }
                """);

        assertCompilationFails(result,
                               "Declarative gRPC client methods must not declare checked exceptions: "
                                       + "com.example.GreetingClient.greet()");
    }

    @Test
    void grpcClientRejectsVoidUnaryMethod() {
        var result = compileGrpcClient("grpc-client-void-unary", """
                @GrpcClient.Endpoint("http://localhost:8080")
                @Grpc.GrpcService("GreetingService")
                interface GreetingClient {
                    @Grpc.Unary("Greet")
                    void greet(GreetingRequest request);
                }
                """);

        assertCompilationFails(result,
                               "Unsupported declarative gRPC client method signature for "
                                       + "com.example.GreetingClient.greet()");
    }

    @Test
    void grpcClientRejectsExtraUnaryParameters() {
        var result = compileGrpcClient("grpc-client-extra-unary-parameters", """
                @GrpcClient.Endpoint("http://localhost:8080")
                @Grpc.GrpcService("GreetingService")
                interface GreetingClient {
                    @Grpc.Unary("Greet")
                    GreetingReply greet(GreetingRequest request, GreetingRequest second);
                }
                """);

        assertCompilationFails(result,
                               "Unsupported declarative gRPC client method signature for "
                                       + "com.example.GreetingClient.greet()");
    }

    @Test
    void grpcClientRejectsUnaryMethodReturningStreamingContainer() {
        var result = compileGrpcClient("grpc-client-unary-streaming-return", """
                @GrpcClient.Endpoint("http://localhost:8080")
                @Grpc.GrpcService("GreetingService")
                interface GreetingClient {
                    @Grpc.Unary("Greet")
                    Iterator<GreetingReply> greet(GreetingRequest request);
                }
                """);

        assertCompilationFails(result,
                               "Unsupported declarative gRPC client method signature for "
                                       + "com.example.GreetingClient.greet()");
    }

    @Test
    void grpcClientRejectsServerStreamingMethodReturningUnaryResponse() {
        var result = compileGrpcClient("grpc-client-server-streaming-unary-return", """
                @GrpcClient.Endpoint("http://localhost:8080")
                @Grpc.GrpcService("GreetingService")
                interface GreetingClient {
                    @Grpc.ServerStreaming("Split")
                    GreetingReply split(GreetingRequest request);
                }
                """);

        assertCompilationFails(result,
                               "Unsupported declarative gRPC client method signature for "
                                       + "com.example.GreetingClient.split()");
    }

    @Test
    void grpcClientRejectsClientStreamingMethodWithUnaryRequest() {
        var result = compileGrpcClient("grpc-client-client-streaming-unary-request", """
                @GrpcClient.Endpoint("http://localhost:8080")
                @Grpc.GrpcService("GreetingService")
                interface GreetingClient {
                    @Grpc.ClientStreaming("Join")
                    GreetingReply join(GreetingRequest request);
                }
                """);

        assertCompilationFails(result,
                               "Unsupported declarative gRPC client method signature for "
                                       + "com.example.GreetingClient.join()");
    }

    @Test
    void grpcClientRejectsBidirectionalMethodWithUnaryRequest() {
        var result = compileGrpcClient("grpc-client-bidi-streaming-unary-request", """
                @GrpcClient.Endpoint("http://localhost:8080")
                @Grpc.GrpcService("GreetingService")
                interface GreetingClient {
                    @Grpc.Bidirectional("Chat")
                    Iterator<GreetingReply> chat(GreetingRequest request);
                }
                """);

        assertCompilationFails(result,
                               "Unsupported declarative gRPC client method signature for "
                                       + "com.example.GreetingClient.chat()");
    }

    @Test
    void grpcClientRejectsMultipleMethodAnnotations() {
        var result = compileGrpcClient("grpc-client-multiple-method-annotations", """
                @GrpcClient.Endpoint("http://localhost:8080")
                @Grpc.GrpcService("GreetingService")
                interface GreetingClient {
                    @Grpc.Unary("Greet")
                    @Grpc.ServerStreaming("Split")
                    void greet(GreetingRequest request, StreamObserver<GreetingReply> responseObserver);
                }
                """);

        assertCompilationFails(result,
                               "Declarative gRPC client method com.example.GreetingClient.greet() "
                                       + "must declare exactly one gRPC method annotation.");
    }

    private static TestCompiler.Result compileGrpcClient(String workDir, String clientType) {
        return TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/" + workDir))
                .addSource("GreetingClient.java", """
                        package com.example;

                        import java.io.IOException;
                        import java.util.Iterator;

                        import io.grpc.stub.StreamObserver;
                        import io.helidon.grpc.api.Grpc;
                        import io.helidon.webclient.grpc.GrpcClient;

                        %s

                        class GreetingRequest {
                        }

                        class GreetingReply {
                        }
                        """.formatted(clientType))
                .addSource("Main.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;

                        @Service.GenerateBinding
                        class Main {
                        }
                        """)
                .build()
                .compile();
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
