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

package io.helidon.declarative.codegen.websocket.server;

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
import io.helidon.common.parameters.Parameters;
import io.helidon.common.types.Annotation;
import io.helidon.common.uri.UriPath;
import io.helidon.config.Config;
import io.helidon.http.BadRequestException;
import io.helidon.http.Headers;
import io.helidon.http.Http;
import io.helidon.http.HttpPrologue;
import io.helidon.http.PathMatchers;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.websocket.WebSocket;
import io.helidon.websocket.WsListenerBase;
import io.helidon.websocket.WsUpgradeException;
import io.helidon.webserver.Route;
import io.helidon.webserver.websocket.WebSocketServer;
import io.helidon.webserver.websocket.WsRoute;
import io.helidon.webserver.websocket.WsRouteRegistration;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class WebSocketServerMappedPathParamCodegenTest {
    private static final List<Class<?>> CLASSPATH = List.of(
            Annotation.class,
            BadRequestException.class,
            Config.class,
            Dependency.class,
            Generated.class,
            GenericType.class,
            Headers.class,
            Http.class,
            HttpPrologue.class,
            Mappers.class,
            Parameters.class,
            PathMatchers.class,
            Prototype.class,
            Route.class,
            Service.class,
            ServiceDescriptor.class,
            UriPath.class,
            WebSocket.class,
            WebSocketServer.class,
            WsListenerBase.class,
            WsRoute.class,
            WsRouteRegistration.class,
            WsUpgradeException.class
    );

    @Test
    void generatedServerListenerUsesSegmentedPathQualifiers() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(Path.of("target/test-compiler/websocket-server-mapped-path-param"))
                .addSource("MappedPathWebSocketEndpoint.java", """
                        package com.example;

                        import io.helidon.http.Http;
                        import io.helidon.service.registry.Service;
                        import io.helidon.webserver.websocket.WebSocketServer;
                        import io.helidon.websocket.WebSocket;

                        @WebSocketServer.Endpoint
                        @Service.Singleton
                        @Http.Path("/ws/{id}")
                        class MappedPathWebSocketEndpoint {
                            @WebSocket.OnHttpUpgrade
                            void onUpgrade(@Http.PathParam("id") Integer id) {
                            }
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
        var generatedListener = result.sourceOutput().resolve("com/example/MappedPathWebSocketEndpoint__WsListener.java");
        assertThat(diagnostics, Files.exists(generatedListener), is(true));

        String listenerContent = Files.readString(generatedListener, StandardCharsets.UTF_8);
        assertThat(listenerContent, containsString("var params = matched.path().pathParameters();"));
        assertThat(listenerContent,
                   containsString("mappers.map(it, GenericType.STRING, GTYPE, me -> new BadRequestException(\"Path"
                                          + " Param id has invalid value.\", me), \"http\", \"path\")"));
        assertThat(listenerContent, not(containsString("\"http/path\"")));
        assertThat(diagnostics, result.success(), is(true));
    }
}
