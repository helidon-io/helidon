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

package io.helidon.service.maven.plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.FilerTextResource;

class MavenFilerTextResource implements FilerTextResource {
    private final Path resourcePath;
    private final ArrayList<String> currentLines;

    private boolean modified;

    MavenFilerTextResource(Path resourcePath) {
        this.resourcePath = resourcePath;
        this.currentLines = new ArrayList<>();
    }

    MavenFilerTextResource(Path resourcePath, List<String> lines) {
        this.resourcePath = resourcePath;
        this.currentLines = new ArrayList<>(lines);
    }

    @Override
    public List<String> lines() {
        return List.copyOf(currentLines);
    }

    @Override
    public void lines(List<String> newLines) {
        currentLines.clear();
        currentLines.addAll(newLines);
        modified = true;
    }

    @Override
    public void write() {
        if (modified) {
            try {
                Files.write(resourcePath, currentLines, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
            } catch (IOException e) {
                throw new CodegenException("Failed to write resource " + resourcePath.toAbsolutePath(), e);
            }
        }
    }
}
