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
import java.time.Instant;

import io.helidon.common.http.Http;

/**
 * Serves files from the filesystem as a static WEB content.
 */
class FileSystemContentHandler extends StaticContentHandler {

    FileSystemContentHandler(String welcomeFilename, ContentTypeSelector contentTypeSelector, Path root) {
        super(welcomeFilename, contentTypeSelector, root);
    }

    @Override
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

        processContentType(path, request.headers(), response.headers());
        if (method == Http.Method.HEAD) {
            response.send();
        } else {
            response.send(path);
        }
        return true;
    }

    /**
     * Find welcome file in provided directory or throw not found {@link HttpException}.
     *
     * @param directory a directory to find in
     * @return a path of the welcome file
     * @throws HttpException if welcome file doesn't exists
     */
    private Path findWelcomeFile(Path directory) {
        String name = getWelcomePageName();
        throwNotFoundIf(name == null || name.isEmpty());
        Path result = directory.resolve(name);
        throwNotFoundIf(!Files.exists(result));
        return result;
    }
}
