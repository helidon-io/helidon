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
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import io.helidon.common.LruCache;
import io.helidon.common.media.type.MediaType;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.InternalServerException;
import io.helidon.http.Method;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

/**
 * Handles static content from the classpath.
 */
class ClassPathContentHandler extends FileBasedContentHandler {
    private static final System.Logger LOGGER = System.getLogger(ClassPathContentHandler.class.getName());

    private final AtomicBoolean populatedInMemoryCache = new AtomicBoolean();
    private final ClassLoader classLoader;
    private final String root;
    private final String rootWithTrailingSlash;
    private final Set<String> cacheInMemory;
    private final LruCache<String, ClassPathResource> resourceCache;
    private final TemporaryStorage tmpStorage;

    ClassPathContentHandler(ClasspathHandlerConfig config) {
        super(config, config.preCompressedCrossOriginSourcingEnabled());

        this.classLoader = config.classLoader()
                .or(() -> Optional.ofNullable(Thread.currentThread().getContextClassLoader()))
                .orElseGet(ClassPathContentHandler.class::getClassLoader);
        this.cacheInMemory = new HashSet<>(config.cachedFiles());
        this.root = cleanRoot(config.location());
        this.rootWithTrailingSlash = root + '/';
        this.resourceCache = config.recordCacheCapacity()
                .map(LruCache::<String, ClassPathResource>create)
                .orElseGet(LruCache::create);

        this.tmpStorage = config.temporaryStorage().orElseGet(TemporaryStorage::create);
    }

