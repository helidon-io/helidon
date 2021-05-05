/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.webserver.Routing;
import io.helidon.webserver.StaticContentSupport;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests {@link io.helidon.webserver.ClassPathContentHandler}.
 */
public class ClassPathContentHandlerTest {
    
    private String filterResponse(TestResponse response) throws InterruptedException, ExecutionException, TimeoutException {
        String s = FileSystemContentHandlerTest.responseToString(response);
        if (s == null) {
            return s;
        }
        List<String> lines = new ArrayList<>();
        StringTokenizer stok = new StringTokenizer(s, "\n\r");
        while (stok.hasMoreTokens()) {
            String line = stok.nextToken();
            String trim = line.trim();
            if (!trim.startsWith("#") && !trim.isEmpty()) {
                lines.add(line);
            }
        }
        return lines.stream().collect(Collectors.joining("\n"));
    }

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
}
