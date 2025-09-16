/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.metadata;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

class MetadataFileImpl implements MetadataFile {
    private static final System.Logger LOGGER = System.getLogger(MetadataFileImpl.class.getName());

    private final String fileName;
    private final String location;
    private final Supplier<InputStream> inputStreamSupplier;
    private final String absolutePath;

    private MetadataFileImpl(String fileName, String location, String absolutePath, Supplier<InputStream> inputStreamSupplier) {
        this.fileName = fileName;
        this.location = location;
        this.absolutePath = absolutePath;
        this.inputStreamSupplier = inputStreamSupplier;
    }

    // create from classpath URL
    static MetadataFile create(String location, String fileName, URL resourceUrl) {
        String absolutePath = resourceUrl.getPath();
        return new MetadataFileImpl(fileName, location, absolutePath, () -> {
            try {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    LOGGER.log(System.Logger.Level.DEBUG,
                               "Open stream for resource: {0}, location: {1}, absolute location: {2}",
                               fileName, location, absolutePath);
                }
                return resourceUrl.openStream();
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to open input stream for URL: " + resourceUrl, e);
            }
        });
    }

    // create from file on "normal" file system
    static MetadataFile create(String location, String fileName, Path resourcepath) {
        String absoluteLocation = resourcepath.toAbsolutePath().normalize().toString();
        return new MetadataFileImpl(fileName, location, absoluteLocation, () -> {
            try {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    LOGGER.log(System.Logger.Level.DEBUG,
                               "Open stream for file: {0}, location: {1}, absolute location: {2}",
                               fileName, location, absoluteLocation);
                }
                return Files.newInputStream(resourcepath);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to open input stream for path: " + resourcepath, e);
            }
        });
    }

    // create from file on "zip" file system
    static MetadataFile create(Path zipFile, String location, String fileName, Path file) {
        String absoluteLocation = zipFile.toAbsolutePath().normalize().toString() + file.toAbsolutePath().normalize();

        return new MetadataFileImpl(fileName, location, absoluteLocation, () -> {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                           "Open stream for file: {0}, location: {1}, absolute location: {2}",
                           fileName, location, absoluteLocation);
            }
            try (var fs = FileSystems.newFileSystem(zipFile, Map.of())) {
                Path fsPath = fs.getPath(file.toString());
                // we cannot keep the file system open, so let's just read the bytes into memory
                byte[] bytes = Files.readAllBytes(fsPath);
                return new ByteArrayInputStream(bytes);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to open input stream for path: " + absoluteLocation, e);
            }
        });
    }

    @Override
    public String toString() {
        return "MetadatumImpl{"
                + "fileName='" + fileName + '\''
                + ", location='" + location + '\''
                + ", absolutePath='" + absolutePath + '\''
                + '}';
    }

    @Override
    public String absoluteLocation() {
        return absolutePath;
    }

    @Override
    public String fileName() {
        return fileName;
    }

    @Override
    public InputStream inputStream() {
        return inputStreamSupplier.get();
    }

    @Override
    public String location() {
        return location;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MetadataFileImpl that)) {
            return false;
        }
        return Objects.equals(fileName, that.fileName)
                && Objects.equals(location, that.location)
                && Objects.equals(absolutePath, that.absolutePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, location, absolutePath);
    }
}
