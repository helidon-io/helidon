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

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.webserver.RequestHeaders;
import io.helidon.webserver.ResponseHeaders;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.testsupport.TestClient;
import io.helidon.webserver.testsupport.TestResponse;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests {@link io.helidon.webserver.staticcontent.ClassPathContentHandler}.
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
}
