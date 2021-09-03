/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.http.Http;
import io.helidon.webserver.HttpException;
import io.helidon.webserver.RequestHeaders;
import io.helidon.webserver.ResponseHeaders;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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

    private static void assertHttpException(Runnable runnable, Http.Status status) {
        try {
            runnable.run();
            throw new AssertionError("Expected HttpException was not thrown!");
        } catch (HttpException he) {
            if (status != null && status.code() != he.status().code()) {
                throw new AssertionError("Unexpected status in HttpException. "
                                                 + "(Expected: " + status.code() + ", Actual: " + status.code() + ")");
            }
        }
    }

    @Test
    void etag_InNonMatch_NotAccept() {
        RequestHeaders req = mock(RequestHeaders.class);
        when(req.values(Http.Header.IF_NONE_MATCH)).thenReturn(List.of("\"ccc\"", "\"ddd\""));
        when(req.values(Http.Header.IF_MATCH)).thenReturn(Collections.emptyList());
        ResponseHeaders res = mock(ResponseHeaders.class);
        StaticContentHandler.processEtag("aaa", req, res);
        verify(res).put(Http.Header.ETAG, "\"aaa\"");
    }

    @Test
    void etag_InNonMatch_Accept() {
        RequestHeaders req = mock(RequestHeaders.class);
        when(req.values(Http.Header.IF_NONE_MATCH)).thenReturn(List.of("\"ccc\"", "W/\"aaa\""));
        when(req.values(Http.Header.IF_MATCH)).thenReturn(Collections.emptyList());
        ResponseHeaders res = mock(ResponseHeaders.class);
        assertHttpException(() -> StaticContentHandler.processEtag("aaa", req, res), Http.Status.NOT_MODIFIED_304);
        verify(res).put(Http.Header.ETAG, "\"aaa\"");
    }

    @Test
    void etag_InMatch_NotAccept() {
        RequestHeaders req = mock(RequestHeaders.class);
        when(req.values(Http.Header.IF_MATCH)).thenReturn(List.of("\"ccc\"", "\"ddd\""));
        when(req.values(Http.Header.IF_NONE_MATCH)).thenReturn(Collections.emptyList());
        ResponseHeaders res = mock(ResponseHeaders.class);
        assertHttpException(() -> StaticContentHandler.processEtag("aaa", req, res), Http.Status.PRECONDITION_FAILED_412);
        verify(res).put(Http.Header.ETAG, "\"aaa\"");
    }

    @Test
    void etag_InMatch_Accept() {
        RequestHeaders req = mock(RequestHeaders.class);
        when(req.values(Http.Header.IF_MATCH)).thenReturn(List.of("\"ccc\"", "\"aaa\""));
        when(req.values(Http.Header.IF_NONE_MATCH)).thenReturn(Collections.emptyList());
        ResponseHeaders res = mock(ResponseHeaders.class);
        StaticContentHandler.processEtag("aaa", req, res);
        verify(res).put(Http.Header.ETAG, "\"aaa\"");
    }

    @Test
    void ifModifySince_Accept() {
        ZonedDateTime modified = ZonedDateTime.now();
        RequestHeaders req = mock(RequestHeaders.class);
        Mockito.doReturn(Optional.of(modified.minusSeconds(60))).when(req).ifModifiedSince();
        Mockito.doReturn(Optional.empty()).when(req).ifUnmodifiedSince();
        ResponseHeaders res = mock(ResponseHeaders.class);
        StaticContentHandler.processModifyHeaders(modified.toInstant(), req, res);
    }

    @Test
    void ifModifySince_NotAccept() {
        ZonedDateTime modified = ZonedDateTime.now();
        RequestHeaders req = mock(RequestHeaders.class);
        Mockito.doReturn(Optional.of(modified)).when(req).ifModifiedSince();
        Mockito.doReturn(Optional.empty()).when(req).ifUnmodifiedSince();
        ResponseHeaders res = mock(ResponseHeaders.class);
        assertHttpException(() -> StaticContentHandler.processModifyHeaders(modified.toInstant(), req, res),
                            Http.Status.NOT_MODIFIED_304);
    }

    @Test
    void ifUnmodifySince_Accept() {
        ZonedDateTime modified = ZonedDateTime.now();
        RequestHeaders req = mock(RequestHeaders.class);
        Mockito.doReturn(Optional.of(modified)).when(req).ifUnmodifiedSince();
        Mockito.doReturn(Optional.empty()).when(req).ifModifiedSince();
        ResponseHeaders res = mock(ResponseHeaders.class);
        StaticContentHandler.processModifyHeaders(modified.toInstant(), req, res);
    }

    @Test
    void ifUnmodifySince_NotAccept() {
        ZonedDateTime modified = ZonedDateTime.now();
        RequestHeaders req = mock(RequestHeaders.class);
        Mockito.doReturn(Optional.of(modified.minusSeconds(60))).when(req).ifUnmodifiedSince();
        Mockito.doReturn(Optional.empty()).when(req).ifModifiedSince();
        ResponseHeaders res = mock(ResponseHeaders.class);
        assertHttpException(() -> StaticContentHandler.processModifyHeaders(modified.toInstant(), req, res),
                            Http.Status.PRECONDITION_FAILED_412);
    }

    @Test
    void redirect() {
        ResponseHeaders resh = mock(ResponseHeaders.class);
        ServerResponse res = mock(ServerResponse.class);
        ServerRequest req = mock(ServerRequest.class);
        Mockito.doReturn(resh).when(res).headers();
        StaticContentHandler.redirect(req, res, "/foo/");
        verify(res).status(Http.Status.MOVED_PERMANENTLY_301);
        verify(resh).put(Http.Header.LOCATION, "/foo/");
        verify(res).send();
    }

    private ServerRequest mockRequestWithPath(String path) {
        ServerRequest.Path p = mock(ServerRequest.Path.class);
        Mockito.doReturn(path).when(p).toString();
        ServerRequest request = mock(ServerRequest.class);
        Mockito.doReturn(p).when(request).path();
        return request;
    }

    @Test
    void handleRoot() {
        ServerRequest request = mockRequestWithPath("/");
        ServerResponse response = mock(ServerResponse.class);
        TestContentHandler handler = TestContentHandler.create(true);
        handler.handle(Http.Method.GET, request, response);
        verify(request, never()).next();
        assertThat(handler.path, is(Paths.get(".").toAbsolutePath().normalize()));
    }

    @Test
    void handleIllegalMethod() {
        ServerRequest request = mockRequestWithPath("/");
        ServerResponse response = mock(ServerResponse.class);
        TestContentHandler handler = TestContentHandler.create(true);
        handler.handle(Http.Method.POST, request, response);
        verify(request).next();
        assertThat(handler.counter.get(), is(0));
    }

    @Test
    void handleValid() {
        ServerRequest request = mockRequestWithPath("/foo/some.txt");
        ServerResponse response = mock(ServerResponse.class);
        TestContentHandler handler = TestContentHandler.create(true);
        handler.handle(Http.Method.GET, request, response);
        verify(request, never()).next();
        assertThat(handler.path, is(Paths.get("foo/some.txt").toAbsolutePath().normalize()));
    }

    @Test
    void handleOutside() {
        ServerRequest request = mockRequestWithPath("/../foo/some.txt");
        ServerResponse response = mock(ServerResponse.class);
        TestContentHandler handler = TestContentHandler.create(true);
        handler.handle(Http.Method.GET, request, response);
        verify(request).next();
        assertThat(handler.counter.get(), is(0));
    }

    @Test
    void handleNextOnFalse() {
        ServerRequest request = mockRequestWithPath("/");
        ServerResponse response = mock(ServerResponse.class);
        TestContentHandler handler = TestContentHandler.create(false);
        handler.handle(Http.Method.GET, request, response);
        verify(request).next();
        assertThat(handler.counter.get(), is(1));
    }

    @Test
    void classpathHandleSpaces() {
        ServerRequest request = mockRequestWithPath("foo/I have spaces.txt");
        ServerResponse response = mock(ServerResponse.class);
        TestClassPathContentHandler handler = TestClassPathContentHandler.create();
        handler.handle(Http.Method.GET, request, response);
        verify(request, never()).next();
        assertThat(handler.counter.get(), is(1));
    }


    static class TestContentHandler extends FileSystemContentHandler {

        final AtomicInteger counter = new AtomicInteger(0);
        final boolean returnValue;
        Path path;

        TestContentHandler(StaticContentSupport.FileSystemBuilder builder, boolean returnValue) {
            super(builder);
            this.returnValue = returnValue;
        }
        
        static TestContentHandler create(boolean returnValue) {
            return new TestContentHandler(StaticContentSupport.builder(Paths.get(".")), returnValue);
        }
        
        @Override
        boolean doHandle(Http.RequestMethod method, Path path, ServerRequest request, ServerResponse response) {
            this.counter.incrementAndGet();
            this.path = path;
            return returnValue;
        }
    }

    static class TestClassPathContentHandler extends ClassPathContentHandler {

        final AtomicInteger counter = new AtomicInteger(0);
        final boolean returnValue;

        TestClassPathContentHandler(StaticContentSupport.ClassPathBuilder builder, boolean returnValue) {
            super(builder);
            this.returnValue = returnValue;
        }

        static TestClassPathContentHandler create() {
            return new TestClassPathContentHandler(StaticContentSupport.builder("/root"), true);
        }
        
        @Override
        boolean doHandle(Http.RequestMethod method, String path, ServerRequest request, ServerResponse response)
                throws IOException, URISyntaxException {
            super.doHandle(method, path, request, response);
            this.counter.incrementAndGet();
            return returnValue;
        }

    }
}
