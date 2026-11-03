/*
 * Copyright (c) 2017, 2026 Oracle and/or its affiliates.
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

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Single;
import io.helidon.webserver.HttpException;
import io.helidon.webserver.RequestHeaders;
import io.helidon.webserver.ResponseHeaders;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.testsupport.TestClient;
import io.helidon.webserver.testsupport.TestResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link io.helidon.webserver.staticcontent.ClassPathContentHandler}.
 */
public class ClassPathContentHandlerTest {
    private static final String EXTRACTED_FILE_PREFIX = "EXTRACTED_FILE=";
    private static final URLStreamHandler UNCACHED_JAR_HANDLER = new URLStreamHandler() {
        @Override
        protected URLConnection openConnection(URL url) throws IOException {
            URLConnection connection = new URL(url.toExternalForm()).openConnection();
            connection.setUseCaches(false);
            return connection;
        }
    };

    @Test
    public void resourceSlashAgnostic() throws Exception {
        // Without slash
        Routing routing = Routing.builder()
                                 .register("/some", StaticContentSupport.create("content"))
                                 .build();
        // /some/root-a.txt
        TestResponse response = TestClient.create(routing)
                                          .path("/some/root-a.txt")
                                          .get();
        assertThat(response.status(), is(Http.Status.OK_200));
        // With slash
        routing = Routing.builder()
                .register("/some", StaticContentSupport.create("/content"))
                .build();
        // /some/root-a.txt
        response = TestClient.create(routing)
                .path("/some/root-a.txt")
                .get();
        assertThat(response.status(), is(Http.Status.OK_200));
    }

    @Test
    public void serveFromFiles() throws Exception {
        Routing routing = Routing.builder()
                .register("/some", StaticContentSupport.create("content"))
                .build();
        // /some/root-a.txt
        TestResponse response = TestClient.create(routing)
                .path("/some/root-a.txt")
                .get();
        assertThat(response.status(), is(Http.Status.OK_200));
        assertThat(filterResponse(response), is("- root A TXT"));
        assertThat(response.headers().first(Http.Header.CONTENT_TYPE), is(Optional.of(MediaType.TEXT_PLAIN.toString())));
        // /some/bar/root-a.txt
        response = TestClient.create(routing)
                .path("/some/bar/root-b.txt")
                .get();
        assertThat(response.status(), is(Http.Status.OK_200));
        assertThat(filterResponse(response), is("- root B TXT"));
        assertThat(response.headers().first(Http.Header.CONTENT_TYPE), is(Optional.of(MediaType.TEXT_PLAIN.toString())));
        // /some/bar/not.exist
        response = TestClient.create(routing)
                .path("/some/bar/not.exist")
                .get();
        assertThat(response.status(), is(Http.Status.NOT_FOUND_404));
    }

    @Test
    public void usesNormalizedResourceForClasspathLookup() throws Exception {
        URL rootResource = Objects.requireNonNull(ClassPathContentHandlerTest.class.getClassLoader()
                                                     .getResource("content/root-a.txt"));
        CapturingClassLoader classLoader = new CapturingClassLoader(rootResource);
        RecordingClassPathContentHandler handler = new RecordingClassPathContentHandler(
                StaticContentSupport.builder("content", classLoader));
        ServerRequest request = Mockito.mock(ServerRequest.class);
        ServerResponse response = Mockito.mock(ServerResponse.class);
        RequestHeaders requestHeaders = Mockito.mock(RequestHeaders.class);
        ResponseHeaders responseHeaders = Mockito.mock(ResponseHeaders.class);
        Mockito.doReturn(requestHeaders).when(request).headers();
        Mockito.doReturn(responseHeaders).when(response).headers();
        Mockito.doReturn(true).when(requestHeaders).isAccepted(Mockito.any(MediaType.class));

        boolean handled = handler.doHandle(Http.Method.GET, "bar/../root-a.txt", request, response);

        assertThat(handled, is(true));
        assertThat(classLoader.requestedResource, is("content/root-a.txt"));
        assertThat(handler.sentPath, is(Paths.get(rootResource.toURI())));
    }

