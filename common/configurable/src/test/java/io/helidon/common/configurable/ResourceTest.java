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

package io.helidon.common.configurable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link Resource}.
 */
class ResourceTest {
    private static final String COPYRIGHT_TEXT = "Copyright (c) 2017,2018 Oracle and/or its affiliates.";
    // intentionally UTF-8 string
    private static final String STRING_CONTENT = "abcdefgčřžúů";
    private static Config config;

    @BeforeAll
    static void initClass() {
        config = Config.create().get("resources");
    }

    @Test
    void testString() throws IOException {
        Resource r = Resource.create("unitTest", STRING_CONTENT);

        assertThat(r.string(), is(STRING_CONTENT));
        assertThat(r.string(StandardCharsets.UTF_8), is(STRING_CONTENT));

        String other = new String(r.bytes(), StandardCharsets.UTF_8);
        assertThat(other, is(STRING_CONTENT));

        assertThat(r.location(), is("unitTest"));
        assertThat(r.sourceType(), is(Resource.Source.CONTENT));

        InputStream is = r.stream();
        byte[] buffer = new byte[128];
        int read = is.read(buffer);
        String s = new String(buffer, 0, read, StandardCharsets.UTF_8);
        assertThat(s, is(STRING_CONTENT));
    }

    @Test
    void testStreamOnlyOnce() throws IOException {
        Resource r = Resource.create("unit-test", new ByteArrayInputStream(STRING_CONTENT.getBytes(StandardCharsets.UTF_8)));

        InputStream is = r.stream();
        byte[] buffer = new byte[128];
        int read = is.read(buffer);
        String s = new String(buffer, 0, read, StandardCharsets.UTF_8);
        assertThat(s, is(STRING_CONTENT));

        assertThrows(IllegalStateException.class, r::string);
    }

    @Test
    void testStreamCached() throws IOException {
        Resource r = Resource.create("unit-test", new ByteArrayInputStream(STRING_CONTENT.getBytes(StandardCharsets.UTF_8)));

        //cache it
        assertThat(r.string(), is(STRING_CONTENT));

        //get stream
        InputStream is = r.stream();
        byte[] buffer = new byte[128];
        int read = is.read(buffer);
        String s = new String(buffer, 0, read, StandardCharsets.UTF_8);
        assertThat(s, is(STRING_CONTENT));

        assertThat(r.string(), is(STRING_CONTENT));
    }

    @Test
    void testConfigPath() {
        Resource resource = config.get("test-1.resource").as(Resource::create).get();
        assertThat(resource.string().trim(), is(COPYRIGHT_TEXT));
    }

    @Test
    void testConfigClassPath() {
        Resource resource = config.get("test-2.resource").as(Resource::create).get();
        assertThat(resource.string().trim(), is(COPYRIGHT_TEXT));
    }

    @Test
    void testConfigPlainContent() {
        Resource resource = config.get("test-4.resource").as(Resource::create).get();
        assertThat(resource.string(), is("content"));
    }

    @Test
    void testConfigContent() {
        Resource resource = config.get("test-5.resource").as(Resource::create).get();
        assertThat(resource.string(), is(STRING_CONTENT));
    }

    @Test
    void testWrongConfig() {
        assertThrows(ConfigException.class, () -> config.get("test-6.resource").as(Resource::create).get());
    }

