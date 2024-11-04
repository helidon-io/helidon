/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenFiler;
import io.helidon.codegen.FilerResource;
import io.helidon.codegen.FilerTextResource;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.TypeName;

class MavenFiler implements CodegenFiler {
    private final Path generatedSourceDir;
    private final Path outputDirectory;
    private boolean generatedSources;

    MavenFiler(Path generatedSourceDir, Path outputDirectory) {
        this.generatedSourceDir = generatedSourceDir;
        this.outputDirectory = outputDirectory;
    }

    static MavenFiler create(Path generatedSourceDir, Path outputDirectory) {
        return new MavenFiler(generatedSourceDir, outputDirectory);
    }

    @Override
    public Path writeSourceFile(TypeName typeName, String content, Object... originatingElements) {
        String pathToSourceFile = typeName.packageName().replace('.', '/');
        String fileName = typeName.className() + ".java";
        Path path = generatedSourceDir.resolve(pathToSourceFile)
                .resolve(fileName);
        Path parentDir = path.getParent();
        if (parentDir != null) {
            mkdirs(parentDir);
        }

        try (Writer writer = Files.newBufferedWriter(path,
                                                     StandardCharsets.UTF_8,
                                                     StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
            writer.write(content);
            generatedSources = true;
        } catch (IOException e) {
            throw new CodegenException("Failed to write new source file: " + path.toAbsolutePath(), e, typeName);
        }
        return path;
    }

    @Override
    public Path writeSourceFile(ClassModel classModel, Object... originatingElements) {
        TypeName typeName = classModel.typeName();
        String pathToSourceFile = typeName.packageName().replace('.', '/');
        String fileName = typeName.className() + ".java";
        Path path = generatedSourceDir.resolve(pathToSourceFile)
                .resolve(fileName);
        Path parentDir = path.getParent();
        if (parentDir != null) {
            mkdirs(parentDir);
        }

        try (Writer writer = Files.newBufferedWriter(path,
                                                     StandardCharsets.UTF_8,
                                                     StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
            classModel.write(writer, "    ");
            generatedSources = true;
        } catch (IOException e) {
            throw new CodegenException("Failed to write new source file: " + path.toAbsolutePath(), e, typeName);
        }
        return path;
    }

    @Override
    public Path writeResource(byte[] resource, String location, Object... originatingElements) {
        Path path = outputDirectory.resolve(location);
        Path parentDir = path.getParent();
        if (parentDir != null) {
            mkdirs(parentDir);
        }
        try (OutputStream out = Files.newOutputStream(path, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
            out.write(resource);
        } catch (IOException e) {
            throw new CodegenException("Failed to write new resource file: " + path.toAbsolutePath(), location);
        }
        return path;
    }

    @Override
    public FilerTextResource textResource(String location, Object... originatingElements) {
        Path resourcePath = outputDirectory.resolve(location);
        Path parentDir = resourcePath.getParent();
        if (parentDir != null) {
            mkdirs(parentDir);
        }
        if (Files.exists(resourcePath)) {
            try {
                return new MavenFilerTextResource(resourcePath, Files.readAllLines(resourcePath));
            } catch (IOException e) {
                throw new CodegenException("Failed to read existing text resource: " + resourcePath.toAbsolutePath(), e);
            }
        } else {
            return new MavenFilerTextResource(resourcePath);
        }
    }

    @Override
    public FilerResource resource(String location, Object... originatingElements) {
        Path resourcePath = outputDirectory.resolve(location);
        Path parentDir = resourcePath.getParent();
        if (parentDir != null) {
            mkdirs(parentDir);
        }
        if (Files.exists(resourcePath)) {
            try {
                return new MavenFilerResource(resourcePath, Files.readAllBytes(resourcePath));
            } catch (IOException e) {
                throw new CodegenException("Failed to read existing resource: " + resourcePath.toAbsolutePath(), e);
            }
        } else {
            return new MavenFilerResource(resourcePath);
        }
    }

    boolean generatedSources() {
        return generatedSources;
    }

    private void mkdirs(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new CodegenException("Failed to create directories for: " + path.toAbsolutePath());
        }
    }
}
