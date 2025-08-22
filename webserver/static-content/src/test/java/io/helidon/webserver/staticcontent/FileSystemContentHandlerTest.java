/*
 * Copyright (c) 2017, 2025 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.common.LogConfig;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.webserver.Routing;
import io.helidon.webserver.testsupport.TemporaryFolder;
import io.helidon.webserver.testsupport.TemporaryFolderExtension;
import io.helidon.webserver.testsupport.TestClient;
import io.helidon.webserver.testsupport.TestResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests {@link io.helidon.webserver.staticcontent.FileSystemContentHandler}.
 */
@ExtendWith(TemporaryFolderExtension.class)
class FileSystemContentHandlerTest {

    private TemporaryFolder folder;

    @BeforeEach
    public void createContent() throws IOException {
        LogConfig.configureRuntime();

        // root
        Path root = folder.root().toPath();
        Files.writeString(root.resolve("index.html"), "Index HTML");
        Files.writeString(root.resolve("foo.txt"), "Foo TXT");
        // css
        Path cssDir = folder.newFolder("css").toPath();
        Files.writeString(cssDir.resolve("a.css"), "A CSS");
        Files.writeString(cssDir.resolve("b.css"), "B CSS");
        // bar
        Path other = folder.newFolder("other").toPath();
        Files.writeString(other.resolve("index.html"), "Index HTML");
    }

    static String responseToString(TestResponse response)
            throws InterruptedException, ExecutionException, TimeoutException {
        return response.asBytes()
                        .thenApply(bytes -> new String(bytes, StandardCharsets.UTF_8))
                        .toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);
    }

    @Test
    void serveFile() throws Exception {
        Routing routing = Routing.builder()
                                 .register("/some", StaticContentSupport.create(folder.root().toPath()))
                                 .build();
        // /some/foo.txt
        TestResponse response = TestClient.create(routing)
                                          .path("/some/foo.txt")
                                          .get();
        assertThat(response.status(), is(Http.Status.OK_200));
        assertThat(responseToString(response), is("Foo TXT"));
        assertThat(response.headers().first(Http.Header.CONTENT_TYPE).orElse(null), is(MediaType.TEXT_PLAIN.toString()));
        // /some/css/b.css
        response = TestClient.create(routing)
                .path("/some/css/b.css")
                .get();
        assertThat(response.status(), is(Http.Status.OK_200));
        assertThat(responseToString(response), is("B CSS"));
        // /some/css/not.exists
        response = TestClient.create(routing)
                .path("/some/css/not.exists")
                .get();
        assertThat(response.status(), is(Http.Status.NOT_FOUND_404));
        // /some/css
        response = TestClient.create(routing)
                .path("/some/css")
                .get();
        assertThat(response.status(), is(Http.Status.MOVED_PERMANENTLY_301));
        assertThat(response.headers().first(Http.Header.LOCATION).orElse(null), is("/some/css/"));
        // /some/css/
        response = TestClient.create(routing)
                .path("/some/css/")
                .get();
        assertThat(response.status(), is(Http.Status.NOT_FOUND_404));
    }

    @Test
    void serveIndex() throws Exception {
        Routing routing = Routing.builder()
                .register(StaticContentSupport.builder(folder.root().toPath())
                                              .welcomeFileName("index.html")
                                              .contentType("css", MediaType.TEXT_PLAIN)
                                              .build())
                .build();
        // /
        TestResponse response = TestClient.create(routing)
                .path("/")
                .get();
        assertThat(response.status(), is(Http.Status.OK_200));
        assertThat(responseToString(response), is("Index HTML"));
        assertThat(response.headers().first(Http.Header.CONTENT_TYPE).orElse(null), is(MediaType.TEXT_HTML.toString()));
        // /other
        response = TestClient.create(routing)
                .path("/other")
                .get();
        assertThat(response.status(), is(Http.Status.MOVED_PERMANENTLY_301));
        assertThat(response.headers().first(Http.Header.LOCATION).orElse(null), is("/other/"));
        // /other/
        response = TestClient.create(routing)
                .path("/other/")
                .get();
        assertThat(response.status(), is(Http.Status.OK_200));
        assertThat(responseToString(response), is("Index HTML"));
        assertThat(response.headers().first(Http.Header.CONTENT_TYPE).orElse(null), is(MediaType.TEXT_HTML.toString()));
        // /css/
        response = TestClient.create(routing)
                .path("/css/")
                .get();
        assertThat(response.status(), is(Http.Status.NOT_FOUND_404));
        // /css/a.css
        response = TestClient.create(routing)
                .path("/css/a.css")
                .get();
        assertThat(response.status(), is(Http.Status.OK_200));
        assertThat(responseToString(response), is("A CSS"));
        assertThat(response.headers().first(Http.Header.CONTENT_TYPE).orElse(null), is(MediaType.TEXT_PLAIN.toString()));
    }

    @Test
    void badCharacters() throws InterruptedException, TimeoutException {
        Routing routing = Routing.builder()
                .register("/some", StaticContentSupport.create(folder.root().toPath()))
                .build();
        TestResponse response;

        // /some/css/not.exists
        response = TestClient.create(routing)
                .path("/some/css/.%00/not.exists")
                .get();
        assertThat(response.status(), is(Http.Status.NOT_FOUND_404));
    }
}
