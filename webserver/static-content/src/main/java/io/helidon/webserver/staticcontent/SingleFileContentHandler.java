/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.ForbiddenException;
import io.helidon.http.Method;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

class SingleFileContentHandler extends FileBasedContentHandler {
    private static final System.Logger LOGGER = System.getLogger(SingleFileContentHandler.class.getName());

    private final boolean cacheInMemory;
    private final Path path;
    // The configured file is pinned for the handler instance lifetime, including stop/start cycles.
    private final AtomicReference<Path> realPath = new AtomicReference<>();

    SingleFileContentHandler(FileSystemHandlerConfig config) {
        super(config);

        this.cacheInMemory = config.cachedFiles().contains(".") || config.cachedFiles().contains("/");
        this.path = config.location().toAbsolutePath().normalize();
    }

    @Override
    public void beforeStart() {
        try {
            Optional<Path> maybeResolvedPath = contentPath(path);
            if (cacheInMemory) {
                // directly cache in memory
                if (maybeResolvedPath.isPresent()) {
                    Path resolvedPath = maybeResolvedPath.get();
                    if (path.equals(resolvedPath)) {
                        Path secureRoot = Optional.ofNullable(realPath.get()).map(Path::getParent).orElse(null);
                        byte[] fileBytes = FileBasedContentHandler.readAllBytes(resolvedPath, false, secureRoot);
                        var contentType = detectType(fileName(path));
                        var lastModified = lastModified(resolvedPath, false, secureRoot);
                        if (lastModified.isPresent()) {
                            cacheInMemory(".", contentType, fileBytes, lastModified.get());
                        } else {
                            cacheInMemory(".", contentType, fileBytes);
                        }
                    } else {
                        LOGGER.log(System.Logger.Level.WARNING, "File " + path + " cannot be added to in memory cache,"
                                + " as it uses a symbolic link.");
                        cacheFileHandler();
                    }
                } else {
                    LOGGER.log(System.Logger.Level.WARNING, "File " + path + " cannot be added to in memory cache,"
                            + " as it does not exist or no longer matches the configured file.");
                    cacheFileHandler();
                }
            } else {
                // cache a handler that loads it from file system
                cacheFileHandler();
            }
        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.WARNING, "Failed to add file to in-memory cache, path: " + path, e);
            try {
                cacheFileHandler();
            } catch (ForbiddenException forbiddenException) {
                LOGGER.log(System.Logger.Level.WARNING,
                           "Failed to resolve static content file during startup: " + path,
                           forbiddenException);
            }
        } catch (ForbiddenException e) {
            LOGGER.log(System.Logger.Level.WARNING, "Failed to resolve static content file during startup: " + path, e);
        }
        super.beforeStart();
    }

    @Override
    boolean doHandle(Method method, String requestedPath, ServerRequest req, ServerResponse res, boolean mapped)
            throws IOException {
        if ("".equals(requestedPath) || "/".equals(requestedPath)) {
            String resource = ".";
            Optional<CachedHandler> cachedHandler = cacheHandler(resource);
            if (cachedHandler.isPresent()) {
                CachedHandler handler = selectSingleFileHandler(cachedHandler.get(), req);
                return handler.handle(handlerCache(), method, req, res, resource);
            }
            Optional<CachedHandlerPath> handler = cacheFileHandler();
            if (handler.isEmpty()) {
                return false;
            }
            CachedHandler selected = selectSingleFileHandler(handler.get(), req);
            return selected.handle(handlerCache(), method, req, res, resource);
        }

        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG, "Requested sub-path for a single file static content: " + requestedPath);
        }
        return false;
    }

    private Optional<CachedHandlerPath> cacheFileHandler() {
        try {
            Optional<Path> maybeResolvedPath = contentPath(path);
            if (maybeResolvedPath.isEmpty()) {
                return Optional.empty();
            }
            Path resolvedPath = maybeResolvedPath.get();
            CachedHandlerPath handler = CachedHandlerPath.create(path,
                                                                 resolvedPath,
                                                                 detectType(fileName(path)),
                                                                 false,
                                                                 resolvedPath.getParent());
            cacheHandler(".", handler);
            return Optional.of(handler);
        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.WARNING, "Failed to resolve static content file: " + path, e);
            return Optional.empty();
        }
    }

    private CachedHandler selectSingleFileHandler(CachedHandler identityHandler, ServerRequest request)
            throws IOException {
        String logicalFileName = fileName(path);
        try {
            return selectHandler(identityHandler, request, (coding, suffix) -> {
                Path pinnedPath = realPath.get();
                if (pinnedPath == null) {
                    Optional<Path> maybePinnedPath = contentPath(path);
                    if (maybePinnedPath.isEmpty()) {
                        return Optional.empty();
                    }
                    pinnedPath = maybePinnedPath.get();
                }
                Path sidecar = pinnedPath.resolveSibling(fileName(pinnedPath) + "." + suffix);
                Optional<Path> resolvedSidecar = sidecarPath(sidecar);
                if (resolvedSidecar.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(CachedHandlerPath.create(sidecar,
                                                            resolvedSidecar.get(),
                                                            detectType(logicalFileName),
                                                            false,
                                                            pinnedPath.getParent()));
            });
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    private Optional<Path> contentPath(Path path) {
        try {
            Path currentRealPath = path.toRealPath();
            Path pinnedRealPath = realPath.get();
            if (pinnedRealPath == null) {
                if (realPath.compareAndSet(null, currentRealPath)) {
                    pinnedRealPath = currentRealPath;
                } else {
                    pinnedRealPath = realPath.get();
                }
            }
            if (pinnedRealPath == null) {
                return Optional.empty();
            }
            if (currentRealPath.equals(pinnedRealPath)) {
                return Optional.of(currentRealPath);
            }
            return Optional.empty();
        } catch (IOException | SecurityException e) {
            return Optional.empty();
        }
    }

    private Optional<Path> sidecarPath(Path sidecar) {
        Path pinnedPath = realPath.get();
        if (pinnedPath == null || !Files.exists(sidecar)) {
            return Optional.empty();
        }

        try {
            Path resolvedSidecar = sidecar.toRealPath();
            if (resolvedSidecar.equals(sidecar.toAbsolutePath().normalize())) {
                return Optional.of(resolvedSidecar);
            }
            return Optional.empty();
        } catch (IOException | SecurityException e) {
            return Optional.empty();
        }
    }
}
