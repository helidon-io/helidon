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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import io.helidon.common.LruCache;
import io.helidon.common.media.type.MediaType;
import io.helidon.http.InternalServerException;
import io.helidon.http.Method;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

/**
 * Handles static content from the classpath.
 */
class ClassPathContentHandler extends FileBasedContentHandler {
    private static final System.Logger LOGGER = System.getLogger(ClassPathContentHandler.class.getName());

    private final AtomicBoolean populatedInMemoryCache = new AtomicBoolean();
    private final Map<String, CachedClassPathHandler> inMemoryHandlers = new ConcurrentHashMap<>();
    private final ClassLoader classLoader;
    private final String root;
    private final String rootWithTrailingSlash;
    private final Set<String> cacheInMemory;
    private final TemporaryStorage tmpStorage;

    ClassPathContentHandler(ClasspathHandlerConfig config) {
        super(config, config.preCompressedCrossOriginSourcingEnabled().orElse(false));

        this.classLoader = config.classLoader()
                .or(() -> Optional.ofNullable(Thread.currentThread().getContextClassLoader()))
                .orElseGet(ClassPathContentHandler.class::getClassLoader);
        this.cacheInMemory = new HashSet<>(config.cachedFiles());
        this.root = cleanRoot(config.location());
        this.rootWithTrailingSlash = root + '/';

        this.tmpStorage = config.temporaryStorage().orElseGet(TemporaryStorage::create);
    }

