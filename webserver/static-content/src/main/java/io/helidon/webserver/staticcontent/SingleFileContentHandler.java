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
import java.nio.file.Path;
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
                return cachedHandler.get().handle(handlerCache(), method, req, res, resource);
            }
            return cacheFileHandler().handle(handlerCache(), method, req, res, ".");
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
}
