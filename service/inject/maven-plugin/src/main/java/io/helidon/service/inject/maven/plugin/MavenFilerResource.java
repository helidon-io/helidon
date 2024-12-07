/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.service.inject.maven.plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.FilerResource;

class MavenFilerResource implements FilerResource {
    private final Path resourcePath;

    private byte[] currentBytes;
    private boolean modified;

    MavenFilerResource(Path resourcePath) {
        this(resourcePath, new byte[0]);
    }

    MavenFilerResource(Path resourcePath, byte[] bytes) {
        this.resourcePath = resourcePath;
        this.currentBytes = bytes;
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(currentBytes, currentBytes.length);
    }

    @Override
    public void bytes(byte[] newBytes) {
        currentBytes = Arrays.copyOf(newBytes, newBytes.length);
        modified = true;
    }

    @Override
    public void write() {
        if (modified) {
            try {
                Files.write(resourcePath, currentBytes, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
            } catch (IOException e) {
                throw new CodegenException("Failed to write resource " + resourcePath.toAbsolutePath(), e);
            }
        }
    }
}