    @Test
    public void doesNotLookupResourceOutsideClasspathRoot() throws Exception {
        CapturingClassLoader classLoader = new CapturingClassLoader(null);
        ClassPathContentHandler handler = new ClassPathContentHandler(StaticContentSupport.builder("content", classLoader));

        boolean handled = handler.doHandle(Http.Method.GET, "bar/../../s-internal/example-a.txt", null, null);

        assertThat(handled, is(false));
        assertThat(classLoader.requestedResource, is((String) null));
    }

    @Test
    public void badAcceptTypes() throws Exception {
        Routing routing = Routing.builder()
                .register("/some", StaticContentSupport.create("content"))
                .build();
        // /some/root-a.txt
        TestResponse response = TestClient.create(routing)
                .path("/some/root-a.txt")
                .header("Accept", "86279333026148305409260801579029123580362081")
                .get();
        assertThat(response.status(), is(Http.Status.BAD_REQUEST_400));
    }

    @Test
    public void serveFromFilesWithWelcome() throws Exception {
        Routing routing = Routing.builder()
                .register(StaticContentSupport.builder("content")
                                              .welcomeFileName("index.txt"))
                .build();
        // /
        TestResponse response = TestClient.create(routing)
                .path("/")
                .get();
        assertThat(response.status(), is(Http.Status.OK_200));
        assertThat(filterResponse(response), is("- index TXT"));
        assertThat(response.headers().first(Http.Header.CONTENT_TYPE), is(Optional.of(MediaType.TEXT_PLAIN.toString())));
        // /bar/
        response = TestClient.create(routing)
                .path("/bar/")
                .get();
        assertThat(response.status(), is(Http.Status.NOT_FOUND_404));
    }

    @Test
    public void serveFromJar() throws Exception {
        Routing routing = Routing.builder()
                .register("/some", StaticContentSupport.create("s-internal"))
                .build();
        // /some/example-a.txt
        TestResponse response = TestClient.create(routing)
                .path("/some/example-a.txt")
                .get();
        assertThat(response.status(), is(Http.Status.OK_200));
        assertThat(filterResponse(response), is("Example A TXT"));
        assertThat(response.headers().first(Http.Header.CONTENT_TYPE), is(Optional.of(MediaType.TEXT_PLAIN.toString())));
        // /some/example-a.txt
        response = TestClient.create(routing)
                .path("/some/a/example-a.txt")
                .get();
        assertThat(response.status(), is(Http.Status.OK_200));
        assertThat(filterResponse(response), is("A / Example A TXT"));
        assertThat(response.headers().first(Http.Header.CONTENT_TYPE), is(Optional.of(MediaType.TEXT_PLAIN.toString())));
        // /some/a/not.exist
        response = TestClient.create(routing)
                .path("/some/a/not.exist")
                .get();
        assertThat(response.status(), is(Http.Status.NOT_FOUND_404));
    }

    @Test
    public void serveFromJarWithWelcome() throws Exception {
        Routing routing = Routing.builder()
                .register(StaticContentSupport.builder("/s-internal")
                                  .welcomeFileName("example-a.txt"))
                .build();
        // /
        TestResponse response = TestClient.create(routing)
                .path("/")
                .get();
        assertThat(response.status(), is(Http.Status.OK_200));
        assertThat(filterResponse(response), is("Example A TXT"));
        assertThat(response.headers().first(Http.Header.CONTENT_TYPE), is(Optional.of(MediaType.TEXT_PLAIN.toString())));
        // /a
        response = TestClient.create(routing)
                .path("/a/")
                .get();
        assertThat(response.status(), is(Http.Status.OK_200));

        // redirect to /a/
        response = TestClient.create(routing)
                .path("/a")
                .get();
        assertThat(response.status(), is(Http.Status.MOVED_PERMANENTLY_301));
        assertThat(response.headers().first("Location"), is(Optional.of("/a/")));

        // another index
        routing = Routing.builder()
                .register(StaticContentSupport.builder("/s-internal")
                                  .welcomeFileName("example-b.txt"))
                .build();
        // /a/
        response = TestClient.create(routing)
                .path("/a/")
                .get();
        assertThat(response.status(), is(Http.Status.NOT_FOUND_404));
    }