    @Test
    void testConfigUriReadTimeout() throws Exception {
        CountDownLatch accepted = new CountDownLatch(1);
        CountDownLatch releaseServer = new CountDownLatch(1);
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();

        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
            Thread serverThread = Thread.ofVirtual().start(() -> {
                try (Socket _ = serverSocket.accept()) {
                    accepted.countDown();
                    releaseServer.await();
                } catch (Throwable t) {
                    serverFailure.set(t);
                }
            });

            try {
                URI uri = URI.create("http://127.0.0.1:" + serverSocket.getLocalPort() + "/resource");
                ResourceConfig resourceConfig = ResourceConfig.builder()
                        .uri(uri)
                        .buildPrototype();

                long beforeLoad = System.nanoTime();
                assertThrows(ResourceException.class,
                             () -> Resource.create(resourceConfig, Duration.ofMillis(50)).bytes());
                long loadMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - beforeLoad);

                assertThat("test server accepted the load", accepted.await(5, TimeUnit.SECONDS), is(true));
                assertThat("stalled load respected its read timeout", loadMillis < 5_000, is(true));
            } finally {
                releaseServer.countDown();
                serverThread.join(TimeUnit.SECONDS.toMillis(5));
                assertThat("test server stopped", serverThread.isAlive(), is(false));
                assertThat("test server failure", serverFailure.get(), is((Throwable) null));
            }
        }
    }

    @Test
    void testConfigUriUsesConfiguredTimeout() {
        TestUrlStreamHandlerProvider.reset();
        URI uri = URI.create(TestUrlStreamHandlerProvider.PROTOCOL + "://resource");
        ResourceConfig resourceConfig = ResourceConfig.builder()
                .uri(uri)
                .buildPrototype();

        Resource.create(resourceConfig, Duration.ofMillis(50)).bytes();

        TestUrlStreamHandlerProvider.RecordingUrlConnection connection = TestUrlStreamHandlerProvider.connection();
        assertThat(connection.getConnectTimeout(), is(50));
        assertThat(connection.getReadTimeout(), is(50));
        assertThat(connection.getUseCaches(), is(false));
    }

    @Test
    void testConfigTimeoutWithNonUriResource() {
        ResourceConfig resourceConfig = ResourceConfig.builder()
                .contentPlain(STRING_CONTENT)
                .description("unit-test")
                .buildPrototype();

        Resource resource = Resource.create(resourceConfig, Duration.ofSeconds(1));

        assertThat(resource.string(), is(STRING_CONTENT));
    }

    @Test
    void testConfigTimeoutPreservesSourcePrecedence() {
        URI unavailableUri = URI.create("http://127.0.0.1:1/unavailable");
        ResourceConfig pathConfig = ResourceConfig.builder()
                .path(Path.of("src/test/resources/sample.txt"))
                .resourcePath("sample.txt")
                .uri(unavailableUri)
                .buildPrototype();
        ResourceConfig classpathConfig = ResourceConfig.builder()
                .resourcePath("sample.txt")
                .uri(unavailableUri)
                .buildPrototype();

        Resource pathResource = Resource.create(pathConfig, Duration.ofSeconds(1));
        Resource classpathResource = Resource.create(classpathConfig, Duration.ofSeconds(1));

        assertThat(pathResource.sourceType(), is(Resource.Source.FILE));
        assertThat(pathResource.string().trim(), is(COPYRIGHT_TEXT));
        assertThat(classpathResource.sourceType(), is(Resource.Source.CLASSPATH));
        assertThat(classpathResource.string().trim(), is(COPYRIGHT_TEXT));
    }

    @Test
    void testConfigTimeoutPreservesUriLocation() {
        URI uri = Path.of("src/test/resources/sample.txt").toUri();
        ResourceConfig resourceConfig = ResourceConfig.builder()
                .uri(uri)
                .description("configured description")
                .buildPrototype();

        Resource resource = Resource.create(resourceConfig, Duration.ofSeconds(1));

        assertThat(resource.sourceType(), is(Resource.Source.URL));
        assertThat(resource.location(), is(uri.toString()));
        assertThat(resource.string().trim(), is(COPYRIGHT_TEXT));
    }

    @Test
    void testConfigRejectsInvalidTimeout() {
        ResourceConfig resourceConfig = ResourceConfig.builder()
                .contentPlain(STRING_CONTENT)
                .buildPrototype();

        assertThrows(NullPointerException.class, () -> Resource.create(resourceConfig, null));
        assertThrows(IllegalArgumentException.class, () -> Resource.create(resourceConfig, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> Resource.create(resourceConfig, Duration.ofNanos(-1)));
    }
}
