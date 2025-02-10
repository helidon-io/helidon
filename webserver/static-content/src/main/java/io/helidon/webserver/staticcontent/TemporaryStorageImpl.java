/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import io.helidon.Main;
import io.helidon.spi.HelidonShutdownHandler;

class TemporaryStorageImpl implements TemporaryStorage {
    private static final System.Logger LOGGER = System.getLogger(TemporaryStorage.class.getName());

    private final TemporaryStorageConfig config;
    private final Supplier<Optional<Path>> tmpFile;

    TemporaryStorageImpl(TemporaryStorageConfig config) {
        this.config = config;
        this.tmpFile = tempFileSupplier(config);
    }

    @Override
    public TemporaryStorageConfig prototype() {
        return config;
    }

    @Override
    public Optional<Path> createFile() {
        return tmpFile.get();
    }

    private static Supplier<Optional<Path>> tempFileSupplier(TemporaryStorageConfig config) {
        if (!config.enabled()) {
            return Optional::empty;
        }

        DeleteFilesHandler deleteFilesHandler = new DeleteFilesHandler();
        if (config.deleteOnExit()) {
            Main.addShutdownHandler(deleteFilesHandler);
        }
        var configuredDir = config.directory();

        IoSupplier<Path> pathSupplier;
        if (configuredDir.isPresent()) {
            pathSupplier = () -> Files.createTempFile(configuredDir.get(), config.filePrefix(), config.fileSuffix());
        } else {
            pathSupplier = () -> Files.createTempFile(config.filePrefix(), config.fileSuffix());
        }

        return () -> {
            deleteFilesHandler.tempFilesLock.lock();
            try {
                if (deleteFilesHandler.closed) {
                    // we are shutting down, cannot provide a temp file, as we would not delete it
                    return Optional.empty();
                }

                Path path = pathSupplier.get();
                deleteFilesHandler.tempFiles.add(path);
                return Optional.of(path);
            } catch (IOException e) {
                LOGGER.log(System.Logger.Level.WARNING, "Failed to create temporary file. Config: " + config, e);
                return Optional.empty();
            } finally {
                deleteFilesHandler.tempFilesLock.unlock();
            }
        };
    }

    private static class DeleteFilesHandler implements HelidonShutdownHandler {

        private final List<Path> tempFiles = new ArrayList<>();
        private final ReentrantLock tempFilesLock = new ReentrantLock();

        private volatile boolean closed;

        @Override
        public void shutdown() {
            tempFilesLock.lock();
            try {
                closed = true;
                for (Path tempFile : tempFiles) {
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (IOException e) {
                        LOGGER.log(System.Logger.Level.WARNING,
                                   "Failed to delete temporary file: " + tempFile.toAbsolutePath(),
                                   e);
                    }
                }

                tempFiles.clear();
            } finally {
                tempFilesLock.unlock();
            }
        }
    }
}
