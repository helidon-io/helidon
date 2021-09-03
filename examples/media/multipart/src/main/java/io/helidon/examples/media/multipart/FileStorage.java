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
package io.helidon.examples.media.multipart;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import io.helidon.webserver.BadRequestException;
import io.helidon.webserver.NotFoundException;

/**
 * Simple bean to managed a directory based storage.
 */
public class FileStorage {

    private final Path storageDir;

    /**
     * Create a new instance.
     */
    public FileStorage() {
        try {
            storageDir = Files.createTempDirectory("fileupload");
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Get the storage directory.
     *
     * @return directory
     */
    public Path storageDir() {
        return storageDir;
    }

    /**
     * Get the names of the files in the storage directory.
     *
     * @return Stream of file names
     */
    public Stream<String> listFiles() {
        try {
            return Files.walk(storageDir)
                        .filter(Files::isRegularFile)
                        .map(storageDir::relativize)
                        .map(java.nio.file.Path::toString);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Create a new file in the storage.
     *
     * @param fname file name
     * @return file
     * @throws BadRequestException if the resolved file is not contained in the storage directory
     */
    public Path create(String fname) {
        Path file = storageDir.resolve(fname);
        if (!file.getParent().equals(storageDir)) {
            throw new BadRequestException("Invalid file name");
        }
        try {
            Files.createFile(file);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return file;
    }

    /**
     * Lookup an existing file in the storage.
     *
     * @param fname file name
     * @return file
     * @throws NotFoundException   If the resolved file does not exist
     * @throws BadRequestException if the resolved file is not contained in the storage directory
     */
    public Path lookup(String fname) {
        Path file = storageDir.resolve(fname);
        if (!file.getParent().equals(storageDir)) {
            throw new BadRequestException("Invalid file name");
        }
        if (!Files.exists(file)) {
            throw new NotFoundException("file not found");
        }
        if (!Files.isRegularFile(file)) {
            throw new BadRequestException("Not a file");
        }
        return file;
    }
}
