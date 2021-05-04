/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver.testsupport;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static java.nio.file.FileVisitResult.CONTINUE;

/**
 * A helper class that represents a temporary folder.
 */
public class TemporaryFolder {

    private File rootFolder;

    /**
     * Create a new regular file inside the root folder.
     * @param name the name of the file to create
     * @return the created {@link java.io.File} instance
     * @throws IOException if an error occurred while create the file
     * @throws IllegalStateException if the file already exist
     */
    public File newFile(String name) throws IOException {
        File result = new File(rootFolder, name);
        if (!result.createNewFile()) {
            throw new IllegalStateException(
                    "file already exist: " + result.getAbsolutePath());
        }
        return result;
    }

    /**
     * Create a new directory inside the root folder.
     *
     * @param name the name of the directory to create
     * @return the created {@link java.io.File} instance
     */
    public File newFolder(String name) {
        File result = new File(rootFolder, name);
        if (!result.mkdir()) {
            throw new IllegalStateException(
                    "directory was not created: " + result.getAbsolutePath());
        }
        return result;
    }

    /**
     * Return the root of the temporary folder.
     * @return the {@link java.io.File} instance for the temporary folder root
     */
    public File root(){
        return rootFolder;
    }

    void prepare() {
        try {
            rootFolder = File.createTempFile("junit5-", ".tmp");
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        if (!rootFolder.delete()) {
            throw new IllegalStateException(
                    "directory was not deleted: " + rootFolder.getAbsolutePath());
        }
        if (!rootFolder.mkdir()) {
            throw new IllegalStateException(
                    "directory was not created: " + rootFolder.getAbsolutePath());
        }
    }

    void cleanUp() {
        if (rootFolder.exists()) {
            try {
                Files.walkFileTree(rootFolder.toPath(), new DeleteAllVisitor());
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
    }

    private static class DeleteAllVisitor extends SimpleFileVisitor<Path> {

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
            Files.delete(file);
            return CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
            Files.delete(directory);
            return CONTINUE;
        }
    }

}
