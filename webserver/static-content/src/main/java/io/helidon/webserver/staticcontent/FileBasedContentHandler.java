/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpException;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

abstract class FileBasedContentHandler extends StaticContentHandler {
    private static final OpenOption[] READ_OPTIONS = {StandardOpenOption.READ};
    private static final OpenOption[] READ_NOFOLLOW_OPTIONS = {StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS};
    private static final LinkOption[] NOFOLLOW_LINK_OPTIONS = {LinkOption.NOFOLLOW_LINKS};
    private static final Set<OpenOption> READ_NOFOLLOW_SET = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);

    private final Map<String, MediaType> customMediaTypes;

    FileBasedContentHandler(BaseHandlerConfig config) {
        super(config);

        this.customMediaTypes = config.contentTypes();
    }

    FileBasedContentHandler(BaseHandlerConfig config, boolean preCompressedCrossOriginSourcingEnabled) {
        super(config, preCompressedCrossOriginSourcingEnabled);

        this.customMediaTypes = config.contentTypes();
    }

    static String fileName(Path path) {
        Path fileName = path.getFileName();

        if (null == fileName) {
            return "";
        }

        return fileName.toString();
    }

    static byte[] readAllBytes(Path path, boolean followLinks, Path secureRoot) throws IOException {
        try (SeekableByteChannel channel = newByteChannel(path, followLinks, secureRoot);
                InputStream in = Channels.newInputStream(channel)) {
            return in.readAllBytes();
        }
    }

    static void processContentLength(long contentLength, ServerResponseHeaders headers) {
        headers.set(HeaderValues.create(HeaderNames.CONTENT_LENGTH, contentLength));
    }

    static void send(ServerRequest request,
                     ServerResponse response,
                     SeekableByteChannel channel,
                     ResponseRepresentation representation) throws IOException {
        if (representation.runtimeEncoded()) {
            sendRuntimeEncoded(response, channel, representation);
            return;
        }

        ServerRequestHeaders headers = request.headers();
        long contentLength = channel.size();
        if (headers.contains(HeaderNames.RANGE)) {
            List<ByteRangeRequest> ranges;
            try {
                ranges = ByteRangeRequest.parse(request,
                                                response,
                                                headers.get(HeaderNames.RANGE).values(),
                                                contentLength);
            } catch (HttpException e) {
                representation.apply(e);
                throw e;
            }
            if (ranges.size() == 1) {
                // single response
                ByteRangeRequest range = ranges.getFirst();

                // only send a part of the file
                representation.apply(response);
                range.setContentRange(response);
                try (OutputStream out = response.outputStream()) {
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
                // send the full file
                representation.apply(response);
                processContentLength(contentLength, response.headers());
                channel.position(0);
                try (InputStream in = Channels.newInputStream(channel);
                        OutputStream out = response.outputStream()) {
                    in.transferTo(out);
                }
            }
        } else {
            // send the full file
            representation.apply(response);
            processContentLength(contentLength, response.headers());
            channel.position(0);
            try (InputStream in = Channels.newInputStream(channel);
                    OutputStream out = response.outputStream()) {
                in.transferTo(out);
            }
        }
    }

    private static void sendRuntimeEncoded(ServerResponse response,
                                           SeekableByteChannel channel,
                                           ResponseRepresentation representation) throws IOException {
        channel.position(0);
        try (InputStream in = Channels.newInputStream(channel)) {
            representation.apply(response);
            try (OutputStream out = representation.outputStream(response.outputStream())) {
                in.transferTo(out);
            }
        }
    }

    static SeekableByteChannel newByteChannel(Path path, boolean followLinks, Path secureRoot) throws IOException {
        if (secureRoot == null) {
            OpenOption[] options = followLinks ? READ_OPTIONS : READ_NOFOLLOW_OPTIONS;
            return Files.newByteChannel(path, options);
        }

        Path relative = relativeToSecureRoot(path, secureRoot);
        validateSecureRoot(secureRoot);
        try (DirectoryStream<Path> directory = Files.newDirectoryStream(secureRoot)) {
            if (directory instanceof SecureDirectoryStream<Path> secureDirectory) {
                List<SecureDirectoryStream<Path>> openedDirectories = new ArrayList<>();
                SecureDirectoryStream<Path> currentDirectory = secureDirectory;
                try {
                    int nameCount = relative.getNameCount();
                    for (int i = 0; i < nameCount - 1; i++) {
                        SecureDirectoryStream<Path> child = currentDirectory.newDirectoryStream(relative.getName(i),
                                                                                                LinkOption.NOFOLLOW_LINKS);
                        openedDirectories.add(child);
                        currentDirectory = child;
                    }
                    return currentDirectory.newByteChannel(relative.getFileName(), READ_NOFOLLOW_SET);
                } finally {
                    for (int i = openedDirectories.size() - 1; i >= 0; i--) {
                        openedDirectories.get(i).close();
                    }
                }
            }
        }

        Path fallbackPath = validatedFallbackPath(path, secureRoot);
        try {
            return Files.newByteChannel(fallbackPath, READ_NOFOLLOW_OPTIONS);
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            return Files.newByteChannel(fallbackPath, READ_OPTIONS);
        }
    }

    static BasicFileAttributes attributes(Path path, boolean followLinks, Path secureRoot) throws IOException {
        if (secureRoot == null) {
            if (followLinks) {
                return Files.readAttributes(path, BasicFileAttributes.class);
            }
            return Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW_LINK_OPTIONS);
        }

        Path relative = relativeToSecureRoot(path, secureRoot);
        validateSecureRoot(secureRoot);
        try (DirectoryStream<Path> directory = Files.newDirectoryStream(secureRoot)) {
            if (directory instanceof SecureDirectoryStream<Path> secureDirectory) {
                List<SecureDirectoryStream<Path>> openedDirectories = new ArrayList<>();
                SecureDirectoryStream<Path> currentDirectory = secureDirectory;
                try {
                    int nameCount = relative.getNameCount();
                    for (int i = 0; i < nameCount - 1; i++) {
                        SecureDirectoryStream<Path> child = currentDirectory.newDirectoryStream(relative.getName(i),
                                                                                                LinkOption.NOFOLLOW_LINKS);
                        openedDirectories.add(child);
                        currentDirectory = child;
                    }
                    BasicFileAttributeView view = currentDirectory.getFileAttributeView(relative.getFileName(),
                                                                                       BasicFileAttributeView.class,
                                                                                       LinkOption.NOFOLLOW_LINKS);
                    return view.readAttributes();
                } finally {
                    for (int i = openedDirectories.size() - 1; i >= 0; i--) {
                        openedDirectories.get(i).close();
                    }
                }
            }
        }

        Path fallbackPath = validatedFallbackPath(path, secureRoot);
        try {
            return Files.readAttributes(fallbackPath, BasicFileAttributes.class, NOFOLLOW_LINK_OPTIONS);
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            return Files.readAttributes(fallbackPath, BasicFileAttributes.class);
        }
    }

    private static void validateSecureRoot(Path secureRoot) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(secureRoot,
                                                              BasicFileAttributes.class,
                                                              NOFOLLOW_LINK_OPTIONS);
        if (!attributes.isDirectory()) {
            throw new NoSuchFileException(secureRoot.toString());
        }
    }

    private static Path relativeToSecureRoot(Path path, Path secureRoot) throws IOException {
        if (!path.startsWith(secureRoot)) {
            throw new NoSuchFileException(path.toString());
        }

        Path relative = secureRoot.relativize(path);
        if (relative.getNameCount() == 0) {
            throw new NoSuchFileException(path.toString());
        }
        return relative;
    }

    private static Path validatedFallbackPath(Path path, Path secureRoot) throws IOException {
        Path realPath = path.toRealPath();
        if (!realPath.startsWith(secureRoot)) {
            throw new NoSuchFileException(path.toString());
        }
        return realPath;
    }

    Optional<MediaType> findCustomMediaType(String fileName) {
        int ind = fileName.lastIndexOf('.');

        if (ind < 0) {
            return Optional.empty();
        }

        String fileSuffix = fileName.substring(ind + 1);

        return Optional.ofNullable(customMediaTypes.get(fileSuffix));
    }

    Optional<CachedHandler> fileHandler(Path path, String logicalFileName, ResponseRepresentation representation) {
        // we know the file exists and is a file
        return Optional.of(new CachedHandlerPath(path,
                                                 detectType(logicalFileName),
                                                 FileBasedContentHandler::lastModified,
                                                 ServerResponseHeaders::lastModified,
                                                 Optional::of,
                                                 true,
                                                 it -> Optional.empty(),
                                                 representation));
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
        return lastModified(path, true, null);
    }

    static Optional<Instant> lastModified(Path path, boolean followLinks, Path secureRoot) throws IOException {
        BasicFileAttributes attributes = attributes(path, followLinks, secureRoot);
        if (attributes.isRegularFile()) {
            return Optional.of(attributes.lastModifiedTime().toInstant());
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
