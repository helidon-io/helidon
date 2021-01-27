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

package io.helidon.integrations.microstream.cdi;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.nio.file.Path;

import javax.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.helidon.integrations.microstream.cdi.MicrostreamStorage;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import one.microstream.storage.embedded.types.EmbeddedStorageManager;

@HelidonTest
class MicrostreamExtensionTest {

    @TempDir
    static Path tempDir;

    @Inject
    @MicrostreamStorage(configNode = "one.microstream.storage.my_storage")
    EmbeddedStorageManager storage;

    @BeforeAll
    private static void beforeAll() {
        System.setProperty("one.microstream.storage.my_storage.storage-directory", tempDir.toString());
    }

    @AfterAll
    private static void afterAll() {
        System.clearProperty("one.microstream.storage.my_storage.storage-directory");
    }

    @Test
    void testInjectedInstances() {
        assertThat(storage, notNullValue());
        assertThat(storage.isRunning(), equalTo(true));
    }

}