    @Test
    public void releasesJarFilesWhenLastServerStops(@TempDir Path directory) throws Exception {
        Path jar = createJar(directory);
        Path temporary = Files.createDirectory(directory.resolve("temporary"));
        ClassPathContentHandler handler = new ClassPathContentHandler(StaticContentSupport.builder("static", jarLoader(jar))
                                                                          .tmpDir(temporary));
        Routing.Rules rules = Mockito.mock(Routing.Rules.class);
        AtomicReference<Consumer<WebServer>> callback = new AtomicReference<>();
        Mockito.doAnswer(invocation -> {
            callback.set(invocation.getArgument(0));
            return rules;
        }).when(rules).onNewWebServer(Mockito.any());
        handler.update(rules);

        WebServer firstServer = Mockito.mock(WebServer.class);
        WebServer secondServer = Mockito.mock(WebServer.class);
        CompletableFuture<WebServer> firstShutdown = new CompletableFuture<>();
        CompletableFuture<WebServer> secondShutdown = new CompletableFuture<>();
        Mockito.when(firstServer.whenShutdown()).thenReturn(Single.create(firstShutdown));
        Mockito.when(secondServer.whenShutdown()).thenReturn(Single.create(secondShutdown));
        callback.get().accept(firstServer);
        callback.get().accept(secondServer);

        try {
            TestClient client = TestClient.create(Routing.builder().register(handler).build());
            serveJarFile(client, "first.txt");
            serveJarFile(client, "first.txt");
            serveJarFile(client, "second.txt");
            assertThat(extractedFiles(temporary), is(2L));

            firstShutdown.complete(firstServer);
            assertThat(extractedFiles(temporary), is(2L));
            secondShutdown.complete(secondServer);
            assertThat(extractedFiles(temporary), is(0L));

            serveJarFile(client, "first.txt");
            assertThat(extractedFiles(temporary), is(1L));
        } finally {
            handler.releaseCache();
        }
    }

