/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.webserver.staticcontent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import io.helidon.common.http.Http;
import io.helidon.common.http.ServerResponseHeaders;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

class SingleFileContentHandler extends FileBasedContentHandler {
    private static final System.Logger LOGGER = System.getLogger(SingleFileContentHandler.class.getName());

    private final boolean cacheInMemory;
    private final Path path;

    SingleFileContentHandler(FileSystemBuilder builder) {
        super(builder);

        this.cacheInMemory = builder.cacheInMemory().contains(".") || builder.cacheInMemory().contains("/");
        this.path = builder.root().toAbsolutePath().normalize();
    }

    @Override
    public void beforeStart() {
        try {
            if (cacheInMemory) {
                // directly cache in memory
                byte[] fileBytes = Files.readAllBytes(path);
                cacheInMemory(".", detectType(fileName(path)), fileBytes, lastModified(path));
            } else {
                // cache a handler that loads it from file system
                cacheFileHandler();
            }
        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.WARNING, "Failed to add file to in-memory cache, path: " + path, e);
        }
        super.beforeStart();
    }

    @Override
    boolean doHandle(Http.Method method, String requestedPath, ServerRequest req, ServerResponse res, boolean mapped)
            throws IOException {
        if ("".equals(requestedPath) || "/".equals(requestedPath)) {
            Optional<CachedHandler> cachedHandler = cacheHandler(".");
            if (cachedHandler.isPresent()) {
                return cachedHandler.get().handle(handlerCache(), method, req, res, requestedPath);
            }
            return doHandle(method, req, res);
        }

        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG, "Requested sub-path for a single file static content: " + requestedPath);
        }
        return false;
    }

    private boolean doHandle(Http.Method method, ServerRequest req, ServerResponse res) throws IOException {
        return cacheFileHandler().handle(handlerCache(), method, req, res, ".");
    }

    private CachedHandler cacheFileHandler() {
        CachedHandler handler = new CachedHandlerPath(path,
                                                      detectType(fileName(path)),
                                                      FileBasedContentHandler::lastModified,
                                                      ServerResponseHeaders::lastModified);
        cacheHandler(".", handler);

        return handler;
    }
}
