/*
 * Copyright (c) 2018, 2024 Oracle and/or its affiliates.
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
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.configurable.LruCache;
import io.helidon.common.parameters.Parameters;
import io.helidon.common.uri.UriFragment;
import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriQuery;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpException;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.RoutedPath;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.Status;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.helidon.http.HeaderNames.ETAG;
import static io.helidon.http.HeaderNames.IF_MATCH;
import static io.helidon.http.HeaderNames.IF_NONE_MATCH;
import static io.helidon.http.HeaderNames.LOCATION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests {@link StaticContentHandler}.
 */
class StaticContentHandlerTest {
    private static final String ETAG_VALUE = "\"aaa\"";

    @Test
    void etag_InNoneMatch_NotAccept() {
        ServerRequestHeaders req = mock(ServerRequestHeaders.class);
        when(req.contains(IF_NONE_MATCH)).thenReturn(true);
        when(req.contains(IF_MATCH)).thenReturn(false);
        when(req.get(IF_NONE_MATCH)).thenReturn(HeaderValues.create(IF_NONE_MATCH, "\"ccc\"", "\"ddd\""));
        ServerResponseHeaders res = mock(ServerResponseHeaders.class);
        StaticContentHandler.processEtag("aaa", req, res);
        verify(res).set(HeaderValues.create(ETAG, true, false, ETAG_VALUE));
    }

    @Test
    void etag_InNoneMatch_Accept() {
        ServerRequestHeaders req = mock(ServerRequestHeaders.class);
        when(req.contains(IF_NONE_MATCH)).thenReturn(true);
        when(req.contains(IF_MATCH)).thenReturn(false);
        when(req.get(IF_NONE_MATCH)).thenReturn(HeaderValues.create(IF_NONE_MATCH, "\"ccc\"", "W/\"aaa\""));
        ServerResponseHeaders res = mock(ServerResponseHeaders.class);
        assertHttpException(() -> StaticContentHandler.processEtag("aaa", req, res), Status.NOT_MODIFIED_304);
        verify(res).set(HeaderValues.create(ETAG, true, false, ETAG_VALUE));
    }

    @Test
    void etag_InMatch_NotAccept() {
        ServerRequestHeaders req = mock(ServerRequestHeaders.class);
        when(req.contains(IF_NONE_MATCH)).thenReturn(false);
        when(req.contains(IF_MATCH)).thenReturn(true);
        when(req.get(IF_MATCH)).thenReturn(HeaderValues.create(IF_MATCH, "\"ccc\"", "\"ddd\""));
        ServerResponseHeaders res = mock(ServerResponseHeaders.class);
        assertHttpException(() -> StaticContentHandler.processEtag("aaa", req, res), Status.PRECONDITION_FAILED_412);
        verify(res).set(HeaderValues.create(ETAG, true, false, ETAG_VALUE));
    }

    @Test
    void etag_InMatch_Accept() {
        ServerRequestHeaders req = mock(ServerRequestHeaders.class);
        when(req.contains(IF_NONE_MATCH)).thenReturn(false);
        when(req.contains(IF_MATCH)).thenReturn(true);
        when(req.get(IF_MATCH)).thenReturn(HeaderValues.create(IF_MATCH, "\"ccc\"", "\"aaa\""));
        ServerResponseHeaders res = mock(ServerResponseHeaders.class);
        StaticContentHandler.processEtag("aaa", req, res);
        verify(res).set(HeaderValues.create(ETAG, true, false, ETAG_VALUE));
    }

    @Test
    void ifModifySince_Accept() {
        ZonedDateTime modified = ZonedDateTime.now();
        ServerRequestHeaders req = mock(ServerRequestHeaders.class);
        Mockito.doReturn(Optional.of(modified.minusSeconds(60))).when(req).ifModifiedSince();
        Mockito.doReturn(Optional.empty()).when(req).ifUnmodifiedSince();
        ServerResponseHeaders res = mock(ServerResponseHeaders.class);
        StaticContentHandler.processModifyHeaders(modified.toInstant(), req, res);
    }

    @Test
    void ifModifySince_NotAccept() {
        ZonedDateTime modified = ZonedDateTime.now();
        ServerRequestHeaders req = mock(ServerRequestHeaders.class);
        Mockito.doReturn(Optional.of(modified)).when(req).ifModifiedSince();
        Mockito.doReturn(Optional.empty()).when(req).ifUnmodifiedSince();
        ServerResponseHeaders res = mock(ServerResponseHeaders.class);
        assertHttpException(() -> StaticContentHandler.processModifyHeaders(modified.toInstant(), req, res),
                            Status.NOT_MODIFIED_304);
    }

    @Test
    void ifUnmodifySince_Accept() {
        ZonedDateTime modified = ZonedDateTime.now();
        ServerRequestHeaders req = mock(ServerRequestHeaders.class);
        Mockito.doReturn(Optional.of(modified)).when(req).ifUnmodifiedSince();
        Mockito.doReturn(Optional.empty()).when(req).ifModifiedSince();
        ServerResponseHeaders res = mock(ServerResponseHeaders.class);
        StaticContentHandler.processModifyHeaders(modified.toInstant(), req, res);
    }

    @Test
    void ifUnmodifySince_NotAccept() {
        ZonedDateTime modified = ZonedDateTime.now();
        ServerRequestHeaders req = mock(ServerRequestHeaders.class);
        Mockito.doReturn(Optional.of(modified.minusSeconds(60))).when(req).ifUnmodifiedSince();
        Mockito.doReturn(Optional.empty()).when(req).ifModifiedSince();
        ServerResponseHeaders res = mock(ServerResponseHeaders.class);
        assertHttpException(() -> StaticContentHandler.processModifyHeaders(modified.toInstant(), req, res),
                            Status.PRECONDITION_FAILED_412);
    }

