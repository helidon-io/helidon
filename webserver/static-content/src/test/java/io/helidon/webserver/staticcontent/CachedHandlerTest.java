/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.uri.UriQuery;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static java.lang.System.Logger.Level.TRACE;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("removal")
class CachedHandlerTest {
    private static final System.Logger LOGGER = System.getLogger(CachedHandlerTest.class.getName());
    private static final MediaType MEDIA_TYPE_ICON = MediaTypes.create("image/x-icon");
    private static final Header ICON_TYPE = HeaderValues.create(HeaderNames.CONTENT_TYPE, MEDIA_TYPE_ICON.text());
    private static final Header RESOURCE_CONTENT_LENGTH = HeaderValues.create(HeaderNames.CONTENT_LENGTH, 7);

    private static ClassPathContentHandler classpathHandler;
    private static FileSystemContentHandler fsHandler;

    @BeforeAll
    static void initTestClass() {
        classpathHandler = (ClassPathContentHandler) StaticContentService.builder("/web")
                .addCacheInMemory("favicon.ico")
                .welcomeFileName("resource.txt")
                .build();
        classpathHandler.beforeStart();

        fsHandler = (FileSystemContentHandler) StaticContentService.builder(Paths.get("./src/test/resources/web"))
                .addCacheInMemory("nested")
                .welcomeFileName("resource.txt")
                .build();
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
                    var url = URI.create("jar:file:" + tmpJarFile.toAbsolutePath().normalize() + "!/resource.txt").toURL();
                    LOGGER.log(TRACE, () -> "Fake jar resource URL: " + url);
                    return url;
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }
            return super.getResource(name);
        }
    }

    private static Path createTmpJarFile() throws IOException {
        Path jarFile = Files.createTempFile("helidon-closed-zip-test-", "jar");
        try (var fos = Files.newOutputStream(jarFile);
                var zipOut = new ZipOutputStream(fos)) {
            var zipEntry = new ZipEntry("resource.txt");
            zipOut.putNextEntry(zipEntry);
            var bytes = "Content".getBytes(StandardCharsets.UTF_8);
            zipOut.write(bytes, 0, bytes.length);
        }
        return jarFile;
    }
}
