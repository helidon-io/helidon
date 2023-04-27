/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.config.test.infra;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;


/**
 * JUnit 5 extension for temporary folder operations.
 * <p>
 * Declare the extension in a test using
 * <p>
 * <code>@RegisterExtension
 * <br>static TemporaryFolderExt folder = TemporaryFolderExt.build();
 * </code>
 * <p>
 * The <code>static</code> is important.
 * <p>
 * When a test needs a temporary folder it invokes <code>folder.newFolder()</code>.
 * <p>
 * The extension automatically deletes the temporary files after all tests in
 * the test class have finished.
 *
 * @deprecated Please use {@code io.helidon.common.testing.junit5.TemporaryFolderExt}
 * form {@code helidon-common-testing-junit5} module instead
 */
@Deprecated(forRemoval = true, since = "3.2.1")
public class TemporaryFolderExt implements BeforeEachCallback, AfterEachCallback {

    private Path root;

    private TemporaryFolderExt() {
    }

    /**
     * Builds an instance of <code>TemporaryFolderExt</code>.
     * @return a TemporaryFolderExt
     */
    public static TemporaryFolderExt build() {
        return new TemporaryFolderExt();
    }

    /**
     * Creates a new temporary folder with a unique generated name.
     * @return File for the newly-created temporary folder
     * @throws IOException in case of error creating the new folder
     */
    public File newFolder() throws IOException {
        final Path tempPath = Files.createTempDirectory(root, "test");
        return tempPath.toFile();
    }

    /**
     * Creates a new temporary folder with the specified name.
     * @param name of the folder to create
     * @return File for the new folder
     * @throws IOException in case of error creating the new folder
     */
    public File newFolder(String name) throws IOException {
        int nameStart = (name.startsWith("/") ? 1 : 0);
        return Files.createDirectory(root.resolve(name.substring(nameStart))).toFile();
    }

    /**
     * Creates a new temporary file with a generated unique name.
     * @return the new File
     * @throws IOException in case of error creating the new file
     */
    public File newFile() throws IOException {
        return Files.createTempFile(root, "test", "file").toFile();
    }

    /**
     * Creates a new temporary file with the specified name.
     * @param name name to be used for the new file
     * @return File for the newly-created file
     * @throws IOException in case of error creating the new file
     */
    public File newFile(String name) throws IOException {
        int nameStart = (name.startsWith("/") ? 1 : 0);
        return Files.createFile(root.resolve(name.substring(nameStart))).toFile();
    }

    /**
     * The root for this test's temporary files.
     * @return the root File
     */
    public File getRoot() {
        return root.toFile();
    }

    @Override
    public void beforeEach(ExtensionContext ec) throws Exception {
        root = Files.createTempDirectory("test");
    }
    @Override
    public void afterEach(ExtensionContext ec) throws Exception {
        deleteDir(root);
    }

    private static void deleteDir(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                if (!Files.isWritable(path)) {
                    //When you try to delete the file on Windows and it is marked as read-only
                    //it would fail unless this change
                    path.toFile().setWritable(true);
                }
                Files.delete(path);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                } else {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            }
        });
    }
}
