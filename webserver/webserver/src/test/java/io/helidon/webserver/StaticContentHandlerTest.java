/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests {@link StaticContentHandler}.
 */
public class StaticContentHandlerTest {

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
    public void etag_InNonMatch_NotAccept() throws Exception {
        RequestHeaders req = mock(RequestHeaders.class);
        when(req.values(Http.Header.IF_NONE_MATCH)).thenReturn(CollectionsHelper.listOf("\"ccc\"", "\"ddd\""));
        when(req.values(Http.Header.IF_MATCH)).thenReturn(Collections.emptyList());
        ResponseHeaders res = mock(ResponseHeaders.class);
        StaticContentHandler.processEtag("aaa", req, res);
        verify(res).put(Http.Header.ETAG, "\"aaa\"");
    }

    @Test
    public void etag_InNonMatch_Accept() throws Exception {
        RequestHeaders req = mock(RequestHeaders.class);
        when(req.values(Http.Header.IF_NONE_MATCH)).thenReturn(CollectionsHelper.listOf("\"ccc\"", "W/\"aaa\""));
        when(req.values(Http.Header.IF_MATCH)).thenReturn(Collections.emptyList());
        ResponseHeaders res = mock(ResponseHeaders.class);
        assertHttpException(() -> StaticContentHandler.processEtag("aaa", req, res), Http.Status.NOT_MODIFIED_304);
        verify(res).put(Http.Header.ETAG, "\"aaa\"");
    }

    @Test
    public void etag_InMatch_NotAccept() throws Exception {
        RequestHeaders req = mock(RequestHeaders.class);
        when(req.values(Http.Header.IF_MATCH)).thenReturn(CollectionsHelper.listOf("\"ccc\"", "\"ddd\""));
        when(req.values(Http.Header.IF_NONE_MATCH)).thenReturn(Collections.emptyList());
        ResponseHeaders res = mock(ResponseHeaders.class);
        assertHttpException(() -> StaticContentHandler.processEtag("aaa", req, res), Http.Status.PRECONDITION_FAILED_412);
        verify(res).put(Http.Header.ETAG, "\"aaa\"");
    }

    @Test
    public void etag_InMatch_Accept() throws Exception {
        RequestHeaders req = mock(RequestHeaders.class);
        when(req.values(Http.Header.IF_MATCH)).thenReturn(CollectionsHelper.listOf("\"ccc\"", "\"aaa\""));
        when(req.values(Http.Header.IF_NONE_MATCH)).thenReturn(Collections.emptyList());
        ResponseHeaders res = mock(ResponseHeaders.class);
        StaticContentHandler.processEtag("aaa", req, res);
        verify(res).put(Http.Header.ETAG, "\"aaa\"");
    }

    @Test
    public void ifModifySince_Accept() throws Exception {
        ZonedDateTime modified = ZonedDateTime.now();
        RequestHeaders req = mock(RequestHeaders.class);
        Mockito.doReturn(Optional.of(modified.minusSeconds(60))).when(req).ifModifiedSince();
        Mockito.doReturn(Optional.empty()).when(req).ifUnmodifiedSince();
        ResponseHeaders res = mock(ResponseHeaders.class);
        StaticContentHandler.processModifyHeaders(modified.toInstant(), req, res);
    }

    @Test
    public void ifModifySince_NotAccept() throws Exception {
        ZonedDateTime modified = ZonedDateTime.now();
        RequestHeaders req = mock(RequestHeaders.class);
        Mockito.doReturn(Optional.of(modified)).when(req).ifModifiedSince();
        Mockito.doReturn(Optional.empty()).when(req).ifUnmodifiedSince();
        ResponseHeaders res = mock(ResponseHeaders.class);
        assertHttpException(() -> StaticContentHandler.processModifyHeaders(modified.toInstant(), req, res),
                            Http.Status.NOT_MODIFIED_304);
    }

    @Test
    public void ifUnmodifySince_Accept() throws Exception {
        ZonedDateTime modified = ZonedDateTime.now();
        RequestHeaders req = mock(RequestHeaders.class);
        Mockito.doReturn(Optional.of(modified)).when(req).ifUnmodifiedSince();
        Mockito.doReturn(Optional.empty()).when(req).ifModifiedSince();
        ResponseHeaders res = mock(ResponseHeaders.class);
        StaticContentHandler.processModifyHeaders(modified.toInstant(), req, res);
    }