    @Test
    public void removesIncompleteJarExtraction(@TempDir Path directory) throws Exception {
        Path jar = createJar(directory);
        Path temporary = Files.createDirectory(directory.resolve("temporary"));
        JarFile jarFile = Mockito.mock(JarFile.class);
        JarEntry entry = new JarEntry("static/first.txt");
        Mockito.when(jarFile.getName()).thenReturn(jar.toString());
        Mockito.when(jarFile.getInputStream(entry)).thenReturn(new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Failed resource stream");
            }
        });
        JarURLConnection connection = Mockito.mock(JarURLConnection.class);
        Mockito.when(connection.getJarFile()).thenReturn(jarFile);
        Mockito.when(connection.getJarEntry()).thenReturn(entry);
        URL resource = new URL(null, "jar:" + jar.toUri() + "!/static/first.txt", new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) {
                return connection;
            }
        });
        ClassLoader loader = Mockito.mock(ClassLoader.class);
        Mockito.when(loader.getResource("static/first.txt")).thenReturn(resource);
        ClassPathContentHandler handler = new ClassPathContentHandler(StaticContentSupport.builder("static", loader)
                                                                          .tmpDir(temporary));
        try {
            HttpException failure = assertThrows(HttpException.class,
                                                 () -> handler.doHandle(Http.Method.GET, "first.txt", null, null));
            assertThat(failure.status(), is(Http.Status.INTERNAL_SERVER_ERROR_500));
            assertThat(extractedFiles(temporary), is(0L));
        } finally {
            handler.releaseCache();
        }
    }

    @Test
    public void closesUncachedJarConnectionForDirectory() throws Exception {
        JarFile jarFile = Mockito.mock(JarFile.class);
        JarURLConnection connection = Mockito.mock(JarURLConnection.class);
        Mockito.when(connection.getJarFile()).thenReturn(jarFile);
        Mockito.when(connection.getJarEntry()).thenReturn(new JarEntry("static/folder/"));
        URL resource = new URL(null, "jar:file:/unused.jar!/static/folder/", new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) {
                return connection;
            }
        });
        ClassLoader loader = Mockito.mock(ClassLoader.class);
        Mockito.when(loader.getResource("static/folder/")).thenReturn(resource);
        ClassPathContentHandler handler = new ClassPathContentHandler(StaticContentSupport.builder("static", loader));

        assertThat(handler.doHandle(Http.Method.GET, "folder/", null, null), is(false));
        Mockito.verify(jarFile).close();
    }

    @Test
    public void waitsForJarExtractionBeforeReleasingCache(@TempDir Path directory) throws Exception {
        Path jar = createJar(directory);
        Path temporary = Files.createDirectory(directory.resolve("temporary"));
        CountDownLatch extractionStarted = new CountDownLatch(1);
        CountDownLatch finishExtraction = new CountDownLatch(1);
        InputStream stream = new FilterInputStream(new ByteArrayInputStream("first.txt".getBytes(StandardCharsets.UTF_8))) {
            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException {
                extractionStarted.countDown();
                try {
                    if (!finishExtraction.await(10, TimeUnit.SECONDS)) {
                        throw new IOException("Timed out waiting for extraction");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while extracting", e);
                }
                return super.read(bytes, offset, length);
            }
        };
        JarFile jarFile = Mockito.mock(JarFile.class);
        JarEntry entry = new JarEntry("static/first.txt");
        Mockito.when(jarFile.getName()).thenReturn(jar.toString());
        Mockito.when(jarFile.getInputStream(entry)).thenReturn(stream);
        JarURLConnection connection = Mockito.mock(JarURLConnection.class);
        Mockito.when(connection.getJarFile()).thenReturn(jarFile);
        Mockito.when(connection.getJarEntry()).thenReturn(entry);
        URL resource = new URL(null, "jar:" + jar.toUri() + "!/static/first.txt", new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) {
                return connection;
            }
        });
        ClassLoader loader = Mockito.mock(ClassLoader.class);
        Mockito.when(loader.getResource("static/first.txt")).thenReturn(resource);
        RecordingClassPathContentHandler handler = new RecordingClassPathContentHandler(
                StaticContentSupport.builder("static", loader).tmpDir(temporary));
        ServerRequest request = Mockito.mock(ServerRequest.class);
        RequestHeaders headers = Mockito.mock(RequestHeaders.class);
        Mockito.when(request.headers()).thenReturn(headers);
        Mockito.when(headers.values(Mockito.anyString())).thenReturn(Collections.emptyList());
        Mockito.when(headers.isAccepted(Mockito.any(MediaType.class))).thenReturn(true);
        Mockito.when(headers.ifModifiedSince()).thenReturn(Optional.empty());
        Mockito.when(headers.ifUnmodifiedSince()).thenReturn(Optional.empty());
        ServerResponse response = Mockito.mock(ServerResponse.class);
        Mockito.when(response.headers()).thenReturn(Mockito.mock(ResponseHeaders.class));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Throwable> cleanupFailure = new AtomicReference<>();
        Thread releasing = new Thread(() -> {
            try {
                handler.releaseCache();
            } catch (Throwable failure) {
                cleanupFailure.set(failure);
            }
        }, "static-content-cache-release");
        try {
            Future<Boolean> serving = executor.submit(() -> handler.doHandle(Http.Method.GET, "first.txt", request, response));
            assertThat(extractionStarted.await(10, TimeUnit.SECONDS), is(true));
            releasing.start();
            // This dedicated worker can only wait in releaseCache while extraction is paused.
            // Observing its blocking state proves it entered cleanup, rather than merely being scheduled to do so.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            Thread.State cleanupState = releasing.getState();
            while (releasing.isAlive() && cleanupState != Thread.State.WAITING && System.nanoTime() < deadline) {
                Thread.onSpinWait();
                cleanupState = releasing.getState();
            }
            assertThat(cleanupState, is(Thread.State.WAITING));
            finishExtraction.countDown();
            assertThat(serving.get(10, TimeUnit.SECONDS), is(true));
            releasing.join(TimeUnit.SECONDS.toMillis(10));
            assertThat(releasing.isAlive(), is(false));
            assertThat(cleanupFailure.get(), nullValue());
            assertThat(extractedFiles(temporary), is(0L));
        } finally {
            finishExtraction.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS), is(true));
            releasing.join(TimeUnit.SECONDS.toMillis(10));
            assertThat(releasing.isAlive(), is(false));
            handler.releaseCache();
        }
    }

    @Test
    public void removesJarFilesOnJvmExit(@TempDir Path directory) throws Exception {
        Path jar = createJar(directory);
        Path temporary = Files.createDirectory(directory.resolve("temporary-é"));
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Process process = new ProcessBuilder(Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
                                             "-Dfile.encoding=ISO-8859-1",
                                             "-cp", classpath, ExtractionProbe.class.getName(),
                                             jar.toString(), temporary.toString())
                .redirectErrorStream(true)
                .start();
        try {
            assertThat(process.waitFor(30, TimeUnit.SECONDS), is(true));
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(output, containsString(EXTRACTED_FILE_PREFIX));
            assertThat(process.exitValue(), is(0));
            Path extracted = Paths.get(output.substring(output.indexOf(EXTRACTED_FILE_PREFIX)
                                                       + EXTRACTED_FILE_PREFIX.length()).split("\\R", 2)[0]);
            assertThat(extracted.getParent(), is(temporary));
            assertThat(Files.notExists(extracted), is(true));
        } finally {
            process.destroyForcibly();
        }
    }

    private static Path createJar(Path directory) throws IOException {
        Path jar = directory.resolve("content.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            for (String name : List.of("first.txt", "second.txt")) {
                output.putNextEntry(new JarEntry("static/" + name));
                output.write(name.getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return jar;
    }

    private static ClassLoader jarLoader(Path jar) throws IOException {
        ClassLoader loader = Mockito.mock(ClassLoader.class);
        for (String name : List.of("first.txt", "second.txt")) {
            URL resource = new URL(null, "jar:" + jar.toUri() + "!/static/" + name, UNCACHED_JAR_HANDLER);
            Mockito.when(loader.getResource("static/" + name)).thenReturn(resource);
        }
        return loader;
    }

    private static long extractedFiles(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.count();
        }
    }

    private static void serveJarFile(TestClient client, String name) throws Exception {
        TestResponse response = client.path("/" + name).get();
        assertThat(response.status(), is(Http.Status.OK_200));
        assertThat(response.asString().get(10, TimeUnit.SECONDS), is(name));
    }

    private String filterResponse(TestResponse response) throws InterruptedException, ExecutionException, TimeoutException {
        String content = FileSystemContentHandlerTest.responseToString(response);
        if (content == null) {
            return content;
        }
        List<String> lines = new ArrayList<>();
        StringTokenizer stok = new StringTokenizer(content, "\n\r");
        while (stok.hasMoreTokens()) {
            String line = stok.nextToken();
            String trim = line.trim();
            if (!trim.startsWith("#") && !trim.isEmpty()) {
                lines.add(line);
            }
        }
        return lines.stream().collect(Collectors.joining("\n"));
    }

    private static class CapturingClassLoader extends ClassLoader {
        private final URL resource;
        private String requestedResource;

        CapturingClassLoader(URL resource) {
            this.resource = resource;
        }

        @Override
        public URL getResource(String name) {
            requestedResource = name;
            return resource;
        }
    }

    private static class RecordingClassPathContentHandler extends ClassPathContentHandler {
        private Path sentPath;

        RecordingClassPathContentHandler(StaticContentSupport.ClassPathBuilder builder) {
            super(builder);
        }

        @Override
        void send(ServerResponse response, Path path) {
            sentPath = path;
        }
    }

    public static class ExtractionProbe {
        public static void main(String[] args) throws Exception {
            Path jar = Paths.get(args[0]);
            Path temporary = Paths.get(args[1]);
            ClassPathContentHandler handler = new ClassPathContentHandler(StaticContentSupport.builder("static", jarLoader(jar))
                                                                              .tmpDir(temporary));
            TestClient client = TestClient.create(Routing.builder().register(handler).build());
            serveJarFile(client, "first.txt");
            assertThat(extractedFiles(temporary), is(1L));
            try (Stream<Path> files = Files.list(temporary)) {
                System.out.write((EXTRACTED_FILE_PREFIX + files.findFirst().orElseThrow() + System.lineSeparator())
                                         .getBytes(StandardCharsets.UTF_8));
                System.out.flush();
            }
        }
    }
}
