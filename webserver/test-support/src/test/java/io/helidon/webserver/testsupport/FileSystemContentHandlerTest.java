/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.testsupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.webserver.Routing;
import io.helidon.webserver.StaticContentSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link io.helidon.webserver.FileSystemContentHandler}.
 */
@ExtendWith(TemporaryFolderExtension.class)
public class FileSystemContentHandlerTest {

    private TemporaryFolder folder;

    @BeforeEach
    public void createContent() throws IOException {
        // root
        Path root = folder.getRoot().toPath();
        Files.write(root.resolve("index.html"), "Index HTML".getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("foo.txt"), "Foo TXT".getBytes(StandardCharsets.UTF_8));
        // css
        Path cssDir = folder.newFolder("css").toPath();
        Files.write(cssDir.resolve("a.css"), "A CSS".getBytes(StandardCharsets.UTF_8));
        Files.write(cssDir.resolve("b.css"), "B CSS".getBytes(StandardCharsets.UTF_8));
        // bar
        Path other = folder.newFolder("other").toPath();
        Files.write(other.resolve("index.html"), "Index HTML".getBytes(StandardCharsets.UTF_8));
    }

    static String responseToString(TestResponse response)
            throws InterruptedException, ExecutionException, TimeoutException {
        return response.asBytes()
                        .thenApply(bytes -> new String(bytes, StandardCharsets.UTF_8))
                        .toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);
    }

    @Test
    public void serveFile() throws Exception {
        try {
        Routing routing = Routing.builder()
                                 .register("/some", StaticContentSupport.create(folder.getRoot().toPath()))
                                 .build();
        // /some/foo.txt
        TestResponse response = TestClient.create(routing)
                                          .path("/some/foo.txt")
                                          .get();
        assertEquals(Http.Status.OK_200, response.status());
        assertEquals("Foo TXT", responseToString(response));
        assertEquals(MediaType.TEXT_PLAIN.toString(), response.headers().first(Http.Header.CONTENT_TYPE).orElse(null));
        // /some/css/b.css
        response = TestClient.create(routing)
                .path("/some/css/b.css")
                .get();
        assertEquals(Http.Status.OK_200, response.status());
        assertEquals("B CSS", responseToString(response));
        // /some/css/not.exists
        response = TestClient.create(routing)
                .path("/some/css/not.exists")
                .get();
        assertEquals(Http.Status.NOT_FOUND_404, response.status());
        // /some/css
        response = TestClient.create(routing)
                .path("/some/css")
                .get();
        assertEquals(Http.Status.MOVED_PERMANENTLY_301, response.status());
        assertEquals("/some/css/", response.headers().first(Http.Header.LOCATION).orElse(null));
        // /some/css/
        response = TestClient.create(routing)
                .path("/some/css/")
                .get();
        assertEquals(Http.Status.NOT_FOUND_404, response.status());
        } catch(Throwable ex){
            ex.printStackTrace();
        }
    }

    @Test
    public void serveIndex() throws Exception {
        Routing routing = Routing.builder()
                .register(StaticContentSupport.builder(folder.getRoot().toPath())
                                              .welcomeFileName("index.html")
                                              .contentType("css", MediaType.TEXT_PLAIN)
                                              .build())
                .build();
        // /
        TestResponse response = TestClient.create(routing)
                .path("/")
                .get();
        assertEquals(Http.Status.OK_200, response.status());
        assertEquals("Index HTML", responseToString(response));
        assertEquals(MediaType.TEXT_HTML.toString(), response.headers().first(Http.Header.CONTENT_TYPE).orElse(null));
        // /other
        response = TestClient.create(routing)
                .path("/other")
                .get();
        assertEquals(Http.Status.MOVED_PERMANENTLY_301, response.status());
        assertEquals("/other/", response.headers().first(Http.Header.LOCATION).orElse(null));
        // /other/
        response = TestClient.create(routing)
                .path("/other/")
                .get();
        assertEquals(Http.Status.OK_200, response.status());
        assertEquals("Index HTML", responseToString(response));
        assertEquals(MediaType.TEXT_HTML.toString(), response.headers().first(Http.Header.CONTENT_TYPE).orElse(null));
        // /css/
        response = TestClient.create(routing)
                .path("/css/")
                .get();
        assertEquals(Http.Status.NOT_FOUND_404, response.status());
        // /css/a.css
        response = TestClient.create(routing)
                .path("/css/a.css")
                .get();
        assertEquals(Http.Status.OK_200, response.status());
        assertEquals("A CSS", responseToString(response));
        assertEquals(MediaType.TEXT_PLAIN.toString(), response.headers().first(Http.Header.CONTENT_TYPE).orElse(null));
    }
}
