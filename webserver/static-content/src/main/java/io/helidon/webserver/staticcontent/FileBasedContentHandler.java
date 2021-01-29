/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.media.common.DefaultMediaSupport;
import io.helidon.media.common.MessageBodyWriter;
import io.helidon.webserver.HttpException;
import io.helidon.webserver.RequestHeaders;
import io.helidon.webserver.ResponseHeaders;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

abstract class FileBasedContentHandler extends StaticContentHandler {
    private static final Logger LOGGER = Logger.getLogger(FileBasedContentHandler.class.getName());
    private static final MessageBodyWriter<Path> PATH_WRITER = DefaultMediaSupport.pathWriter();

    private final Map<String, MediaType> customMediaTypes;

    FileBasedContentHandler(StaticContentSupport.Builder builder) {
        super(builder);

        this.customMediaTypes = builder.specificContentTypes();
    }

    static String fileName(Path path) {
        Path fileName = path.getFileName();

        if (null == fileName) {
            return "";
        }

        return fileName.toString();
    }

    /**
     * Find welcome file in provided directory or throw not found {@link io.helidon.webserver.HttpException}.
     *
     * @param directory a directory to find in
     * @param name welcome file name
     * @return a path of the welcome file
     * @throws io.helidon.webserver.HttpException if welcome file doesn't exists
     */
    private static Path resolveWelcomeFile(Path directory, String name) {
        throwNotFoundIf(name == null || name.isEmpty());
        Path result = directory.resolve(name);
        throwNotFoundIf(!Files.exists(result));
        return result;
    }

    /**
     * Determines and set a Content-Type header based on filename extension.
     *
     * @param filename        a filename
     * @param requestHeaders  an HTTP request headers
     * @param responseHeaders an HTTP response headers
     */
    void processContentType(String filename,
                            RequestHeaders requestHeaders,
                            ResponseHeaders responseHeaders) {
        // Try to get Content-Type
        responseHeaders.contentType(detectType(filename, requestHeaders));
    }

    private MediaType detectType(String fileName, RequestHeaders requestHeaders) {
        Objects.requireNonNull(fileName);
        Objects.requireNonNull(requestHeaders);

        // first try to see if we have an override
        // then find if we have a detected type
        // then check the type is accepted by the request
        return findCustomMediaType(fileName)
                .or(() -> MediaTypes.detectType(fileName)
                        .map(MediaType::parse))
                .map(it -> {
                    if (requestHeaders.isAccepted(it)) {
                        return it;
                    }
                    throw new HttpException("Media type " + it + " is not accepted by request",
                                            Http.Status.UNSUPPORTED_MEDIA_TYPE_415);
                })
                .orElseGet(() -> {
                    List<MediaType> acceptedTypes = requestHeaders.acceptedTypes();
                    if (acceptedTypes.isEmpty()) {
                        return MediaType.APPLICATION_OCTET_STREAM;
                    } else {
                        return acceptedTypes.iterator().next();
                    }
                });
    }

    Optional<MediaType> findCustomMediaType(String fileName) {
        int ind = fileName.lastIndexOf('.');

        if (ind < 0) {
            return Optional.empty();
        }

        String fileSuffix = fileName.substring(ind + 1);

        return Optional.ofNullable(customMediaTypes.get(fileSuffix));
    }

    void sendFile(Http.RequestMethod method,
                  Path pathParam,
                  ServerRequest request,
                  ServerResponse response,
                  String welcomePage)
            throws IOException {

        LOGGER.fine(() -> "Sending static content from file: " + pathParam);

        Path path = pathParam;
        // we know the file exists, though it may be a directory
        //First doHandle a directory case
        if (Files.isDirectory(path)) {
            String rawFullPath = request.uri().getRawPath();
            if (rawFullPath.endsWith("/")) {
                // Try to found welcome file
                path = resolveWelcomeFile(path, welcomePage);
            } else {
                // Or redirect to slash ended
                redirect(response, rawFullPath + "/");
                return;
            }
        }

        // now it exists and is a file
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

        processContentType(fileName(path), request.headers(), response.headers());
        if (method == Http.Method.HEAD) {
            response.send();
        } else {
            send(response, path);
        }
    }

    void send(ServerResponse response, Path path) {
        response.send(PATH_WRITER.marshall(path));
    }

}
