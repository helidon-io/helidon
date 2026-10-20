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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger.Level;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import io.helidon.common.media.type.MediaType;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.InternalServerException;
import io.helidon.http.Method;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

/**
 * Handles static content from the classpath.
 */
class ClassPathContentHandler extends FileBasedContentHandler {
    private static final int CACHE_LOCK_COUNT = 64;
    private static final System.Logger LOGGER = System.getLogger(ClassPathContentHandler.class.getName());

    private final AtomicBoolean populatedInMemoryCache = new AtomicBoolean();
    private final ReentrantLock[] cacheLocks = createCacheLocks();
    private final ClassLoader classLoader;
    private final String root;
    private final String rootWithTrailingSlash;
    private final Set<String> cacheInMemory;
    private final TemporaryStorage tmpStorage;

    ClassPathContentHandler(ClasspathHandlerConfig config) {
        super(config);

        this.classLoader = config.classLoader()
                .or(() -> Optional.ofNullable(Thread.currentThread().getContextClassLoader()))
                .orElseGet(ClassPathContentHandler.class::getClassLoader);
        this.cacheInMemory = new HashSet<>(config.cachedFiles());
        this.root = cleanRoot(config.location());
        this.rootWithTrailingSlash = root + '/';

        this.tmpStorage = config.temporaryStorage().orElseGet(TemporaryStorage::create);
    }

    @SuppressWarnings("removal") // will be replaced with HttpService once removed
    static StaticContentService create(ClasspathHandlerConfig config) {
        if (config.singleFile()) {
            return new SingleFileClassPathContentHandler(config);
        }
        return new ClassPathContentHandler(config);
    }

    static String cleanRoot(String location) {
        String cleanRoot = location;
        if (cleanRoot.startsWith("/")) {
            cleanRoot = cleanRoot.substring(1);
        }
        while (cleanRoot.endsWith("/")) {
            cleanRoot = cleanRoot.substring(0, cleanRoot.length() - 1);
        }

        if (cleanRoot.isEmpty()) {
            throw new IllegalArgumentException("Cannot serve full classpath, please configure a classpath prefix");
        }
        return cleanRoot;
    }

    @Override
    void populateCacheOnStart() {
        if (populatedInMemoryCache.compareAndSet(false, true)) {
            for (String resource : cacheInMemory) {
                try {
                    addToInMemoryCache(resource);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to add file to in-memory cache", e);
                }
            }
        }
        super.populateCacheOnStart();
    }

    @Override
    void releaseCache() {
        populatedInMemoryCache.set(false);
        super.releaseCache();
    }

    @Override
    boolean doHandle(Method method, String requestedPath, ServerRequest request, ServerResponse response, boolean mapped)
            throws IOException, URISyntaxException {

        String rawPath = request.prologue().uriPath().rawPath();
        String requestedResource = requestedResource(rawPath, requestedPath, mapped);

        if (!requestedResource.equals(root) && !requestedResource.startsWith(rootWithTrailingSlash)) {
            // trying to get path outside of project root (such as requesting ../../etc/hosts)
            return false;
        }

        Optional<CachedHandler> cached = acquireHandler(requestedResource);
        if (cached.isPresent()) {
            return handleAcquired(cached.get(), method, request, response, requestedResource);
        }

        ReentrantLock cacheLock = cacheLock(requestedResource);
        cacheLock.lock();
        try {
            cached = acquireHandler(requestedResource);
            if (cached.isEmpty()) {
                cached = findHandler(rawPath, requestedResource);
                if (cached.isPresent()) {
                    CachedHandler handler = cached.get();
                    handler.acquire();
                    cacheHandler(requestedResource, handler);
                }
            }
        } finally {
            cacheLock.unlock();
        }

        if (cached.isEmpty()) {
            return false;
        }
        return handleAcquired(cached.get(), method, request, response, requestedResource);
    }

    Optional<CachedHandler> cachedHandler(String requestedResource, URL url) throws IOException, URISyntaxException {
        return switch (url.getProtocol()) {
        case "file" -> fileHandler(Paths.get(url.toURI()));
        case "jar" -> jarHandler(requestedResource, url);
        default -> urlStreamHandler(url);
        };
    }