    static HttpService create(ClasspathHandlerConfig config) {
        if (config.singleFile()) {
            return new SingleFileClassPathContentHandler(config);
        }
        return new ClassPathContentHandler(config);
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
        resourceCache.clear();
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
        Optional<CachedHandler> cached = cacheHandler(requestedResource);

        if (cached.isPresent()) {
            CachedHandler cachedHandler = cached.get();
            if (cachedHandler instanceof CachedHandlerRedirect) {
                return cachedHandler.handle(handlerCache(), method, request, response, requestedResource);
            }
            CachedHandler handler = selectCachedClassPathHandler(requestedResource, rawPath, cachedHandler, request);
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
                        cacheResource(requestedResource, welcomeFileResource, welcomeUrl);
                        CachedHandler handler = selectClassPathHandler(welcomeFileResource,
                                                                       inMemoryMaybe.get(),
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

        CachedHandler cachedHandler = handler.get();
        cacheHandler(requestedResource, cachedHandler);
        cacheResource(requestedResource, logicalResource, url);

        CachedHandler selected = selectClassPathHandler(logicalResource, cachedHandler, request, url);
        return selected.handle(handlerCache(), method, request, response, requestedResource);
    }

    Optional<CachedHandler> cachedHandler(String requestedResource, URL url) throws IOException, URISyntaxException {
        return cachedHandler(requestedResource, url, fileName(url), ResponseRepresentation.plain());
    }

    Optional<CachedHandler> cachedHandler(String requestedResource,
                                          URL url,
                                          String logicalFileName,
                                          ResponseRepresentation representation) throws IOException, URISyntaxException {
        return switch (url.getProtocol()) {
        case "file" -> fileHandler(Paths.get(url.toURI()), logicalFileName, representation);
        case "jar" -> jarHandler(requestedResource, url, logicalFileName, representation);
        default -> urlStreamHandler(url, logicalFileName, representation);
        };
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

    void addToInMemoryCache(String requestedResource, URL url) throws IOException {
        // now we do have a resource, and we want to load it into memory
        // we are not checking the size, as this is explicitly configured by the user, and if we run out of memory, we just do...
        Optional<Instant> lastModified = lastModified(url);
        MediaType contentType = detectType(fileName(url));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream in = url.openStream()) {
            in.transferTo(baos);
        }
        byte[] entityBytes = baos.toByteArray();

        cacheInMemory(requestedResource, contentType, entityBytes, lastModified);
        cacheResource(requestedResource, requestedResource, url);
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
                                               String logicalFileName,
                                               ResponseRepresentation representation) throws IOException {
        JarURLConnection jarUrlConnection = (JarURLConnection) url.openConnection();
        JarEntry jarEntry = jarUrlConnection.getJarEntry();

        if (jarEntry.isDirectory()) {
            // we cannot cache this - as we consider this to be 404
            return Optional.empty();
        }

        var contentLength = jarEntry.getSize();
        var contentType = detectType(logicalFileName);
        Optional<Instant> lastModified;

        JarFile jarFile = jarUrlConnection.getJarFile();
        try {
            lastModified = lastModified(jarFile.getName());
        } finally {
            if (!jarUrlConnection.getUseCaches()) {
                jarFile.close();
            }
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
                                                        contentLength,
                                                        representation));
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
                                                              contentLength,
                                                              representation);

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
                                                             long contentLength,
                                                             ResponseRepresentation representation) {

        Header contentLengthHeader = HeaderValues.create(HeaderNames.CONTENT_LENGTH,
                                                         contentLength);
        return () -> {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream in = url.openStream()) {
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
                                             contentLengthHeader,
                                             representation);
        };
    }

    private Optional<CachedHandler> urlStreamHandler(URL url,
                                                     String logicalFileName,
                                                     ResponseRepresentation representation) {
        return Optional.of(new CachedHandlerUrlStream(detectType(logicalFileName), url, representation));
    }

    CachedHandler selectClassPathHandler(String logicalResource,
                                         CachedHandler identityHandler,
                                         ServerRequest request,
                                         URL identityUrl) throws IOException, URISyntaxException {
        String logicalFileName = fileName(logicalResource);
        return selectHandler(identityHandler, request, (coding, suffix) -> {
            String sidecarResource = logicalResource + "." + suffix;
            Enumeration<URL> sidecarUrls = classLoader.getResources(sidecarResource);
            while (sidecarUrls.hasMoreElements()) {
                URL sidecarUrl = sidecarUrls.nextElement();
                if (sameOrigin(logicalResource, identityUrl, sidecarResource, sidecarUrl, suffix)) {
                    // Sidecar bytes still use the shared in-memory cache so the memory limit remains global.
                    return cachedHandler(sidecarMemoryCacheKey(logicalResource, coding),
                                         sidecarUrl,
                                         logicalFileName,
                                         ResponseRepresentation.encoded(coding));
                }
            }
            return Optional.empty();
        });
    }

    CachedHandler selectCachedClassPathHandler(String requestedResource,
                                               String rawPath,
                                               CachedHandler identityHandler,
                                               ServerRequest request) throws IOException, URISyntaxException {
        ClassPathResource resource = resource(requestedResource, rawPath);
        return selectClassPathHandler(resource.logicalResource(), identityHandler, request, resource.identityUrl());
    }

    void cacheResource(String requestedResource, String logicalResource, URL identityUrl) {
        resourceCache.put(requestedResource, new ClassPathResource(logicalResource, identityUrl));
    }

    private ClassPathResource resource(String requestedResource, String rawPath) {
        Optional<ClassPathResource> cached = resourceCache.get(requestedResource);
        if (cached.isPresent()) {
            return cached.get();
        }

        ClassPathResource resolved = resolveResource(requestedResource, rawPath);
        resourceCache.put(requestedResource, resolved);
        return resolved;
    }

    private ClassPathResource resolveResource(String requestedResource, String rawPath) {
        URL identityUrl = classLoader.getResource(requestedResource);
        String logicalResource = requestedResource;
        String welcomeFileName = welcomePageName();
        if (welcomeFileName != null) {
            String welcomeFileResource = requestedResource
                    + (requestedResource.endsWith("/") ? "" : "/")
                    + welcomeFileName;
            URL welcomeUrl = classLoader.getResource(welcomeFileResource);
            if (welcomeUrl != null && rawPath.endsWith("/")) {
                identityUrl = welcomeUrl;
                logicalResource = welcomeFileResource;
            }
        }
        return new ClassPathResource(logicalResource, identityUrl);
    }

    private boolean sameOrigin(String logicalResource,
                               URL identityUrl,
                               String sidecarResource,
                               URL sidecarUrl,
                               String suffix) throws IOException, URISyntaxException {
        if (preCompressedCrossOriginSourcingEnabled()) {
            return true;
        }
        if (identityUrl == null || !identityUrl.getProtocol().equals(sidecarUrl.getProtocol())) {
            return false;
        }
        return switch (identityUrl.getProtocol()) {
        case "file" -> sameFileOrigin(identityUrl, sidecarUrl, suffix);
        case "jar" -> sameJarOrigin(logicalResource, identityUrl, sidecarResource, sidecarUrl);
        default -> false;
        };
    }

    private static boolean sameFileOrigin(URL identityUrl, URL sidecarUrl, String suffix) throws URISyntaxException {
        var identityPath = Paths.get(identityUrl.toURI()).toAbsolutePath().normalize();
        var expectedSidecar = identityPath.resolveSibling(identityPath.getFileName() + "." + suffix);
        var sidecarPath = Paths.get(sidecarUrl.toURI()).toAbsolutePath().normalize();
        return expectedSidecar.equals(sidecarPath);
    }

    private static boolean sameJarOrigin(String logicalResource,
                                         URL identityUrl,
                                         String sidecarResource,
                                         URL sidecarUrl) throws IOException {
        JarURLConnection identityConnection = (JarURLConnection) identityUrl.openConnection();
        JarURLConnection sidecarConnection = (JarURLConnection) sidecarUrl.openConnection();
        return Objects.equals(identityConnection.getJarFileURL(), sidecarConnection.getJarFileURL())
                && logicalResource.equals(identityConnection.getEntryName())
                && sidecarResource.equals(sidecarConnection.getEntryName());
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
        JarURLConnection jarUrlConnection = (JarURLConnection) url.openConnection();
        JarFile jarFile = jarUrlConnection.getJarFile();
        return lastModified(jarFile.getName());
    }

    private Optional<Instant> lastModified(String path) throws IOException {
        return lastModified(Paths.get(path));
    }

    private record ClassPathResource(String logicalResource, URL identityUrl) {
    }
}
