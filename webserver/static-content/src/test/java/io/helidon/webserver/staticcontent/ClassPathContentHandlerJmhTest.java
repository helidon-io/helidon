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

import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import io.helidon.common.http.Http;
import io.helidon.webserver.Routing;
import io.helidon.webserver.testsupport.TestClient;
import io.helidon.webserver.testsupport.TestResponse;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.ThreadParams;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ClassPathContentHandlerJmhTest {
    private static final int RESOURCE_COUNT = 32;
    private static final byte[] CONTENT = "cached classpath resource".getBytes(StandardCharsets.UTF_8);

    @Test
    void cachedRequestsReturnExpectedContent() throws Exception {
        for (String method : List.of("GET", "HEAD", "CONDITIONAL")) {
            CachedResources resources = new CachedResources();
            resources.method = method;
            resources.setup();
            try {
                RequestPath path = new RequestPath();
                path.path = "/resource-0.txt";
                int expected = switch (method) {
                case "GET" -> CONTENT.length;
                case "HEAD" -> 200;
                default -> 304;
                };
                assertThat(cachedRequest(resources, path), is(expected));
            } finally {
                resources.close();
            }
        }
    }

    @Benchmark
    public int cachedRequest(CachedResources resources, RequestPath path) throws Exception {
        var request = resources.client.path(path.path);
        if (resources.method.equals("CONDITIONAL")) {
            request.header(Http.Header.IF_NONE_MATCH, "*");
        }
        TestResponse response = resources.method.equals("HEAD") ? request.head() : request.get();
        Http.Status expected = resources.method.equals("CONDITIONAL") ? Http.Status.NOT_MODIFIED_304 : Http.Status.OK_200;
        if (response.status() != expected) {
            throw new IllegalStateException("Unexpected response status: " + response.status());
        }
        byte[] body = response.asBytes().get(10, TimeUnit.SECONDS);
        return resources.method.equals("GET") ? body.length : response.status().code();
    }

    @State(Scope.Benchmark)
    public static class CachedResources {
        @Param({"GET", "HEAD", "CONDITIONAL"})
        public String method;

        private Path directory;
        private URLClassLoader loader;
        private JarFile cachedJar;
        private ClassPathContentHandler handler;
        private TestClient client;

        @Setup
        public void setup() throws Exception {
            directory = Files.createTempDirectory("helidon-static-content-jmh");
            try {
                Path jar = directory.resolve("resources.jar");
                try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
                    for (int index = 0; index < RESOURCE_COUNT; index++) {
                        output.putNextEntry(new JarEntry("static/resource-" + index + ".txt"));
                        output.write(CONTENT);
                        output.closeEntry();
                    }
                }
                Path extracted = Files.createDirectory(directory.resolve("extracted"));
                loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null);
                // JarURLConnection has a separate cache from URLClassLoader; own both handles for this private fixture JAR.
                JarURLConnection connection = (JarURLConnection) loader.getResource("static/resource-0.txt").openConnection();
                cachedJar = connection.getJarFile();
                handler = new ClassPathContentHandler(StaticContentSupport.builder("static", loader).tmpDir(extracted));
                client = TestClient.create(Routing.builder().register(handler).build());
                for (int index = 0; index < RESOURCE_COUNT; index++) {
                    TestResponse response = client.path("/resource-" + index + ".txt").get();
                    if (response.status() != Http.Status.OK_200
                            || response.asBytes().get(10, TimeUnit.SECONDS).length != CONTENT.length) {
                        throw new IllegalStateException("Could not warm static resource " + index);
                    }
                }
            } catch (Exception | Error failure) {
                try {
                    close();
                } catch (Exception cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }

        @TearDown
        public void close() throws Exception {
            try {
                if (handler != null) {
                    handler.releaseCache();
                }
            } finally {
                try {
                    if (cachedJar != null) {
                        cachedJar.close();
                    }
                } finally {
                    try {
                        if (loader != null) {
                            loader.close();
                        }
                    } finally {
                        if (directory != null) {
                            try (Stream<Path> paths = Files.walk(directory)) {
                                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                                    Files.deleteIfExists(path);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @State(Scope.Thread)
    public static class RequestPath {
        private String path;

        @Setup
        public void setup(ThreadParams thread) {
            if (thread.getThreadIndex() >= RESOURCE_COUNT) {
                throw new IllegalArgumentException("Benchmark supports at most " + RESOURCE_COUNT + " threads");
            }
            path = "/resource-" + thread.getThreadIndex() + ".txt";
        }
    }
}