    @Test
    public void ifUnmodifySince_NotAccept() throws Exception {
        ZonedDateTime modified = ZonedDateTime.now();
        RequestHeaders req = mock(RequestHeaders.class);
        Mockito.doReturn(Optional.of(modified.minusSeconds(60))).when(req).ifUnmodifiedSince();
        Mockito.doReturn(Optional.empty()).when(req).ifModifiedSince();
        ResponseHeaders res = mock(ResponseHeaders.class);
        assertHttpException(() ->  StaticContentHandler.processModifyHeaders(modified.toInstant(), req, res),
                            Http.Status.PRECONDITION_FAILED_412);
    }

    @Test
    public void redirect() throws Exception {
        ResponseHeaders resh = mock(ResponseHeaders.class);
        ServerResponse res = mock(ServerResponse.class);
        Mockito.doReturn(resh).when(res).headers();
        StaticContentHandler.redirect(res, "/foo/");
        verify(res).status(Http.Status.MOVED_PERMANENTLY_301);
        verify(resh).put(Http.Header.LOCATION, "/foo/");
        verify(res).send();
    }

    @Test
    public void processContentType() throws Exception {
        ContentTypeSelector selector = mock(ContentTypeSelector.class);
        when(selector.determine(any(), any())).thenReturn(MediaType.TEXT_HTML);
        TestContentHandler handler = new TestContentHandler(null, selector, Paths.get("/root"), false);
        RequestHeaders req = mock(RequestHeaders.class);
        ResponseHeaders res = mock(ResponseHeaders.class);
        handler.processContentType(Paths.get("/root/index.html"), req, res);
        verify(res).contentType(MediaType.TEXT_HTML);
    }

    private ServerRequest mockRequestWithPath(String path) {
        ServerRequest.Path p = mock(ServerRequest.Path.class);
        Mockito.doReturn(path).when(p).toString();
        ServerRequest request = mock(ServerRequest.class);
        Mockito.doReturn(p).when(request).path();
        return request;
    }

    @Test
    public void handleRoot() throws Exception {
        ServerRequest request = mockRequestWithPath("/");
        ServerResponse response = mock(ServerResponse.class);
        TestContentHandler handler = new TestContentHandler("/root", true);
        handler.handle(Http.Method.GET, request, response);
        verify(request, never()).next();
        assertEquals(Paths.get("/root"), handler.path);
    }

    @Test
    public void handleIllegalMethod() throws Exception {
        ServerRequest request = mockRequestWithPath("/");
        ServerResponse response = mock(ServerResponse.class);
        TestContentHandler handler = new TestContentHandler("/root", true);
        handler.handle(Http.Method.POST, request, response);
        verify(request).next();
        assertEquals(0, handler.counter.get());
    }

    @Test
    public void handleValid() throws Exception {
        ServerRequest request = mockRequestWithPath("/foo/some.txt");
        ServerResponse response = mock(ServerResponse.class);
        TestContentHandler handler = new TestContentHandler("/root", true);
        handler.handle(Http.Method.GET, request, response);
        verify(request, never()).next();
        assertEquals(Paths.get("/root/foo/some.txt"), handler.path);
    }

    @Test
    public void handleOutside() throws Exception {
        ServerRequest request = mockRequestWithPath("/../foo/some.txt");
        ServerResponse response = mock(ServerResponse.class);
        TestContentHandler handler = new TestContentHandler("/root", true);
        handler.handle(Http.Method.GET, request, response);
        verify(request).next();
        assertEquals(0, handler.counter.get());
    }

    @Test
    public void handleNextOnFalse() throws Exception {
        ServerRequest request = mockRequestWithPath("/");
        ServerResponse response = mock(ServerResponse.class);
        TestContentHandler handler = new TestContentHandler("/root", false);
        handler.handle(Http.Method.GET, request, response);
        verify(request).next();
        assertEquals(1, handler.counter.get());
    }

    static class TestContentHandler extends StaticContentHandler {

        final AtomicInteger counter = new AtomicInteger(0);
        final boolean returnValue;
        Path path;

        TestContentHandler(String welcomeFilename, ContentTypeSelector contentTypeSelector, Path root, boolean returnValue) {
            super(welcomeFilename, contentTypeSelector, root);
            this.returnValue = returnValue;
        }

        TestContentHandler(String path, boolean returnValue) {
            this(null, mock(ContentTypeSelector.class), Paths.get(path), returnValue);
        }

        @Override
        boolean doHandle(Http.RequestMethod method, Path path, ServerRequest request, ServerResponse response)
                throws IOException {
            this.counter.incrementAndGet();
            this.path = path;
            return returnValue;
        }

    }
}
