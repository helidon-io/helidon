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
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClassPathResourceConnectionsTest {
    private static final String RESOURCE = "web/resource.txt";
    private static final String DIRECTORY = "web/directory/";
    private static final String CONTENT = "Static content from a jar";

    @TempDir
    Path tempDir;

    @Test
    void closesJarConnectionsAfterExtractingContent() throws Exception {
        try (JarResources resources = new JarResources(tempDir.resolve("content.jar"))) {
            ClassPathContentHandler handler = new ClassPathContentHandler(handlerConfig(resources).build());
            try {
                handler.beforeStart();
                assertContent(handler);
                resources.assertClosed(2);
            } finally {
                handler.afterStop();
            }
        }
    }

    @Test
    void closesJarConnectionsWhenStreamingWithoutTemporaryStorage() throws Exception {
        try (JarResources resources = new JarResources(tempDir.resolve("content.jar"))) {
            ClassPathContentHandler handler = new ClassPathContentHandler(handlerConfig(resources)
                    .temporaryStorage(TemporaryStorage.create(builder -> builder.enabled(false)))
                    .build());
            try {
                handler.beforeStart();
                assertContent(handler);
                resources.assertClosed(2);

                assertContent(handler);
                resources.assertClosed(3);
            } finally {
                handler.afterStop();
            }
        }
    }

    @Test
    void closesJarConnectionsAfterCachingContentInMemory() throws Exception {
        try (JarResources resources = new JarResources(tempDir.resolve("content.jar"))) {
            ClassPathContentHandler handler = new ClassPathContentHandler(handlerConfig(resources)
                    .memoryCache(MemoryCache.builder().enabled(true).build())
                    .build());
            try {
                handler.beforeStart();
                assertContent(handler);
                resources.assertClosed(2);

                assertContent(handler);
                resources.assertClosed(2);
            } finally {
                handler.afterStop();
            }
        }
    }

    @Test
    void closesJarConnectionsAfterPreloadingContent() throws Exception {
        try (JarResources resources = new JarResources(tempDir.resolve("content.jar"))) {
            ClassPathContentHandler handler = new ClassPathContentHandler(handlerConfig(resources)
                    .cachedFiles(Set.of("resource.txt"))
                    .build());
            try {
                handler.beforeStart();
                resources.assertClosed(2);

                assertContent(handler);
                resources.assertClosed(2);
            } finally {
                handler.afterStop();
            }
        }
    }

    @Test
    void closesJarConnectionWhenResourceIsDirectory() throws Exception {
        try (JarResources resources = new JarResources(tempDir.resolve("content.jar"))) {
            ClassPathContentHandler handler = new ClassPathContentHandler(handlerConfig(resources).build());
            try {
                handler.beforeStart();
                assertThat(handler.cachedHandler(DIRECTORY, resources.getResource(DIRECTORY)), is(Optional.empty()));
                resources.assertClosed(1);
            } finally {
                handler.afterStop();
            }
        }
    }

    private static void assertContent(ClassPathContentHandler handler) throws Exception {
        ServerRequest request = mock(ServerRequest.class);
        when(request.headers()).thenReturn(ServerRequestHeaders.create());
        when(request.prologue()).thenReturn(HttpPrologue.create("http/1.1",
                                                                "http",
                                                                "1.1",
                                                                Method.GET,
                                                                "/resource.txt",
                                                                false));

        try (ByteArrayOutputStream content = new ByteArrayOutputStream()) {
            ServerResponse response = mock(ServerResponse.class);
            when(response.headers()).thenReturn(ServerResponseHeaders.create());
            when(response.outputStream()).thenReturn(content);
            doAnswer(invocation -> {
                content.writeBytes(invocation.getArgument(0));
                return null;
            }).when(response).send(any(byte[].class));

            assertThat(handler.doHandle(Method.GET, "resource.txt", request, response, false), is(true));
            assertThat(content.toString(StandardCharsets.UTF_8), is(CONTENT));
        }
    }

    private ClasspathHandlerConfig.Builder handlerConfig(ClassLoader classLoader) {
        return ClasspathHandlerConfig.builder()
                .location("/web")
                .classLoader(classLoader)
                .temporaryStorage(TemporaryStorage.create(builder -> builder
                        .directory(tempDir)
                        .deleteOnExit(false)));
    }

    private static final class JarResources extends ClassLoader implements AutoCloseable {
        private final List<JarURLConnection> connections = new ArrayList<>();
        private final Path jarPath;

        private JarResources(Path jarPath) throws IOException {
            super(null);
            this.jarPath = jarPath;
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jarPath))) {
                zip.putNextEntry(new ZipEntry(RESOURCE));
                zip.write(CONTENT.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                zip.putNextEntry(new ZipEntry(DIRECTORY));
                zip.closeEntry();
            }
        }

        @Override
        public URL getResource(String name) {
            if (!RESOURCE.equals(name) && !DIRECTORY.equals(name)) {
                return null;
            }
            try {
                URI uri = URI.create("jar:" + jarPath.toUri() + "!/" + name);
                URL original = uri.toURL();
                return URL.of(uri, new URLStreamHandler() {
                    @Override
                    protected URLConnection openConnection(URL url) throws IOException {
                        JarURLConnection connection = (JarURLConnection) original.openConnection();
                        connections.add(connection);
                        return connection;
                    }
                });
            } catch (MalformedURLException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void close() throws IOException {
            for (JarURLConnection connection : connections) {
                connection.getJarFile().close();
            }
        }

        private void assertClosed(int expectedConnections) throws IOException {
            assertThat(connections, hasSize(expectedConnections));
            for (JarURLConnection connection : connections) {
                assertThat(connection.getUseCaches(), is(false));
                JarFile jarFile = connection.getJarFile();
                assertThrows(IllegalStateException.class, jarFile::size);
            }
        }
    }
}
