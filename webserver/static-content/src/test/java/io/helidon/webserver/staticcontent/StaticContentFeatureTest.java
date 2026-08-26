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

package io.helidon.webserver.staticcontent;

import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Predicate;

import io.helidon.http.HeaderNames;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.PathMatcher;
import io.helidon.http.RoutedPath;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.WritableHeaders;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.spi.ServerFeature.ServerFeatureContext;
import io.helidon.webserver.spi.ServerFeature.SocketBuilders;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.noHeader;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaticContentFeatureTest {
    @Test
    void testSetupWithNullContextClassLoader() {
        StaticContentFeature feature = StaticContentFeature.create(builder -> builder
                .addClasspath(it -> it.location("web")));
        ServerFeatureContext context = mock(ServerFeatureContext.class);
        SocketBuilders socketBuilders = mock(SocketBuilders.class);
        HttpRouting.Builder routing = mock(HttpRouting.Builder.class);

        when(context.sockets()).thenReturn(Set.of());
        when(context.socketExists(WebServer.DEFAULT_SOCKET_NAME)).thenReturn(true);
        when(context.socket(WebServer.DEFAULT_SOCKET_NAME)).thenReturn(socketBuilders);
        when(socketBuilders.httpRouting()).thenReturn(routing);

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);

            feature.setup(context);

            verify(routing).register(eq("/"), any(HttpService.class));
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void testCrossOriginSourcingFeatureDefaultAndHandlerOverride(@TempDir Path tempDir) throws Exception {
        Path identityRoot = tempDir.resolve("identity");
        Path sidecarRoot = tempDir.resolve("sidecar");
        Files.createDirectories(identityRoot.resolve("web"));
        Files.createDirectories(sidecarRoot.resolve("web"));
        Files.writeString(identityRoot.resolve("web/resource.txt"), "Content");
        Files.writeString(sidecarRoot.resolve("web/resource.txt.br"), "Brotli content");

        URL[] classPath = {identityRoot.toUri().toURL(), sidecarRoot.toUri().toURL()};
        try (URLClassLoader classLoader = new URLClassLoader(classPath, null)) {
            StaticContentFeature feature = StaticContentFeature.create(builder -> builder
                    .preCompressedCrossOriginSourcingEnabled(true)
                    .addClasspath(classpath -> classpath
                            .context("/inherited")
                            .location("/web")
                            .classLoader(classLoader))
                    .addClasspath(classpath -> classpath
                            .context("/overridden")
                            .location("/web")
                            .classLoader(classLoader)
                            .preCompressedCrossOriginSourcingEnabled(false)));

            ServerFeatureContext featureContext = mock(ServerFeatureContext.class);
            SocketBuilders socketBuilders = mock(SocketBuilders.class);
            HttpRouting.Builder routing = mock(HttpRouting.Builder.class);
            when(featureContext.sockets()).thenReturn(Set.of());
            when(featureContext.socketExists(WebServer.DEFAULT_SOCKET_NAME)).thenReturn(true);
            when(featureContext.socket(WebServer.DEFAULT_SOCKET_NAME)).thenReturn(socketBuilders);
            when(socketBuilders.httpRouting()).thenReturn(routing);

            feature.setup(featureContext);

            ArgumentCaptor<HttpService> inheritedService = ArgumentCaptor.forClass(HttpService.class);
            verify(routing).register(eq("/inherited"), inheritedService.capture());
            assertResponse(inheritedService.getValue(), "br", "Brotli content");

            ArgumentCaptor<HttpService> overriddenService = ArgumentCaptor.forClass(HttpService.class);
            verify(routing).register(eq("/overridden"), overriddenService.capture());
            assertResponse(overriddenService.getValue(), null, "Content");
        }
    }

    private static void assertResponse(HttpService service, String contentEncoding, String expectedBody) throws Exception {
        HttpRules rules = mock(HttpRules.class);
        ArgumentCaptor<Handler> handler = ArgumentCaptor.forClass(Handler.class);
        service.routing(rules);
        verify(rules).route(ArgumentMatchers.<Predicate<Method>>any(), any(PathMatcher.class), handler.capture());

        WritableHeaders<?> requestHeaders = WritableHeaders.create();
        requestHeaders.add(HeaderNames.ACCEPT_ENCODING, "br");
        RoutedPath path = mock(RoutedPath.class);
        when(path.rawPathNoParams()).thenReturn("/resource.txt");
        ServerRequest request = mock(ServerRequest.class);
        when(request.headers()).thenReturn(ServerRequestHeaders.create(requestHeaders));
        when(request.path()).thenReturn(path);
        when(request.prologue()).thenReturn(HttpPrologue.create("http/1.1",
                                                                "http",
                                                                "1.1",
                                                                Method.GET,
                                                                "/resource.txt",
                                                                false));

        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        ServerResponse response = mock(ServerResponse.class);
        when(response.headers()).thenReturn(responseHeaders);
        when(response.outputStream()).thenReturn(body);

        handler.getValue().handle(request, response);

        if (contentEncoding == null) {
            assertThat(responseHeaders, noHeader(HeaderNames.CONTENT_ENCODING));
        } else {
            assertThat(responseHeaders, hasHeader(HeaderNames.CONTENT_ENCODING, contentEncoding));
        }
        assertThat(body.toString(StandardCharsets.UTF_8), is(expectedBody));
    }
}
