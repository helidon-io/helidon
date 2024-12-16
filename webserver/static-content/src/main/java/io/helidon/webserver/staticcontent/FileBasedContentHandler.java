/*
 * Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

abstract class FileBasedContentHandler extends StaticContentHandler {
    private final Map<String, MediaType> customMediaTypes;

    FileBasedContentHandler(BaseHandlerConfig config) {
        super(config);

        this.customMediaTypes = config.contentTypes();
    }

    static String fileName(Path path) {
        Path fileName = path.getFileName();

        if (null == fileName) {
            return "";
        }

        return fileName.toString();
    }

    static void processContentLength(Path path, ServerResponseHeaders headers) {
        headers.set(HeaderValues.create(HeaderNames.CONTENT_LENGTH, contentLength(path)));
    }

    static long contentLength(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void send(ServerRequest request, ServerResponse response, Path path) throws IOException {
        ServerRequestHeaders headers = request.headers();
        if (headers.contains(HeaderNames.RANGE)) {
            long contentLength = contentLength(path);
            List<ByteRangeRequest> ranges = ByteRangeRequest.parse(request,
                                                                   response,
                                                                   headers.get(HeaderNames.RANGE).values(),
                                                                   contentLength);
            if (ranges.size() == 1) {
                // single response
                ByteRangeRequest range = ranges.getFirst();
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

    Optional<MediaType> findCustomMediaType(String fileName) {
        int ind = fileName.lastIndexOf('.');

        if (ind < 0) {
            return Optional.empty();
        }

        String fileSuffix = fileName.substring(ind + 1);

        return Optional.ofNullable(customMediaTypes.get(fileSuffix));
    }

    Optional<CachedHandler> fileHandler(Path path) {
        // we know the file exists and is a file
        return Optional.of(new CachedHandlerPath(path,
                                                 detectType(fileName(path)),
                                                 FileBasedContentHandler::lastModified,
                                                 ServerResponseHeaders::lastModified));
    }

    MediaType detectType(String fileName) {
        Objects.requireNonNull(fileName);

        // first try to see if we have an override
        // then find if we have a detected type
        /*
        From HTTP/1.1 specification of status codes:
              Note: HTTP/1.1 servers are allowed to return responses which are
              not acceptable according to the accept headers sent in the
              request. In some cases, this may even be preferable to sending a
              406 response. User agents are encouraged to inspect the headers of
              an incoming response to determine if it is acceptable.
         The 415 we used before is for the case when request entity does not match the method, so wrong here
         If we cannot identify a media type, just use octet stream (just bytes....)
         */
        return findCustomMediaType(fileName)
                .or(() -> MediaTypes.detectType(fileName))
                .orElse(MediaTypes.APPLICATION_OCTET_STREAM);
    }

    static Optional<Instant> lastModified(Path path) throws IOException {
        if (Files.exists(path) && Files.isRegularFile(path)) {
            return Optional.of(Files.getLastModifiedTime(path).toInstant());
        }
        return Optional.empty();
    }

    /**
     * Find welcome file in provided directory or throw not found {@link io.helidon.http.RequestException}.
     *
     * @param directory a directory to find in
     * @param name      welcome file name
     * @return a path of the welcome file
     * @throws io.helidon.http.RequestException if welcome file doesn't exists
     */
    static Path resolveWelcomeFile(Path directory, String name) {
        throwNotFoundIf(name == null || name.isEmpty());
        Path result = directory.resolve(name);
        throwNotFoundIf(!Files.exists(result));
        return result;
    }

}
