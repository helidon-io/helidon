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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
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
import io.helidon.http.HttpException;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.Status;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static java.lang.System.Logger.Level.TRACE;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CachedHandlerTest {
    private static final System.Logger LOGGER = System.getLogger(CachedHandlerTest.class.getName());
    private static final MediaType MEDIA_TYPE_ICON = MediaTypes.create("image/x-icon");
    private static final Header ICON_TYPE = HeaderValues.create(HeaderNames.CONTENT_TYPE, MEDIA_TYPE_ICON.text());
    private static final Header RESOURCE_CONTENT_LENGTH = HeaderValues.create(HeaderNames.CONTENT_LENGTH, 7);

    private static ClassPathContentHandler classpathHandler;
    private static FileSystemContentHandler fsHandler;

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
        assertThat("Media type", cached.metadata().mediaType(), is(MEDIA_TYPE_ICON));
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
        assertThat("Last modified", pathHandler.metadata().lastModified(), notNullValue());
        assertThat("Content length", pathHandler.metadata().contentLength(), is(7L));
        assertThat("Media type", pathHandler.metadata().mediaType(), is(MediaTypes.TEXT_PLAIN));
    }

    @Test
    void testClasspathDynamicCacheIsReleasedOnRestart(@TempDir Path tempDir) throws IOException, URISyntaxException {
        Path resource = Files.writeString(Files.createDirectories(tempDir.resolve("web")).resolve("dynamic.txt"),
                                          "Initial");
        FileTime initialLastModified = Files.getLastModifiedTime(resource);

        try (var classLoader = new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, null)) {
            ClassPathContentHandler handler = (ClassPathContentHandler) StaticContentFeature.createService(
                    ClasspathHandlerConfig.builder()
                            .location("/web")
                            .classLoader(classLoader)
                            .build());
            handler.beforeStart();

            ServerRequest request = mock(ServerRequest.class);
            when(request.headers()).thenReturn(ServerRequestHeaders.create());
            HttpPrologue prologue = HttpPrologue.create("http/1.1",
                                                        "http",
                                                        "1.1",
                                                        Method.GET,
                                                        "/dynamic.txt",
                                                        false);
            when(request.prologue()).thenReturn(prologue);

            ByteArrayOutputStream initialOutput = new ByteArrayOutputStream();
            ServerResponseHeaders initialHeaders = ServerResponseHeaders.create();
            ServerResponse initialResponse = mock(ServerResponse.class);
            when(initialResponse.headers()).thenReturn(initialHeaders);
            when(initialResponse.outputStream()).thenReturn(initialOutput);

            assertThat("Initial classpath resource should be served",
                       handler.doHandle(Method.GET, "dynamic.txt", request, initialResponse, false),
                       is(true));
            assertThat(initialOutput.toString(StandardCharsets.UTF_8), is("Initial"));
            String initialEtag = initialHeaders.get(HeaderNames.ETAG).get();
            String initialLastModifiedHeader = initialHeaders.get(HeaderNames.LAST_MODIFIED).get();

            handler.afterStop();
            byte[] updatedBytes = "Updated content".getBytes(StandardCharsets.UTF_8);
            Files.write(resource, updatedBytes);
            Files.setLastModifiedTime(resource, FileTime.from(initialLastModified.toInstant().plusSeconds(2)));
            assertThat("Updated resource must have a distinct last-modified time",
                       Files.getLastModifiedTime(resource),
                       not(is(initialLastModified)));
            handler.beforeStart();

            ByteArrayOutputStream updatedOutput = new ByteArrayOutputStream();
            ServerResponseHeaders updatedHeaders = ServerResponseHeaders.create();
            ServerResponse updatedResponse = mock(ServerResponse.class);
            when(updatedResponse.headers()).thenReturn(updatedHeaders);
            when(updatedResponse.outputStream()).thenReturn(updatedOutput);

            assertThat("Updated classpath resource should be served",
                       handler.doHandle(Method.GET, "dynamic.txt", request, updatedResponse, false),
                       is(true));
            assertThat("Updated classpath resource content", updatedOutput.toByteArray(), is(updatedBytes));
            assertThat(updatedHeaders,
                       hasHeader(HeaderNames.CONTENT_LENGTH, String.valueOf(updatedBytes.length)));
            assertThat("Updated resource ETag", updatedHeaders.get(HeaderNames.ETAG).get(), not(is(initialEtag)));
            assertThat("Updated resource last-modified header",
                       updatedHeaders.get(HeaderNames.LAST_MODIFIED).get(),
                       not(is(initialLastModifiedHeader)));

            ServerRequestHeaders rangeRequestHeaders = mock(ServerRequestHeaders.class);
            when(rangeRequestHeaders.contains(HeaderNames.RANGE)).thenReturn(true);
            when(rangeRequestHeaders.get(HeaderNames.RANGE))
                    .thenReturn(HeaderValues.create(HeaderNames.RANGE, "bytes=0-0"));
            ServerRequest rangeRequest = mock(ServerRequest.class);
            when(rangeRequest.headers()).thenReturn(rangeRequestHeaders);
            when(rangeRequest.prologue()).thenReturn(prologue);

            ByteArrayOutputStream rangeOutput = new ByteArrayOutputStream();
            ServerResponse rangeResponse = mock(ServerResponse.class);
            when(rangeResponse.headers()).thenReturn(ServerResponseHeaders.create());
            when(rangeResponse.outputStream()).thenReturn(rangeOutput);

            assertThat("Updated classpath resource range should be served",
                       handler.doHandle(Method.GET, "dynamic.txt", rangeRequest, rangeResponse, false),
                       is(true));
            assertThat("Updated classpath resource range", rangeOutput.toString(StandardCharsets.UTF_8), is("U"));

            ArgumentCaptor<Header> contentRange = ArgumentCaptor.forClass(Header.class);
            verify(rangeResponse).header(contentRange.capture());
            assertThat(contentRange.getValue().headerName(), is(HeaderNames.CONTENT_RANGE));
            assertThat(contentRange.getValue().get(), is("bytes 0-0/" + updatedBytes.length));
            verify(rangeResponse).contentLength(1);
            verify(rangeResponse).status(Status.PARTIAL_CONTENT_206);
        }
    }

    @Test
    void testSingleFileClasspathCachesAreReleasedOnRestart(@TempDir Path tempDir) throws IOException {
        Path resource = Files.writeString(tempDir.resolve("single.txt"), "Initial");

        try (var classLoader = new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, null)) {
            SingleFileClassPathContentHandler handler =
                    (SingleFileClassPathContentHandler) StaticContentFeature.createService(
                            ClasspathHandlerConfig.builder()
                                    .location("/single.txt")
                                    .classLoader(classLoader)
                                    .singleFile(true)
                                    .cachedFiles(Set.of("."))
                                    .build());
            handler.beforeStart();

            assertThat("Single-file classpath resource should be cached in memory",
                       handler.cacheInMemory("single.txt"),
                       optionalPresent());

            ServerRequest request = mock(ServerRequest.class);
            when(request.headers()).thenReturn(ServerRequestHeaders.create());

            ServerResponse initialResponse = mock(ServerResponse.class);
            when(initialResponse.headers()).thenReturn(ServerResponseHeaders.create());

            assertThat("Initial single-file classpath resource should be served",
                       handler.doHandle(Method.GET, "", request, initialResponse, false),
                       is(true));
            ArgumentCaptor<byte[]> initialOutput = ArgumentCaptor.forClass(byte[].class);
            verify(initialResponse).send(initialOutput.capture());
            assertThat("Initial single-file classpath resource content",
                       initialOutput.getValue(),
                       is("Initial".getBytes(StandardCharsets.UTF_8)));

            handler.afterStop();
            Files.delete(resource);
            handler.beforeStart();

            assertThat("Stopped single-file classpath handler must clear its memory cache",
                       handler.cacheInMemory("single.txt"),
                       optionalEmpty());
            assertThat("Stopped single-file classpath handler must clear its handler cache",
                       handler.cacheHandler("single.txt"),
                       optionalEmpty());

            ServerResponse removedResponse = mock(ServerResponse.class);
            when(removedResponse.headers()).thenReturn(ServerResponseHeaders.create());

            assertThrows(IllegalStateException.class,
                         () -> handler.doHandle(Method.GET, "", request, removedResponse, false));
        }
    }

    @Test
    void testUrlStreamPreconditionsWithoutLastModified() throws IOException {
        URL url = URL.of(URI.create("test:/resource.txt"), new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) {
                return new URLConnection(url) {
                    @Override
                    public void connect() {
                    }

                    @Override
                    public long getLastModified() {
                        return 0;
                    }
                };
            }
        });

        ServerRequestHeaders requestHeaders = mock(ServerRequestHeaders.class);
        when(requestHeaders.contains(HeaderNames.IF_NONE_MATCH)).thenReturn(true);
        when(requestHeaders.get(HeaderNames.IF_NONE_MATCH))
                .thenReturn(HeaderValues.create(HeaderNames.IF_NONE_MATCH, "*"));
        ServerRequest request = mock(ServerRequest.class);
        when(request.headers()).thenReturn(requestHeaders);

        ServerResponse response = mock(ServerResponse.class);
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        when(response.headers()).thenReturn(responseHeaders);

        CachedHandlerUrlStream handler = CachedHandlerUrlStream.create(MediaTypes.TEXT_PLAIN, url);
        HttpException exception = assertThrows(HttpException.class,
                                               () -> handler.handle(LruCache.create(),
                                                                    Method.HEAD,
                                                                    request,
                                                                    response,
                                                                    "resource.txt"));

        assertThat(exception.status(), is(Status.NOT_MODIFIED_304));
        assertThat(responseHeaders.contains(HeaderNames.ETAG), is(false));
        assertThat(exception.headers().contains(HeaderNames.ETAG), is(false));
    }

    @Test
    void testUrlStreamMetadataIsResolvedOnce() throws IOException {
        AtomicInteger connectionCount = new AtomicInteger();
        URL url = URL.of(URI.create("test:/resource.txt"), new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) {
                connectionCount.incrementAndGet();
                return new URLConnection(url) {
                    @Override
                    public void connect() {
                    }

                    @Override
                    public long getLastModified() {
                        return 1234;
                    }

                    @Override
                    public long getContentLengthLong() {
                        return 7;
                    }
                };
            }
        });

        ServerRequest request = mock(ServerRequest.class);
        when(request.headers()).thenReturn(ServerRequestHeaders.create());

        ServerResponse response = mock(ServerResponse.class);
        when(response.headers()).thenReturn(ServerResponseHeaders.create());

        CachedHandlerUrlStream handler = CachedHandlerUrlStream.create(MediaTypes.TEXT_PLAIN, url);
        handler.handle(LruCache.create(), Method.HEAD, request, response, "resource.txt");
        handler.handle(LruCache.create(), Method.HEAD, request, response, "resource.txt");

        assertThat("URL metadata should be resolved only when the handler is created",
                   connectionCount.get(),
                   is(1));
    }

    @Test
    void testJarExtractionFailureUsesUrlStream(@TempDir Path tempDir) throws IOException {
        byte[] content = "Content".getBytes(StandardCharsets.UTF_8);
        AtomicInteger streamCount = new AtomicInteger();
        URL url = URL.of(URI.create("test:/resource.txt"), new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) {
                return new URLConnection(url) {
                    @Override
                    public void connect() {
                    }

                    @Override
                    public InputStream getInputStream() {
                        if (streamCount.getAndIncrement() != 0) {
                            return new ByteArrayInputStream(content);
                        }
                        return new InputStream() {
                            private boolean firstRead = true;

                            @Override
                            public int read() throws IOException {
                                throw new IOException("Extraction failed");
                            }

                            @Override
                            public int read(byte[] bytes, int offset, int length) throws IOException {
                                if (!firstRead) {
                                    throw new IOException("Extraction failed");
                                }
                                firstRead = false;
                                int copied = Math.min(3, length);
                                System.arraycopy(content, 0, bytes, offset, copied);
                                return copied;
                            }
                        };
                    }
                };
            }
        });
        TemporaryStorage storage = mock(TemporaryStorage.class);
        when(storage.createFile()).thenReturn(Optional.of(tempDir.resolve("resource.txt")));

        CachedHandlerJar handler = CachedHandlerJar.create(storage, url, null, MediaTypes.TEXT_PLAIN, content.length);
        ServerRequest request = mock(ServerRequest.class);
        when(request.headers()).thenReturn(ServerRequestHeaders.create());

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ServerResponse response = mock(ServerResponse.class);
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        when(response.headers()).thenReturn(responseHeaders);
        when(response.outputStream()).thenReturn(output);

        handler.handle(LruCache.create(), Method.GET, request, response, "resource.txt");

        assertThat("Failed extraction should fall back to the URL stream", output.toByteArray(), is(content));
        assertThat("The URL should be opened again after extraction fails", streamCount.get(), is(2));
        assertThat(responseHeaders, hasHeader(RESOURCE_CONTENT_LENGTH));
    }

    @Test
    void testJarExtractionResolvesUnknownContentLength(@TempDir Path tempDir) throws IOException {
        byte[] content = "Content".getBytes(StandardCharsets.UTF_8);
        URL url = URL.of(URI.create("test:/resource.txt"), new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) {
                return new URLConnection(url) {
                    @Override
                    public void connect() {
                    }

                    @Override
                    public InputStream getInputStream() {
                        return new ByteArrayInputStream(content);
                    }
                };
            }
        });
        TemporaryStorage storage = mock(TemporaryStorage.class);
        when(storage.createFile()).thenReturn(Optional.of(tempDir.resolve("resource.txt")));

        CachedHandlerJar handler = CachedHandlerJar.create(storage, url, null, MediaTypes.TEXT_PLAIN, -1);
        ServerRequest request = mock(ServerRequest.class);
        when(request.headers()).thenReturn(ServerRequestHeaders.create());

        ServerResponse response = mock(ServerResponse.class);
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        when(response.headers()).thenReturn(responseHeaders);

        handler.handle(LruCache.create(), Method.HEAD, request, response, "resource.txt");

        assertThat(responseHeaders, hasHeader(RESOURCE_CONTENT_LENGTH));
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
        assertThat("Media type", cached.metadata().mediaType(), is(MediaTypes.TEXT_PLAIN));
    }

    @Test
    void testFsInMemoryCacheSkipsSymlinkOutsideRoot(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("root");
        Path externalDir = tempDir.resolve("external");
        Files.createDirectories(root);
        Files.createDirectories(externalDir);
        Files.writeString(externalDir.resolve("resource.txt"), "External content");

        Path link = root.resolve("resource.txt");
        createSymbolicLink(link, externalDir.resolve("resource.txt"));

        FileSystemContentHandler handler = (FileSystemContentHandler) StaticContentFeature.createService(
                FileSystemHandlerConfig.builder()
                        .location(root)
                        .cachedFiles(Set.of("resource.txt"))
                        .build());
        handler.beforeStart();

        assertThat("Out-of-root symlink should not be cached in memory",
                   handler.cacheInMemory("resource.txt"),
                   optionalEmpty());
    }

    @Test
    void testFsInMemoryCacheSkipsSymlinkInsideRoot(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);
        Files.writeString(root.resolve("target.txt"), "Content");

        Path link = root.resolve("resource.txt");
        createSymbolicLink(link, root.resolve("target.txt"));

        FileSystemContentHandler handler = (FileSystemContentHandler) StaticContentFeature.createService(
                FileSystemHandlerConfig.builder()
                        .location(root)
                        .cachedFiles(Set.of("resource.txt"))
                        .build());
        handler.beforeStart();

        assertThat("In-root symlink should not be cached in memory",
                   handler.cacheInMemory("resource.txt"),
                   optionalEmpty());
    }

    @Test
    void testFsInMemoryCacheSkipsSymlinkRoot(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("root");
        Path linkRoot = tempDir.resolve("link-root");
        Files.createDirectories(root);
        Files.writeString(root.resolve("resource.txt"), "Content");
        createSymbolicLink(linkRoot, root);

        FileSystemContentHandler handler = (FileSystemContentHandler) StaticContentFeature.createService(
                FileSystemHandlerConfig.builder()
                        .location(linkRoot)
                        .cachedFiles(Set.of("resource.txt"))
                        .build());
        handler.beforeStart();

        assertThat("Resource under symlink root should not be cached in memory",
                   handler.cacheInMemory("resource.txt"),
                   optionalEmpty());
    }

    @Test
    void testNioFallbackServesNestedPathWithoutSecureDirectoryStream(@TempDir Path tempDir) throws IOException {
        URI zipUri = URI.create("jar:" + tempDir.resolve("content.zip").toUri());
        try (FileSystem fileSystem = FileSystems.newFileSystem(zipUri, Map.of("create", "true"))) {
            Path root = fileSystem.getPath("/root");
            Path nested = root.resolve("nested");
            Files.createDirectories(nested);
            Path nestedResource = nested.resolve("resource.txt");
            Files.writeString(nestedResource, "Nested content");

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
                assertThat(stream, not(instanceOf(SecureDirectoryStream.class)));
            }

            BasicFileAttributes attributes = FileBasedContentHandler.attributes(nestedResource, false, root);
            assertThat(attributes.isRegularFile(), is(true));
            byte[] bytes = FileBasedContentHandler.readAllBytes(nestedResource, false, root);
            assertThat(new String(bytes, StandardCharsets.UTF_8), is("Nested content"));
        }
    }

    @Test
    void testFileSystemSymlinkRoot(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("root");
        Path linkRoot = tempDir.resolve("link-root");
        Files.createDirectories(root);
        Files.writeString(root.resolve("resource.txt"), "Content");
        createSymbolicLink(linkRoot, root);

        FileSystemContentHandler handler = (FileSystemContentHandler) StaticContentFeature.createService(
                FileSystemHandlerConfig.builder()
                        .location(linkRoot)
                        .build());
        handler.beforeStart();

        ServerRequest req = mock(ServerRequest.class);
        when(req.prologue()).thenReturn(HttpPrologue.create("http/1.1",
                                                            "http",
                                                            "1.1",
                                                            Method.HEAD,
                                                            "/resource.txt",
                                                            false));
        when(req.headers()).thenReturn(ServerRequestHeaders.create());

        ServerResponse res = mock(ServerResponse.class);
        when(res.headers()).thenReturn(ServerResponseHeaders.create());

        assertThat("Symlink root should serve its resource tree",
                   handler.doHandle(Method.HEAD, "resource.txt", req, res, false),
                   is(true));
    }

    @Test
    void testFsInMemoryCacheSkipsSymlinkInCachedDirectory(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("root");
        Path dir = root.resolve("dir");
        Path externalDir = tempDir.resolve("external");
        Files.createDirectories(dir);
        Files.createDirectories(externalDir);
        Files.writeString(dir.resolve("resource.txt"), "Content");
        Files.writeString(externalDir.resolve("resource.txt"), "External content");
        createSymbolicLink(dir.resolve("link.txt"), externalDir.resolve("resource.txt"));

        FileSystemContentHandler handler = (FileSystemContentHandler) StaticContentFeature.createService(
                FileSystemHandlerConfig.builder()
                        .location(root)
                        .cachedFiles(Set.of("dir"))
                        .build());
        handler.beforeStart();

        assertThat("Plain file in cached directory should be cached in memory",
                   handler.cacheInMemory("dir/resource.txt"),
                   optionalPresent());
        assertThat("Symlink child in cached directory should not be cached in memory",
                   handler.cacheInMemory("dir/link.txt"),
                   optionalEmpty());

        ServerRequest req = mock(ServerRequest.class);
        when(req.prologue()).thenReturn(HttpPrologue.create("http/1.1", "http", "1.1", Method.HEAD, "/dir/link.txt", false));

        ServerResponse res = mock(ServerResponse.class);

        assertThat("Out-of-root symlink child should not be served",
                   handler.doHandle(Method.HEAD, "dir/link.txt", req, res, false),
                   is(false));
        assertThat("Out-of-root symlink child should not remain cached",
                   handler.cacheHandler("dir/link.txt"),
                   optionalEmpty());
    }

    @Test
    void testFsInMemoryCacheEvictsStaleEntryOnRestart(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("root");
        Path externalDir = tempDir.resolve("external");
        Files.createDirectories(root);
        Files.createDirectories(externalDir);
        Files.writeString(root.resolve("resource.txt"), "Content");
        Files.writeString(externalDir.resolve("resource.txt"), "External content");

        FileSystemContentHandler handler = (FileSystemContentHandler) StaticContentFeature.createService(
                FileSystemHandlerConfig.builder()
                        .location(root)
                        .cachedFiles(Set.of("resource.txt"))
                        .build());
        handler.beforeStart();

        assertThat("Initial resource should be cached in memory",
                   handler.cacheInMemory("resource.txt"),
                   optionalPresent());

        handler.afterStop();
        createSymbolicLink(root.resolve("resource.txt"), externalDir.resolve("resource.txt"));
        handler.beforeStart();

        assertThat("Stale in-memory resource should be evicted after restart",
                   handler.cacheInMemory("resource.txt"),
                   optionalEmpty());
    }

    @Test
    void testMemoryCacheClearReleasesCapacity(@TempDir Path tempDir) throws IOException {
        Path firstRoot = tempDir.resolve("first-root");
        Path secondRoot = tempDir.resolve("second-root");
        Files.createDirectories(firstRoot);
        Files.createDirectories(secondRoot);
        Files.writeString(firstRoot.resolve("resource.txt"), "Content");
        Files.writeString(secondRoot.resolve("resource.txt"), "Content");

        MemoryCache memoryCache = MemoryCache.create(it -> it.capacity(Size.create(14)));
        FileSystemContentHandler firstHandler = (FileSystemContentHandler) StaticContentFeature.createService(
                FileSystemHandlerConfig.builder()
                        .location(firstRoot)
                        .cachedFiles(Set.of("resource.txt"))
                        .memoryCache(memoryCache)
                        .build());
        FileSystemContentHandler secondHandler = (FileSystemContentHandler) StaticContentFeature.createService(
                FileSystemHandlerConfig.builder()
                        .location(secondRoot)
                        .cachedFiles(Set.of("resource.txt"))
                        .memoryCache(memoryCache)
                        .build());
        firstHandler.beforeStart();
        secondHandler.beforeStart();

        assertThat("Both configured files should consume the shared memory cache capacity",
                   memoryCache.available(1),
                   is(false));

        firstHandler.afterStop();

        assertThat("Clearing one handler memory cache should release only that handler capacity",
                   memoryCache.available(7),
                   is(true));
        assertThat("Clearing one handler memory cache should remove that handler entry",
                   firstHandler.cacheInMemory("resource.txt"),
                   optionalEmpty());
        assertThat("Clearing one handler memory cache should preserve the other handler entry",
                   secondHandler.cacheInMemory("resource.txt"),
                   optionalPresent());
        assertThat("The second handler should still consume its shared capacity",
                   memoryCache.available(8),
                   is(false));

        secondHandler.afterStop();

        assertThat("Clearing both handlers should release all shared capacity",
                   memoryCache.available(14),
                   is(true));
    }

    @Test
    void testMemoryCacheReplaceKeepsCapacity(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        MemoryCache memoryCache = MemoryCache.create(it -> it.capacity(Size.create(8)));
        FileSystemContentHandler handler = (FileSystemContentHandler) StaticContentFeature.createService(
                FileSystemHandlerConfig.builder()
                        .location(root)
                        .memoryCache(memoryCache)
                        .build());

        handler.cacheInMemory("resource.txt",
                              MediaTypes.TEXT_PLAIN,
                              "Content".getBytes(StandardCharsets.UTF_8));
        handler.cacheInMemory("resource.txt",
                              MediaTypes.TEXT_PLAIN,
                              "Content".getBytes(StandardCharsets.UTF_8));
        handler.cacheInMemory("resource.txt",
                              MediaTypes.TEXT_PLAIN,
                              "X".getBytes(StandardCharsets.UTF_8));

        assertThat("Replacing a cached resource with a smaller payload should release capacity",
                   memoryCache.available(7),
                   is(true));

        handler.cacheInMemory("resource.txt",
                              MediaTypes.TEXT_PLAIN,
                              "Content".getBytes(StandardCharsets.UTF_8));

        assertThat("Replacing a cached resource should not double-count its size",
                   memoryCache.available(1),
                   is(true));
        assertThat("Replacing a cached resource with a larger payload should consume capacity",
                   memoryCache.available(2),
                   is(false));
    }

    @Test
    void testMemoryCacheCountsAliasedHandlerOnce(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        MemoryCache memoryCache = MemoryCache.create(it -> it.capacity(Size.create(10)));
        FileSystemContentHandler handler = (FileSystemContentHandler) StaticContentFeature.createService(
                FileSystemHandlerConfig.builder()
                        .location(root)
                        .memoryCache(memoryCache)
                        .build());
        handler.cacheInMemory("resource.txt",
                              MediaTypes.TEXT_PLAIN,
                              "12345".getBytes(StandardCharsets.UTF_8));
        CachedHandlerInMemory cached = handler.cacheInMemory("resource.txt").orElseThrow();

        handler.cacheInMemory("alias.txt", cached);

        assertThat("Aliased cache keys should share the payload capacity",
                   memoryCache.available(5),
                   is(true));
        assertThat("The shared payload should still consume its capacity",
                   memoryCache.available(6),
                   is(false));

        byte[] replacementBytes = "1".getBytes(StandardCharsets.UTF_8);
        CachedHandlerInMemory replacement = new CachedHandlerInMemory(StaticContentMetadata.create(MediaTypes.TEXT_PLAIN,
                                                                                                    replacementBytes.length),
                                                                       replacementBytes);
        handler.cacheInMemory("alias.txt", replacement);

        assertThat("Replacing one alias should retain both distinct payloads",
                   memoryCache.available(4),
                   is(true));
        assertThat("Both distinct payloads should consume capacity",
                   memoryCache.available(5),
                   is(false));

        handler.cacheInMemory("resource.txt", replacement);

        assertThat("Replacing the last original alias should release its payload",
                   memoryCache.available(9),
                   is(true));

        handler.releaseCache();

        assertThat("Clearing aliases should release their shared capacity",
                   memoryCache.available(10),
                   is(true));
    }

    @Test
    void testSingleFileSymlink(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("root");
        Path externalDir = tempDir.resolve("external");
        Files.createDirectories(root);
        Files.createDirectories(externalDir);
        Files.writeString(root.resolve("resource.txt"), "Content");
        Files.writeString(externalDir.resolve("resource.txt"), "External content");

        Path link = root.resolve("link.txt");
        createSymbolicLink(link, root.resolve("resource.txt"));

        SingleFileContentHandler handler = (SingleFileContentHandler) StaticContentFeature.createService(
                FileSystemHandlerConfig.builder()
                        .location(link)
                        .build());
        handler.beforeStart();
        ServerRequest req = mock(ServerRequest.class);
        when(req.headers()).thenReturn(ServerRequestHeaders.create());

        ServerResponse res = mock(ServerResponse.class);
        when(res.headers()).thenReturn(ServerResponseHeaders.create());

        assertThat("Initial single-file symlink target should be served",
                   handler.doHandle(Method.HEAD, "", req, res, false),
                   is(true));
    }

    @Test
    void testSingleFileAdditionAfterMiss(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("added-after-miss.txt");
        SingleFileContentHandler handler = (SingleFileContentHandler) StaticContentFeature.createService(
                FileSystemHandlerConfig.create(file));
        handler.beforeStart();

        ServerRequest req = mock(ServerRequest.class);
        when(req.headers()).thenReturn(ServerRequestHeaders.create());

        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        ServerResponse res = mock(ServerResponse.class);
        when(res.headers()).thenReturn(responseHeaders);

        assertThat("Missing single file should not be cached",
                   handler.doHandle(Method.HEAD, "", req, res, false),
                   is(false));

        Files.writeString(file, "Content");

        assertThat("New single file should be discovered after an earlier miss",
                   handler.doHandle(Method.HEAD, "", req, res, false),
                   is(true));
        assertThat(responseHeaders, hasHeader(RESOURCE_CONTENT_LENGTH));
        assertThat(responseHeaders, hasHeader(HeaderNames.ETAG));
        assertThat(responseHeaders, hasHeader(HeaderNames.LAST_MODIFIED));
    }

    @Test
    void testSingleFileRemovalAndReaddition(@TempDir Path tempDir) throws IOException {
        Path file = Files.writeString(tempDir.resolve("removed-and-readded.txt"), "Initial content");
        SingleFileContentHandler handler = (SingleFileContentHandler) StaticContentFeature.createService(
                FileSystemHandlerConfig.create(file));
        handler.beforeStart();

        ServerRequest req = mock(ServerRequest.class);
        when(req.headers()).thenReturn(ServerRequestHeaders.create());

        ServerResponse initialResponse = mock(ServerResponse.class);
        ServerResponseHeaders initialHeaders = ServerResponseHeaders.create();
        when(initialResponse.headers()).thenReturn(initialHeaders);

        assertThat("Initial single file should be served",
                   handler.doHandle(Method.HEAD, "", req, initialResponse, false),
                   is(true));
        assertThat(initialHeaders, hasHeader(HeaderNames.CONTENT_LENGTH, "15"));

        Files.delete(file);

        ServerResponse removedResponse = mock(ServerResponse.class);
        ServerResponseHeaders removedHeaders = ServerResponseHeaders.create();
        when(removedResponse.headers()).thenReturn(removedHeaders);

        assertThat("Removed single file should not be served",
                   handler.doHandle(Method.HEAD, "", req, removedResponse, false),
                   is(false));
        assertThat("Removed single file handler should be evicted", handler.cacheHandler("."), optionalEmpty());
        assertThat(removedHeaders.contains(HeaderNames.CONTENT_LENGTH), is(false));

        Files.writeString(file, "Re-added content");

        ServerResponse readdedResponse = mock(ServerResponse.class);
        ServerResponseHeaders readdedHeaders = ServerResponseHeaders.create();
        when(readdedResponse.headers()).thenReturn(readdedHeaders);

        assertThat("Re-added single file should be served",
                   handler.doHandle(Method.HEAD, "", req, readdedResponse, false),
                   is(true));
        assertThat(readdedHeaders, hasHeader(HeaderNames.CONTENT_LENGTH, "16"));
    }

    @Test
    void testCachedSingleFileGetEvictsUnopenablePathAndRecovers(@TempDir Path tempDir) throws IOException {
        assertCachedSingleFileEvictsUnopenablePathAndRecovers(tempDir, Method.GET);
    }

    @Test
    void testCachedSingleFileHeadEvictsUnopenablePathAndRecovers(@TempDir Path tempDir) throws IOException {
        assertCachedSingleFileEvictsUnopenablePathAndRecovers(tempDir, Method.HEAD);
    }

    @Test
    void testCachedSingleFileGetEvictsUnreadablePath(@TempDir Path tempDir) throws IOException {
        assertCachedSingleFileEvictsUnreadablePath(tempDir, Method.GET);
    }

    @Test
    void testCachedSingleFileHeadEvictsUnreadablePath(@TempDir Path tempDir) throws IOException {
        assertCachedSingleFileEvictsUnreadablePath(tempDir, Method.HEAD);
    }

    @Test
    void testInMemoryFileRemainsAfterSourceRemoval(@TempDir Path tempDir) throws IOException {
        Path root = Files.createDirectory(tempDir.resolve("root"));
        Path file = Files.writeString(root.resolve("resource.txt"), "Content");
        FileSystemContentHandler handler = (FileSystemContentHandler) StaticContentFeature.createService(
                FileSystemHandlerConfig.builder()
                        .location(root)
                        .cachedFiles(Set.of("resource.txt"))
                        .build());
        handler.beforeStart();

        Files.delete(file);

        ServerRequest req = mock(ServerRequest.class);
        when(req.headers()).thenReturn(ServerRequestHeaders.create());
        when(req.prologue()).thenReturn(HttpPrologue.create("http/1.1",
                                                            "http",
                                                            "1.1",
                                                            Method.HEAD,
                                                            "/resource.txt",
                                                            false));

        ServerResponse res = mock(ServerResponse.class);
        ServerResponseHeaders responseHeaders = ServerResponseHeaders.create();
        when(res.headers()).thenReturn(responseHeaders);

        assertThat("In-memory snapshot should remain available after source removal",
                   handler.doHandle(Method.HEAD, "resource.txt", req, res, false),
                   is(true));
        assertThat(responseHeaders, hasHeader(RESOURCE_CONTENT_LENGTH));
    }

    @Test
    void testSingleHiddenFileIsRequestScopedForbidden(@TempDir Path tempDir) throws IOException {
        Path file = Files.writeString(tempDir.resolve(".hidden.txt"), "Content");
        assumeTrue(Files.isHidden(file), "Hidden files are not supported on this file system");

        SingleFileContentHandler handler = (SingleFileContentHandler) StaticContentFeature.createService(
                FileSystemHandlerConfig.create(file));

        handler.beforeStart();

        ServerRequest req = mock(ServerRequest.class);
        when(req.headers()).thenReturn(ServerRequestHeaders.create());

        ServerResponse res = mock(ServerResponse.class);
        when(res.headers()).thenReturn(ServerResponseHeaders.create());

        assertThrows(ForbiddenException.class, () -> handler.doHandle(Method.HEAD, "", req, res, false));
        assertThat("Forbidden single file should not be cached", handler.cacheHandler("."), optionalEmpty());
    }

    @Test
    void testSingleFileInMemoryCacheSkipsSymlink(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("root");
        Path externalDir = tempDir.resolve("external");
        Files.createDirectories(root);
        Files.createDirectories(externalDir);
        Files.writeString(root.resolve("resource.txt"), "Content");
        Files.writeString(externalDir.resolve("resource.txt"), "External content");

        Path link = root.resolve("link.txt");
        createSymbolicLink(link, root.resolve("resource.txt"));

        SingleFileContentHandler handler = (SingleFileContentHandler) StaticContentFeature.createService(
                FileSystemHandlerConfig.builder()
                        .location(link)
                        .cachedFiles(Set.of("."))
                        .build());
        handler.beforeStart();

        assertThat("Single-file symlink should not be cached in memory",
                   handler.cacheInMemory("."),
                   optionalEmpty());

        ServerRequest req = mock(ServerRequest.class);
        when(req.headers()).thenReturn(ServerRequestHeaders.create());

        ServerResponse res = mock(ServerResponse.class);
        when(res.headers()).thenReturn(ServerResponseHeaders.create());

        assertThat("Initial symlink target should be served", handler.doHandle(Method.HEAD, "", req, res, false), is(true));

    }

    @Test
    void testSingleFileInMemoryCacheSkipsSymlinkParent(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("root");
        Path linkRoot = tempDir.resolve("link-root");
        Files.createDirectories(root);
        Files.writeString(root.resolve("resource.txt"), "Content");
        createSymbolicLink(linkRoot, root);

        SingleFileContentHandler handler = (SingleFileContentHandler) StaticContentFeature.createService(
                FileSystemHandlerConfig.builder()
                        .location(linkRoot.resolve("resource.txt"))
                        .cachedFiles(Set.of("."))
                        .build());
        handler.beforeStart();

        assertThat("Resource under symlink parent should not be cached in memory",
                   handler.cacheInMemory("."),
                   optionalEmpty());
        assertThat("Resource under symlink parent should fall back to a path handler",
                   handler.cacheHandler(".").orElse(null),
                   instanceOf(CachedHandlerPath.class));
    }

    @Test
    void testFsHiddenSymlinkIsForbidden(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);
        Files.writeString(root.resolve("resource.txt"), "Content");

        Path link = root.resolve(".link");
        createSymbolicLink(link, root.resolve("resource.txt"));
        assumeTrue(Files.isHidden(link), "Hidden symbolic links are not supported on this file system");

        FileSystemContentHandler handler = (FileSystemContentHandler) StaticContentFeature.createService(
                FileSystemHandlerConfig.builder()
                        .location(root)
                        .build());

        ServerRequest req = mock(ServerRequest.class);
        when(req.prologue()).thenReturn(HttpPrologue.create("http/1.1", "http", "1.1", Method.GET, "/.link", false));

        ServerResponse res = mock(ServerResponse.class);

        assertThrows(ForbiddenException.class, () -> handler.doHandle(Method.GET, ".link", req, res, false));
        assertThat("Hidden symlink should not remain cached",
                   handler.cacheHandler(".link"),
                   optionalEmpty());
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
        assertThat("Last modified", pathHandler.metadata().lastModified(), notNullValue());
        assertThat("Content length", pathHandler.metadata().contentLength(), is(7L));
        assertThat("Media type", pathHandler.metadata().mediaType(), is(MediaTypes.TEXT_PLAIN));
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

        // Filesystem redirects depend on mutable path state, so they must not be cached.
        Optional<CachedHandler> cachedHandler = fsHandler.cacheHandler("nested");
        assertThat("Handler should not be cached", cachedHandler, optionalEmpty());
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

    private static void assertCachedSingleFileEvictsUnopenablePathAndRecovers(Path tempDir, Method method)
            throws IOException {
        Path file = Files.writeString(tempDir.resolve("resource.txt"), "Initial content");
        SingleFileContentHandler handler = (SingleFileContentHandler) StaticContentFeature.createService(
                FileSystemHandlerConfig.create(file));
        handler.beforeStart();

        ServerRequest request = mock(ServerRequest.class);
        when(request.headers()).thenReturn(ServerRequestHeaders.create());

        ByteArrayOutputStream initialOutput = new ByteArrayOutputStream();
        ServerResponse initialResponse = mock(ServerResponse.class);
        when(initialResponse.headers()).thenReturn(ServerResponseHeaders.create());
        when(initialResponse.outputStream()).thenReturn(initialOutput);

        assertThat("Initial single file should be served for " + method,
                   handler.doHandle(method, "", request, initialResponse, false),
                   is(true));
        assertThat("Initial single file should be cached for " + method, handler.cacheHandler("."), optionalPresent());

        Files.delete(file);
        Files.createDirectory(file);

        ByteArrayOutputStream unavailableOutput = new ByteArrayOutputStream();
        ServerResponseHeaders unavailableHeaders = ServerResponseHeaders.create();
        ServerResponse unavailableResponse = mock(ServerResponse.class);
        when(unavailableResponse.headers()).thenReturn(unavailableHeaders);
        when(unavailableResponse.outputStream()).thenReturn(unavailableOutput);

        assertThrows(ForbiddenException.class,
                     () -> handler.doHandle(method, "", request, unavailableResponse, false),
                     method + " should reject a cached path that is no longer an openable file");
        assertThat("Unopenable single file handler should be evicted for " + method,
                   handler.cacheHandler("."),
                   optionalEmpty());
        assertThat("Unopenable single file should not publish stale content length for " + method,
                   unavailableHeaders.contains(HeaderNames.CONTENT_LENGTH),
                   is(false));

        Files.delete(file);
        Files.writeString(file, "Re-added content");

        ByteArrayOutputStream readdedOutput = new ByteArrayOutputStream();
        ServerResponseHeaders readdedHeaders = ServerResponseHeaders.create();
        ServerResponse readdedResponse = mock(ServerResponse.class);
        when(readdedResponse.headers()).thenReturn(readdedHeaders);
        when(readdedResponse.outputStream()).thenReturn(readdedOutput);

        assertThat("Re-added single file should be served for " + method,
                   handler.doHandle(method, "", request, readdedResponse, false),
                   is(true));
        assertThat(readdedHeaders, hasHeader(HeaderNames.CONTENT_LENGTH, "16"));
        if (method == Method.GET) {
            assertThat("Re-added single file content",
                       readdedOutput.toByteArray(),
                       is("Re-added content".getBytes(StandardCharsets.UTF_8)));
        }
    }

    private static void assertCachedSingleFileEvictsUnreadablePath(Path tempDir, Method method) throws IOException {
        Path file = Files.writeString(tempDir.resolve("resource.txt"), "Content");
        assumeTrue(Files.getFileStore(file).supportsFileAttributeView("posix"),
                   "POSIX file permissions are not supported");

        SingleFileContentHandler handler = (SingleFileContentHandler) StaticContentFeature.createService(
                FileSystemHandlerConfig.create(file));
        handler.beforeStart();

        ServerRequest request = mock(ServerRequest.class);
        when(request.headers()).thenReturn(ServerRequestHeaders.create());

        ServerResponse initialResponse = mock(ServerResponse.class);
        when(initialResponse.headers()).thenReturn(ServerResponseHeaders.create());
        when(initialResponse.outputStream()).thenReturn(new ByteArrayOutputStream());

        assertThat("Initial single file should be served for " + method,
                   handler.doHandle(method, "", request, initialResponse, false),
                   is(true));
        assertThat("Initial single file should be cached for " + method, handler.cacheHandler("."), optionalPresent());

        Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(file);
        Set<PosixFilePermission> unreadablePermissions = EnumSet.copyOf(originalPermissions);
        unreadablePermissions.remove(PosixFilePermission.OWNER_READ);
        unreadablePermissions.remove(PosixFilePermission.GROUP_READ);
        unreadablePermissions.remove(PosixFilePermission.OTHERS_READ);

        try {
            Files.setPosixFilePermissions(file, unreadablePermissions);
            assumeTrue(!Files.isReadable(file), "Read access cannot be revoked for this test process");

            ServerResponseHeaders unavailableHeaders = ServerResponseHeaders.create();
            ServerResponse unavailableResponse = mock(ServerResponse.class);
            when(unavailableResponse.headers()).thenReturn(unavailableHeaders);
            when(unavailableResponse.outputStream()).thenReturn(new ByteArrayOutputStream());

            assertThrows(ForbiddenException.class,
                         () -> handler.doHandle(method, "", request, unavailableResponse, false),
                         method + " should reject a cached file after read access is revoked");
            assertThat("Unreadable single file handler should be evicted for " + method,
                       handler.cacheHandler("."),
                       optionalEmpty());
            assertThat("Unreadable single file should not publish stale content length for " + method,
                       unavailableHeaders.contains(HeaderNames.CONTENT_LENGTH),
                       is(false));
        } finally {
            Files.setPosixFilePermissions(file, originalPermissions);
        }
    }

    private static void createSymbolicLink(Path link, Path target) throws IOException {
        try {
            Files.deleteIfExists(link);
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | SecurityException e) {
            assumeTrue(false, "Symbolic links are not supported");
        } catch (IOException e) {
            assumeTrue(false, "Symbolic links cannot be created: " + e.getMessage());
        }
    }
}