    void addToInMemoryCache(String requestedResource, URL url) throws IOException {
        // now we do have a resource, and we want to load it into memory
        // we are not checking the size, as this is explicitly configured by the user, and if we run out of memory, we just do...
        Optional<Instant> lastModified = lastModified(url);
        MediaType contentType = detectType(fileName(url));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream in = ResourceConnections.openStream(url)) {
            in.transferTo(baos);
        }
        byte[] entityBytes = baos.toByteArray();

        cacheInMemory(requestedResource, contentType, entityBytes, lastModified);
    }

    private static ReentrantLock[] createCacheLocks() {
        ReentrantLock[] locks = new ReentrantLock[CACHE_LOCK_COUNT];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new ReentrantLock();
        }
        return locks;
    }

    private static String fileName(URL url) {
        String path = url.getPath();
        int index = path.lastIndexOf('/');
        if (index > -1) {
            return path.substring(index + 1);
        }

        return path;
    }

    private Optional<CachedHandler> findHandler(String rawPath, String requestedResource)
            throws IOException, URISyntaxException {
        // try to find the resource on classpath (cannot use root URL and then resolve, as root and sub-resource
        // may be from different jar files/directories
        URL url = classLoader.getResource(requestedResource);

        String welcomeFileName = welcomePageName();
        if (welcomeFileName != null) {
            String welcomeFileResource = requestedResource
                    + (requestedResource.endsWith("/") ? "" : "/")
                    + welcomeFileName;
            URL welcomeUrl = classLoader.getResource(welcomeFileResource);
            if (welcomeUrl != null) {
                // there is a welcome file under requested resource, ergo requested resource was a directory
                if (rawPath.endsWith("/")) {
                    // this is OK, as the path ends with a forward slash

                    // first check if this is an in-memory resource
                    Optional<CachedHandlerInMemory> inMemoryMaybe = cacheInMemory(welcomeFileResource);
                    if (inMemoryMaybe.isPresent()) {
                        // reference to the same definition, never times out
                        cacheInMemory(requestedResource, inMemoryMaybe.get());
                        return Optional.of(inMemoryMaybe.get());
                    }

                    url = welcomeUrl;
                } else {
                    // must redirect
                    String redirectLocation = rawPath + "/";
                    return Optional.of(new CachedHandlerRedirect(redirectLocation));
                }
            }
        }

        if (url == null || url.getPath().endsWith("/")) {
            if (LOGGER.isLoggable(Level.TRACE)) {
                LOGGER.log(Level.TRACE, "Requested resource " + requestedResource
                        + " does not exist or is a directory without welcome file.");
            }
            // not caching 404, to prevent intentional cache pollution by users
            return Optional.empty();
        }

        if (LOGGER.isLoggable(Level.TRACE)) {
            LOGGER.log(Level.TRACE, "Located resource url. Resource: " + requestedResource + ", URL: " + url);
        }

        // now read the URL - we have direct support for files and jar files, others are handled by stream only
        return cachedHandler(requestedResource, url);
    }

    private String requestedResource(String rawPath, String requestedPath, boolean mapped) throws URISyntaxException {
        String resource = requestedPath.isEmpty() || "/".equals(requestedPath) ? root : (rootWithTrailingSlash + requestedPath);

        if (LOGGER.isLoggable(Level.TRACE)) {
            LOGGER.log(Level.TRACE, "Requested class path resource: " + resource);
        }

        // this MUST be done, so we do not escape the bounds of configured directory
        // We use multi-arg constructor so it performs url encoding
        URI myuri = new URI(null, null, resource, null);

        String result = myuri.normalize().getPath();
        if (mapped) {
            return result;
        }
        return rawPath.endsWith("/") ? result + "/" : result;
    }

    private ReentrantLock cacheLock(String resource) {
        return cacheLocks[Math.floorMod(resource.hashCode(), cacheLocks.length)];
    }

    private Optional<CachedHandler> jarHandler(String requestedResource, URL url) throws IOException {
        JarURLConnection jarUrlConnection = ResourceConnections.openJarConnection(url);
        long contentLength;
        var contentType = detectType(fileName(url));
        Optional<Instant> lastModified;

        try (JarFile jarFile = jarUrlConnection.getJarFile()) {
            JarEntry jarEntry = jarUrlConnection.getJarEntry();
            if (jarEntry.isDirectory()) {
                // we cannot cache this - as we consider this to be 404
                return Optional.empty();
            }
            contentLength = jarEntry.getSize();
            lastModified = lastModified(jarFile.getName());
        }

        var lastModifiedHandler = lastModifiedHandler(lastModified);

        /*
        We have all the information we need to process a jar file
        Now we have two options:
        1. The file will be cached in memory
        2. The file will be handled through CachedHandlerJar (and possibly extracted to a temporary directory)
         */
        if (contentLength <= Integer.MAX_VALUE && canCacheInMemory((int) contentLength)) {
            // we may be able to cache this entry
            var cached = cacheInMemory(requestedResource,
                                       (int) contentLength,
                                       inMemorySupplier(url,
                                                        lastModified.orElse(null),
                                                        lastModifiedHandler,
                                                        contentType,
                                                        contentLength));
            if (cached.isPresent()) {
                // we have successfully cached the entry in memory
                return Optional.of(cached.get());
            }
        }

        // cannot cache in memory (too big file, cache full)
        CachedHandlerJar jarHandler = CachedHandlerJar.create(tmpStorage,
                                                              url,
                                                              lastModified.orElse(null),
                                                              contentType,
                                                              contentLength);

        return Optional.of(jarHandler);
    }

    private BiConsumer<ServerResponseHeaders, Instant> lastModifiedHandler(Optional<Instant> lastModified) {
        if (lastModified.isPresent()) {
            Header lastModifiedHeader = HeaderValues.create(HeaderNames.LAST_MODIFIED,
                                                            true,
                                                            false,
                                                            formatLastModified(lastModified.get()));
            return (headers, instant) -> headers.set(lastModifiedHeader);
        } else {
            return (headers, instant) -> {
            };
        }
    }

    private Supplier<CachedHandlerInMemory> inMemorySupplier(URL url,
                                                             Instant lastModified,
                                                             BiConsumer<ServerResponseHeaders, Instant> lastModifiedHandler,
                                                             MediaType contentType,
                                                             long contentLength) {

        Header contentLengthHeader = HeaderValues.create(HeaderNames.CONTENT_LENGTH,
                                                         contentLength);
        return () -> {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream in = ResourceConnections.openStream(url)) {
                in.transferTo(baos);
            } catch (IOException e) {
                throw new InternalServerException("Cannot load resource", e);
            }
            byte[] bytes = baos.toByteArray();
            return new CachedHandlerInMemory(contentType,
                                             lastModified,
                                             lastModifiedHandler,
                                             bytes,
                                             bytes.length,
                                             contentLengthHeader);
        };
    }

    private Optional<CachedHandler> urlStreamHandler(URL url) {
        return Optional.of(new CachedHandlerUrlStream(detectType(fileName(url)), url));
    }

    private void addToInMemoryCache(String resource) throws IOException {
        /*
          we need to know:
          - content size
          - media type
          - last modified timestamp
          - content
         */

        String requestedResource;
        try {
            requestedResource = requestedResource("", resource, true);
        } catch (URISyntaxException e) {
            LOGGER.log(Level.WARNING, "Resource " + resource + " cannot be added to in memory cache, as it is not a valid"
                    + " identifier", e);
            return;
        }

        if (!requestedResource.equals(root) && !requestedResource.startsWith(rootWithTrailingSlash)) {
            LOGGER.log(Level.WARNING, "Resource " + resource + " cannot be added to in memory cache, as it is not within"
                    + " the resource root directory.");
            return;
        }

        URL url = classLoader.getResource(requestedResource);
        if (url == null) {
            LOGGER.log(Level.WARNING, "Resource " + resource + " cannot be added to in memory cache, as it does "
                    + "not exist on classpath");
            return;
        }

        addToInMemoryCache(requestedResource, url);
    }

    private Optional<Instant> lastModified(URL url) {
        try {
            return switch (url.getProtocol()) {
                case "file" -> lastModified(Paths.get(url.toURI()));
                case "jar" -> lastModifiedFromJar(url);
                default -> Optional.empty();
            };
        } catch (IOException | URISyntaxException e) {
            if (LOGGER.isLoggable(Level.TRACE)) {
                LOGGER.log(Level.TRACE, "Failed to get last modification of a file for URL: " + url, e);
            }
            return Optional.empty();
        }
    }

    private Optional<Instant> lastModifiedFromJar(URL url) throws IOException {
        JarURLConnection jarUrlConnection = ResourceConnections.openJarConnection(url);
        try (JarFile jarFile = jarUrlConnection.getJarFile()) {
            return lastModified(jarFile.getName());
        }
    }

    private Optional<Instant> lastModified(String path) throws IOException {
        return lastModified(Paths.get(path));
    }
}
