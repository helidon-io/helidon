/*
 * Copyright (c) 2018, 2026 Oracle and/or its affiliates.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.LruCache;
import io.helidon.common.Size;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.parameters.Parameters;
import io.helidon.common.uri.UriFragment;
import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriQuery;
import io.helidon.http.BadRequestException;
import io.helidon.http.ForbiddenException;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpException;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.RoutedPath;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.encoding.ContentEncoder;
import io.helidon.http.encoding.ContentEncoding;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.noHeader;
import static io.helidon.common.testing.junit5.MatcherWithRetry.assertThatWithRetry;
import static io.helidon.http.HeaderNames.ETAG;
import static io.helidon.http.HeaderNames.IF_MATCH;
import static io.helidon.http.HeaderNames.IF_MODIFIED_SINCE;
import static io.helidon.http.HeaderNames.IF_NONE_MATCH;
import static io.helidon.http.HeaderNames.LOCATION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests {@link StaticContentHandler}.
 */
class StaticContentHandlerTest {
    private static final String ETAG_VALUE = "\"aaa\"";

    @TempDir
    Path tempDir;

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
    void handlePreservesServerConnectionException() {
        ServerRequest request = mockRequestWithPath(Method.GET, "/foo/some.txt");
        ServerResponse response = mock(ServerResponse.class);
        ServerConnectionException expected = new ServerConnectionException("Failed to write response",
                                                                          new UncheckedIOException(new IOException(
                                                                                  "Broken pipe")));
        ThrowingContentHandler handler = ThrowingContentHandler.create(expected);

        ServerConnectionException actual = assertThrows(ServerConnectionException.class,
                                                        () -> handler.handle(request, response));

        assertThat(actual, is(expected));
        verify(response, never()).next();
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

    @Test
    void preCompressedEncodingsNormalizeCodingAndSuffix() {
        Map<String, String> normalized = StaticContentConfigSupport.normalizePreCompressedEncodings(
                Map.of("GZIP", ".gz"));

        assertThat(normalized, is(Map.of("gzip", "gz")));
    }

    @Test
    void preCompressedEncodingsAllowEmptyMap() {
        Map<String, String> normalized = StaticContentConfigSupport.normalizePreCompressedEncodings(Map.of());

        assertThat(normalized, is(Map.of()));
    }

    @Test
    void preCompressedEncodingsRejectReservedCoding() {
        assertThrows(IllegalArgumentException.class,
                     () -> StaticContentConfigSupport.normalizePreCompressedEncodings(Map.of("identity", "gz")));
        assertThrows(IllegalArgumentException.class,
                     () -> StaticContentConfigSupport.normalizePreCompressedEncodings(Map.of("*", "gz")));
    }

    @Test
    void preCompressedEncodingsRejectInvalidCoding() {
        assertThrows(IllegalArgumentException.class,
                     () -> StaticContentConfigSupport.normalizePreCompressedEncodings(Map.of("\u00e9", "gz")));
    }

    @Test
    void preCompressedRuntimeEncodingIgnoresRanges() throws IOException, URISyntaxException {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = inMemoryHandler("Nested content");
        ServerRequest request = mockRequestWithHeaders("gzip", "bytes=0-3", runtimeContentEncodingContext());
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);
        ByteArrayOutputStream sent = new ByteArrayOutputStream();

        when(response.headers()).thenReturn(responseHeaders);
        when(response.outputStream()).thenReturn(sent);

        CachedHandler selected = handler.selectHandler(identityHandler, request, (coding, suffix) -> Optional.empty());

        selected.handle(LruCache.create(), Method.GET, request, response, "nested/resource.txt");

        assertThat(responseHeaders, hasHeader(HeaderNames.CONTENT_ENCODING, "gzip"));
        assertThat(responseHeaders, noHeader(HeaderNames.CONTENT_RANGE));
        assertThat(sent.toString(StandardCharsets.UTF_8), is("runtime:Nested content"));
        verify(response, never()).status(Status.PARTIAL_CONTENT_206);
    }

    @Test
    void preCompressedDisabledRangeDisablesAutomaticEncoding() throws IOException, URISyntaxException {
        TestContentHandler handler = new TestContentHandler(FileSystemHandlerConfig.builder()
                                                                 .location(Paths.get("."))
                                                                 .preCompressedEnabled(false)
                                                                 .build(),
                                                             true);
        CachedHandler identityHandler = inMemoryHandler("Nested content");
        ServerRequest request = mockRequestWithHeaders("gzip", "bytes=0-3", runtimeContentEncodingContext());
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        RoutingResponse response = mock(RoutingResponse.class);
        AtomicReference<byte[]> sent = new AtomicReference<>();

        when(response.headers()).thenReturn(responseHeaders);
        Mockito.doAnswer(inv -> {
            sent.set(inv.getArgument(0));
            return null;
        }).when(response).send(any(byte[].class));

        CachedHandler selected = handler.selectHandler(identityHandler, request, (coding, suffix) -> Optional.empty());

        selected.handle(LruCache.create(), Method.GET, request, response, "nested/resource.txt");

        verify(response).automaticContentEncoding(false);
        verify(response).status(Status.PARTIAL_CONTENT_206);
        verify(response).header(HeaderValues.create(HeaderNames.CONTENT_RANGE, true, false, "bytes 0-3/14"));
        verify(response).contentLength(4);
        assertThat(responseHeaders, noHeader(HeaderNames.CONTENT_ENCODING));
        assertThat(new String(sent.get(), StandardCharsets.UTF_8), is("Nest"));
    }

    @Test
    void preCompressedDisabledRangeRejectsRejectedIdentity() throws IOException, URISyntaxException {
        TestContentHandler handler = new TestContentHandler(FileSystemHandlerConfig.builder()
                                                                 .location(Paths.get("."))
                                                                 .preCompressedEnabled(false)
                                                                 .build(),
                                                             true);
        CachedHandler identityHandler = inMemoryHandler("Nested content");
        ServerRequest request = mockRequestWithHeaders("identity;q=0", "bytes=0-3", runtimeContentEncodingContext());

        HttpException actual = assertThrows(HttpException.class,
                                            () -> handler.selectHandler(identityHandler,
                                                                       request,
                                                                       (coding, suffix) -> Optional.empty()));

        assertThat(actual.status(), is(Status.NOT_ACCEPTABLE_406));
    }

    @Test
    void preCompressedRuntimeEncodingSatisfiesRejectedIdentity() throws IOException, URISyntaxException {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = inMemoryHandler("Nested content");
        ServerRequest request = mockRequestWithHeaders("gzip, identity;q=0", null, runtimeContentEncodingContext());
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);
        ByteArrayOutputStream sent = new ByteArrayOutputStream();

        when(response.headers()).thenReturn(responseHeaders);
        when(response.outputStream()).thenReturn(sent);

        CachedHandler selected = handler.selectHandler(
                identityHandler,
                                                       request,
                                                       (coding, suffix) -> Optional.empty());

        selected.handle(LruCache.create(), Method.GET, request, response, "nested/resource.txt");