    static HttpService create(ClasspathHandlerConfig config) {
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

    static boolean sameJarOrigin(String logicalResource,
                                 URL identityUrl,
                                 String sidecarResource,
                                 URL sidecarUrl) throws IOException, URISyntaxException {
        JarURLConnection identityConnection = ResourceConnections.openJarConnection(identityUrl);
        JarURLConnection sidecarConnection = ResourceConnections.openJarConnection(sidecarUrl);
        return identityConnection.getJarFileURL().toURI().equals(sidecarConnection.getJarFileURL().toURI())
                && logicalResource.equals(identityConnection.getEntryName())
                && sidecarResource.equals(sidecarConnection.getEntryName());
    }

    @Override
    public void beforeStart() {
        if (populatedInMemoryCache.compareAndSet(false, true)) {
            for (String resource : cacheInMemory) {
                try {
                    addToInMemoryCache(resource);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to add file to in-memory cache", e);
                }
            }
        }
        super.beforeStart();
    }

    @Override
    void releaseCache() {
        populatedInMemoryCache.set(false);
        inMemoryHandlers.clear();
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

        // we have a resource that we support, let's try to use one from the cache
        Optional<CachedHandler> cached = cachedClassPathHandler(requestedResource);
        if (cached.isPresent()) {
            CachedHandler cachedRecord = cached.get();
            if (cachedRecord instanceof CachedHandlerRedirect) {
                return cachedRecord.handle(handlerCache(), method, request, response, requestedResource);
            }
            CachedHandler handler = selectCachedClassPathHandler(requestedResource, cachedRecord, request);
            // this requested resource is cached and can be safely returned
            return handler.handle(handlerCache(), method, request, response, requestedResource);
        }

        // if it is not cached, find the resource and cache it (or return 404 and do not cache)

        // try to find the resource on classpath (cannot use root URL and then resolve, as root and sub-resource
        // may be from different jar files/directories
        URL url = classLoader.getResource(requestedResource);
        String logicalResource = requestedResource;

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
                        CachedHandler cachedHandler = cacheClassPathHandler(requestedResource,
                                                                            welcomeFileResource,
                                                                            welcomeUrl,
                                                                            inMemoryMaybe.get());
                        CachedHandler handler = selectClassPathHandler(welcomeFileResource,
                                                                       cachedHandler,
                                                                       request,
                                                                       welcomeUrl);
                        return handler.handle(handlerCache(), method, request, response, requestedResource);
                    }

                    url = welcomeUrl;
                    logicalResource = welcomeFileResource;
                } else {
                    // must redirect
                    String redirectLocation = rawPath + "/";
                    CachedHandlerRedirect handler = new CachedHandlerRedirect(redirectLocation);
                    cacheHandler(requestedResource, handler);
                    return handler.handle(handlerCache(), method, request, response, requestedResource);
                }
            }
        }

        if (url == null || url.getPath().endsWith("/")) {
            if (LOGGER.isLoggable(Level.TRACE)) {
                LOGGER.log(Level.TRACE, "Requested resource " + requestedResource
                        + " does not exist or is a directory without welcome file.");
            }
            // not caching 404, to prevent intentional cache pollution by users
            return false;
        }

        if (LOGGER.isLoggable(Level.TRACE)) {
            LOGGER.log(Level.TRACE, "Located resource url. Resource: " + requestedResource + ", URL: " + url);
        }

        // now read the URL - we have direct support for files and jar files, others are handled by stream only
        Optional<CachedHandler> handler = cachedHandler(logicalResource, url);

        if (handler.isEmpty()) {
            return false;
        }

        CachedHandler cachedHandler = cacheClassPathHandler(requestedResource, logicalResource, url, handler.get());

        CachedHandler selected = selectClassPathHandler(logicalResource, cachedHandler, request, url);
        return selected.handle(handlerCache(), method, request, response, requestedResource);
    }

    Optional<CachedHandler> cachedHandler(String requestedResource, URL url) throws IOException, URISyntaxException {
        return cachedHandler(requestedResource, url, fileName(url), false);
    }

    Optional<CachedHandler> cachedHandler(String requestedResource,
                                          URL url,
                                          String logicalFileName,
                                          boolean sidecar) throws IOException, URISyntaxException {
        return switch (url.getProtocol()) {
        case "file" -> {
            Path path = Paths.get(url.toURI());
            if (!sidecar || preCompressedCrossOriginSourcingEnabled()) {
                yield fileHandler(path, logicalFileName);
            }
            Path secureRoot = path.getParent();
            if (secureRoot == null) {
                yield Optional.empty();
            }
            Optional<Path> resolvedPath = exactPath(path);
            if (resolvedPath.isEmpty()) {
                yield Optional.empty();
            }
            yield Optional.of(CachedHandlerPath.create(path,
                                                       resolvedPath.get(),
                                                       detectType(logicalFileName),
                                                       false,
                                                       secureRoot));
        }
        case "jar" -> jarHandler(requestedResource, url, logicalFileName);
        default -> urlStreamHandler(url, logicalFileName);
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

        CachedHandlerInMemory handler;
        if (lastModified.isPresent()) {
            handler = cacheInMemory(requestedResource, contentType, entityBytes, lastModified.get());
        } else {
            handler = cacheInMemory(requestedResource, contentType, entityBytes);
        }
        cacheClassPathHandler(requestedResource, requestedResource, url, handler);
    }

    CachedHandler selectClassPathHandler(String logicalResource,
                                         CachedHandler identityHandler,
                                         ServerRequest request,
                                         URL identityUrl) throws IOException, URISyntaxException {
        String logicalFileName = fileName(logicalResource);
        return selectHandler(identityHandler, request, (coding, suffix) -> {
            String sidecarResource = logicalResource + "." + suffix;
            String sidecarCacheKey = sidecarMemoryCacheKey(logicalResource, coding);
            Enumeration<URL> sidecarUrls = classLoader.getResources(sidecarResource);
            while (sidecarUrls.hasMoreElements()) {
                URL sidecarUrl = sidecarUrls.nextElement();
                Optional<URL> trustedSidecarUrl = sameOrigin(logicalResource,
                                                            identityUrl,
                                                            sidecarResource,
                                                            sidecarUrl,
                                                            suffix);
                if (trustedSidecarUrl.isPresent()) {
                    // Sidecar bytes still use the shared in-memory cache so the memory limit remains global.
                    Optional<CachedHandlerInMemory> cached = cacheInMemory(sidecarCacheKey);
                    if (cached.isPresent()) {
                        return Optional.of(cached.get());
                    }
                    return cachedHandler(sidecarCacheKey, trustedSidecarUrl.get(), logicalFileName, true);
                }
            }
            return Optional.empty();
        });
    }

    CachedHandler selectCachedClassPathHandler(String requestedResource,
                                               CachedHandler identityHandler,
                                               ServerRequest request) throws IOException, URISyntaxException {
        if (identityHandler instanceof CachedClassPathHandler cachedHandler) {
            return selectClassPathHandler(cachedHandler.logicalResource(),
                                          cachedHandler,
                                          request,
                                          cachedHandler.identityUrl());
        }
        throw new IllegalStateException("Cached classpath handler is missing resource metadata: " + requestedResource);
    }

    CachedHandler cacheClassPathHandler(String requestedResource,
                                        String logicalResource,
                                        URL identityUrl,
                                        CachedHandler delegate) {
        CachedClassPathHandler handler = new CachedClassPathHandler(delegate, logicalResource, identityUrl);
        if (delegate instanceof CachedHandlerInMemory) {
            inMemoryHandlers.put(requestedResource, handler);
        } else {
            cacheHandler(requestedResource, handler);
        }
        return handler;
    }

    Optional<CachedHandler> cachedClassPathHandler(String requestedResource) {
        CachedHandler inMemoryHandler = inMemoryHandlers.get(requestedResource);
        return inMemoryHandler == null ? handlerCache().get(requestedResource) : Optional.of(inMemoryHandler);
    }

    private static String fileName(URL url) {
        String path = url.getPath();
        int index = path.lastIndexOf('/');
        if (index > -1) {
            return path.substring(index + 1);
        }

        return path;
    }

    private static String fileName(String resource) {
        int index = resource.lastIndexOf('/');
        if (index > -1) {
            return resource.substring(index + 1);
        }
        return resource;
    }

    private static Optional<URL> sameFileOrigin(URL identityUrl,
                                                URL sidecarUrl,
                                                String suffix) throws IOException, URISyntaxException {
        var identityPath = Paths.get(identityUrl.toURI()).toRealPath();
        var expectedSidecar = identityPath.resolveSibling(identityPath.getFileName() + "." + suffix);
        var sidecarPath = Paths.get(sidecarUrl.toURI()).toRealPath();
        if (expectedSidecar.equals(sidecarPath)) {
            return Optional.of(expectedSidecar.toUri().toURL());
        }
        return Optional.empty();
    }

    private static Optional<Path> exactPath(Path path) {
        try {
            Path realPath = path.toRealPath();
            return realPath.equals(path) ? Optional.of(realPath) : Optional.empty();
        } catch (IOException | SecurityException e) {
            return Optional.empty();
        }
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

    private Optional<CachedHandler> jarHandler(String requestedResource,
                                               URL url,
                                               String logicalFileName) throws IOException {
        JarURLConnection jarUrlConnection = ResourceConnections.openJarConnection(url);

        long contentLength;
        var contentType = detectType(logicalFileName);
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

        /*
        We have all the information we need to process a jar file
        Now we have two options:
        1. The file will be cached in memory
        2. The file will be handled through CachedHandlerJar (and possibly extracted to a temporary directory)
         */
        if (contentLength <= Integer.MAX_VALUE && canCacheInMemory((int) contentLength)) {
            // we may be able to cache this entry
            Optional<CachedHandlerInMemory> cached;
            try {
                cached = cacheInMemory(requestedResource,
                                       (int) contentLength,
                                       inMemorySupplier(url,
                                                        lastModified.orElse(null),
                                                        contentType,
                                                        contentLength));
            } catch (InternalServerException e) {
                if (e.getCause() instanceof IOException ioException) {
                    throw ioException;
                }
                throw e;
            }
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

    private Supplier<CachedHandlerInMemory> inMemorySupplier(URL url,
                                                             Instant lastModified,
                                                             MediaType contentType,
                                                             long contentLength) {
        return () -> {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream in = ResourceConnections.openStream(url)) {
                in.transferTo(baos);
            } catch (IOException e) {
                throw new InternalServerException("Cannot load resource", e);
            }
            byte[] bytes = baos.toByteArray();
            return new CachedHandlerInMemory(StaticContentMetadata.create(contentType,
                                                                           lastModified,
                                                                           contentLength),
                                             bytes);
        };
    }

    private Optional<CachedHandler> urlStreamHandler(URL url,
                                                     String logicalFileName) throws IOException {
        return Optional.of(CachedHandlerUrlStream.create(detectType(logicalFileName), url));
    }

    private Optional<URL> sameOrigin(String logicalResource,
                                     URL identityUrl,
                                     String sidecarResource,
                                     URL sidecarUrl,
                                     String suffix) throws IOException, URISyntaxException {
        if (preCompressedCrossOriginSourcingEnabled()) {
            return Optional.of(sidecarUrl);
        }
        if (identityUrl == null || !identityUrl.getProtocol().equals(sidecarUrl.getProtocol())) {
            return Optional.empty();
        }
        return switch (identityUrl.getProtocol()) {
        case "file" -> sameFileOrigin(identityUrl, sidecarUrl, suffix);
        case "jar" -> sameJarOrigin(logicalResource, identityUrl, sidecarResource, sidecarUrl)
                ? Optional.of(sidecarUrl)
                : Optional.empty();
        default -> Optional.empty();
        };
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

    record CachedClassPathHandler(CachedHandler delegate,
                                  String logicalResource,
                                  URL identityUrl) implements CachedHandler {
        @Override
        public Optional<PreparedContent> prepare(LruCache<String, CachedHandler> cache,
                                                 String requestedResource) throws IOException {
            return delegate.prepare(cache, requestedResource);
        }

        @Override
        public Optional<PreparedContent> prepareSidecar(SidecarCache sidecarCache,
                                                        String coding,
                                                        LruCache<String, CachedHandler> cache,
                                                        String requestedResource) throws IOException {
            return delegate.prepareSidecar(sidecarCache, coding, cache, requestedResource);
        }

        @Override
        public boolean available() throws IOException {
            return delegate.available();
        }

        @Override
        public SidecarCache sidecarCache() {
            return delegate.sidecarCache();
        }
    }
}
