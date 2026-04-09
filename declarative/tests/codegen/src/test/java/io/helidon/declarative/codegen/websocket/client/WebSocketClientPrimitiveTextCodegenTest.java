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

package io.helidon.declarative.codegen.websocket.client;

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
import io.helidon.common.mapper.Mappers;
import io.helidon.common.types.Annotation;
import io.helidon.config.Config;
import io.helidon.http.Http;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.webclient.websocket.WebSocketClient;
import io.helidon.websocket.WebSocket;
import io.helidon.websocket.WsSession;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class WebSocketClientPrimitiveTextCodegenTest {
    private static final List<Class<?>> CLASSPATH = List.of(
            Annotation.class,
            Config.class,
            Dependency.class,
            Generated.class,
            GenericType.class,
            Http.class,
            Mappers.class,
            Prototype.class,
            Service.class,
            ServiceDescriptor.class,
            WebSocket.class,
            WebSocketClient.class,
            WsSession.class
    );

    @Test
    void generatedPrimitiveTextListenerCompilesWithoutPathParams() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/websocket-client-primitive-text"))
                .addSource("PrimitiveClientEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webclient.websocket.WebSocketClient;
                        import io.helidon.websocket.WebSocket;

                        @SuppressWarnings("deprecation")
                        @WebSocketClient.Endpoint("ws://localhost:8080")
                        @Service.Singleton
                        @Http.Path("/primitive")
                        class PrimitiveClientEndpoint {
                            @WebSocket.OnMessage
                            void onMessage(int message) {
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));

        var generatedListener = result.sourceOutput().resolve("com/example/PrimitiveClientEndpoint__WsListener.java");
        assertThat(diagnostics, Files.exists(generatedListener), is(true));

        var listenerContent = Files.readString(generatedListener, StandardCharsets.UTF_8);
        assertThat(listenerContent, containsString("private final Mappers mappers;"));
        assertThat(listenerContent, containsString("this.mappers = mappers;"));

        var generatedFactory = result.sourceOutput().resolve("com/example/PrimitiveClientEndpointFactory.java");
        assertThat(diagnostics, Files.exists(generatedFactory), is(true));

        var factoryContent = Files.readString(generatedFactory, StandardCharsets.UTF_8);
        assertThat(factoryContent,
                   containsString("new PrimitiveClientEndpoint__WsListener(mappers, endpointSupplier.get())"));
    }
}
