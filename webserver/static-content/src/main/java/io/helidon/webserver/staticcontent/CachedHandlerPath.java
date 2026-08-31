/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import io.helidon.common.LruCache;
import io.helidon.common.media.type.MediaType;
import io.helidon.http.ForbiddenException;

record CachedHandlerPath(Path sourcePath,
                         Path path,
                         StaticContentMetadata metadata,
                         boolean followLinks,
                         Path secureRoot,
                         Object fileKey,
                         SidecarCache sidecarCache) implements CachedHandler {
    private static final System.Logger LOGGER = System.getLogger(CachedHandlerPath.class.getName());

    static CachedHandlerPath create(Path path,
                                    Path resolvedPath,
                                    MediaType mediaType,
                                    boolean followLinks,
                                    Path secureRoot) throws IOException {
        BasicFileAttributes attributes;
        try {
            attributes = FileBasedContentHandler.attributes(resolvedPath, followLinks, secureRoot);
        } catch (IOException e) {
            throw new ForbiddenException("File is not accessible", e);
        }
        if (!attributes.isRegularFile()
                || !Files.isReadable(resolvedPath)
                || Files.isHidden(path)
                || Files.isHidden(resolvedPath)) {
            throw new ForbiddenException("File is not accessible");
        }

        Instant modified = attributes.lastModifiedTime().toInstant();
        return new CachedHandlerPath(path,
                                     resolvedPath,
                                     StaticContentMetadata.create(mediaType, modified, attributes.size()),
                                     followLinks,
                                     secureRoot,
                                     attributes.fileKey(),
                                     SidecarCache.create());
    }

    @Override
    public Optional<PreparedContent> prepare(LruCache<String, CachedHandler> cache,
                                             String requestedResource) throws IOException {
        return prepare(cache::remove, requestedResource, false);
    }

    @Override
    public Optional<PreparedContent> prepareSidecar(SidecarCache sidecarCache,
                                                    String coding,
                                                    LruCache<String, CachedHandler> cache,
                                                    String requestedResource) throws IOException {
        return prepare(_ -> sidecarCache.remove(coding), requestedResource, true);
    }

    @Override
    public boolean available() {
        if (!Files.exists(sourcePath)) {
            return false;
        }
        try {
            BasicFileAttributes attributes = FileBasedContentHandler.attributes(path, followLinks, secureRoot);
            if (!attributes.isRegularFile()
                    || !Files.isReadable(path)
                    || Files.isHidden(sourcePath)
                    || Files.isHidden(path)) {
                throw new ForbiddenException("File is not accessible");
            }
            return true;
        } catch (IOException e) {
            throw new ForbiddenException("File is not accessible", e);
        }
    }

    @Override
    public SidecarCache sidecarCache() {
        return sidecarCache;
    }

    private Optional<PreparedContent> prepare(Consumer<String> invalidate,
                                              String requestedResource,
                                              boolean validateSnapshot) throws IOException {
        if (!Files.exists(sourcePath)) {
            invalidate.accept(requestedResource);
            return Optional.empty();
        }

        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            LOGGER.log(System.Logger.Level.TRACE, "Sending static content from path: " + path);
        }

        try {
            BasicFileAttributes attributes = FileBasedContentHandler.attributes(path, followLinks, secureRoot);
            if (!attributes.isRegularFile()
                    || !Files.isReadable(path)
                    || Files.isHidden(sourcePath)
                    || Files.isHidden(path)) {
                invalidate.accept(requestedResource);
                throw new ForbiddenException("File is not accessible");
            }
            if (validateSnapshot && !matchesSnapshot(attributes)) {
                invalidate.accept(requestedResource);
                return Optional.empty();
            }
        } catch (IOException e) {
            invalidate.accept(requestedResource);
            throw new ForbiddenException("File is not accessible", e);
        }

        IoSupplier<PreparedContent.Body> bodySource = () -> {
            SeekableByteChannel channel = null;
            try {
                channel = FileBasedContentHandler.newByteChannel(path, followLinks, secureRoot);
                if (validateSnapshot
                        && !matchesSnapshot(FileBasedContentHandler.attributes(path, followLinks, secureRoot))) {
                    throw new IOException("Static content changed before its body was opened");
                }
                return PreparedContent.channel(channel);
            } catch (IOException e) {
                closeAfterFailure(channel, e);
                invalidate.accept(requestedResource);
                throw new ForbiddenException("File is not accessible", e);
            } catch (RuntimeException | Error e) {
                closeAfterFailure(channel, e);
                invalidate.accept(requestedResource);
                throw e;
            }
        };

        return Optional.of(new PreparedContent(metadata,
                                               null,
                                               bodySource));
    }

    private static void closeAfterFailure(SeekableByteChannel channel, Throwable failure) {
        if (channel == null) {
            return;
        }
        try {
            channel.close();
        } catch (IOException e) {
            failure.addSuppressed(e);
        }
    }

    private boolean matchesSnapshot(BasicFileAttributes attributes) {
        return attributes.size() == metadata.contentLength()
                && attributes.lastModifiedTime().toInstant().equals(metadata.lastModified())
                && Objects.equals(attributes.fileKey(), fileKey);
    }
}
