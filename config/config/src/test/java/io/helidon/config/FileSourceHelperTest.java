/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import io.helidon.config.test.infra.TemporaryFolderExt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;

/**
 * Tests {@link io.helidon.config.FileSourceHelper}.
 */
public class FileSourceHelperTest {

    @RegisterExtension
    static TemporaryFolderExt folder = TemporaryFolderExt.build();

    @Test
    public void testDigestSameContent() throws Exception {
        File file1 = folder.newFile("test1");
        File file2 = folder.newFile("test2");
        Files.write(file1.toPath(), "test file".getBytes());
        Files.write(file2.toPath(), "test file".getBytes());

        assertThat(FileSourceHelper.digest(file1.toPath()).get(), equalTo(FileSourceHelper.digest(file2.toPath()).get()));
    }

    @Test
    public void testDigestDifferentContent() throws Exception {
        File file1 = folder.newFile("test1");
        File file2 = folder.newFile("test2");
        Files.write(file1.toPath(), "test file1".getBytes());
        Files.write(file2.toPath(), "test file2".getBytes());

        assertThat(FileSourceHelper.digest(file1.toPath()), not(equalTo(FileSourceHelper.digest(file2.toPath()))));
    }

}
