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
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
                .addCacheInMemory("favicon.ico")
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
        Optional<CachedHandlerInMemory> cachedHandlerInMemory = fsHandler.cacheInMemory("favicon.ico");
        assertThat("Handler should be cached in memory", cachedHandlerInMemory, optionalPresent());
        CachedHandlerInMemory cached = cachedHandlerInMemory.get();
        assertThat("Cached bytes must not be null", cached.bytes(), notNullValue());
        assertThat("Cached bytes must not be empty", cached.bytes(), not(BufferData.EMPTY_BYTES));
        assertThat("Content length", cached.contentLength(), is(1230));
        assertThat("Last modified", cached.lastModified(), notNullValue());
        assertThat("Media type", cached.mediaType(), is(MEDIA_TYPE_ICON));
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
    void testSecureFallbackRejectsNestedPathWithoutSecureDirectoryStream(@TempDir Path tempDir) throws IOException {
        URI zipUri = URI.create("jar:" + tempDir.resolve("content.zip").toUri());
        try (FileSystem fileSystem = FileSystems.newFileSystem(zipUri, Map.of("create", "true"))) {
            Path root = fileSystem.getPath("/root");
            Path nested = root.resolve("nested");
            Files.createDirectories(nested);
            Path nestedResource = nested.resolve("resource.txt");
            Files.writeString(nestedResource, "Nested content");

            assertThat(StaticContentTestSupport.supportsSecureDirectoryStream(root), is(false));

            assertThrows(NoSuchFileException.class,
                         () -> FileBasedContentHandler.attributes(nestedResource, false, root));
            assertThrows(NoSuchFileException.class,
                         () -> FileBasedContentHandler.newByteChannel(nestedResource, false, root).close());
        }
    }

    @Test
    void testFileSystemSymlinkRootRetargetingAfterStart(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("root");
        Path nestedRoot = root.resolve("nested");
        Path alternateRoot = tempDir.resolve("alternate-root");
        Path linkRoot = tempDir.resolve("link-root");
        Files.createDirectories(nestedRoot);
        Files.createDirectories(alternateRoot);
        Files.writeString(root.resolve("resource.txt"), "Content");
        Files.writeString(nestedRoot.resolve("resource.txt"), "Nested content");
        Files.writeString(alternateRoot.resolve("resource.txt"), "Alternate content");
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

        createSymbolicLink(linkRoot, nestedRoot);

        assertThat("Retargeted symlink root should not remap the visible resource tree",
                   handler.doHandle(Method.HEAD, "resource.txt", req, res, false),
                   is(false));

        createSymbolicLink(linkRoot, root);
        assertThat("Original symlink root should still serve the pinned resource tree",
                   handler.doHandle(Method.HEAD, "resource.txt", req, res, false),
                   is(true));

        createSymbolicLink(linkRoot, alternateRoot);

        assertThat("Out-of-root symlink root should not be served",
                   handler.doHandle(Method.HEAD, "resource.txt", req, res, false),
                   is(false));
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

        if (StaticContentTestSupport.supportsSecureDirectoryStream(root)) {
            assertThat("Plain file in cached directory should be cached in memory",
                       handler.cacheInMemory("dir/resource.txt"),
                       optionalPresent());
        } else {
            assertThat("Plain file in cached directory should fail closed without secure directory traversal",
                       handler.cacheInMemory("dir/resource.txt"),
                       optionalEmpty());
        }
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
                              "Content".getBytes(StandardCharsets.UTF_8),
                              Optional.empty());
        handler.cacheInMemory("resource.txt",
                              MediaTypes.TEXT_PLAIN,
                              "Content".getBytes(StandardCharsets.UTF_8),
                              Optional.empty());
        handler.cacheInMemory("resource.txt",
                              MediaTypes.TEXT_PLAIN,
                              "X".getBytes(StandardCharsets.UTF_8),
                              Optional.empty());

        assertThat("Replacing a cached resource with a smaller payload should release capacity",
                   memoryCache.available(7),
                   is(true));

        handler.cacheInMemory("resource.txt",
                              MediaTypes.TEXT_PLAIN,
                              "Content".getBytes(StandardCharsets.UTF_8),
                              Optional.empty());

        assertThat("Replacing a cached resource should not double-count its size",
                   memoryCache.available(1),
                   is(true));
        assertThat("Replacing a cached resource with a larger payload should consume capacity",
                   memoryCache.available(2),
                   is(false));
    }

    @Test
    void testSingleFileSymlinkRetargetingAfterStart(@TempDir Path tempDir) throws IOException {
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
        createSymbolicLink(link, externalDir.resolve("resource.txt"));

        ServerRequest req = mock(ServerRequest.class);
        when(req.headers()).thenReturn(ServerRequestHeaders.create());

        ServerResponse res = mock(ServerResponse.class);
        when(res.headers()).thenReturn(ServerResponseHeaders.create());

        assertThat("Retargeted single-file symlink should not become the trusted file",
                   handler.doHandle(Method.HEAD, "", req, res, false),
                   is(false));
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

        createSymbolicLink(link, externalDir.resolve("resource.txt"));

        assertThat("Retargeted symlink should not be served", handler.doHandle(Method.HEAD, "", req, res, false), is(false));
        assertThat("Retargeted symlink handler should be evicted",
                   handler.cacheHandler("."),
                   optionalEmpty());
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
                                                            "favicon.ico",
                                                            false));

        ServerResponse res = mock(ServerResponse.class);
        when(res.headers()).thenReturn(responseHeaders);

        boolean result = fsHandler.doHandle(Method.GET, "favicon.ico", req, res, false);

        assertThat("Handler should have found favicon.ico", result, is(true));
        assertThat(responseHeaders, hasHeader(ICON_TYPE));
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