    @Test
    void redirect() throws IOException {
        ServerResponseHeaders resh = mock(ServerResponseHeaders.class);
        ServerResponse res = mock(ServerResponse.class);
        ServerRequest req = mock(ServerRequest.class);
        when(res.headers()).thenReturn(resh);
        when(req.query()).thenReturn(UriQuery.empty());

        CachedHandlerRedirect redirectHandler = new CachedHandlerRedirect("/foo/");
        redirectHandler.handle(LruCache.create(), Method.GET, req, res, "/foo");
        verify(res).status(Status.MOVED_PERMANENTLY_301);
        verify(resh).set(LOCATION, "/foo/");
        verify(res).send();
    }

    @Test
    void handleRoot() {
        ServerRequest request = mockRequestWithPath(Method.GET, "/");
        ServerResponse response = mock(ServerResponse.class);
        TestContentHandler handler = TestContentHandler.create(true);
        handler.handle(request, response);
        verify(response, never()).next();
        assertThat(handler.path, is(Paths.get(".").toAbsolutePath().normalize()));
    }

    @Test
    void handleValid() {
        ServerRequest request = mockRequestWithPath(Method.GET, "/foo/some.txt");
        ServerResponse response = mock(ServerResponse.class);
        TestContentHandler handler = TestContentHandler.create(true);
        handler.handle(request, response);
        // the file is valid, but it does not exist
        verify(response, never()).next();
        assertThat(handler.path, is(Paths.get("foo/some.txt").toAbsolutePath().normalize()));
    }

    @Test
    void handleOutside() {
        ServerRequest request = mockRequestWithPath(Method.GET, "/../foo/some.txt");
        ServerResponse response = mock(ServerResponse.class);
        TestContentHandler handler = TestContentHandler.create(true);
        handler.handle(request, response);
        verify(response).next();
        assertThat(handler.counter.get(), is(0));
    }

    @Test
    void handleNextOnFalse() {
        ServerRequest request = mockRequestWithPath(Method.GET, "/");
        ServerResponse response = mock(ServerResponse.class);
        TestContentHandler handler = TestContentHandler.create(false);
        handler.handle(request, response);
        verify(response).next();
        assertThat(handler.counter.get(), is(1));
    }

    @Test
    void classpathHandleSpaces() {
        ServerRequest request = mockRequestWithPath(Method.GET, "foo/I have spaces.txt");
        ServerResponse response = mock(ServerResponse.class);
        TestClassPathContentHandler handler = TestClassPathContentHandler.create();
        handler.handle(request, response);
        verify(response, never()).next();
        assertThat(handler.counter.get(), is(1));
    }

    private static void assertHttpException(Runnable runnable, Status status) {
        try {
            runnable.run();
            throw new AssertionError("Expected HttpException was not thrown!");
        } catch (HttpException he) {
            if (status != null && status.code() != he.status().code()) {
                throw new AssertionError("Unexpected status in RequestException. "
                                                 + "(Expected: " + status.code() + ", Actual: " + status.code() + ")");
            }
        }
    }

    private ServerRequest mockRequestWithPath(Method method, String path) {
        UriPath uriPath = UriPath.create(path);
        HttpPrologue prologue = HttpPrologue.create("HTTP/1.1",
                                                    "HTTP",
                                                    "1.1",
                                                    method,
                                                    uriPath,
                                                    UriQuery.empty(),
                                                    UriFragment.empty());
        ServerRequest request = mock(ServerRequest.class);
        when(request.prologue()).thenReturn(prologue);
        when(request.path()).thenReturn(new RoutedPath() {
            @Override
            public Parameters pathParameters() {
                return Parameters.empty("http/path");
            }

            @Override
            public RoutedPath absolute() {
                return this;
            }

            @Override
            public String rawPath() {
                return path;
            }

            @Override
            public String rawPathNoParams() {
                return path;
            }

            @Override
            public String path() {
                return path;
            }

            @Override
            public Parameters matrixParameters() {
                return Parameters.empty("unit-routed-path");
            }

            @Override
            public void validate() {
            }
        });
        return request;
    }

    static class TestContentHandler extends FileSystemContentHandler {

        final AtomicInteger counter = new AtomicInteger(0);
        final boolean returnValue;
        Path path;

        TestContentHandler(FileSystemHandlerConfig config, boolean returnValue) {
            super(config);
            this.returnValue = returnValue;
        }

        static TestContentHandler create(boolean returnValue) {
            return new TestContentHandler(FileSystemHandlerConfig.builder()
                                                  .location(Paths.get("."))
                                                  .build(), returnValue);
        }

        @Override
        boolean doHandle(Method method,
                         String requestedResource,
                         ServerRequest req,
                         ServerResponse res,
                         String rawPath,
                         Path path) {

            this.counter.incrementAndGet();
            this.path = path;
            return returnValue;
        }
    }

    static class TestClassPathContentHandler extends ClassPathContentHandler {

        final AtomicInteger counter = new AtomicInteger(0);
        final boolean returnValue;

        TestClassPathContentHandler(ClasspathHandlerConfig config, boolean returnValue) {
            super(config);
            this.returnValue = returnValue;
        }

        static TestClassPathContentHandler create() {
            return new TestClassPathContentHandler(ClasspathHandlerConfig.builder()
                                                           .location("/root")
                                                           .build(), true);
        }

        @Override
        boolean doHandle(Method method, String path, ServerRequest request, ServerResponse response, boolean mapped)
                throws IOException, URISyntaxException {
            super.doHandle(method, path, request, response, mapped);
            this.counter.incrementAndGet();
            return returnValue;
        }

    }
}
