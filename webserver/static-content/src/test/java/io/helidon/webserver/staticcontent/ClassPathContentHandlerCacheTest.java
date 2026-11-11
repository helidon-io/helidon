/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.WritableHeaders;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClassPathContentHandlerCacheTest {
    private static final String RACE_RESOURCE = "web/race.bin";
    private static final int REQUESTS = 48;

    @TempDir
    Path tempDir;

    @TestFactory
    Stream<DynamicTest> rejectsNegativeRecordCacheCapacity() {
        return Stream.of(-1, Integer.MIN_VALUE).map(capacity -> dynamicTest("capacity " + capacity, () -> {
            ClasspathHandlerConfig config = ClasspathHandlerConfig.builder()
                    .location("/web")
                    .recordCacheCapacity(capacity)
                    .build();

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                             () -> StaticContentFeature.createService(config));

            assertThat(exception.getMessage(), is("Capacity must not be negative"));
        }));
    }

    @TestFactory
    Stream<DynamicTest> rejectsNegativeRecordCacheCapacityFromConfig() {
        return Stream.of(-1, Integer.MIN_VALUE).map(capacity -> dynamicTest("capacity " + capacity, () -> {
            Config config = Config.just(ConfigSources.create(Map.of("location", "/web",
                                                                    "record-cache-capacity", Integer.toString(capacity))));
            ClasspathHandlerConfig handlerConfig = ClasspathHandlerConfig.builder().config(config).build();

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                             () -> StaticContentFeature.createService(handlerConfig));

            assertThat(exception.getMessage(), is("Capacity must not be negative"));
        }));
    }

    @TestFactory
    Stream<DynamicTest> preservesNonNegativeRecordCacheCapacity() {
        return Stream.of(0, 1, Integer.MAX_VALUE).map(capacity -> dynamicTest("capacity " + capacity, () -> {
            CachedHandlerCache cache = new CachedHandlerCache(capacity);

            assertThat(cache.capacity(), is(capacity));
        }));
    }

    @Test
    void createsOneTemporaryFileForConcurrentCacheMisses() throws Exception {
        Path tempStorage = tempDir.resolve("race-temp");
        Files.createDirectories(tempStorage);

        Path jarFile = tempDir.resolve("race-content.jar");
        createJar(jarFile, RACE_RESOURCE);

        RaceGate raceGate = new RaceGate(REQUESTS, 500);
        ClassLoader classLoader = new GatedJarClassLoader(jarFile, raceGate);
        CountDownLatch coldMisses = new CountDownLatch(REQUESTS);
        ClassPathContentHandler handler = new ClassPathContentHandler(
                handlerConfig(classLoader, tempStorage, 10_000).build()) {
            @Override
            Optional<CachedHandler> acquireClassPathHandler(String requestedResource) {
                Optional<CachedHandler> cached = super.acquireClassPathHandler(requestedResource);
                if (RACE_RESOURCE.equals(requestedResource) && cached.isEmpty() && coldMisses.getCount() > 0) {
                    coldMisses.countDown();
                    try {
                        if (!coldMisses.await(30, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out waiting for concurrent cache misses");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted waiting for concurrent cache misses", e);
                    }
                }
                return cached;
            }
        };
        handler.beforeStart();
        try {
            runConcurrentHeadRequests(handler, "race.bin");

            assertThat(raceGate.openedStreams(), is(1));
            assertThat(countTemporaryFiles(tempStorage), is(1L));
        } finally {
            handler.afterStop();
        }
        assertThat(countTemporaryFiles(tempStorage), is(0L));
    }

    @Test
    void deletesTemporaryFilesWhenCacheEntriesAreEvicted() throws Exception {
        Path tempStorage = tempDir.resolve("eviction-temp");
        Files.createDirectories(tempStorage);

        Path jarFile = tempDir.resolve("eviction-content.jar");
        createJar(jarFile, "web/first.bin", "web/second.bin");

        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {jarFile.toUri().toURL()}, null)) {
            ClassPathContentHandler handler = createHandler(classLoader, tempStorage, 1);
            handler.beforeStart();
            try {
                assertThat(headRequest(handler, "first.bin"), is(true));
                assertThat(countTemporaryFiles(tempStorage), is(1L));

                assertThat(headRequest(handler, "second.bin"), is(true));
                assertThat(countTemporaryFiles(tempStorage), is(1L));

                assertThat(headRequest(handler, "first.bin"), is(true));
                assertThat(countTemporaryFiles(tempStorage), is(1L));
            } finally {
                handler.afterStop();
            }
            assertThat(countTemporaryFiles(tempStorage), is(0L));
        }
    }

    @Test
    void keepsTemporaryFilesUntilRetiredHandlersAreReleased() throws Exception {
        Path tempStorage = tempDir.resolve("active-request-temp");
        Files.createDirectories(tempStorage);

        Path jarFile = tempDir.resolve("active-request-content.jar");
        createJar(jarFile, "web/first.bin", "web/second.bin");

        TemporaryStorage temporaryStorage = TemporaryStorage.create(builder -> builder
                .directory(tempStorage)
                .deleteOnExit(false));
        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {jarFile.toUri().toURL()}, null)) {
            URL firstUrl = classLoader.getResource("web/first.bin");
            URL secondUrl = classLoader.getResource("web/second.bin");
            assertThat(firstUrl, notNullValue());
            assertThat(secondUrl, notNullValue());
            CachedHandlerJar first = CachedHandlerJar.create(temporaryStorage,
                                                             firstUrl,
                                                             null,
                                                             MediaTypes.APPLICATION_OCTET_STREAM,
                                                             8192);
            CachedHandlerJar second = CachedHandlerJar.create(temporaryStorage,
                                                              secondUrl,
                                                              null,
                                                              MediaTypes.APPLICATION_OCTET_STREAM,
                                                              8192);
            CachedHandlerCache cache = new CachedHandlerCache(1);

            first.acquire();
            cache.put("first", first);
            cache.put("second", second);
            assertThat(countTemporaryFiles(tempStorage), is(2L));

            first.release();
            assertThat(countTemporaryFiles(tempStorage), is(1L));

            cache.clear();
            assertThat(countTemporaryFiles(tempStorage), is(0L));
        }
    }

    @Test
    void retiresEachHandlerInstanceOnceOnClear() {
        CachedHandler first = mock(CachedHandler.class);
        CachedHandler second = mock(CachedHandler.class);
        CachedHandlerCache cache = new CachedHandlerCache(3);
        cache.put("first", first);
        cache.put("alias", first);
        cache.put("second", second);

        cache.clear();
        cache.clear();

        assertThat(cache.size(), is(0));
        verify(first).dispose();
        verify(second).dispose();
    }

    @Test
    void keepsSingleFileHandlerWhenStartupOverlapsShutdown() throws Exception {
        Path storage = Files.createDirectory(tempDir.resolve("overlapping-lifecycle-temp"));
        Path jar = tempDir.resolve("single-file-content.jar");
        createJar(jar, RACE_RESOURCE);

        try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
            PausingSingleFileHandler handler = new PausingSingleFileHandler(
                    handlerConfig(loader, storage, 1).location('/' + RACE_RESOURCE).singleFile(true).build());
            handler.beforeStart();

            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                handler.pauseNextCachePopulation();
                Future<?> start = executor.submit(handler::beforeStart);
                assertThat(handler.cached.await(10, TimeUnit.SECONDS), is(true));

                CountDownLatch stopStarted = new CountDownLatch(1);
                AtomicReference<Thread> stopThread = new AtomicReference<>();
                Future<?> stop = executor.submit(() -> {
                    stopThread.set(Thread.currentThread());
                    stopStarted.countDown();
                    handler.afterStop();
                });
                assertThat(stopStarted.await(10, TimeUnit.SECONDS), is(true));
                assertThat(shutdownWaitsForStartup(stopThread.get(), stop), is(true));

                handler.resumeCachePopulation();
                start.get(10, TimeUnit.SECONDS);
                stop.get(10, TimeUnit.SECONDS);
                assertThat(countTemporaryFiles(storage), is(1L));
                assertThat(headRequest(handler, "race.bin"), is(true));
            } finally {
                handler.resumeCachePopulation();
                executor.shutdownNow();
                assertThat(executor.awaitTermination(10, TimeUnit.SECONDS), is(true));
                handler.afterStop();
            }
            assertThat(countTemporaryFiles(storage), is(0L));
        }
    }

    @Test
    void retiresPrecompressedVariantsWithTheirIdentity() throws Exception {
        Path storage = Files.createDirectory(tempDir.resolve("sidecar-temp"));
        Path jar = tempDir.resolve("sidecar.jar");
        createJar(jar, "web/first.bin", "web/first.bin.gz", "web/second.bin");
        try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
            ClassPathContentHandler handler = (ClassPathContentHandler) StaticContentFeature.createService(
                    handlerConfig(loader, storage, 1).preCompressedEnabled(true).build());
            handler.beforeStart();
            try {
                ServerResponseHeaders headers = compressedHead(handler, "first.bin");
                assertThat(headers.get(HeaderNames.CONTENT_ENCODING).get(), is("gzip"));
                assertThat(countTemporaryFiles(storage), is(2L));
                assertThat(headRequest(handler, "second.bin"), is(true));
                assertThat(countTemporaryFiles(storage), is(1L));
            } finally {
                handler.afterStop();
            }
            assertThat(countTemporaryFiles(storage), is(0L));
        }
    }

    @Test
    void retainsPrecompressedFilesUntilActiveResponseCompletes() throws Exception {
        Path storage = Files.createDirectory(tempDir.resolve("active-sidecar-temp"));
        Path jar = tempDir.resolve("active-sidecar.jar");
        createJar(jar, "web/first.bin", "web/first.bin.gz", "web/second.bin");
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
            ClassPathContentHandler handler = (ClassPathContentHandler) StaticContentFeature.createService(
                    handlerConfig(loader, storage, 1).preCompressedEnabled(true).build());
            handler.beforeStart();
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ServerResponseHeaders headers = ServerResponseHeaders.create();
                ServerResponse response = mock(ServerResponse.class);
                when(response.headers()).thenReturn(headers);
                when(response.outputStream()).thenAnswer(_ -> {
                    writing.countDown();
                    assertThat(resume.await(10, TimeUnit.SECONDS), is(true));
                    return output;
                });
                Future<Boolean> request = executor.submit(() -> handler.doHandle(Method.GET,
                                                                                 "first.bin",
                                                                                 compressedRequest(Method.GET, "first.bin"),
                                                                                 response,
                                                                                 false));
                assertThat(writing.await(10, TimeUnit.SECONDS), is(true));
                assertThat(countTemporaryFiles(storage), is(2L));
                assertThat(headRequest(handler, "second.bin"), is(true));
                assertThat(countTemporaryFiles(storage), is(3L));
                resume.countDown();
                assertThat(request.get(10, TimeUnit.SECONDS), is(true));
                assertThat(output.size(), is(8192));
                assertThat(headers.get(HeaderNames.CONTENT_ENCODING).get(), is("gzip"));
                assertThat(countTemporaryFiles(storage), is(1L));
            } finally {
                resume.countDown();
                executor.shutdownNow();
                assertThat(executor.awaitTermination(10, TimeUnit.SECONDS), is(true));
                handler.afterStop();
            }
            assertThat(countTemporaryFiles(storage), is(0L));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void retiresSidecarsOfPreloadedInMemoryResourcesOnStop() throws Exception {
        Path storage = Files.createDirectory(tempDir.resolve("memory-sidecar-temp"));
        Path jar = tempDir.resolve("memory-sidecar.jar");
        createJar(jar, "web/first.bin", "web/first.bin.gz");
        try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
            ClassPathContentHandler handler = (ClassPathContentHandler) StaticContentFeature.createService(
                    handlerConfig(loader, storage, 1).preCompressedEnabled(true)
                            .cachedFiles(Set.of("first.bin")).build());
            handler.beforeStart();
            try {
                compressedHead(handler, "first.bin");
                assertThat(countTemporaryFiles(storage), is(1L));
            } finally {
                handler.afterStop();
            }
            assertThat(countTemporaryFiles(storage), is(0L));
        }
    }

    @Test
    void releasesUncachedIdentityAndSidecarAfterEachRequest() throws Exception {
        Path storage = Files.createDirectory(tempDir.resolve("uncached-temp"));
        Path jar = tempDir.resolve("uncached.jar");
        createJar(jar, "web/first.bin", "web/first.bin.gz");
        try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
            ClassPathContentHandler handler = (ClassPathContentHandler) StaticContentFeature.createService(
                    handlerConfig(loader, storage, 0).preCompressedEnabled(true).build());
            handler.beforeStart();
            try {
                for (int i = 0; i < 3; i++) {
                    compressedHead(handler, "first.bin");
                    assertThat(countTemporaryFiles(storage), is(0L));
                }
            } finally {
                handler.afterStop();
            }
        }
    }

    @Test
    void coordinatesConcurrentSingleFileReloads() throws Exception {
        Path storage = Files.createDirectory(tempDir.resolve("single-temp"));
        Path jar = tempDir.resolve("single.jar");
        createJar(jar, "web/first.bin");
        try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
            ClassPathContentHandler handler = (ClassPathContentHandler) StaticContentFeature.createService(
                    handlerConfig(loader, storage, 1).location("/web/first.bin").singleFile(true).build());
            handler.beforeStart();
            try {
                assertThat(countTemporaryFiles(storage), is(1L));
                handler.handlerCache().clear();
                assertThat(countTemporaryFiles(storage), is(0L));
                runConcurrentHeadRequests(handler, "first.bin");
                assertThat(countTemporaryFiles(storage), is(1L));
            } finally {
                handler.afterStop();
            }
            assertThat(countTemporaryFiles(storage), is(0L));
        }
    }

    private static ClassPathContentHandler createHandler(ClassLoader classLoader,
                                                         Path tempStorage,
                                                         int recordCacheCapacity) {
        return (ClassPathContentHandler) StaticContentFeature.createService(
                handlerConfig(classLoader, tempStorage, recordCacheCapacity).build());
    }

    private static ClasspathHandlerConfig.Builder handlerConfig(ClassLoader classLoader,
                                                                Path tempStorage,
                                                                int recordCacheCapacity) {
        TemporaryStorage temporaryStorage = TemporaryStorage.create(builder -> builder
                .directory(tempStorage)
                .deleteOnExit(false));

        return ClasspathHandlerConfig.builder()
                .location("/web")
                .classLoader(classLoader)
                .temporaryStorage(temporaryStorage)
                .recordCacheCapacity(recordCacheCapacity);
    }

    private static ServerRequest compressedRequest(Method method, String resource) {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderNames.ACCEPT_ENCODING, "gzip, identity;q=0");
        ServerRequest request = mock(ServerRequest.class);
        when(request.headers()).thenReturn(ServerRequestHeaders.create(headers));
        when(request.prologue()).thenReturn(HttpPrologue.create("http/1.1", "http", "1.1", method, '/' + resource, false));
        return request;
    }

    private static ServerResponseHeaders compressedHead(ClassPathContentHandler handler, String resource) throws Exception {
        ServerResponse response = mock(ServerResponse.class);
        ServerResponseHeaders headers = ServerResponseHeaders.create();
        when(response.headers()).thenReturn(headers);
        assertThat(handler.doHandle(Method.HEAD, resource, compressedRequest(Method.HEAD, resource), response, false), is(true));
        return headers;
    }

    private static void runConcurrentHeadRequests(ClassPathContentHandler handler, String resource) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(REQUESTS);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            results.add(executor.submit(new HeadRequestTask(handler, resource, start)));
        }

        start.countDown();
        try {
            for (Future<Boolean> result : results) {
                assertThat(result.get(30, TimeUnit.SECONDS), is(true));
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS), is(true));
        }
    }

    private static boolean headRequest(ClassPathContentHandler handler, String resource) throws Exception {
        return new HeadRequestTask(handler, resource, new CountDownLatch(0)).call();
    }

    private static long countTemporaryFiles(Path tempStorage) throws IOException {
        try (Stream<Path> files = Files.list(tempStorage)) {
            return files
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith("helidon-ws") && name.endsWith(".je");
                    })
                    .count();
        }
    }

    private static void createJar(Path jarFile, String... resources) throws IOException {
        byte[] content = new byte[8192];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) ('A' + (i % 26));
        }

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jarFile))) {
            for (String resource : resources) {
                zip.putNextEntry(new ZipEntry(resource));
                zip.write(content);
                zip.closeEntry();
            }
        }
    }

    private static boolean shutdownWaitsForStartup(Thread thread, Future<?> stop) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (stop.isDone()) {
                return false;
            }
            Thread.State state = thread.getState();
            if (state == Thread.State.WAITING || state == Thread.State.BLOCKED) {
                return true;
            }
            Thread.sleep(1);
        }
        throw new IllegalStateException("Shutdown did not reach the lifecycle lock");
    }

    private static final class PausingSingleFileHandler extends SingleFileClassPathContentHandler {
        private final CountDownLatch cached = new CountDownLatch(1);
        private final CountDownLatch resume = new CountDownLatch(1);
        private final AtomicBoolean pause = new AtomicBoolean();

        private PausingSingleFileHandler(ClasspathHandlerConfig config) {
            super(config);
        }

        @Override
        void cacheHandler(String resource, CachedHandler cachedResource) {
            super.cacheHandler(resource, cachedResource);
            if (pause.compareAndSet(true, false)) {
                cached.countDown();
                try {
                    if (!resume.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for concurrent shutdown");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
        }

        private void pauseNextCachePopulation() {
            pause.set(true);
        }

        private void resumeCachePopulation() {
            resume.countDown();
        }
    }

    private static final class HeadRequestTask implements Callable<Boolean> {
        private final ClassPathContentHandler handler;
        private final String resource;
        private final CountDownLatch start;

        private HeadRequestTask(ClassPathContentHandler handler, String resource, CountDownLatch start) {
            this.handler = handler;
            this.resource = resource;
            this.start = start;
        }

        @Override
        public Boolean call() throws Exception {
            start.await();

            ServerRequest request = mock(ServerRequest.class);
            when(request.headers()).thenReturn(ServerRequestHeaders.create());
            when(request.prologue()).thenReturn(HttpPrologue.create("http/1.1",
                                                                    "http",
                                                                    "1.1",
                                                                    Method.HEAD,
                                                                    '/' + resource,
                                                                    false));

            ServerResponse response = mock(ServerResponse.class);
            when(response.headers()).thenReturn(ServerResponseHeaders.create());

            return handler.doHandle(Method.HEAD, resource, request, response, false);
        }
    }

    private static final class GatedJarClassLoader extends ClassLoader {
        private final Path jarFile;
        private final RaceGate raceGate;

        private GatedJarClassLoader(Path jarFile, RaceGate raceGate) {
            super(Thread.currentThread().getContextClassLoader());
            this.jarFile = jarFile;
            this.raceGate = raceGate;
        }

        @Override
        public URL getResource(String name) {
            if (!RACE_RESOURCE.equals(name)) {
                return super.getResource(name);
            }
            try {
                return new URL(null,
                               "jar:" + jarFile.toUri() + "!/" + RACE_RESOURCE,
                               new GatedJarUrlStreamHandler(jarFile, raceGate));
            } catch (MalformedURLException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static final class GatedJarUrlStreamHandler extends URLStreamHandler {
        private final Path jarFile;
        private final RaceGate raceGate;

        private GatedJarUrlStreamHandler(Path jarFile, RaceGate raceGate) {
            this.jarFile = jarFile;
            this.raceGate = raceGate;
        }

        @Override
        protected URLConnection openConnection(URL url) throws IOException {
            return new GatedJarURLConnection(url, jarFile, raceGate);
        }
    }

    private static final class GatedJarURLConnection extends JarURLConnection {
        private final JarFile jarFile;
        private final JarEntry jarEntry;
        private final RaceGate raceGate;

        private GatedJarURLConnection(URL url, Path jarPath, RaceGate raceGate) throws IOException {
            super(url);
            setUseCaches(false);
            this.jarFile = new JarFile(jarPath.toFile());
            this.jarEntry = jarFile.getJarEntry(RACE_RESOURCE);
            this.raceGate = raceGate;
            if (jarEntry == null) {
                jarFile.close();
                throw new IOException("test jar should contain " + RACE_RESOURCE);
            }
        }

        @Override
        public void connect() {
        }

        @Override
        public JarFile getJarFile() {
            return jarFile;
        }

        @Override
        public JarEntry getJarEntry() {
            return jarEntry;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new GatedInputStream(jarFile, jarFile.getInputStream(jarEntry), raceGate);
        }
    }

    private static final class GatedInputStream extends InputStream {
        private final JarFile jarFile;
        private final InputStream delegate;
        private final RaceGate raceGate;
        private boolean entered;

        private GatedInputStream(JarFile jarFile, InputStream delegate, RaceGate raceGate) {
            this.jarFile = jarFile;
            this.delegate = delegate;
            this.raceGate = raceGate;
        }

        @Override
        public int read() throws IOException {
            enterGate();
            return delegate.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            enterGate();
            return delegate.read(bytes, offset, length);
        }

        @Override
        public void close() throws IOException {
            try {
                delegate.close();
            } finally {
                jarFile.close();
            }
        }

        private void enterGate() throws IOException {
            if (!entered) {
                entered = true;
                raceGate.enter();
            }
        }
    }

    private static final class RaceGate {
        private final int expectedStreams;
        private final int gateMillis;
        private final AtomicInteger openedStreams = new AtomicInteger();
        private final AtomicBoolean releaseTimerStarted = new AtomicBoolean();
        private final CountDownLatch release = new CountDownLatch(1);

        private RaceGate(int expectedStreams, int gateMillis) {
            this.expectedStreams = expectedStreams;
            this.gateMillis = gateMillis;
        }

        private void enter() throws IOException {
            int opened = openedStreams.incrementAndGet();
            if (releaseTimerStarted.compareAndSet(false, true)) {
                startReleaseTimer();
            }
            if (opened >= expectedStreams) {
                release.countDown();
            }
            try {
                if (!release.await(Math.max(5_000, gateMillis * 4L), TimeUnit.MILLISECONDS)) {
                    throw new IOException("timed out waiting for race gate release");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
        }

        private int openedStreams() {
            return openedStreams.get();
        }

        private void startReleaseTimer() {
            Thread releaseThread = new Thread(() -> {
                try {
                    Thread.sleep(gateMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    release.countDown();
                }
            }, "static-content-cache-race-gate");
            releaseThread.setDaemon(true);
            releaseThread.start();
        }
    }
}
