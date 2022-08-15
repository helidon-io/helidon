/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.http.HeadersServerRequest;
import io.helidon.common.http.HeadersServerResponse;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Header;
import io.helidon.common.http.Http.HeaderValues;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.nima.webserver.SimpleHandler;
import io.helidon.nima.webserver.http.HttpException;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

abstract class FileBasedContentHandler extends StaticContentHandler {
    private static final System.Logger LOGGER = System.getLogger(FileBasedContentHandler.class.getName());

    private final Map<String, MediaType> customMediaTypes;

    FileBasedContentHandler(StaticContentSupport.FileBasedBuilder<?> builder) {
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
     * Determines and set a Content-Type header based on filename extension.
     *
     * @param filename        a filename
     * @param requestHeaders  an HTTP request headers
     * @param responseHeaders an HTTP response headers
     */
    void processContentType(String filename,
                            HeadersServerRequest requestHeaders,
                            HeadersServerResponse responseHeaders) {
        // Try to get Content-Type
        responseHeaders.contentType(detectType(filename, requestHeaders));
    }

    Optional<MediaType> findCustomMediaType(String fileName) {
        int ind = fileName.lastIndexOf('.');

        if (ind < 0) {
            return Optional.empty();
        }

        String fileSuffix = fileName.substring(ind + 1);

        return Optional.ofNullable(customMediaTypes.get(fileSuffix));
    }

    void sendFile(Http.Method method,
                  Path pathParam,
                  ServerRequest request,
                  ServerResponse response,
                  String welcomePage)
            throws IOException {

        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            LOGGER.log(System.Logger.Level.TRACE, "Sending static content from file: " + pathParam);
        }

        Path path = pathParam;
        // we know the file exists, though it may be a directory
        //First doHandle a directory case
        if (Files.isDirectory(path)) {
            String rawFullPath = request.path().rawPath();
            if (rawFullPath.endsWith("/")) {
                // Try to found welcome file
                path = resolveWelcomeFile(path, welcomePage);
            } else {
                // Or redirect to slash ended
                redirect(request, response, rawFullPath + "/");
                return;
            }
        }

        // now it exists and is a file
        if (!Files.isRegularFile(path) || !Files.isReadable(path) || Files.isHidden(path)) {
            throw HttpException.builder()
                    .message("File is not accessible")
                    .type(SimpleHandler.EventType.OTHER)
                    .status(Http.Status.FORBIDDEN_403)
                    .build();
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
            processContentLength(path, response.headers());
            response.header(HeaderValues.ACCEPT_RANGES_BYTES)
                    .send();
        } else {
            send(request, response, path);
        }
    }

    void processContentLength(Path path, HeadersServerResponse headers) {
        headers.set(Header.CONTENT_LENGTH.withValue(String.valueOf(contentLength(path))));
    }

    long contentLength(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void send(ServerRequest request, ServerResponse response, Path path) throws IOException {
        HeadersServerRequest headers = request.headers();
        if (headers.contains(Header.RANGE)) {
            long contentLength = contentLength(path);
            List<ByteRangeRequest> ranges = ByteRangeRequest.parse(request,
                                                                   response,
                                                                   headers.get(Header.RANGE).values(),
                                                                   contentLength);
            if (ranges.size() == 1) {
                // single response
                ByteRangeRequest range = ranges.get(0);
                range.setContentRange(response);

                // only send a part of the file
                try (OutputStream out = response.outputStream(); SeekableByteChannel channel = Files.newByteChannel(path)) {
                    WritableByteChannel outChannel = Channels.newChannel(out);
                    channel.position(range.offset());
                    long toRead = range.length();
                    ByteBuffer buffer = ByteBuffer.allocate((int) Math.min(toRead, 1000));
                    while (toRead != 0) {
                        int read = channel.read(buffer);
                        int toWrite = (int) Math.min(toRead, read);
                        buffer.flip();
                        buffer.limit(toWrite);
                        outChannel.write(buffer);
                        buffer.flip();
                        toRead -= toWrite;
                    }
                }
            } else {
                // multipart response not yet supported, send all
                processContentLength(path, response.headers());
                // send the full file
                try (InputStream in = Files.newInputStream(path); OutputStream out = response.outputStream()) {
                    in.transferTo(out);
                }
            }
        } else {
            processContentLength(path, response.headers());
            // send the full file
            try (InputStream in = Files.newInputStream(path); OutputStream out = response.outputStream()) {
                in.transferTo(out);
            }
        }
    }

    /**
     * Find welcome file in provided directory or throw not found {@link io.helidon.nima.webserver.http.HttpException}.
     *
     * @param directory a directory to find in
     * @param name      welcome file name
     * @return a path of the welcome file
     * @throws io.helidon.nima.webserver.http.HttpException if welcome file doesn't exists
     */
    private static Path resolveWelcomeFile(Path directory, String name) {
        throwNotFoundIf(name == null || name.isEmpty());
        Path result = directory.resolve(name);
        throwNotFoundIf(!Files.exists(result));
        return result;
    }

    private MediaType detectType(String fileName, HeadersServerRequest requestHeaders) {
        Objects.requireNonNull(fileName);
        Objects.requireNonNull(requestHeaders);

        // first try to see if we have an override
        // then find if we have a detected type
        // then check the type is accepted by the request
        return findCustomMediaType(fileName)
                .or(() -> MediaTypes.detectType(fileName))
                .map(it -> {
                    if (requestHeaders.isAccepted(it)) {
                        return it;
                    }
                    throw HttpException.builder()
                            .message("Media type " + it + " is not accepted by request")
                            .type(SimpleHandler.EventType.OTHER)
                            .status(Http.Status.UNSUPPORTED_MEDIA_TYPE_415)
                            .build();
                })
                .orElseGet(() -> {
                    List<HttpMediaType> acceptedTypes = requestHeaders.acceptedTypes();
                    if (acceptedTypes.isEmpty()) {
                        return MediaTypes.APPLICATION_OCTET_STREAM;
                    } else {
                        return acceptedTypes.iterator().next().mediaType();
                    }
                });
    }

}
