/*
 * Copyright (c) 2017, 2024 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.http.Method;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

/**
 * Serves files from the filesystem as a static WEB content.
 */
class FileSystemContentHandler extends FileBasedContentHandler {
    private static final System.Logger LOGGER = System.getLogger(FileSystemContentHandler.class.getName());

    private final AtomicBoolean populatedInMemoryCache = new AtomicBoolean();
    private final Path root;
    private final Set<String> cacheInMemory;

    FileSystemContentHandler(FileSystemHandlerConfig config) {
        super(config);

        this.root = config.location().toAbsolutePath().normalize();
        this.cacheInMemory = config.cachedFiles();
    }

    @SuppressWarnings("removal")
    static StaticContentService create(FileSystemHandlerConfig config) {
        Path location = config.location();
        if (Files.isDirectory(location)) {
            return new FileSystemContentHandler(config);
        } else {
            return new SingleFileContentHandler(config);
        }
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
    }

    @Override
    boolean doHandle(Method method, String requestedPath, ServerRequest req, ServerResponse res, boolean mapped)
            throws IOException {
        Path path = requestedPath(requestedPath);
        if (LOGGER.isLoggable(Level.DEBUG)) {
            LOGGER.log(Level.DEBUG, "Requested file: " + path.toAbsolutePath());
        }
        if (!path.startsWith(root)) {
            return false;
        }

        String rawPath = req.prologue().uriPath().rawPath();

        String relativePath = root.relativize(path).toString().replace("\\", "/");
        String requestedResource;
        if (mapped) {
            requestedResource = relativePath;
        } else {
            requestedResource = rawPath.endsWith("/") ? relativePath + "/" : relativePath;
        }

        // we have a resource that we support, let's try to use one from the cache
        Optional<CachedHandler> cached = cacheHandler(requestedResource);

        if (cached.isPresent()) {
            // this requested resource is cached and can be safely returned
            return cached.get().handle(handlerCache(), method, req, res, requestedResource);
        }

        // if it is not cached, find the resource and cache it (or return 404 and do not cache)
        return doHandle(method, requestedResource, req, res, rawPath, path);
    }

    boolean doHandle(Method method,
                     String requestedResource,
                     ServerRequest req,
                     ServerResponse res,
                     String rawPath,
                     Path path) throws IOException {

        // Check existence
        if (!Files.exists(path)) {
            // not caching 404
            return false;
        }

        // we know the file exists, though it may be a directory
        // First doHandle a directory case
        String welcomeFileName = welcomePageName();
        if (welcomeFileName != null) {
            if (Files.isDirectory(path)) {
                String welcomeFileResource = requestedResource
                        + (requestedResource.endsWith("/") ? "" : "/")
                        + welcomeFileName;

                if (rawPath.endsWith("/")) {
                    Optional<CachedHandlerInMemory> inMemoryMaybe = cacheInMemory(welcomeFileResource);
                    if (inMemoryMaybe.isPresent()) {
                        // reference to the same definition, never times out
                        cacheInMemory(requestedResource, inMemoryMaybe.get());
                        return inMemoryMaybe.get().handle(handlerCache(),
                                                          method,
                                                          req,
                                                          res,
                                                          requestedResource);
                    }

                    // Try to find welcome file
                    path = resolveWelcomeFile(path, welcomePageName());
                } else {
                    // Or redirect to slash ended
                    String redirectLocation = rawPath + "/";
                    CachedHandlerRedirect handler = new CachedHandlerRedirect(redirectLocation);
                    cacheHandler(requestedResource, handler);
                    return handler.handle(handlerCache(), method, req, res, requestedResource);
                }
            }
        }

        CachedHandler handler = new CachedHandlerPath(path,
                                                      detectType(fileName(path)),
                                                      FileBasedContentHandler::lastModified,
                                                      ServerResponseHeaders::lastModified);
        cacheHandler(requestedResource, handler);
        return handler.handle(handlerCache(), method, req, res, requestedResource);
    }

    private void addToInMemoryCache(String resource) throws IOException {
        /*
          we need to know:
          - content size
          - media type
          - last modified timestamp
          - content
         */
        Path path = requestedPath(resource);
        if (!path.startsWith(root)) {
            LOGGER.log(Level.WARNING, "File " + resource + " cannot be added to in memory cache, as it is not within"
                    + " the root directory.");
            return;
        }

        if (!Files.exists(path)) {
            LOGGER.log(Level.WARNING, "File " + resource + " cannot be added to in memory cache, as it does not exist");
            return;
        }

        if (Files.isDirectory(path)) {
            try (var paths = Files.newDirectoryStream(path)) {
                paths.forEach(child -> {
                    if (!Files.isDirectory(child)) {
                        // we need to use forward slash even on Windows
                        String childResource = root.relativize(child).toString().replace('\\', '/');
                        try {
                            addToInMemoryCache(childResource, child);
                        } catch (IOException e) {
                            LOGGER.log(Level.WARNING, "File " + child + " cannot be added to in memory cache", e);
                        }
                    }
                });
            }
        } else {
            addToInMemoryCache(resource, path);
        }
    }

    private void addToInMemoryCache(String resource, Path path) throws IOException {
        byte[] fileBytes = Files.readAllBytes(path);
        cacheInMemory(resource, detectType(fileName(path)), fileBytes, lastModified(path));
    }

    private Path requestedPath(String requestedPath) {
        if (requestedPath.isEmpty()) {
            return root;
        }
        return root.resolve(requestedPath).toAbsolutePath().normalize();
    }
}
