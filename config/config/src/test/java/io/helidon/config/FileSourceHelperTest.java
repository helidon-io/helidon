/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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

package io.helidon.config;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.Provider;
import java.security.Security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link io.helidon.config.FileSourceHelper}.
 */
public class FileSourceHelperTest {

    @TempDir
    Path tempFolder;

    @Test
    public void testDigestSameContent() throws Exception {
        File file1 = Files.createFile(tempFolder.resolve("test1")).toFile();
        File file2 = Files.createFile(tempFolder.resolve("test2")).toFile();
        Files.write(file1.toPath(), "test file".getBytes());
        Files.write(file2.toPath(), "test file".getBytes());

        assertThat(FileSourceHelper.digest(file1.toPath()).get(), equalTo(FileSourceHelper.digest(file2.toPath()).get()));
    }

    @Test
    public void testDigestDifferentContent() throws Exception {
        File file1 = Files.createFile(tempFolder.resolve("test1")).toFile();
        File file2 = Files.createFile(tempFolder.resolve("test2")).toFile();
        Files.write(file1.toPath(), "test file1".getBytes());
        Files.write(file2.toPath(), "test file2".getBytes());

        assertThat(FileSourceHelper.digest(file1.toPath()), not(equalTo(FileSourceHelper.digest(file2.toPath()))));
    }


    @Test
    public void testDigestMD5() {
        MessageDigest md = FileSourceHelper.digest();
        assertThat(md.getAlgorithm().toUpperCase(), equalTo(FileSourceHelper.ALGORITHM_MD5));
    }

    @Test
    public void testDigestSHA256() {
        Provider sunProvider = Security.getProvider("SUN");
        try {
            Security.removeProvider(sunProvider.getName());
            Exception e = assertThrows(ConfigException.class, () -> FileSourceHelper.digest());
            assertThat(e.getMessage(), containsString(FileSourceHelper.ALGORITHM_SHA256));
        } finally {
            Security.addProvider(sunProvider);
        }
    }

    @Test
    public void testDigestSpecified() {
        String dummyAlgorithm = "dummy-algorithm";
        System.setProperty(FileSourceHelper.PROPERTY_DIGEST_ALGORITHM, dummyAlgorithm);
        try {
            Exception e = assertThrows(ConfigException.class, () -> FileSourceHelper.digest());
            assertThat(e.getMessage(), containsString(dummyAlgorithm));
        } finally {
            System.clearProperty(FileSourceHelper.PROPERTY_DIGEST_ALGORITHM);
        }
    }

}
