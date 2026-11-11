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
import java.io.InputStream;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.LruCache;
import io.helidon.common.media.type.MediaType;

/**
 * Handles a jar file entry.
 * The entry may be extracted into a temporary file (optional).
 */
class CachedHandlerJar implements CachedHandler {
    private static final System.Logger LOGGER = System.getLogger(CachedHandlerJar.class.getName());

    private final StaticContentMetadata metadata;
    private final ReentrantLock lifecycleLock = new ReentrantLock();
    private final TemporaryStorage temporaryStorage;
    private final Path path;
    private final URL url;
    private final SidecarCache sidecarCache;

    private int users;
    private boolean disposed;

    private CachedHandlerJar(TemporaryStorage temporaryStorage,
                             StaticContentMetadata metadata,
                             URL url,
                             Path path,
                             SidecarCache sidecarCache) {
        this.temporaryStorage = temporaryStorage;
        this.metadata = metadata;
        this.url = url;
        this.path = path;
        this.sidecarCache = sidecarCache;
    }

    static CachedHandlerJar create(TemporaryStorage tmpStorage,
                                   URL fileUrl,
                                   Instant lastModified,
                                   MediaType mediaType,
                                   long contentLength) {
        SidecarCache sidecarCache = SidecarCache.create();
        var createdTmpFile = tmpStorage.createFile();
        if (createdTmpFile.isPresent()) {
            Path tmpFile = createdTmpFile.get();
            try (InputStream is = ResourceConnections.openStream(fileUrl)) {
                long extractedLength = Files.copy(is, tmpFile, StandardCopyOption.REPLACE_EXISTING);
                if (contentLength < 0 || extractedLength == contentLength) {
                    return new CachedHandlerJar(tmpStorage,
                                                StaticContentMetadata.create(mediaType,
                                                                             lastModified,
                                                                             extractedLength),
                                                fileUrl,
                                                tmpFile,
                                                sidecarCache);
                }
            } catch (IOException e) {
                LOGGER.log(Level.TRACE, "Failed to create temporary extracted file for " + fileUrl, e);
            }
            deleteTemporaryFile(tmpStorage, tmpFile);
        }
        return new CachedHandlerJar(tmpStorage,
                                    StaticContentMetadata.create(mediaType, lastModified, contentLength),
                                    fileUrl,
                                    null,
                                    sidecarCache);
    }

    @Override
    public void acquire() {
        CachedHandler.super.acquire();
        lifecycleLock.lock();
        try {
            users++;
        } finally {
            lifecycleLock.unlock();
        }
    }

    @Override
    public void release() {
        boolean delete;
        lifecycleLock.lock();
        try {
            users--;
            delete = disposed && users == 0;
        } finally {
            lifecycleLock.unlock();
        }
        if (delete && path != null) {
            deleteTemporaryFile(temporaryStorage, path);
        }
        CachedHandler.super.release();
    }

    @Override
    public void dispose() {
        boolean delete;
        lifecycleLock.lock();
        try {
            delete = !disposed && users == 0;
            disposed = true;
        } finally {
            lifecycleLock.unlock();
        }
        if (delete && path != null) {
            deleteTemporaryFile(temporaryStorage, path);
        }
        CachedHandler.super.dispose();
    }

    @Override
    public Optional<PreparedContent> prepare(LruCache<String, CachedHandler> cache,
                                             String requestedResource) throws IOException {
        if (LOGGER.isLoggable(Level.TRACE)) {
            LOGGER.log(Level.TRACE, "Sending static content from jar: " + requestedResource);
        }

        IoSupplier<PreparedContent.Body> bodySource = () -> {
            if (path != null && Files.isRegularFile(path)) {
                try {
                    return PreparedContent.channel(Files.newByteChannel(path));
                } catch (IOException e) {
                    if (LOGGER.isLoggable(Level.TRACE)) {
                        LOGGER.log(Level.TRACE, "Failed to open jar entry from extracted path: " + path
                                           + ", will send directly from jar",
                                   e);
                    }
                }
            }
            return PreparedContent.stream(ResourceConnections.openStream(url));
        };

        return Optional.of(new PreparedContent(metadata,
                                               null,
                                               bodySource));
    }

    @Override
    public SidecarCache sidecarCache() {
        return sidecarCache;
    }

    private static void deleteTemporaryFile(TemporaryStorage temporaryStorage, Path path) {
        if (temporaryStorage instanceof TemporaryStorageImpl storage) {
            storage.deleteFile(path);
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to delete temporary file: " + path.toAbsolutePath(), e);
        }
    }
}
