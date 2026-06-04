/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.helidon.common.LruCache;
import io.helidon.common.Size;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.uri.UriQuery;
import io.helidon.http.ForbiddenException;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.WritableHeaders;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.noHeader;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static java.lang.System.Logger.Level.TRACE;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CachedHandlerTest {
    private static final System.Logger LOGGER = System.getLogger(CachedHandlerTest.class.getName());
    private static final MediaType MEDIA_TYPE_ICON = MediaTypes.create("image/x-icon");
    private static final Header ICON_TYPE = HeaderValues.create(HeaderNames.CONTENT_TYPE, MEDIA_TYPE_ICON.text());
    private static final Header RESOURCE_CONTENT_LENGTH = HeaderValues.create(HeaderNames.CONTENT_LENGTH, 7);

    private static ClassPathContentHandler classpathHandler;
    private static FileSystemContentHandler fsHandler;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void initTestClass() {
        classpathHandler = (ClassPathContentHandler) StaticContentFeature.createService(
                ClasspathHandlerConfig.builder()
                        .location("/web")
                        .cachedFiles(Set.of("favicon.ico"))
                        .welcome("resource.txt")
                        .build());
        classpathHandler.beforeStart();

        fsHandler = (FileSystemContentHandler) StaticContentFeature.createService(
                FileSystemHandlerConfig.builder()
                        .location(Paths.get("./src/test/resources/web"))
                        .cachedFiles(Set.of("nested"))
                        .welcome("resource.txt")
                        .build());
        fsHandler.beforeStart();
    }

    @Test
    void testClasspathInMemoryCache() {
        Optional<CachedHandlerInMemory> cachedHandlerInMemory = classpathHandler.cacheInMemory("web/favicon.ico");
        assertThat("Handler should be cached in memory", cachedHandlerInMemory, optionalPresent());
        CachedHandlerInMemory cached = cachedHandlerInMemory.get();
        assertThat("Cached bytes must not be null", cached.bytes(), notNullValue());
        assertThat("Cached bytes must not be empty", cached.bytes(), not(BufferData.EMPTY_BYTES));
        assertThat("Content length", cached.contentLength(), is(1230));
        assertThat("Last modified", cached.lastModified(), notNullValue());
        assertThat("Media type", cached.mediaType(), is(MEDIA_TYPE_ICON));
    }

    @Test
    void testClasspathFromInMemory() throws IOException, URISyntaxException {
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();

        ServerRequest req = mock(ServerRequest.class);
        when(req.headers()).thenReturn(ServerRequestHeaders.create());
        when(req.prologue()).thenReturn(HttpPrologue.create("http/1.1",
                                                            "http",
                                                            "1.1",
                                                            Method.GET,
                                                            "/favicon.ico",
                                                            false));

        ServerResponse res = mock(ServerResponse.class);
        when(res.headers()).thenReturn(responseHeaders);

        boolean result = classpathHandler.doHandle(Method.GET, "favicon.ico", req, res, false);

        assertThat("Handler should have found favicon.ico", result, is(true));
        assertThat(responseHeaders, hasHeader(ICON_TYPE));
        assertThat(responseHeaders, hasHeader(HeaderNames.ETAG));
        assertThat(responseHeaders, hasHeader(HeaderNames.LAST_MODIFIED));
    }

    @Test
    void testClasspathCacheFound() throws IOException, URISyntaxException {
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();

        ServerRequest req = mock(ServerRequest.class);
        when(req.headers()).thenReturn(ServerRequestHeaders.create());
        when(req.prologue()).thenReturn(HttpPrologue.create("http/1.1", "http", "1.1", Method.GET, "/resource.txt", false));

        ServerResponse res = mock(ServerResponse.class);
        when(res.headers()).thenReturn(responseHeaders);
        when(res.outputStream()).thenReturn(new ByteArrayOutputStream());

        boolean result = classpathHandler.doHandle(Method.GET, "resource.txt", req, res, false);

        assertThat("Handler should have found resource.txt", result, is(true));
        assertThat(responseHeaders, hasHeader(HeaderValues.CONTENT_TYPE_TEXT_PLAIN));
        assertThat(responseHeaders, hasHeader(RESOURCE_CONTENT_LENGTH));
        assertThat(responseHeaders, hasHeader(HeaderNames.ETAG));
        assertThat(responseHeaders, hasHeader(HeaderNames.LAST_MODIFIED));

        // now make sure it is cached
        Optional<CachedHandler> cachedHandler = classpathHandler.cacheHandler("web/resource.txt");
        assertThat("Handler should be cached", cachedHandler, optionalPresent());
        CachedHandler cached = cachedHandler.get();
        assertThat("During tests, classpath should be loaded from file system", cached, instanceOf(CachedHandlerPath.class));
        CachedHandlerPath pathHandler = (CachedHandlerPath) cached;
        assertThat("Path", pathHandler.path(), notNullValue());
        assertThat("Last modified function", pathHandler.lastModified(), notNullValue());
        assertThat("Last modified", pathHandler.lastModified().apply(pathHandler.path()), notNullValue());
        assertThat("Media type", pathHandler.mediaType(), is(MediaTypes.TEXT_PLAIN));
    }

    @Test
    void testJarUnknownContentLengthUsesExtractedFileSize() throws IOException, URISyntaxException {
        Path jarFile = createTmpJarFile();
        CachedHandlerJar handler = CachedHandlerJar.create(TemporaryStorage.create(builder -> builder
                                                                      .directory(tempDir)
                                                                      .deleteOnExit(false)),
                                                           jarUrl(jarFile, "resource.txt"),
                                                           null,
                                                           MediaTypes.TEXT_PLAIN,
                                                           -1);
        ServerRequest req = mock(ServerRequest.class);
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse res = mock(ServerResponse.class);
        ByteArrayOutputStream body = new ByteArrayOutputStream();

        when(req.headers()).thenReturn(ServerRequestHeaders.create());
        when(res.headers()).thenReturn(responseHeaders);
        when(res.outputStream()).thenReturn(body);

        assertThat(handler.handle(LruCache.create(), Method.GET, req, res, "resource.txt"), is(true));
        assertThat(responseHeaders, hasHeader(HeaderNames.CONTENT_LENGTH, "7"));
        assertThat(body.toString(StandardCharsets.UTF_8), is("Content"));
    }

    @Test
    void testJarUnknownContentLengthOmitsHeadContentLengthWhenNotExtracted() throws IOException, URISyntaxException {
        Path jarFile = createTmpJarFile();
        CachedHandlerJar handler = CachedHandlerJar.create(TemporaryStorage.create(builder -> builder.enabled(false)),
                                                           jarUrl(jarFile, "resource.txt"),
                                                           null,
                                                           MediaTypes.TEXT_PLAIN,
                                                           -1);
        ServerRequest req = mock(ServerRequest.class);
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse res = mock(ServerResponse.class);

        when(res.headers()).thenReturn(responseHeaders);

        assertThat(handler.handle(LruCache.create(), Method.HEAD, req, res, "resource.txt"), is(true));
        assertThat(responseHeaders, noHeader(HeaderNames.CONTENT_LENGTH));
    }

    @Test
    void testJarExtractedPathUnavailableFallsBackToJarStream() throws IOException, URISyntaxException {
        Path jarFile = createTmpJarFile();
        CachedHandlerJar handler = CachedHandlerJar.create(TemporaryStorage.create(builder -> builder
                                                                      .directory(tempDir)
                                                                      .deleteOnExit(false)),
                                                           jarUrl(jarFile, "resource.txt"),
                                                           null,
                                                           MediaTypes.TEXT_PLAIN,
                                                           7);
        List<Path> extractedFiles;
        try (Stream<Path> files = Files.list(tempDir)) {
            extractedFiles = files.toList();
        }
        Path extractedFile = extractedFiles.getFirst();
        Files.delete(extractedFile);
        Files.createDirectory(extractedFile);
        ServerRequest req = mock(ServerRequest.class);
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse res = mock(ServerResponse.class);
        ByteArrayOutputStream body = new ByteArrayOutputStream();

        when(req.headers()).thenReturn(ServerRequestHeaders.create());
        when(res.headers()).thenReturn(responseHeaders);
        when(res.outputStream()).thenReturn(body);

        assertThat(handler.handle(LruCache.create(), Method.GET, req, res, "resource.txt"), is(true));
        assertThat(body.toString(StandardCharsets.UTF_8), is("Content"));
    }

    @Test
    void testClasspathCacheHitDoesNotResolveIdentityUrlAgain() throws IOException, URISyntaxException {
        CountingClassLoader classLoader = new CountingClassLoader("web/resource.txt", "web/resource.txt.br");
        ClassPathContentHandler handler = (ClassPathContentHandler) StaticContentFeature.createService(
                ClasspathHandlerConfig.builder()
                        .location("/web")
                        .classLoader(classLoader)
                        .build());
        ServerRequest req = mock(ServerRequest.class);
        when(req.headers()).thenReturn(acceptEncodingHeaders("br"));
        when(req.prologue()).thenReturn(HttpPrologue.create("http/1.1", "http", "1.1", Method.GET, "/resource.txt", false));

        ServerResponse res = mock(ServerResponse.class);
        when(res.headers()).thenReturn(ServerResponseHeaders.create());
        when(res.outputStream()).thenReturn(new ByteArrayOutputStream());

        assertThat(handler.doHandle(Method.GET, "resource.txt", req, res, false), is(true));
        assertThat(classLoader.lookups("web/resource.txt"), is(1));
        assertThat(classLoader.lookups("web/resource.txt.br"), is(1));

        assertThat(handler.doHandle(Method.GET, "resource.txt", req, res, false), is(true));
        assertThat(classLoader.lookups("web/resource.txt"), is(1));
        assertThat(classLoader.lookups("web/resource.txt.br"), is(1));
    }

    @Test
    void testSingleFileClasspathCacheHitDoesNotResolveIdentityUrlAgain() throws IOException, URISyntaxException {
        CountingClassLoader classLoader = new CountingClassLoader("web/resource.txt", "web/resource.txt.br");
        SingleFileClassPathContentHandler handler = (SingleFileClassPathContentHandler) StaticContentFeature.createService(
                ClasspathHandlerConfig.builder()
                        .location("/web/resource.txt")
                        .singleFile(true)
                        .classLoader(classLoader)
                        .build());
        handler.beforeStart();
        ServerRequest req = mock(ServerRequest.class);
        when(req.headers()).thenReturn(acceptEncodingHeaders("br"));
        when(req.prologue()).thenReturn(HttpPrologue.create("http/1.1", "http", "1.1", Method.GET, "/resource.txt", false));

        ServerResponse res = mock(ServerResponse.class);
        when(res.headers()).thenReturn(ServerResponseHeaders.create());
        when(res.outputStream()).thenReturn(new ByteArrayOutputStream());

        assertThat(classLoader.lookups("web/resource.txt"), is(1));
        assertThat(handler.doHandle(Method.GET, "resource.txt", req, res, false), is(true));
        assertThat(classLoader.lookups("web/resource.txt"), is(1));
        assertThat(classLoader.lookups("web/resource.txt.br"), is(1));

        assertThat(handler.doHandle(Method.GET, "resource.txt", req, res, false), is(true));
        assertThat(classLoader.lookups("web/resource.txt"), is(1));
        assertThat(classLoader.lookups("web/resource.txt.br"), is(1));
    }

    @Test
    void testClasspathSidecarMissCacheDoesNotEvictPrimaryRecord() throws IOException, URISyntaxException {
        CountingClassLoader classLoader = new CountingClassLoader("web/nested/resource.txt", "web/nested/resource.txt.br");
        ClassPathContentHandler handler = (ClassPathContentHandler) StaticContentFeature.createService(
                ClasspathHandlerConfig.builder()
                        .location("/web")
                        .classLoader(classLoader)
                        .recordCacheCapacity(1)
                        .build());
        ServerRequestHeaders requestHeaders = acceptEncodingHeaders("br");

        ByteArrayOutputStream firstBody = new ByteArrayOutputStream();
        assertThat(handler.doHandle(Method.GET,
                                    "nested/resource.txt",
                                    request("/nested/resource.txt", requestHeaders),
                                    response(ServerResponseHeaders.create(), firstBody),
                                    false), is(true));
        assertThat(firstBody.toString(StandardCharsets.UTF_8), is("Nested content"));
        assertThat(classLoader.lookups("web/nested/resource.txt"), is(1));
        assertThat(classLoader.lookups("web/nested/resource.txt.br"), is(1));

        ByteArrayOutputStream secondBody = new ByteArrayOutputStream();
        assertThat(handler.doHandle(Method.GET,
                                    "nested/resource.txt",
                                    request("/nested/resource.txt", requestHeaders),
                                    response(ServerResponseHeaders.create(), secondBody),
                                    false), is(true));
        assertThat(secondBody.toString(StandardCharsets.UTF_8), is("Nested content"));
        assertThat(classLoader.lookups("web/nested/resource.txt"), is(1));
        assertThat(classLoader.lookups("web/nested/resource.txt.br"), is(1));
    }

    @Test
    void testSingleFileClasspathSidecarMissCacheDoesNotEvictPrimaryRecord() throws IOException, URISyntaxException {
        CountingClassLoader classLoader = new CountingClassLoader("web/nested/resource.txt", "web/nested/resource.txt.br");
        SingleFileClassPathContentHandler handler = (SingleFileClassPathContentHandler) StaticContentFeature.createService(
                ClasspathHandlerConfig.builder()
                        .location("/web/nested/resource.txt")
                        .singleFile(true)
                        .classLoader(classLoader)
                        .recordCacheCapacity(1)
                        .build());
        handler.beforeStart();
        ServerRequestHeaders requestHeaders = acceptEncodingHeaders("br");

        assertThat(classLoader.lookups("web/nested/resource.txt"), is(1));
        ByteArrayOutputStream firstBody = new ByteArrayOutputStream();
        assertThat(handler.doHandle(Method.GET,
                                    "nested/resource.txt",
                                    request("/nested/resource.txt", requestHeaders),
                                    response(ServerResponseHeaders.create(), firstBody),
                                    false), is(true));
        assertThat(firstBody.toString(StandardCharsets.UTF_8), is("Nested content"));
        assertThat(classLoader.lookups("web/nested/resource.txt"), is(1));
        assertThat(classLoader.lookups("web/nested/resource.txt.br"), is(1));

        ByteArrayOutputStream secondBody = new ByteArrayOutputStream();
        assertThat(handler.doHandle(Method.GET,
                                    "nested/resource.txt",
                                    request("/nested/resource.txt", requestHeaders),
                                    response(ServerResponseHeaders.create(), secondBody),
                                    false), is(true));
        assertThat(secondBody.toString(StandardCharsets.UTF_8), is("Nested content"));
        assertThat(classLoader.lookups("web/nested/resource.txt"), is(1));
        assertThat(classLoader.lookups("web/nested/resource.txt.br"), is(1));
    }

    @Test
    void testClasspathSidecarCacheDoesNotPolluteLiteralResource() throws IOException, URISyntaxException {
        Path jarFile = createTmpJarFile(Map.of("web/resource.txt", "Content",
                                               "web/resource.txt.br", "Brotli content"));
        ClassPathContentHandler handler = (ClassPathContentHandler) StaticContentFeature.createService(
                ClasspathHandlerConfig.builder()
                        .location("/web")
                        .classLoader(new JarResourceClassLoader(jarFile))
                        .memoryCache(MemoryCache.create(builder -> builder.enabled(true)))
                        .build());

        ServerResponseHeaders brHeaders = ServerResponseHeaders.create();
        ByteArrayOutputStream brBody = new ByteArrayOutputStream();

        assertThat(handler.doHandle(Method.GET,
                                    "resource.txt",
                                    request("/resource.txt", acceptEncodingHeaders("br")),
                                    response(brHeaders, brBody),
                                    false), is(true));
        assertThat(brHeaders, hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
        assertThat(brBody.toString(StandardCharsets.UTF_8), is("Brotli content"));

        ServerResponseHeaders literalHeaders = ServerResponseHeaders.create();
        ByteArrayOutputStream literalBody = new ByteArrayOutputStream();

        assertThat(handler.doHandle(Method.GET,
                                    "resource.txt.br",
                                    request("/resource.txt.br", ServerRequestHeaders.create()),
                                    response(literalHeaders, literalBody),
                                    false), is(true));
        assertThat(literalHeaders, noHeader(HeaderNames.CONTENT_ENCODING));
        assertThat(literalBody.toString(StandardCharsets.UTF_8), is("Brotli content"));
    }

    @Test
    void testClasspathSidecarBytesUseGlobalMemoryCacheCapacity() throws IOException, URISyntaxException {
        Path jarFile = createTmpJarFile(Map.of("web/resource.txt", "Content larger than memory",
                                               "web/resource.txt.br", "br-data!"));
        ClassPathContentHandler handler = (ClassPathContentHandler) StaticContentFeature.createService(
                ClasspathHandlerConfig.builder()
                        .location("/web")
                        .classLoader(new JarResourceClassLoader(jarFile))
                        .memoryCache(MemoryCache.create(builder -> builder.enabled(true)
                                .capacity(Size.create(12))))
                        .build());

        ServerResponseHeaders headers = ServerResponseHeaders.create();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        assertThat(handler.doHandle(Method.GET,
                                    "resource.txt",
                                    request("/resource.txt", acceptEncodingHeaders("br")),
                                    response(headers, body),
                                    false), is(true));

        assertThat(headers, hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
        assertThat(body.toString(StandardCharsets.UTF_8), is("br-data!"));
        assertThat(handler.canCacheInMemory(4), is(true));
        assertThat(handler.canCacheInMemory(5), is(false));
    }

    @Test
    void testClasspathCachedSidecarBytesSurviveRecordCacheEviction() throws IOException, URISyntaxException {
        Path identityJar = createTmpJarFile(Map.of("web/resource.txt", "Content larger than memory",
                                                   "web/other.txt", "Other larger than memory"));
        Path sidecarJar = createTmpJarFile(Map.of("web/resource.txt.br", "br-data!"));
        ClassPathContentHandler handler = (ClassPathContentHandler) StaticContentFeature.createService(
                ClasspathHandlerConfig.builder()
                        .location("/web")
                        .classLoader(new MappedJarResourceClassLoader(
                                Map.of("web/resource.txt", List.of(identityJar),
                                       "web/resource.txt.br", List.of(sidecarJar),
                                       "web/other.txt", List.of(identityJar)),
                                true))
                        .memoryCache(MemoryCache.create(builder -> builder.enabled(true)
                                .capacity(Size.create(8))))
                        .recordCacheCapacity(1)
                        .preCompressedCrossOriginSourcingEnabled(true)
                        .build());

        ServerResponseHeaders firstHeaders = ServerResponseHeaders.create();
        ByteArrayOutputStream firstBody = new ByteArrayOutputStream();
        assertThat(handler.doHandle(Method.GET,
                                    "resource.txt",
                                    request("/resource.txt", acceptEncodingHeaders("br")),
                                    response(firstHeaders, firstBody),
                                    false), is(true));
        assertThat(firstHeaders, hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
        assertThat(firstBody.toString(StandardCharsets.UTF_8), is("br-data!"));
        assertThat(handler.canCacheInMemory(1), is(false));

        assertThat(handler.doHandle(Method.GET,
                                    "other.txt",
                                    request("/other.txt", ServerRequestHeaders.create()),
                                    response(ServerResponseHeaders.create(), new ByteArrayOutputStream()),
                                    false), is(true));

        Files.delete(sidecarJar);

        ServerResponseHeaders secondHeaders = ServerResponseHeaders.create();
        ByteArrayOutputStream secondBody = new ByteArrayOutputStream();
        assertThat(handler.doHandle(Method.GET,
                                    "resource.txt",
                                    request("/resource.txt", acceptEncodingHeaders("br")),
                                    response(secondHeaders, secondBody),
                                    false), is(true));
        assertThat(secondHeaders, hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
        assertThat(secondBody.toString(StandardCharsets.UTF_8), is("br-data!"));
    }

    @Test
    void testClasspathSidecarFromDifferentJarIsIgnored() throws IOException, URISyntaxException {
        Path identityJar = createTmpJarFile(Map.of("web/resource.txt", "Content"));
        Path sidecarJar = createTmpJarFile(Map.of("web/resource.txt.br", "Brotli content"));
        ClassPathContentHandler handler = (ClassPathContentHandler) StaticContentFeature.createService(
                ClasspathHandlerConfig.builder()
                        .location("/web")
                        .classLoader(new MappedJarResourceClassLoader(Map.of("web/resource.txt", identityJar,
                                                                              "web/resource.txt.br", sidecarJar)))
                        .build());

        ServerResponseHeaders headers = ServerResponseHeaders.create();
        ByteArrayOutputStream body = new ByteArrayOutputStream();

        assertThat(handler.doHandle(Method.GET,
                                    "resource.txt",
                                    request("/resource.txt", acceptEncodingHeaders("br")),
                                    response(headers, body),
                                    false), is(true));

        assertThat(headers, noHeader(HeaderNames.CONTENT_ENCODING));
        assertThat(headers, hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
        assertThat(body.toString(StandardCharsets.UTF_8), is("Content"));
    }

    @Test
    void testClasspathCrossOriginSidecarCanBeEnabled() throws IOException, URISyntaxException {
        Path identityJar = createTmpJarFile(Map.of("web/resource.txt", "Content"));
        Path sidecarJar = createTmpJarFile(Map.of("web/resource.txt.br", "Brotli content"));
        ClassPathContentHandler handler = (ClassPathContentHandler) StaticContentFeature.createService(
                ClasspathHandlerConfig.builder()
                        .location("/web")
                        .classLoader(new MappedJarResourceClassLoader(Map.of("web/resource.txt", identityJar,
                                                                              "web/resource.txt.br", sidecarJar)))
                        .preCompressedCrossOriginSourcingEnabled(true)
                        .build());

        ServerResponseHeaders headers = ServerResponseHeaders.create();
        ByteArrayOutputStream body = new ByteArrayOutputStream();

        assertThat(handler.doHandle(Method.GET,
                                    "resource.txt",
                                    request("/resource.txt", acceptEncodingHeaders("br")),
                                    response(headers, body),
                                    false), is(true));

        assertThat(headers, hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
        assertThat(headers, hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
        assertThat(body.toString(StandardCharsets.UTF_8), is("Brotli content"));
    }

    @Test
    void testClasspathSidecarSkipsCrossOriginAndUsesSameOriginCandidate() throws IOException, URISyntaxException {
        Path identityJar = createTmpJarFile(Map.of("web/resource.txt", "Content",
                                                   "web/resource.txt.br", "Same origin Brotli content"));
        Path crossOriginJar = createTmpJarFile(Map.of("web/resource.txt.br", "Cross origin Brotli content"));
        ClassPathContentHandler handler = (ClassPathContentHandler) StaticContentFeature.createService(
                ClasspathHandlerConfig.builder()
                        .location("/web")
                        .classLoader(new MappedJarResourceClassLoader(
                                Map.of("web/resource.txt", List.of(identityJar),
                                       "web/resource.txt.br", List.of(crossOriginJar, identityJar)),
                                true))
                        .build());

        ServerResponseHeaders headers = ServerResponseHeaders.create();
        ByteArrayOutputStream body = new ByteArrayOutputStream();

        assertThat(handler.doHandle(Method.GET,
                                    "resource.txt",
                                    request("/resource.txt", acceptEncodingHeaders("br")),
                                    response(headers, body),
                                    false), is(true));

        assertThat(headers, hasHeader(HeaderNames.CONTENT_ENCODING, "br"));
        assertThat(headers, hasHeader(HeaderNames.VARY, HeaderNames.ACCEPT_ENCODING_NAME));
        assertThat(body.toString(StandardCharsets.UTF_8), is("Same origin Brotli content"));
    }

    @Test
    void testFileSystemSidecarMissIsCachedUntilCacheRelease() throws IOException {
        Path resource = tempDir.resolve("resource.txt");
        Path gzip = tempDir.resolve("resource.txt.gz");
        Files.writeString(resource, "Content");
        FileSystemContentHandler handler = (FileSystemContentHandler) StaticContentFeature.createService(
                FileSystemHandlerConfig.builder()
                        .location(tempDir)
                        .build());

        ServerResponseHeaders missHeaders = ServerResponseHeaders.create();
        ByteArrayOutputStream missBody = new ByteArrayOutputStream();
        assertThat(handler.doHandle(Method.GET,
                                    "resource.txt",
                                    request("/resource.txt", acceptEncodingHeaders("gzip")),
                                    response(missHeaders, missBody),
                                    false), is(true));
        assertThat(missHeaders, noHeader(HeaderNames.CONTENT_ENCODING));
        assertThat(missBody.toString(StandardCharsets.UTF_8), is("Content"));

        Files.writeString(gzip, "Gzip content");

        ServerResponseHeaders cachedMissHeaders = ServerResponseHeaders.create();
        ByteArrayOutputStream cachedMissBody = new ByteArrayOutputStream();
        assertThat(handler.doHandle(Method.GET,
                                    "resource.txt",
                                    request("/resource.txt", acceptEncodingHeaders("gzip")),
                                    response(cachedMissHeaders, cachedMissBody),
                                    false), is(true));
        assertThat(cachedMissHeaders, noHeader(HeaderNames.CONTENT_ENCODING));
        assertThat(cachedMissBody.toString(StandardCharsets.UTF_8), is("Content"));

        handler.releaseCache();

        ServerResponseHeaders hitHeaders = ServerResponseHeaders.create();
        ByteArrayOutputStream hitBody = new ByteArrayOutputStream();
        assertThat(handler.doHandle(Method.GET,
                                    "resource.txt",
                                    request("/resource.txt", acceptEncodingHeaders("gzip")),
                                    response(hitHeaders, hitBody),
                                    false), is(true));
        assertThat(hitHeaders, hasHeader(HeaderNames.CONTENT_ENCODING, "gzip"));
        assertThat(hitBody.toString(StandardCharsets.UTF_8), is("Gzip content"));
    }

    @Test
    void testFileSystemCachedSidecarDeletionFallsBackToIdentity() throws IOException {
        Path resource = tempDir.resolve("resource.txt");
        Path gzip = tempDir.resolve("resource.txt.gz");
        Files.writeString(resource, "Content");
        Files.writeString(gzip, "Gzip content");
        FileSystemContentHandler handler = (FileSystemContentHandler) StaticContentFeature.createService(
                FileSystemHandlerConfig.builder()
                        .location(tempDir)
                        .build());

        ServerResponseHeaders hitHeaders = ServerResponseHeaders.create();
        ByteArrayOutputStream hitBody = new ByteArrayOutputStream();
        assertThat(handler.doHandle(Method.GET,
                                    "resource.txt",
                                    request("/resource.txt", acceptEncodingHeaders("gzip")),
                                    response(hitHeaders, hitBody),
                                    false), is(true));
        assertThat(hitHeaders, hasHeader(HeaderNames.CONTENT_ENCODING, "gzip"));
        assertThat(hitBody.toString(StandardCharsets.UTF_8), is("Gzip content"));

        Files.delete(gzip);

        ServerResponseHeaders staleHeaders = ServerResponseHeaders.create();
        ByteArrayOutputStream staleBody = new ByteArrayOutputStream();
        assertThat(handler.doHandle(Method.GET,
                                    "resource.txt",
                                    request("/resource.txt", acceptEncodingHeaders("gzip")),
                                    response(staleHeaders, staleBody),
                                    false), is(true));
        assertThat(staleHeaders, noHeader(HeaderNames.CONTENT_ENCODING));
        assertThat(staleBody.toString(StandardCharsets.UTF_8), is("Content"));
    }

    @Test
    void testFileSystemSidecarRequiresAvailableIdentityResource() throws IOException {
        Path resource = tempDir.resolve("resource.txt");
        Path gzip = tempDir.resolve("resource.txt.gz");
        Files.writeString(resource, "Content");
        Files.writeString(gzip, "Gzip content");
        FileSystemContentHandler handler = (FileSystemContentHandler) StaticContentFeature.createService(
                FileSystemHandlerConfig.builder()
                        .location(tempDir)
                        .build());

        ServerResponseHeaders headers = ServerResponseHeaders.create();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        assertThat(handler.doHandle(Method.GET,
                                    "resource.txt",
                                    request("/resource.txt", acceptEncodingHeaders("gzip")),
                                    response(headers, body),
                                    false), is(true));
        assertThat(headers, hasHeader(HeaderNames.CONTENT_ENCODING, "gzip"));

        Files.delete(resource);

        assertThrows(ForbiddenException.class,
                     () -> handler.doHandle(Method.GET,
                                            "resource.txt",
                                            request("/resource.txt", acceptEncodingHeaders("gzip")),
                                            response(ServerResponseHeaders.create(), new ByteArrayOutputStream()),
                                            false));
    }

    @Test
    void testSingleFileSystemSidecarMissIsCachedUntilCacheRelease() throws IOException {
        Path resource = tempDir.resolve("single.txt");
        Path gzip = tempDir.resolve("single.txt.gz");
        Files.writeString(resource, "Content");
        SingleFileContentHandler handler = (SingleFileContentHandler) StaticContentFeature.createService(
                FileSystemHandlerConfig.builder()
                        .location(resource)
                        .build());

        ServerResponseHeaders missHeaders = ServerResponseHeaders.create();
        ByteArrayOutputStream missBody = new ByteArrayOutputStream();
        assertThat(handler.doHandle(Method.GET,
                                    "",
                                    request("/single", acceptEncodingHeaders("gzip")),
                                    response(missHeaders, missBody),
                                    false), is(true));
        assertThat(missHeaders, noHeader(HeaderNames.CONTENT_ENCODING));
        assertThat(missBody.toString(StandardCharsets.UTF_8), is("Content"));

        Files.writeString(gzip, "Gzip content");

        ServerResponseHeaders cachedMissHeaders = ServerResponseHeaders.create();
        ByteArrayOutputStream cachedMissBody = new ByteArrayOutputStream();
        assertThat(handler.doHandle(Method.GET,
                                    "",
                                    request("/single", acceptEncodingHeaders("gzip")),
                                    response(cachedMissHeaders, cachedMissBody),
                                    false), is(true));
        assertThat(cachedMissHeaders, noHeader(HeaderNames.CONTENT_ENCODING));
        assertThat(cachedMissBody.toString(StandardCharsets.UTF_8), is("Content"));

        handler.releaseCache();

        ServerResponseHeaders hitHeaders = ServerResponseHeaders.create();
        ByteArrayOutputStream hitBody = new ByteArrayOutputStream();
        assertThat(handler.doHandle(Method.GET,
                                    "",
                                    request("/single", acceptEncodingHeaders("gzip")),
                                    response(hitHeaders, hitBody),
                                    false), is(true));
        assertThat(hitHeaders, hasHeader(HeaderNames.CONTENT_ENCODING, "gzip"));
        assertThat(hitBody.toString(StandardCharsets.UTF_8), is("Gzip content"));
    }

    @Test
    void testSingleFileSystemCachedSidecarDeletionFallsBackToIdentity() throws IOException {
        Path resource = tempDir.resolve("single.txt");
        Path gzip = tempDir.resolve("single.txt.gz");
        Files.writeString(resource, "Content");
        Files.writeString(gzip, "Gzip content");
        SingleFileContentHandler handler = (SingleFileContentHandler) StaticContentFeature.createService(
                FileSystemHandlerConfig.builder()
                        .location(resource)
                        .build());

        ServerResponseHeaders hitHeaders = ServerResponseHeaders.create();
        ByteArrayOutputStream hitBody = new ByteArrayOutputStream();
        assertThat(handler.doHandle(Method.GET,
                                    "",
                                    request("/single", acceptEncodingHeaders("gzip")),
                                    response(hitHeaders, hitBody),
                                    false), is(true));
        assertThat(hitHeaders, hasHeader(HeaderNames.CONTENT_ENCODING, "gzip"));
        assertThat(hitBody.toString(StandardCharsets.UTF_8), is("Gzip content"));

        Files.delete(gzip);

        ServerResponseHeaders staleHeaders = ServerResponseHeaders.create();
        ByteArrayOutputStream staleBody = new ByteArrayOutputStream();
        assertThat(handler.doHandle(Method.GET,
                                    "",
                                    request("/single", acceptEncodingHeaders("gzip")),
                                    response(staleHeaders, staleBody),
                                    false), is(true));
        assertThat(staleHeaders, noHeader(HeaderNames.CONTENT_ENCODING));
        assertThat(staleBody.toString(StandardCharsets.UTF_8), is("Content"));
    }

    @Test
    void testSingleFileSystemSidecarRequiresAvailableIdentityResource() throws IOException {
        Path resource = tempDir.resolve("single.txt");
        Path gzip = tempDir.resolve("single.txt.gz");
        Files.writeString(resource, "Content");
        Files.writeString(gzip, "Gzip content");
        SingleFileContentHandler handler = (SingleFileContentHandler) StaticContentFeature.createService(
                FileSystemHandlerConfig.builder()
                        .location(resource)
                        .build());

        ServerResponseHeaders headers = ServerResponseHeaders.create();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        assertThat(handler.doHandle(Method.GET,
                                    "",
                                    request("/single", acceptEncodingHeaders("gzip")),
                                    response(headers, body),
                                    false), is(true));
        assertThat(headers, hasHeader(HeaderNames.CONTENT_ENCODING, "gzip"));

        Files.delete(resource);

        assertThrows(ForbiddenException.class,
                     () -> handler.doHandle(Method.GET,
                                            "",
                                            request("/single", acceptEncodingHeaders("gzip")),
                                            response(ServerResponseHeaders.create(), new ByteArrayOutputStream()),
                                            false));
    }

    @Test
    void testClasspathCacheRedirectFound() throws IOException, URISyntaxException {
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();

        ServerRequest req = mock(ServerRequest.class);
        when(req.headers()).thenReturn(ServerRequestHeaders.create());
        when(req.prologue()).thenReturn(HttpPrologue.create("http/1.1", "http", "1.1", Method.GET, "/nested", false));
        when(req.query()).thenReturn(UriQuery.empty());

        ServerResponse res = mock(ServerResponse.class);
        when(res.headers()).thenReturn(responseHeaders);
        when(res.outputStream()).thenReturn(new ByteArrayOutputStream());

        boolean result = classpathHandler.doHandle(Method.GET, "/nested", req, res, false);

        assertThat("Handler should have redirected", result, is(true));
        assertThat(responseHeaders, hasHeader(HeaderNames.LOCATION, "/nested/"));

        // now make sure it is cached
        Optional<CachedHandler> cachedHandler = classpathHandler.cacheHandler("web/nested");
        assertThat("Handler should be cached", cachedHandler, optionalPresent());
        CachedHandler cached = cachedHandler.get();
        assertThat("This should be a cached redirect handler", cached, instanceOf(CachedHandlerRedirect.class));
        CachedHandlerRedirect redirectHandler = (CachedHandlerRedirect) cached;
        assertThat(redirectHandler.location(), is("/nested/"));
    }

    @Test
    void testFsInMemoryCache() {
        Optional<CachedHandlerInMemory> cachedHandlerInMemory = fsHandler.cacheInMemory("nested/resource.txt");
        assertThat("Handler should be cached in memory", cachedHandlerInMemory, optionalPresent());
        CachedHandlerInMemory cached = cachedHandlerInMemory.get();
        assertThat("Cached bytes must not be null", cached.bytes(), notNullValue());
        assertThat("Cached bytes must not be empty", cached.bytes(), not(BufferData.EMPTY_BYTES));
        // content is: "Nested content"
        assertThat("Content length", cached.contentLength(), is(14));
        assertThat("Last modified", cached.lastModified(), notNullValue());
        assertThat("Media type", cached.mediaType(), is(MediaTypes.TEXT_PLAIN));
    }

    @Test
    void testFsFromInMemory() throws IOException {
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();

        ServerRequest req = mock(ServerRequest.class);
        when(req.headers()).thenReturn(ServerRequestHeaders.create());
        when(req.prologue()).thenReturn(HttpPrologue.create("http/1.1",
                                                            "http",
                                                            "1.1",
                                                            Method.GET,
                                                            "nested/resource.txt",
                                                            false));

        ServerResponse res = mock(ServerResponse.class);
        when(res.headers()).thenReturn(responseHeaders);

        boolean result = fsHandler.doHandle(Method.GET, "nested/resource.txt", req, res, false);

        assertThat("Handler should have found nested/resource.txt", result, is(true));
        assertThat(responseHeaders, hasHeader(HeaderValues.CONTENT_TYPE_TEXT_PLAIN));
        assertThat(responseHeaders, hasHeader(HeaderNames.ETAG));
        assertThat(responseHeaders, hasHeader(HeaderNames.LAST_MODIFIED));
    }

    @Test
    void testFsCacheFound() throws IOException {
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();

        ServerRequest req = mock(ServerRequest.class);
        when(req.headers()).thenReturn(ServerRequestHeaders.create());
        when(req.prologue()).thenReturn(HttpPrologue.create("http/1.1", "http", "1.1", Method.GET, "/resource.txt", false));

        ServerResponse res = mock(ServerResponse.class);
        when(res.headers()).thenReturn(responseHeaders);
        when(res.outputStream()).thenReturn(new ByteArrayOutputStream());

        boolean result = fsHandler.doHandle(Method.GET, "resource.txt", req, res, false);

        assertThat("Handler should have found resource.txt", result, is(true));
        assertThat(responseHeaders, hasHeader(HeaderValues.CONTENT_TYPE_TEXT_PLAIN));
        assertThat(responseHeaders, hasHeader(RESOURCE_CONTENT_LENGTH));
        assertThat(responseHeaders, hasHeader(HeaderNames.ETAG));
        assertThat(responseHeaders, hasHeader(HeaderNames.LAST_MODIFIED));

        // now make sure it is cached
        Optional<CachedHandler> cachedHandler = fsHandler.cacheHandler("resource.txt");
        assertThat("Handler should be cached", cachedHandler, optionalPresent());
        CachedHandler cached = cachedHandler.get();
        assertThat("During tests, fs should be loaded from file system", cached, instanceOf(CachedHandlerPath.class));
        CachedHandlerPath pathHandler = (CachedHandlerPath) cached;
        assertThat("Path", pathHandler.path(), notNullValue());
        assertThat("Last modified function", pathHandler.lastModified(), notNullValue());
        assertThat("Last modified", pathHandler.lastModified().apply(pathHandler.path()), notNullValue());
        assertThat("Media type", pathHandler.mediaType(), is(MediaTypes.TEXT_PLAIN));
    }

    @Test
    void testFsCacheRedirectFound() throws IOException {
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();

        ServerRequest req = mock(ServerRequest.class);
        when(req.headers()).thenReturn(ServerRequestHeaders.create());
        when(req.prologue()).thenReturn(HttpPrologue.create("http/1.1", "http", "1.1", Method.GET, "/nested", false));
        when(req.query()).thenReturn(UriQuery.empty());

        ServerResponse res = mock(ServerResponse.class);
        when(res.headers()).thenReturn(responseHeaders);
        when(res.outputStream()).thenReturn(new ByteArrayOutputStream());

        boolean result = fsHandler.doHandle(Method.GET, "nested", req, res, false);

        assertThat("Handler should have redirected", result, is(true));
        assertThat(responseHeaders, hasHeader(HeaderNames.LOCATION, "/nested/"));

        // now make sure it is cached
        Optional<CachedHandler> cachedHandler = fsHandler.cacheHandler("nested");
        assertThat("Handler should be cached", cachedHandler, optionalPresent());
        CachedHandler cached = cachedHandler.get();
        assertThat("This should be a cached redirect handler", cached, instanceOf(CachedHandlerRedirect.class));
        CachedHandlerRedirect redirectHandler = (CachedHandlerRedirect) cached;
        assertThat(redirectHandler.location(), is("/nested/"));
    }

    @Test
    void zipFileClosedTest() throws IOException {
        var tmpJarFile = createTmpJarFile();
        var testClassLoader = new TestClassLoader(tmpJarFile);
        var req = mock(ServerRequest.class);
        when(req.headers()).thenReturn(ServerRequestHeaders.create());
        when(req.prologue()).thenReturn(HttpPrologue.create("http/1.1", "http", "1.1", Method.GET, "/resource.txt", false));
        when(req.query()).thenReturn(UriQuery.empty());

        Stream.generate(() -> {
                    var baos = new ByteArrayOutputStream();
                    var res = mock(ServerResponse.class);
                    when(res.headers()).thenReturn(ServerResponseHeaders.create());
                    when(res.outputStream()).thenReturn(baos);

                    for (int i = 0; i < 100; i++) {
                        var service = (ClassPathContentHandler) StaticContentFeature.createService(
                                ClasspathHandlerConfig
                                        .builder()
                                        .location("/web")
                                        .classLoader(testClassLoader)
                                        .build()
                        );

                        try {
                            service.doHandle(Method.GET, "/resource.txt", req, res, false);
                            assertThat(baos.toString(), is("Content"));
                            baos.reset();
                        } catch (IOException | URISyntaxException e) {
                            throw new RuntimeException(e);
                        }

                    }
                    return null;
                })
                .limit(10)
                .parallel()
                .toList();
    }

    private static class TestClassLoader extends ClassLoader {
        private final Path tmpJarFile;

        public TestClassLoader(Path tmpJarFile) {
            super(Thread.currentThread().getContextClassLoader());
            this.tmpJarFile = tmpJarFile;
        }

        @Override
        public URL getResource(String name) {
            if ("web/resource.txt".equals(name)) {
                try {
                    var uri = tmpJarFile.toUri();
                    var url = new URI("jar:file", null, uri.getPath() + "!/resource.txt", null).toURL();
                    LOGGER.log(TRACE, () -> "Fake jar resource URL: " + url);
                    return url;
                } catch (MalformedURLException | URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }
            return super.getResource(name);
        }
    }

    private static class JarResourceClassLoader extends ClassLoader {
        private final Path jarFile;

        JarResourceClassLoader(Path jarFile) {
            super(Thread.currentThread().getContextClassLoader());
            this.jarFile = jarFile;
        }

        @Override
        public URL getResource(String name) {
            return jarUrl(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) {
            return Collections.enumeration(List.of(jarUrl(name)));
        }

        private URL jarUrl(String name) {
            try {
                return new URI("jar:file", null, jarFile.toUri().getPath() + "!/" + name, null).toURL();
            } catch (MalformedURLException | URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class MappedJarResourceClassLoader extends ClassLoader {
        private final Map<String, List<Path>> jarFiles;

        MappedJarResourceClassLoader(Map<String, Path> jarFiles) {
            super(Thread.currentThread().getContextClassLoader());
            this.jarFiles = new HashMap<>();
            jarFiles.forEach((resource, jarFile) -> this.jarFiles.put(resource, List.of(jarFile)));
        }

        MappedJarResourceClassLoader(Map<String, List<Path>> jarFiles, boolean multi) {
            super(Thread.currentThread().getContextClassLoader());
            this.jarFiles = jarFiles;
        }

        @Override
        public URL getResource(String name) {
            List<Path> jars = jarFiles.get(name);
            if (jars == null || jars.isEmpty()) {
                return super.getResource(name);
            }
            return jarUrl(jars.get(0), name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            List<Path> jars = jarFiles.get(name);
            if (jars == null || jars.isEmpty()) {
                return super.getResources(name);
            }
            return Collections.enumeration(jars.stream()
                                                   .map(jar -> jarUrl(jar, name))
                                                   .toList());
        }

        private static URL jarUrl(Path jarFile, String name) {
            try {
                return new URI("jar:file", null, jarFile.toUri().getPath() + "!/" + name, null).toURL();
            } catch (MalformedURLException | URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static ServerRequestHeaders acceptEncodingHeaders(String acceptEncoding) {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderNames.ACCEPT_ENCODING, acceptEncoding);
        return ServerRequestHeaders.create(headers);
    }

    private static ServerRequest request(String rawPath, ServerRequestHeaders headers) {
        ServerRequest request = mock(ServerRequest.class);
        when(request.headers()).thenReturn(headers);
        when(request.prologue()).thenReturn(HttpPrologue.create("http/1.1",
                                                                "http",
                                                                "1.1",
                                                                Method.GET,
                                                                rawPath,
                                                                false));
        return request;
    }

    private static ServerResponse response(ServerResponseHeaders headers, ByteArrayOutputStream outputStream)
            throws IOException {
        ServerResponse response = mock(ServerResponse.class);
        when(response.headers()).thenReturn(headers);
        when(response.outputStream()).thenReturn(outputStream);
        org.mockito.Mockito.doAnswer(invocation -> {
            outputStream.writeBytes(invocation.getArgument(0));
            return null;
        }).when(response).send(org.mockito.ArgumentMatchers.any(byte[].class));
        return response;
    }

    private static class CountingClassLoader extends ClassLoader {
        private final Map<String, AtomicInteger> lookups = new HashMap<>();

        CountingClassLoader(String... countedResources) {
            super(Thread.currentThread().getContextClassLoader());
            for (String countedResource : countedResources) {
                lookups.put(countedResource, new AtomicInteger());
            }
        }

        @Override
        public URL getResource(String name) {
            AtomicInteger counter = lookups.get(name);
            if (counter != null) {
                counter.incrementAndGet();
            }
            return super.getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            AtomicInteger counter = lookups.get(name);
            if (counter != null) {
                counter.incrementAndGet();
            }
            return super.getResources(name);
        }

        int lookups(String name) {
            AtomicInteger counter = lookups.get(name);
            return counter == null ? 0 : counter.get();
        }
    }

    private static Path createTmpJarFile() throws IOException {
        return createTmpJarFile(Map.of("resource.txt", "Content"));
    }

    private static Path createTmpJarFile(Map<String, String> entries) throws IOException {
        Path jarFile = Files.createTempFile("helidon-closed-zip-test-", "jar");
        try (var fos = Files.newOutputStream(jarFile);
                var zipOut = new ZipOutputStream(fos)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                var zipEntry = new ZipEntry(entry.getKey());
                zipOut.putNextEntry(zipEntry);
                var bytes = entry.getValue().getBytes(StandardCharsets.UTF_8);
                zipOut.write(bytes, 0, bytes.length);
            }
        }
        return jarFile;
    }

    private static URL jarUrl(Path jarFile, String name) throws MalformedURLException, URISyntaxException {
        return new URI("jar:file", null, jarFile.toUri().getPath() + "!/" + name, null).toURL();
    }
}
