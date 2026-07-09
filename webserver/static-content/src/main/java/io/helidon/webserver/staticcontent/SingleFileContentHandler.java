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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.Method;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

class SingleFileContentHandler extends FileBasedContentHandler {
    private static final System.Logger LOGGER = System.getLogger(SingleFileContentHandler.class.getName());

    private final boolean cacheInMemory;
    private final Path path;
    private final Path configuredParent;
    // The configured file is pinned for the handler instance lifetime, including stop/start cycles.
    private final AtomicReference<Path> realPath = new AtomicReference<>();
    // The configured parent is pinned separately for resolving sibling pre-compressed files.
    private final AtomicReference<Path> realParent = new AtomicReference<>();

    SingleFileContentHandler(FileSystemHandlerConfig config) {
        super(config);

        this.cacheInMemory = config.cachedFiles().contains(".") || config.cachedFiles().contains("/");
        this.path = config.location().toAbsolutePath().normalize();
        this.configuredParent = Objects.requireNonNull(path.getParent());
    }

    @Override
    public void beforeStart() {
        try {
            realParent();
            Optional<Path> maybeResolvedPath = contentPath(path);
            if (cacheInMemory) {
                // directly cache in memory
                if (maybeResolvedPath.isPresent()) {
                    Path resolvedPath = maybeResolvedPath.get();
                    if (path.equals(resolvedPath)) {
                        Path secureRoot = Optional.ofNullable(realPath.get()).map(Path::getParent).orElse(null);
                        byte[] fileBytes = FileBasedContentHandler.readAllBytes(resolvedPath, false, secureRoot);
                        cacheInMemory(".", detectType(fileName(path)), fileBytes, lastModified(resolvedPath, false, secureRoot));
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
            cacheFileHandler();
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
                CachedHandler identityHandler = cachedHandler.get();
                CachedHandler handler = selectSingleFileHandler(identityHandler, req);
                return handler.handle(handlerCache(), method, req, res, ".");
            }
            CachedHandler handler = cacheFileHandler();
            return selectSingleFileHandler(handler, req).handle(handlerCache(), method, req, res, ".");
        }

        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG, "Requested sub-path for a single file static content: " + requestedPath);
        }
        return false;
    }

    private CachedHandler cacheFileHandler() {
        CachedHandler handler = new CachedHandlerPath(path,
                                                      detectType(fileName(path)),
                                                      FileBasedContentHandler::lastModified,
                                                      ServerResponseHeaders::lastModified,
                                                      this::contentPath,
                                                      false,
                                                      it -> Optional.ofNullable(realPath.get()).map(Path::getParent));
        cacheHandler(".", handler);

        return handler;
    }

    private CachedHandler selectSingleFileHandler(CachedHandler identityHandler, ServerRequest request)
            throws IOException {
        String logicalFileName = fileName(path);
        try {
            return selectHandler(identityHandler, request, (coding, suffix) -> {
                Path sidecar = path.resolveSibling(logicalFileName + "." + suffix);
                if (sidecarPath(sidecar).isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(new CachedHandlerPath(sidecar,
                                                         detectType(logicalFileName),
                                                         FileBasedContentHandler::lastModified,
                                                         ServerResponseHeaders::lastModified,
                                                         this::sidecarPath,
                                                         false,
                                                         it -> Optional.ofNullable(realParent.get()),
                                                         ResponseRepresentation.encoded(coding)));
            });
        } catch (java.net.URISyntaxException e) {
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
        if (!sidecar.startsWith(configuredParent) || !Files.exists(sidecar)) {
            return Optional.empty();
        }

        try {
            Optional<Path> maybeRealParent = realParent();
            if (maybeRealParent.isEmpty()) {
                return Optional.empty();
            }
            Path currentRealParent = maybeRealParent.get();
            Path expectedPath = currentRealParent.resolve(configuredParent.relativize(sidecar)).normalize();
            Path expectedRealPath = expectedPath.toRealPath();
            Path resolvedSidecar = sidecar.toRealPath();
            if (expectedRealPath.startsWith(currentRealParent) && resolvedSidecar.equals(expectedRealPath)) {
                return Optional.of(resolvedSidecar);
            }
            return Optional.empty();
        } catch (IOException | SecurityException e) {
            return Optional.empty();
        }
    }

    private Optional<Path> realParent() {
        Path currentRealParent = realParent.get();
        if (currentRealParent != null) {
            return Optional.of(currentRealParent);
        }
        try {
            Path resolvedParent = configuredParent.toRealPath();
            if (realParent.compareAndSet(null, resolvedParent)) {
                return Optional.of(resolvedParent);
            }
            return Optional.ofNullable(realParent.get());
        } catch (IOException | SecurityException e) {
            return Optional.empty();
        }
    }
}