        assertThat(responseHeaders, hasHeader(HeaderNames.CONTENT_ENCODING, "gzip"));
        assertThat(sent.toString(StandardCharsets.UTF_8), is("runtime:Nested content"));
    }

    @Test
    void preCompressedExplicitIdentityTieUsesHeaderOrder() throws IOException, URISyntaxException {
        assertRuntimeEncodingSelected("gzip, identity");
        assertIdentitySelected("identity, gzip");
    }

    @Test
    void preCompressedImplicitIdentityBeatsWildcard() throws IOException, URISyntaxException {
        assertIdentitySelected("*");
    }

    @Test
    void preCompressedIdentityWinnerSkipsSidecarLookup() throws IOException, URISyntaxException {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = inMemoryHandler("Nested content");
        ServerRequest request = mockRequestWithHeaders("br;q=0.1, gzip;q=0.1", null, ContentEncodingContext.create());
        AtomicInteger lookups = new AtomicInteger();

        handler.selectHandler(identityHandler, request, (coding, suffix) -> {
            lookups.incrementAndGet();
            return Optional.empty();
        });

        assertThat(lookups.get(), is(0));
    }

    @Test
    void preCompressedLowerQualitySidecarIsResolvedOnlyOnFallback() throws IOException, URISyntaxException {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = inMemoryHandler("Nested content");
        CachedHandler brotliHandler = (cache, method, request, response, requestedResource) -> false;
        CachedHandler gzipHandler = inMemoryHandler("Gzip content");
        ServerRequest request = mockRequestWithHeaders("br, gzip;q=0.5, identity;q=0",
                                                       null,
                                                       ContentEncodingContext.create());
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);
        AtomicReference<byte[]> sent = new AtomicReference<>();
        List<String> lookups = new ArrayList<>();

        when(response.headers()).thenReturn(responseHeaders);
        Mockito.doAnswer(invocation -> {
            sent.set(invocation.getArgument(0));
            return null;
        }).when(response).send(any(byte[].class));

        CachedHandler selected = handler.selectHandler(identityHandler, request, (coding, suffix) -> {
            lookups.add(coding);
            return Optional.of("br".equals(coding) ? brotliHandler : gzipHandler);
        });

        assertThat(lookups, is(List.of("br")));

        selected.handle(LruCache.create(), Method.GET, request, response, "nested/resource.txt");

        assertThat(lookups, is(List.of("br", "gzip")));
        assertThat(responseHeaders, hasHeader(HeaderNames.CONTENT_ENCODING, "gzip"));
        assertThat(new String(sent.get(), StandardCharsets.UTF_8), is("Gzip content"));
    }

    @Test
    void preCompressedMissingPreferredSidecarResolvesNext() throws IOException, URISyntaxException {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = inMemoryHandler("Nested content");
        CachedHandler gzipHandler = inMemoryHandler("Gzip content");
        ServerRequest request = mockRequestWithHeaders("br, gzip;q=0.5, identity;q=0",
                                                       null,
                                                       ContentEncodingContext.create());
        List<String> lookups = new ArrayList<>();

        CachedHandler selected = handler.selectHandler(identityHandler, request, (coding, suffix) -> {
            lookups.add(coding);
            return "gzip".equals(coding) ? Optional.of(gzipHandler) : Optional.empty();
        });

        assertThat(lookups, is(List.of("br", "gzip")));

        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);
        when(response.headers()).thenReturn(responseHeaders);

        selected.handle(LruCache.create(), Method.HEAD, request, response, "nested/resource.txt");

        assertThat(responseHeaders, hasHeader(HeaderNames.CONTENT_ENCODING, "gzip"));
    }

    @Test
    void preCompressedBestSidecarIsResolvedFirst() throws IOException, URISyntaxException {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = inMemoryHandler("Nested content");
        CachedHandler sidecarHandler = inMemoryHandler("Encoded content");
        ServerRequest request = mockRequestWithHeaders("gzip, br;q=0.5, identity;q=0",
                                                       null,
                                                       ContentEncodingContext.create());
        List<String> lookups = new ArrayList<>();

        handler.selectHandler(identityHandler, request, (coding, suffix) -> {
            lookups.add(coding);
            return Optional.of(sidecarHandler);
        });

        assertThat(lookups, is(List.of("gzip")));
    }

    @Test
    void preCompressedRuntimeEncodingEncodesEmptyInMemoryResource() throws IOException, URISyntaxException {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = inMemoryHandler("");
        ServerRequest request = mockRequestWithHeaders("gzip, identity;q=0", null, runtimeContentEncodingContext());
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);
        ByteArrayOutputStream sent = new ByteArrayOutputStream();

        when(response.headers()).thenReturn(responseHeaders);
        when(response.outputStream()).thenReturn(sent);

        CachedHandler selected = handler.selectHandler(
                identityHandler,
                                                       request,
                                                       (coding, suffix) -> Optional.empty());

        selected.handle(LruCache.create(), Method.GET, request, response, "empty.txt");

        assertThat(responseHeaders, hasHeader(HeaderNames.CONTENT_ENCODING, "gzip"));
        assertThat(sent.toString(StandardCharsets.UTF_8), is("runtime:"));
    }

    @Test
    void preCompressedSidecarSatisfiesRejectedIdentityWithoutRuntimeProvider() throws IOException, URISyntaxException {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = inMemoryHandler("Nested content");
        CachedHandler sidecarHandler = inMemoryHandler("Brotli content")
                .withRepresentation(ResponseRepresentation.encoded("br"));
        ServerRequest request = mockRequestWithHeaders("br, identity;q=0", null, ContentEncodingContext.create());
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);
        AtomicReference<byte[]> sent = new AtomicReference<>();

        when(response.headers()).thenReturn(responseHeaders);
        Mockito.doAnswer(inv -> {
            sent.set(inv.getArgument(0));
            return null;
        }).when(response).send(any(byte[].class));

        CachedHandler selected = handler.selectHandler(
                identityHandler,
                request,
                (coding, suffix) -> Optional.of(sidecarHandler));

        selected.handle(LruCache.create(), Method.GET, request, response, "nested/resource.txt");

        assertThat(responseHeaders, hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
        assertThat(responseHeaders, hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
        assertThat(new String(sent.get(), StandardCharsets.UTF_8), is("Brotli content"));
        verify(response, never()).status(Status.NOT_ACCEPTABLE_406);
    }

    @Test
    void preCompressedSidecarUsesAcceptedAliasWhenCanonicalCodingIsRejected() throws IOException, URISyntaxException {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = inMemoryHandler("Nested content");
        CachedHandler sidecarHandler = inMemoryHandler("Gzip content")
                .withRepresentation(ResponseRepresentation.encoded("gzip"));
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        ServerRequest request = mockRequestWithHeaders("x-gzip, gzip;q=0, identity;q=0", null, contentEncodingContext);
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);
        AtomicReference<byte[]> sent = new AtomicReference<>();
        AtomicReference<String> resolvedCoding = new AtomicReference<>();

        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.contentEncodingSupported("x-gzip")).thenReturn(true);
        when(contentEncodingContext.canonicalEncodingId("gzip")).thenReturn(Optional.of("gzip"));
        when(contentEncodingContext.canonicalEncodingId("x-gzip")).thenReturn(Optional.of("gzip"));
        when(contentEncodingContext.prototype()).thenThrow(new AssertionError("Static content must not inspect the prototype"));
        when(response.headers()).thenReturn(responseHeaders);
        Mockito.doAnswer(inv -> {
            sent.set(inv.getArgument(0));
            return null;
        }).when(response).send(any(byte[].class));

        CachedHandler selected = handler.selectHandler(
                identityHandler,
                request,
                (coding, suffix) -> {
                    resolvedCoding.set(coding);
                    assertThat(suffix, is("gz"));
                    return Optional.of(sidecarHandler);
                });

        selected.handle(LruCache.create(), Method.GET, request, response, "nested/resource.txt");

        assertThat(resolvedCoding.get(), is("gzip"));
        assertThat(responseHeaders, hasHeader(HeaderNames.CONTENT_ENCODING, "x-gzip"));
        assertThat(responseHeaders, hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
        assertThat(new String(sent.get(), StandardCharsets.UTF_8), is("Gzip content"));
        verify(contentEncodingContext, never()).prototype();
        verify(response, never()).status(Status.NOT_ACCEPTABLE_406);
    }

    @Test
    void preCompressedSidecarRechecksIdentityAvailabilityWhenHandled() throws IOException, URISyntaxException {
        Path identity = tempDir.resolve("resource.txt");
        Path sidecar = tempDir.resolve("resource.txt.gz");
        Files.writeString(identity, "Content");
        Files.writeString(sidecar, "Gzip content");
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = new CachedHandlerPath(identity,
                                                              MediaTypes.TEXT_PLAIN,
                                                              path -> Optional.of(Instant.EPOCH),
                                                              ServerResponseHeaders::lastModified);
        CachedHandler sidecarHandler = new CachedHandlerPath(sidecar,
                                                             MediaTypes.TEXT_PLAIN,
                                                             path -> Optional.of(Instant.EPOCH),
                                                             ServerResponseHeaders::lastModified)
                .withRepresentation(ResponseRepresentation.encoded("gzip"));
        ServerRequest request = mockRequestWithHeaders("gzip, identity;q=0", null, ContentEncodingContext.create());
        ServerResponse response = mock(ServerResponse.class);

        CachedHandler selected = handler.selectHandler(
                identityHandler,
                request,
                (coding, suffix) -> Optional.of(sidecarHandler));

        Files.delete(identity);

        assertThrows(ForbiddenException.class,
                     () -> selected.handle(LruCache.create(), Method.GET, request, response, "resource.txt"));
    }

    @Test
    void preCompressedUnavailableIdentityDoesNotSendAfterRecovery() throws IOException, URISyntaxException {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = mock(CachedHandler.class);
        CachedHandler sidecarHandler = inMemoryHandler("Gzip content");
        ServerRequest request = mockRequestWithHeaders("gzip, identity;q=0", null, ContentEncodingContext.create());
        ServerResponse response = mock(ServerResponse.class);
        LruCache<String, CachedHandler> cache = LruCache.create();

        when(identityHandler.available()).thenReturn(false);
        when(identityHandler.handle(any(), any(), any(), any(), anyString())).thenReturn(true);
        cache.put("resource.txt", identityHandler);

        CachedHandler selected = handler.selectHandler(
                identityHandler,
                request,
                (coding, suffix) -> Optional.of(sidecarHandler));

        assertThrows(ForbiddenException.class,
                     () -> selected.handle(cache, Method.GET, request, response, "resource.txt"));
        verify(identityHandler, never()).handle(any(), any(), any(), any(), anyString());
        assertThat(cache.get("resource.txt").isEmpty(), is(true));
    }

    @Test
    void preCompressedDeletedSidecarFallsBackToIdentityWhenHandled() throws IOException, URISyntaxException {
        Path identity = tempDir.resolve("resource.txt");
        Path sidecar = tempDir.resolve("resource.txt.gz");
        Files.writeString(identity, "Content");
        Files.writeString(sidecar, "Gzip content");
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = new CachedHandlerPath(identity,
                                                              MediaTypes.TEXT_PLAIN,
                                                              path -> Optional.of(Instant.EPOCH),
                                                              ServerResponseHeaders::lastModified);
        CachedHandler sidecarHandler = new CachedHandlerPath(sidecar,
                                                             MediaTypes.TEXT_PLAIN,
                                                             path -> Optional.of(Instant.EPOCH),
                                                             ServerResponseHeaders::lastModified)
                .withRepresentation(ResponseRepresentation.encoded("gzip"));
        ServerRequest request = mockRequestWithHeaders("gzip", null, ContentEncodingContext.create());
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);
        ByteArrayOutputStream sent = new ByteArrayOutputStream();

        when(response.headers()).thenReturn(responseHeaders);
        when(response.outputStream()).thenReturn(sent);

        CachedHandler selected = handler.selectHandler(
                identityHandler,
                request,
                (coding, suffix) -> Optional.of(sidecarHandler));

        Files.delete(sidecar);

        selected.handle(LruCache.create(), Method.GET, request, response, "resource.txt");

        assertThat(responseHeaders, noHeader(HeaderNames.CONTENT_ENCODING));
        assertThat(responseHeaders, hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
        assertThat(sent.toString(StandardCharsets.UTF_8), is("Content"));
    }

    @Test
    void preCompressedDeletedSidecarWithRejectedIdentityReturnsNotAcceptableWhenHandled()
            throws IOException, URISyntaxException {
        Path identity = tempDir.resolve("resource.txt");
        Path sidecar = tempDir.resolve("resource.txt.gz");
        Files.writeString(identity, "Content");
        Files.writeString(sidecar, "Gzip content");
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = new CachedHandlerPath(identity,
                                                              MediaTypes.TEXT_PLAIN,
                                                              path -> Optional.of(Instant.EPOCH),
                                                              ServerResponseHeaders::lastModified);
        CachedHandler sidecarHandler = new CachedHandlerPath(sidecar,
                                                             MediaTypes.TEXT_PLAIN,
                                                             path -> Optional.of(Instant.EPOCH),
                                                             ServerResponseHeaders::lastModified)
                .withRepresentation(ResponseRepresentation.encoded("gzip"));
        ServerRequest request = mockRequestWithHeaders("gzip, identity;q=0", null, ContentEncodingContext.create());
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);

        when(response.headers()).thenReturn(responseHeaders);

        CachedHandler selected = handler.selectHandler(
                identityHandler,
                request,
                (coding, suffix) -> Optional.of(sidecarHandler));

        Files.delete(sidecar);

        selected.handle(LruCache.create(), Method.GET, request, response, "resource.txt");

        assertThat(responseHeaders, noHeader(HeaderNames.CONTENT_ENCODING));
        assertThat(responseHeaders, hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
        verify(response).status(Status.NOT_ACCEPTABLE_406);
        verify(response).send();
    }

    @Test
    void preCompressedDeletedSidecarDuringSendUsesRuntimeFallback() throws IOException, URISyntaxException {
        Path identity = tempDir.resolve("resource.txt");
        Path sidecar = tempDir.resolve("resource.txt.gz");
        Files.writeString(identity, "Content");
        Files.writeString(sidecar, "Gzip content");
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = new CachedHandlerPath(identity,
                                                              MediaTypes.TEXT_PLAIN,
                                                              path -> Optional.of(Instant.EPOCH),
                                                              ServerResponseHeaders::lastModified);
        CachedHandler sidecarHandler = new CachedHandlerPath(sidecar,
                                                             MediaTypes.TEXT_PLAIN,
                                                             path -> {
                                                                 Files.delete(path);
                                                                 return Optional.empty();
                                                             },
                                                             ServerResponseHeaders::lastModified)
                .withRepresentation(ResponseRepresentation.encoded("gzip"));
        ServerRequest request = mockRequestWithHeaders("gzip, identity;q=0", null, runtimeContentEncodingContext());
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);
        ByteArrayOutputStream sent = new ByteArrayOutputStream();

        when(response.headers()).thenReturn(responseHeaders);
        when(response.outputStream()).thenReturn(sent);

        CachedHandler selected = handler.selectHandler(
                identityHandler,
                request,
                (coding, suffix) -> Optional.of(sidecarHandler));

        selected.handle(LruCache.create(), Method.GET, request, response, "resource.txt");

        assertThat(responseHeaders, hasHeader(HeaderNames.CONTENT_ENCODING, "gzip"));
        assertThat(responseHeaders, hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
        assertThat(sent.toString(StandardCharsets.UTF_8), is("runtime:Content"));
    }

    @Test
    void preCompressedDeletedHeadSidecarFallbackClearsSelectedHeaders() throws IOException, URISyntaxException {
        Path identity = tempDir.resolve("resource.txt");
        Path sidecar = tempDir.resolve("resource.txt.gz");
        Files.writeString(identity, "Content");
        Files.writeString(sidecar, "Gzip content");
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = new CachedHandlerPath(identity,
                                                              MediaTypes.TEXT_PLAIN,
                                                              path -> Optional.of(Instant.EPOCH),
                                                              ServerResponseHeaders::lastModified);
        CachedHandler sidecarHandler = new CachedHandlerPath(sidecar,
                                                             MediaTypes.TEXT_PLAIN,
                                                             path -> {
                                                                 sidecar.toFile().delete();
                                                                 return Optional.empty();
                                                             },
                                                             ServerResponseHeaders::lastModified)
                .withRepresentation(ResponseRepresentation.encoded("gzip"));
        ServerRequest request = mockRequestWithHeaders("gzip, identity;q=0", null, ContentEncodingContext.create());
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);

        when(response.headers()).thenReturn(responseHeaders);

        CachedHandler selected = handler.selectHandler(
                identityHandler,
                request,
                (coding, suffix) -> Optional.of(sidecarHandler));

        selected.handle(LruCache.create(), Method.HEAD, request, response, "resource.txt");

        assertThat(responseHeaders, noHeader(HeaderNames.CONTENT_ENCODING));
        assertThat(responseHeaders, noHeader(HeaderNames.CONTENT_LENGTH));
        assertThat(responseHeaders, noHeader(HeaderNames.CONTENT_TYPE));
        assertThat(responseHeaders, hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
        verify(response).status(Status.NOT_ACCEPTABLE_406);
        verify(response).send();
    }

    @Test
    void preCompressedSidecarUsesHeaderOrderWhenQualityTies() throws IOException, URISyntaxException {
        assertSidecarSelected("gzip, br, identity;q=0", "gzip", "Gzip content");
        assertSidecarSelected("br, gzip, identity;q=0", "br", "Brotli content");
    }

    @Test
    void preCompressedRuntimeEncodingUsesHeaderOrderWhenQualityTies() throws IOException, URISyntaxException {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = inMemoryHandler("Nested content");
        ServerRequest request = mockRequestWithHeaders("gzip, br, identity;q=0",
                                                       null,
                                                       runtimeContentEncodingContext(new TestEncoding("br", "br-runtime:"),
                                                                                     new TestEncoding("gzip",
                                                                                                      "gzip-runtime:")));
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);
        ByteArrayOutputStream sent = new ByteArrayOutputStream();

        when(response.headers()).thenReturn(responseHeaders);
        when(response.outputStream()).thenReturn(sent);

        CachedHandler selected = handler.selectHandler(
                identityHandler,
                                                       request,
                                                       (coding, suffix) -> Optional.empty());

        selected.handle(LruCache.create(), Method.GET, request, response, "nested/resource.txt");

        assertThat(responseHeaders, hasHeader(HeaderNames.CONTENT_ENCODING, "gzip"));
        assertThat(sent.toString(StandardCharsets.UTF_8), is("gzip-runtime:Nested content"));
    }

    @Test
    void preCompressedRuntimeEncodingUsesListenerContextIds() throws IOException, URISyntaxException {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = inMemoryHandler("Nested content");
        ContentEncoder runtimeEncoder = new TestEncoding("gzip", "context-runtime:").encoder();
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        ServerRequest request = mockRequestWithHeaders("gzip, identity;q=0", null, contentEncodingContext);
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);
        ByteArrayOutputStream sent = new ByteArrayOutputStream();

        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.contentEncodingSupported("gzip")).thenReturn(true);
        when(contentEncodingContext.contentEncodingIds()).thenReturn(List.of("gzip"));
        when(contentEncodingContext.encoder("gzip")).thenReturn(runtimeEncoder);
        when(response.headers()).thenReturn(responseHeaders);
        when(response.outputStream()).thenReturn(sent);

        CachedHandler selected = handler.selectHandler(
                identityHandler,
                                                       request,
                                                       (coding, suffix) -> Optional.empty());

        selected.handle(LruCache.create(), Method.GET, request, response, "nested/resource.txt");

        assertThat(responseHeaders, hasHeader(HeaderNames.CONTENT_ENCODING, "gzip"));
        assertThat(sent.toString(StandardCharsets.UTF_8), is("context-runtime:Nested content"));
        verify(contentEncodingContext, never()).prototype();
    }

    @Test
    void preCompressedRuntimeEncodingDoesNotResolveUnacceptedProviderId() throws IOException, URISyntaxException {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = inMemoryHandler("Nested content");
        ContentEncoder gzipEncoder = new TestEncoding("gzip", "gzip-runtime:").encoder();
        ContentEncoder brEncoder = new TestEncoding("br", "br-runtime:").encoder();
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        ServerRequest request = mockRequestWithHeaders("br, identity;q=0", null, contentEncodingContext);
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);
        ByteArrayOutputStream sent = new ByteArrayOutputStream();

        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.contentEncodingSupported("gzip")).thenReturn(true);
        when(contentEncodingContext.contentEncodingSupported("br")).thenReturn(true);
        when(contentEncodingContext.contentEncodingIds()).thenReturn(List.of("gzip"));
        when(contentEncodingContext.encoder("gzip")).thenReturn(gzipEncoder);
        when(contentEncodingContext.encoder("br")).thenReturn(brEncoder);
        when(response.headers()).thenReturn(responseHeaders);
        when(response.outputStream()).thenReturn(sent);

        CachedHandler selected = handler.selectHandler(
                identityHandler,
                                                       request,
                                                       (coding, suffix) -> Optional.empty());

        selected.handle(LruCache.create(), Method.GET, request, response, "nested/resource.txt");

        assertThat(responseHeaders, hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
        assertThat(sent.toString(StandardCharsets.UTF_8), is("br-runtime:Nested content"));
        verify(contentEncodingContext, never()).encoder("gzip");
        verify(contentEncodingContext).encoder("br");
    }

    @Test
    void preCompressedRuntimeEncodingUsesSelectedContentEncoding() throws IOException, URISyntaxException {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = inMemoryHandler("Nested content");
        ContentEncoder runtimeEncoder = new TestEncoding("gzip", "context-runtime:").encoder();
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        ServerRequest request = mockRequestWithHeaders("gzip, x-gzip, identity;q=0", null, contentEncodingContext);
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);
        ByteArrayOutputStream sent = new ByteArrayOutputStream();

        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.contentEncodingSupported("x-gzip")).thenReturn(true);
        when(contentEncodingContext.contentEncodingIds()).thenReturn(List.of("x-gzip"));
        when(contentEncodingContext.encoder("x-gzip")).thenReturn(runtimeEncoder);
        when(response.headers()).thenReturn(responseHeaders);
        when(response.outputStream()).thenReturn(sent);

        CachedHandler selected = handler.selectHandler(
                identityHandler,
                                                       request,
                                                       (coding, suffix) -> Optional.empty());

        selected.handle(LruCache.create(), Method.GET, request, response, "nested/resource.txt");

        assertThat(responseHeaders, hasHeader(HeaderNames.CONTENT_ENCODING, "x-gzip"));
        assertThat(sent.toString(StandardCharsets.UTF_8), is("context-runtime:Nested content"));
    }

    @Test
    void preCompressedRuntimeEncodingUsesAcceptedAliasWhenCanonicalCodingIsRejected() throws IOException, URISyntaxException {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = inMemoryHandler("Nested content");
        ContentEncoder runtimeEncoder = new TestEncoding("gzip", "context-runtime:").encoder();
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        ServerRequest request = mockRequestWithHeaders("x-gzip, gzip;q=0, identity;q=0", null, contentEncodingContext);
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);
        ByteArrayOutputStream sent = new ByteArrayOutputStream();

        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.contentEncodingSupported("gzip")).thenReturn(true);
        when(contentEncodingContext.contentEncodingSupported("x-gzip")).thenReturn(true);
        when(contentEncodingContext.contentEncodingIds()).thenReturn(List.of("gzip", "x-gzip"));
        when(contentEncodingContext.encoder("gzip")).thenReturn(runtimeEncoder);
        when(contentEncodingContext.encoder("x-gzip")).thenReturn(runtimeEncoder);
        when(response.headers()).thenReturn(responseHeaders);
        when(response.outputStream()).thenReturn(sent);

        CachedHandler selected = handler.selectHandler(
                identityHandler,
                                                       request,
                                                       (coding, suffix) -> Optional.empty());

        selected.handle(LruCache.create(), Method.GET, request, response, "nested/resource.txt");

        assertThat(responseHeaders, hasHeader(HeaderNames.CONTENT_ENCODING, "x-gzip"));
        assertThat(sent.toString(StandardCharsets.UTF_8), is("context-runtime:Nested content"));
        verify(response, never()).status(Status.NOT_ACCEPTABLE_406);
    }

    @Test
    void preCompressedRuntimeEncodingWildcardUsesAliasWhenCanonicalCodingIsRejected() throws IOException, URISyntaxException {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = inMemoryHandler("Nested content");
        ContentEncoder runtimeEncoder = new TestEncoding("gzip", "context-runtime:").encoder();
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        ServerRequest request = mockRequestWithHeaders("gzip;q=0, *, identity;q=0", null, contentEncodingContext);
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);
        ByteArrayOutputStream sent = new ByteArrayOutputStream();

        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(contentEncodingContext.contentEncodingSupported("x-gzip")).thenReturn(true);
        when(contentEncodingContext.contentEncodingIds()).thenReturn(List.of("x-gzip"));
        when(contentEncodingContext.encoder("x-gzip")).thenReturn(runtimeEncoder);
        when(response.headers()).thenReturn(responseHeaders);
        when(response.outputStream()).thenReturn(sent);

        CachedHandler selected = handler.selectHandler(
                identityHandler,
                                                       request,
                                                       (coding, suffix) -> Optional.empty());

        selected.handle(LruCache.create(), Method.GET, request, response, "nested/resource.txt");

        assertThat(responseHeaders, hasHeader(HeaderNames.CONTENT_ENCODING, "x-gzip"));
        assertThat(sent.toString(StandardCharsets.UTF_8), is("context-runtime:Nested content"));
        verify(response, never()).status(Status.NOT_ACCEPTABLE_406);
    }

    @Test
    void preCompressedRuntimeEncodingPrefersConcreteCodingOverWildcard() throws IOException, URISyntaxException {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = inMemoryHandler("Nested content");
        ServerRequest request = mockRequestWithHeaders("*, gzip, identity;q=0",
                                                       null,
                                                       runtimeContentEncodingContext(new TestEncoding("br", "br-runtime:"),
                                                                                     new TestEncoding("gzip",
                                                                                                      "gzip-runtime:")));
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);
        ByteArrayOutputStream sent = new ByteArrayOutputStream();

        when(response.headers()).thenReturn(responseHeaders);
        when(response.outputStream()).thenReturn(sent);

        CachedHandler selected = handler.selectHandler(
                identityHandler,
                                                       request,
                                                       (coding, suffix) -> Optional.empty());

        selected.handle(LruCache.create(), Method.GET, request, response, "nested/resource.txt");

        assertThat(responseHeaders, hasHeader(HeaderNames.CONTENT_ENCODING, "gzip"));
        assertThat(sent.toString(StandardCharsets.UTF_8), is("gzip-runtime:Nested content"));
    }

    @Test
    void preCompressedWildcardSelectsSidecarWhenIdentityRejected() throws IOException, URISyntaxException {
        assertSidecarSelected("*, identity;q=0", "br", "Brotli content");
    }

    @Test
    void preCompressedWildcardPrefersSidecarOverRuntimeEncoding() throws IOException, URISyntaxException {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = inMemoryHandler("Nested content");
        CachedHandler sidecarHandler = inMemoryHandler("Brotli content")
                .withRepresentation(ResponseRepresentation.encoded("br"));
        ServerRequest request = mockRequestWithHeaders("*, identity;q=0",
                                                       null,
                                                       runtimeContentEncodingContext(new TestEncoding("gzip", "runtime:")));
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);
        AtomicReference<byte[]> sent = new AtomicReference<>();

        when(response.headers()).thenReturn(responseHeaders);
        Mockito.doAnswer(inv -> {
            sent.set(inv.getArgument(0));
            return null;
        }).when(response).send(any(byte[].class));

        CachedHandler selected = handler.selectHandler(
                identityHandler,
                request,
                (coding, suffix) -> "br".equals(coding) ? Optional.of(sidecarHandler) : Optional.empty());

        selected.handle(LruCache.create(), Method.GET, request, response, "nested/resource.txt");

        assertThat(responseHeaders, hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
        assertThat(responseHeaders, hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
        assertThat(new String(sent.get(), StandardCharsets.UTF_8), is("Brotli content"));
        verify(response, never()).status(Status.NOT_ACCEPTABLE_406);
    }

    @Test
    void preCompressedSidecarSkipsRuntimeProviderEnumerationWhenItWins() throws IOException, URISyntaxException {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = inMemoryHandler("Nested content");
        CachedHandler sidecarHandler = inMemoryHandler("Brotli content")
                .withRepresentation(ResponseRepresentation.encoded("br"));
        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        ServerRequest request = mockRequestWithHeaders("br, gzip", null, contentEncodingContext);
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);
        AtomicReference<byte[]> sent = new AtomicReference<>();

        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(true);
        when(response.headers()).thenReturn(responseHeaders);
        Mockito.doAnswer(inv -> {
            sent.set(inv.getArgument(0));
            return null;
        }).when(response).send(any(byte[].class));

        CachedHandler selected = handler.selectHandler(
                identityHandler,
                                                       request,
                                                       (coding, suffix) -> Optional.of(sidecarHandler));

        selected.handle(LruCache.create(), Method.GET, request, response, "nested/resource.txt");

        assertThat(responseHeaders, hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
        assertThat(new String(sent.get(), StandardCharsets.UTF_8), is("Brotli content"));
        verify(contentEncodingContext, never()).prototype();
        verify(contentEncodingContext, never()).contentEncodingIds();
        verify(contentEncodingContext, never()).encoder(anyString());
    }

    @Test
    void preCompressedSidecarUnavailableDuringHandleFallsBackToIdentity() throws IOException, URISyntaxException {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = inMemoryHandler("Nested content");
        CachedHandler sidecarHandler = (cache, method, request, response, requestedResource) -> false;
        ServerRequest request = mockRequestWithHeaders("gzip", null, ContentEncodingContext.create());
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);
        AtomicReference<byte[]> sent = new AtomicReference<>();

        when(response.headers()).thenReturn(responseHeaders);
        Mockito.doAnswer(invocation -> {
            sent.set(invocation.getArgument(0));
            return null;
        }).when(response).send(any(byte[].class));

        CachedHandler selected = handler.selectHandler(identityHandler,
                                                        request,
                                                        (coding, suffix) -> Optional.of(sidecarHandler));

        selected.handle(LruCache.create(), Method.GET, request, response, "nested/resource.txt");

        assertThat(responseHeaders, noHeader(HeaderNames.CONTENT_ENCODING));
        assertThat(responseHeaders, hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
        assertThat(new String(sent.get(), StandardCharsets.UTF_8), is("Nested content"));
    }

    @Test
    void preCompressedNoAcceptableRepresentationSendsEmpty406() throws IOException, URISyntaxException {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = inMemoryHandler("Nested content");
        ServerRequest request = mockRequestWithHeaders("br, identity;q=0", null, runtimeContentEncodingContext());
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);

        when(response.headers()).thenReturn(responseHeaders);

        CachedHandler selected = handler.selectHandler(
                identityHandler,
                                                       request,
                                                       (coding, suffix) -> Optional.empty());

        selected.handle(LruCache.create(), Method.GET, request, response, "nested/resource.txt");

        assertThat(responseHeaders, noHeader(HeaderNames.CONTENT_ENCODING));
        verify(response).status(Status.NOT_ACCEPTABLE_406);
        verify(response).send();
    }

    @Test
    void preCompressedNoAcceptableRepresentationRemovesStaleIdentity() throws IOException, URISyntaxException {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = mock(CachedHandler.class);
        ServerRequest request = mockRequestWithHeaders("br, identity;q=0", null, ContentEncodingContext.create());
        ServerResponse response = mock(ServerResponse.class);
        LruCache<String, CachedHandler> cache = LruCache.create();

        when(identityHandler.available()).thenReturn(false);
        cache.put("nested/resource.txt", identityHandler);

        CachedHandler selected = handler.selectHandler(
                identityHandler,
                                                       request,
                                                       (coding, suffix) -> Optional.empty());

        boolean handled = selected.handle(cache, Method.GET, request, response, "nested/resource.txt");

        assertThat(handled, is(false));
        assertThat(cache.get("nested/resource.txt").isEmpty(), is(true));
        verify(identityHandler, never()).handle(any(), any(), any(), any(), anyString());
    }

    @Test
    void preCompressedNoAcceptableRepresentationDoesNotSendIdentityAfterRecovery()
            throws IOException, URISyntaxException {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = mock(CachedHandler.class);
        ServerRequest request = mockRequestWithHeaders("br, identity;q=0", null, ContentEncodingContext.create());
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);
        AtomicBoolean available = new AtomicBoolean();

        when(identityHandler.available()).thenAnswer(_ -> available.get());
        when(response.headers()).thenReturn(responseHeaders);

        CachedHandler selected = handler.selectHandler(
                identityHandler,
                                                       request,
                                                       (coding, suffix) -> Optional.empty());
        available.set(true);

        boolean handled = selected.handle(LruCache.create(), Method.GET, request, response, "nested/resource.txt");

        assertThat(handled, is(true));
        verify(identityHandler, never()).handle(any(), any(), any(), any(), anyString());
        verify(response).status(Status.NOT_ACCEPTABLE_406);
        verify(response).send();
    }

    @Test
    void preCompressedUnknownAcceptEncodingIsUnavailable() throws IOException, URISyntaxException {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = inMemoryHandler("Nested content");
        ServerRequest request = mockRequestWithHeaders("zstd, identity;q=0", null, ContentEncodingContext.create());
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);

        when(response.headers()).thenReturn(responseHeaders);

        CachedHandler selected = handler.selectHandler(
                identityHandler,
                                                       request,
                                                       (coding, suffix) -> Optional.empty());

        selected.handle(LruCache.create(), Method.GET, request, response, "nested/resource.txt");

        assertThat(responseHeaders, noHeader(HeaderNames.CONTENT_ENCODING));
        verify(response).status(Status.NOT_ACCEPTABLE_406);
        verify(response).send();
    }

    @Test
    void preCompressedInvalidAcceptEncodingReturnsBadRequest() {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = inMemoryHandler("Nested content");
        ServerRequest request = mockRequestWithHeaders("g zip, identity;q=0", null, ContentEncodingContext.create());

        BadRequestException actual = assertThrows(BadRequestException.class,
                                                  () -> handler.selectHandler(
                                                          identityHandler,
                                                                              request,
                                                                              (coding, suffix) -> Optional.empty()));

        assertThat(actual.status(), is(Status.BAD_REQUEST_400));
    }

    @Test
    void preCompressedSidecarLookupIsCached() throws IOException, URISyntaxException {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = inMemoryHandler("Content");
        CachedHandler sidecarHandler = inMemoryHandler("Brotli content")
                .withRepresentation(ResponseRepresentation.encoded("br"));
        ServerRequest request = mockRequestWithHeaders("br", null, ContentEncodingContext.create());
        AtomicInteger lookups = new AtomicInteger();

        SidecarCache.Resolver resolver = (coding, suffix) -> {
            lookups.incrementAndGet();
            return Optional.of(sidecarHandler);
        };

        handler.selectHandler(identityHandler, request, resolver);
        handler.selectHandler(identityHandler, request, resolver);

        assertThat(lookups.get(), is(1));

        CachedHandler otherIdentityHandler = inMemoryHandler("Other content");

        handler.selectHandler(otherIdentityHandler, request, resolver);
        handler.selectHandler(otherIdentityHandler, request, resolver);

        assertThat(lookups.get(), is(2));
    }

    @Test
    void preCompressedConcurrentSidecarLookupIsCoalesced() throws Exception {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = inMemoryHandler("Content");
        CachedHandler sidecarHandler = inMemoryHandler("Brotli content");
        ServerRequest request = mockRequestWithHeaders("br", null, ContentEncodingContext.create());
        AtomicInteger lookups = new AtomicInteger();
        CountDownLatch firstResolverEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstResolver = new CountDownLatch(1);
        CountDownLatch secondLookupStarted = new CountDownLatch(1);
        AtomicReference<Thread> secondLookupThread = new AtomicReference<>();

        SidecarCache.Resolver resolver = (coding, suffix) -> {
            lookups.incrementAndGet();
            firstResolverEntered.countDown();
            try {
                if (!releaseFirstResolver.await(10, TimeUnit.SECONDS)) {
                    throw new IOException("Timed out awaiting release of the first lookup");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while awaiting release of the first lookup", e);
            }
            return Optional.of(sidecarHandler);
        };

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<CachedHandler> first = executor.submit(() -> handler.selectHandler(identityHandler, request, resolver));
            Future<CachedHandler> second;
            try {
                assertThat(firstResolverEntered.await(5, TimeUnit.SECONDS), is(true));
                second = executor.submit(() -> {
                    secondLookupThread.set(Thread.currentThread());
                    secondLookupStarted.countDown();
                    return handler.selectHandler(identityHandler, request, resolver);
                });
                assertThat(secondLookupStarted.await(5, TimeUnit.SECONDS), is(true));
                assertThatWithRetry(secondLookupThread.get()::getState, is(Thread.State.WAITING));
                assertThat(lookups.get(), is(1));
            } finally {
                releaseFirstResolver.countDown();
            }

            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void preCompressedDifferentCodingLookupsAreConcurrent() throws Exception {
        SidecarCache sidecarCache = SidecarCache.create();
        CachedHandler brotliHandler = inMemoryHandler("Brotli content");
        CachedHandler gzipHandler = inMemoryHandler("Gzip content");
        CountDownLatch brotliResolverEntered = new CountDownLatch(1);
        CountDownLatch releaseBrotliResolver = new CountDownLatch(1);

        SidecarCache.Resolver resolver = (coding, suffix) -> {
            if ("gzip".equals(coding)) {
                return Optional.of(gzipHandler);
            }
            brotliResolverEntered.countDown();
            try {
                if (!releaseBrotliResolver.await(10, TimeUnit.SECONDS)) {
                    throw new IOException("Timed out awaiting release of the Brotli lookup");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while awaiting release of the Brotli lookup", e);
            }
            return Optional.of(brotliHandler);
        };

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Optional<CachedHandler>> brotli = executor.submit(() -> sidecarCache.resolve("br", ".br", resolver));
            try {
                assertThat(brotliResolverEntered.await(5, TimeUnit.SECONDS), is(true));
                Future<Optional<CachedHandler>> gzip =
                        executor.submit(() -> sidecarCache.resolve("gzip", ".gz", resolver));
                assertThat(gzip.get(5, TimeUnit.SECONDS), is(Optional.of(gzipHandler)));
            } finally {
                releaseBrotliResolver.countDown();
            }
            assertThat(brotli.get(5, TimeUnit.SECONDS), is(Optional.of(brotliHandler)));
        }
    }

    @Test
    void preCompressedMissingSidecarLookupIsCached() throws IOException, URISyntaxException {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = inMemoryHandler("Content");
        ServerRequest request = mockRequestWithHeaders("br", null, ContentEncodingContext.create());
        AtomicInteger lookups = new AtomicInteger();

        SidecarCache.Resolver resolver = (coding, suffix) -> {
            lookups.incrementAndGet();
            return Optional.empty();
        };

        handler.selectHandler(identityHandler, request, resolver);
        handler.selectHandler(identityHandler, request, resolver);

        assertThat(lookups.get(), is(1));
    }

    @Test
    void preCompressedRepresentationKeepsContentEncodingOnNotModified() {
        HttpException exception = new HttpException("not modified", Status.NOT_MODIFIED_304, true);

        ResponseRepresentation.encoded("br").apply(exception);

        assertThat(exception.headers(), hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
        assertThat(exception.headers(), hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
    }

    @Test
    void preCompressedRepresentationDoesNotPutContentEncodingOnErrorResponse() {
        HttpException exception = new HttpException("precondition failed", Status.PRECONDITION_FAILED_412, true);

        ResponseRepresentation.encoded("br").apply(exception);

        assertThat(exception.headers(), hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
        assertThat(exception.headers(), noHeader(HeaderNames.CONTENT_ENCODING));
    }

    @Test
    void preCompressedRepresentationEtagNeedsLengthOnlyForSidecar() {
        assertThat(ResponseRepresentation.plain().etagRequiresContentLength(), is(false));
        assertThat(ResponseRepresentation.identity(true).etagRequiresContentLength(), is(false));
        assertThat(ResponseRepresentation.runtime("gzip", new TestEncoding().encoder()).etagRequiresContentLength(),
                   is(false));
        assertThat(ResponseRepresentation.encoded("br").etagRequiresContentLength(), is(true));
    }

    @Test
    void preCompressedIfNoneMatchPreventsIfModifiedSinceFallback() throws IOException {
        byte[] bytes = "Brotli content".getBytes(StandardCharsets.UTF_8);
        CachedHandler handler = new CachedHandlerInMemory(MediaTypes.TEXT_PLAIN,
                                                          Instant.EPOCH,
                                                          ServerResponseHeaders::lastModified,
                                                          bytes,
                                                          bytes.length,
                                                          HeaderValues.create(HeaderNames.CONTENT_LENGTH, bytes.length))
                .withRepresentation(ResponseRepresentation.encoded("br"));
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(IF_NONE_MATCH, "\"0\"");
        headers.add(IF_MODIFIED_SINCE, "Thu, 01 Jan 1970 00:00:00 GMT");
        ServerRequest request = mock(ServerRequest.class);
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);
        AtomicReference<byte[]> sent = new AtomicReference<>();

        when(request.headers()).thenReturn(ServerRequestHeaders.create(headers));
        when(response.headers()).thenReturn(responseHeaders);
        Mockito.doAnswer(inv -> {
            sent.set(inv.getArgument(0));
            return null;
        }).when(response).send(any(byte[].class));

        handler.handle(LruCache.create(), Method.GET, request, response, "resource.txt");

        assertThat(responseHeaders, hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
        assertThat(new String(sent.get(), StandardCharsets.UTF_8), is("Brotli content"));
        verify(response, never()).status(Status.NOT_MODIFIED_304);
    }

    @Test
    void memoryCacheClearReleasesCapacity() {
        MemoryCache memoryCache = MemoryCache.create(builder -> builder.enabled(true)
                .capacity(Size.create(10)));
        TestContentHandler handler = new TestContentHandler(FileSystemHandlerConfig.builder()
                                                               .location(Paths.get("."))
                                                               .memoryCache(memoryCache)
                                                               .build(),
                                                           true);

        assertThat(handler.cacheInMemory("first", 5, () -> inMemoryHandler("12345")).isPresent(), is(true));
        assertThat(handler.canCacheInMemory(6), is(false));

        handler.releaseCache();

        assertThat(handler.canCacheInMemory(10), is(true));
    }

    @Test
    void memoryCacheCountsAliasedHandlerOnce() {
        MemoryCache memoryCache = MemoryCache.create(builder -> builder.enabled(true)
                .capacity(Size.create(10)));
        TestContentHandler handler = new TestContentHandler(FileSystemHandlerConfig.builder()
                                                               .location(Paths.get("."))
                                                               .memoryCache(memoryCache)
                                                               .build(),
                                                           true);
        CachedHandlerInMemory cached = inMemoryHandler("12345");

        handler.cacheInMemory("first", cached);
        handler.cacheInMemory("alias", cached);

        assertThat(handler.canCacheInMemory(5), is(true));
        assertThat(handler.canCacheInMemory(6), is(false));

        handler.releaseCache();

        assertThat(handler.canCacheInMemory(10), is(true));
    }

    @Test
    void memoryCacheReservesCapacityWhileSupplierLoads() {
        MemoryCache memoryCache = MemoryCache.create(builder -> builder.enabled(true)
                .capacity(Size.create(5)));
        TestContentHandler handler = new TestContentHandler(FileSystemHandlerConfig.builder()
                                                               .location(Paths.get("."))
                                                               .memoryCache(memoryCache)
                                                               .build(),
                                                           true);
        AtomicInteger supplierCalls = new AtomicInteger();

        Optional<CachedHandlerInMemory> cached = handler.cacheInMemory("first", 5, () -> {
            supplierCalls.incrementAndGet();
            assertThat(handler.canCacheInMemory(1), is(false));
            return inMemoryHandler("12345");
        });

        assertThat(cached.isPresent(), is(true));
        assertThat(supplierCalls.get(), is(1));
        assertThat(handler.canCacheInMemory(1), is(false));
    }

    @Test
    void preCompressedPreconditionErrorDoesNotSetResponseContentEncoding() {
        byte[] bytes = "Brotli content".getBytes(StandardCharsets.UTF_8);
        CachedHandler handler = new CachedHandlerInMemory(MediaTypes.TEXT_PLAIN,
                                                          Instant.EPOCH,
                                                          ServerResponseHeaders::lastModified,
                                                          bytes,
                                                          bytes.length,
                                                          HeaderValues.create(HeaderNames.CONTENT_LENGTH, bytes.length))
                .withRepresentation(ResponseRepresentation.encoded("br"));
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(IF_MATCH, "\"missing\"");
        ServerRequest request = mock(ServerRequest.class);
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);

        when(request.headers()).thenReturn(ServerRequestHeaders.create(headers));
        when(response.headers()).thenReturn(responseHeaders);

        HttpException actual = assertThrows(HttpException.class,
                                            () -> handler.handle(LruCache.create(),
                                                                 Method.GET,
                                                                 request,
                                                                 response,
                                                                 "resource.txt"));

        assertThat(actual.status(), is(Status.PRECONDITION_FAILED_412));
        assertThat(responseHeaders, noHeader(HeaderNames.CONTENT_ENCODING));
        assertThat(actual.headers(), noHeader(HeaderNames.CONTENT_ENCODING));
    }

    @Test
    void preCompressedRangeErrorDoesNotSetResponseContentEncoding() throws IOException {
        Path resource = tempDir.resolve("resource.txt.br");
        Files.writeString(resource, "Brotli content");
        CachedHandler handler = new CachedHandlerPath(resource,
                                                      MediaTypes.TEXT_PLAIN,
                                                      path -> Optional.empty(),
                                                      ServerResponseHeaders::lastModified)
                .withRepresentation(ResponseRepresentation.encoded("br"));
        ServerRequest request = mockRequestWithHeaders("br", "bytes=999-1000", ContentEncodingContext.create());
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);

        when(response.headers()).thenReturn(responseHeaders);

        HttpException actual = assertThrows(HttpException.class,
                                            () -> handler.handle(LruCache.create(),
                                                                 Method.GET,
                                                                 request,
                                                                 response,
                                                                 "resource.txt"));

        assertThat(actual.status(), is(Status.REQUESTED_RANGE_NOT_SATISFIABLE_416));
        assertThat(actual.headers(), hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
        assertThat(actual.headers(), noHeader(HeaderNames.CONTENT_ENCODING));
        assertThat(responseHeaders, noHeader(HeaderNames.CONTENT_ENCODING));
    }

    @Test
    void preCompressedInMemoryRangeErrorDoesNotSetResponseContentEncoding() throws IOException {
        byte[] bytes = "Brotli content".getBytes(StandardCharsets.UTF_8);
        CachedHandler handler = new CachedHandlerInMemory(MediaTypes.TEXT_PLAIN,
                                                          Instant.EPOCH,
                                                          ServerResponseHeaders::lastModified,
                                                          bytes,
                                                          bytes.length,
                                                          HeaderValues.create(HeaderNames.CONTENT_LENGTH, bytes.length))
                .withRepresentation(ResponseRepresentation.encoded("br"));
        ServerRequest request = mockRequestWithHeaders("br", "bytes=999-1000", ContentEncodingContext.create());
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);

        when(response.headers()).thenReturn(responseHeaders);

        HttpException actual = assertThrows(HttpException.class,
                                            () -> handler.handle(LruCache.create(),
                                                                 Method.GET,
                                                                 request,
                                                                 response,
                                                                 "resource.txt"));

        assertThat(actual.status(), is(Status.REQUESTED_RANGE_NOT_SATISFIABLE_416));
        assertThat(actual.headers(), hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
        assertThat(actual.headers(), noHeader(HeaderNames.CONTENT_ENCODING));
        assertThat(responseHeaders, noHeader(HeaderNames.CONTENT_ENCODING));
    }

    @Test
    void preCompressedInMemoryRangeSendsSelectedBytes() throws IOException {
        byte[] bytes = "Brotli content".getBytes(StandardCharsets.UTF_8);
        CachedHandler handler = new CachedHandlerInMemory(MediaTypes.TEXT_PLAIN,
                                                          Instant.EPOCH,
                                                          ServerResponseHeaders::lastModified,
                                                          bytes,
                                                          bytes.length,
                                                          HeaderValues.create(HeaderNames.CONTENT_LENGTH, bytes.length))
                .withRepresentation(ResponseRepresentation.encoded("br"));
        ServerRequest request = mockRequestWithHeaders("br", "bytes=2-5", ContentEncodingContext.create());
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);
        AtomicReference<byte[]> sent = new AtomicReference<>();

        when(response.headers()).thenReturn(responseHeaders);
        Mockito.doAnswer(inv -> {
            sent.set(inv.getArgument(0));
            return null;
        }).when(response).send(any(byte[].class));

        handler.handle(LruCache.create(), Method.GET, request, response, "resource.txt");

        assertThat(responseHeaders, hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
        assertThat(new String(sent.get(), StandardCharsets.UTF_8), is("otli"));
        verify(response).status(Status.PARTIAL_CONTENT_206);
    }

    private void assertRuntimeEncodingSelected(String acceptEncoding) throws IOException, URISyntaxException {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = inMemoryHandler("Nested content");
        ServerRequest request = mockRequestWithHeaders(acceptEncoding, null, runtimeContentEncodingContext());
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);
        ByteArrayOutputStream sent = new ByteArrayOutputStream();

        when(response.headers()).thenReturn(responseHeaders);
        when(response.outputStream()).thenReturn(sent);

        CachedHandler selected = handler.selectHandler(
                identityHandler,
                                                       request,
                                                       (coding, suffix) -> Optional.empty());

        selected.handle(LruCache.create(), Method.GET, request, response, "nested/resource.txt");

        assertThat(responseHeaders, hasHeader(HeaderNames.CONTENT_ENCODING, "gzip"));
        assertThat(responseHeaders, hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
        assertThat(sent.toString(StandardCharsets.UTF_8), is("runtime:Nested content"));
    }

    private void assertIdentitySelected(String acceptEncoding) throws IOException, URISyntaxException {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = inMemoryHandler("Nested content");
        ServerRequest request = mockRequestWithHeaders(acceptEncoding, null, runtimeContentEncodingContext());
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);
        AtomicReference<byte[]> sent = new AtomicReference<>();

        when(response.headers()).thenReturn(responseHeaders);
        Mockito.doAnswer(inv -> {
            sent.set(inv.getArgument(0));
            return null;
        }).when(response).send(any(byte[].class));

        CachedHandler selected = handler.selectHandler(
                identityHandler,
                                                       request,
                                                       (coding, suffix) -> Optional.empty());

        selected.handle(LruCache.create(), Method.GET, request, response, "nested/resource.txt");

        assertThat(responseHeaders, noHeader(HeaderNames.CONTENT_ENCODING));
        assertThat(responseHeaders, hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
        assertThat(new String(sent.get(), StandardCharsets.UTF_8), is("Nested content"));
    }

    private void assertSidecarSelected(String acceptEncoding, String contentEncoding, String body)
            throws IOException, URISyntaxException {
        TestContentHandler handler = TestContentHandler.create(true);
        CachedHandler identityHandler = inMemoryHandler("Nested content");
        CachedHandler brHandler = inMemoryHandler("Brotli content")
                .withRepresentation(ResponseRepresentation.encoded("br"));
        CachedHandler gzipHandler = inMemoryHandler("Gzip content")
                .withRepresentation(ResponseRepresentation.encoded("gzip"));
        ServerRequest request = mockRequestWithHeaders(acceptEncoding, null, ContentEncodingContext.create());
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse response = mock(ServerResponse.class);
        AtomicReference<byte[]> sent = new AtomicReference<>();

        when(response.headers()).thenReturn(responseHeaders);
        Mockito.doAnswer(inv -> {
            sent.set(inv.getArgument(0));
            return null;
        }).when(response).send(any(byte[].class));

        CachedHandler selected = handler.selectHandler(
                identityHandler,
                                                       request,
                                                       (coding, suffix) -> switch (coding) {
                                                           case "br" -> Optional.of(brHandler);
                                                           case "gzip" -> Optional.of(gzipHandler);
                                                           default -> Optional.empty();
                                                       });

        selected.handle(LruCache.create(), Method.GET, request, response, "nested/resource.txt");

        assertThat(responseHeaders, hasHeader(HeaderNames.CONTENT_ENCODING, contentEncoding));
        assertThat(new String(sent.get(), StandardCharsets.UTF_8), is(body));
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

    private CachedHandlerInMemory inMemoryHandler(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return new CachedHandlerInMemory(MediaTypes.TEXT_PLAIN,
                                         null,
                                         null,
                                         bytes,
                                         bytes.length,
                                         HeaderValues.create(HeaderNames.CONTENT_LENGTH, bytes.length));
    }

    private ServerRequest mockRequestWithHeaders(String acceptEncoding,
                                                 String range,
                                                 ContentEncodingContext contentEncodingContext) {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderNames.ACCEPT_ENCODING, acceptEncoding);
        if (range != null) {
            headers.add(HeaderNames.RANGE, range);
        }
        ServerRequest request = mock(ServerRequest.class);
        ListenerContext listenerContext = mock(ListenerContext.class);
        when(listenerContext.contentEncodingContext()).thenReturn(contentEncodingContext);
        when(request.headers()).thenReturn(ServerRequestHeaders.create(headers));
        when(request.listenerContext()).thenReturn(listenerContext);
        return request;
    }

    private ContentEncodingContext runtimeContentEncodingContext() {
        return runtimeContentEncodingContext(new TestEncoding());
    }

    private ContentEncodingContext runtimeContentEncodingContext(ContentEncoding... encodings) {
        var builder = ContentEncodingContext.builder();
        for (ContentEncoding encoding : encodings) {
            builder.addContentEncoding(encoding);
        }
        return builder.build();
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

    static class ThrowingContentHandler extends FileSystemContentHandler {
        private final RuntimeException exception;

        ThrowingContentHandler(FileSystemHandlerConfig config, RuntimeException exception) {
            super(config);
            this.exception = exception;
        }

        static ThrowingContentHandler create(RuntimeException exception) {
            return new ThrowingContentHandler(FileSystemHandlerConfig.builder()
                                                    .location(Paths.get("."))
                                                    .build(),
                                             exception);
        }

        @Override
        boolean doHandle(Method method,
                         String requestedResource,
                         ServerRequest req,
                         ServerResponse res,
                         String rawPath,
                         Path path) {
            throw exception;
        }
    }

    private record TestEncoding(String id, String prefix, Set<String> ids, AtomicInteger headerCalls)
            implements ContentEncoding {
        TestEncoding() {
            this("gzip", "runtime:");
        }

        TestEncoding(String id, String prefix) {
            this(id, prefix, Set.of(id));
        }

        TestEncoding(String id, String prefix, Set<String> ids) {
            this(id, prefix, ids, new AtomicInteger());
        }

        @Override
        public Set<String> ids() {
            return ids;
        }

        @Override
        public boolean supportsEncoding() {
            return true;
        }

        @Override
        public boolean supportsDecoding() {
            return false;
        }

        @Override
        public ContentDecoder decoder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ContentEncoder encoder() {
            return new ContentEncoder() {
                @Override
                public OutputStream apply(OutputStream network) {
                    return new OutputStream() {
                        private boolean prefixWritten;

                        @Override
                        public void write(int b) throws IOException {
                            writePrefix();
                            network.write(b);
                        }

                        @Override
                        public void write(byte[] bytes, int offset, int length) throws IOException {
                            writePrefix();
                            network.write(bytes, offset, length);
                        }

                        @Override
                        public void close() throws IOException {
                            writePrefix();
                            network.close();
                        }

                        private void writePrefix() throws IOException {
                            if (!prefixWritten) {
                                network.write(prefix.getBytes(StandardCharsets.UTF_8));
                                prefixWritten = true;
                            }
                        }
                    };
                }

                @Override
                public void headers(WritableHeaders<?> headers) {
                    headerCalls.incrementAndGet();
                    headers.set(HeaderNames.CONTENT_ENCODING, id);
                    headers.remove(HeaderNames.CONTENT_LENGTH);
                }
            };
        }

        @Override
        public String name() {
            return id;
        }

        @Override
        public String type() {
            return id;
        }
    }
}
