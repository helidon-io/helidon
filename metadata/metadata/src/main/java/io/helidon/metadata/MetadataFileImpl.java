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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

class MetadataFileImpl implements MetadataFile {
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

    static MetadataFile create(String location, String fileName, URL resourceUrl) {
        return new MetadataFileImpl(fileName, location, resourceUrl.getPath(), () -> {
            try {
                return resourceUrl.openStream();
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to open input stream for URL: " + resourceUrl, e);
            }
        });
    }

    static MetadataFile create(String location, String fileName, Path resourcepath) {
        return new MetadataFileImpl(fileName, location, resourcepath.toAbsolutePath().normalize().toString(), () -> {
            try {
                return Files.newInputStream(resourcepath);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to open input stream for path: " + resourcepath, e);
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
