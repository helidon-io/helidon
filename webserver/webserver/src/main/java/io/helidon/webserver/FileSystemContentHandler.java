/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.logging.Logger;

import io.helidon.common.http.Http;

/**
 * Serves files from the filesystem as a static WEB content.
 */
class FileSystemContentHandler extends StaticContentHandler {
    private static final Logger LOGGER = Logger.getLogger(FileSystemContentHandler.class.getName());
    private final Path root;

    FileSystemContentHandler(String welcomeFilename, ContentTypeSelector contentTypeSelector, Path root) {
        super(welcomeFilename, contentTypeSelector);

        this.root = root.toAbsolutePath().normalize();
    }

    public static StaticContentHandler create(String welcomeFileName, ContentTypeSelector selector, Path fsRoot) {
        if (Files.exists(fsRoot) && Files.isDirectory(fsRoot)) {
            return new FileSystemContentHandler(welcomeFileName, selector, fsRoot);
        } else {
            throw new IllegalArgumentException("Cannot create file system static content, path "
                                                       + fsRoot.toAbsolutePath()
                                                       + " does not exist or is not a directory");
        }
    }

    @Override
    boolean doHandle(Http.RequestMethod method, String requestedPath, ServerRequest request, ServerResponse response)
            throws IOException {
        Path resolved;
        if (requestedPath.isEmpty()) {
            resolved = root;
        } else {
            resolved = root.resolve(Paths.get(requestedPath)).normalize();
            LOGGER.finest(() -> "Requested file: " + resolved.toAbsolutePath());
            if (!resolved.startsWith(root)) {
                return false;
            }
        }

        return doHandle(method, resolved, request, response);
    }

    boolean doHandle(Http.RequestMethod method, Path path, ServerRequest request, ServerResponse response) throws IOException {
        // Check existence
        if (!Files.exists(path)) {
            return false;
        }

        //First doHandle a directory case
        if (Files.isDirectory(path)) {
            String rawFullPath = request.uri().getRawPath();
            if (rawFullPath.endsWith("/")) {
                // Try to found welcome file
                path = findWelcomeFile(path);
            } else {
                // Or redirect to slash ended
                redirect(response, rawFullPath + "/");
                return true;
            }
        }

        sendFile(method, path, request, response, contentTypeSelector());

        return true;
    }

    static void sendFile(Http.RequestMethod method,
                         Path path,
                         ServerRequest request,
                         ServerResponse response,
                         ContentTypeSelector contentTypeSelector)
            throws IOException {
        if (!Files.isRegularFile(path) || !Files.isReadable(path) || Files.isHidden(path)) {
            throw new HttpException("File is not accessible", Http.Status.FORBIDDEN_403);
        }

        // Caching headers support
        try {
            Instant lastMod = Files.getLastModifiedTime(path).toInstant();
            processEtag(String.valueOf(lastMod.toEpochMilli()), request.headers(), response.headers());
            processModifyHeaders(lastMod, request.headers(), response.headers());
        } catch (IOException | SecurityException e) {
            // Cannot get mod time or size - well, we cannot tell if it was modified or not. Don't support cache headers
        }

        processContentType(fileName(path), request.headers(), response.headers(), contentTypeSelector);
        if (method == Http.Method.HEAD) {
            response.send();
        } else {
            response.send(path);
        }
    }

    /**
     * Find welcome file in provided directory or throw not found {@link HttpException}.
     *
     * @param directory a directory to find in
     * @return a path of the welcome file
     * @throws HttpException if welcome file doesn't exists
     */
    private Path findWelcomeFile(Path directory) {
        String name = welcomePageName();
        throwNotFoundIf(name == null || name.isEmpty());
        Path result = directory.resolve(name);
        throwNotFoundIf(!Files.exists(result));
        return result;
    }
}
