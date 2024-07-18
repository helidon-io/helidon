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

package io.helidon.integrations.eclipsestore.cdi;

import io.helidon.microprofile.testing.junit5.HelidonTest;
import jakarta.inject.Inject;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@HelidonTest
class EclipseStoreExtensionTest {

    @TempDir
    static Path tempDir;

    @Inject
    @EclipseStoreStorage(configNode = "org.eclipse.store.storage.my_storage")
    EmbeddedStorageManager storage;

    @BeforeAll
    static void beforeAll() {
        System.setProperty("org.eclipse.store.storage.my_storage.storage-directory", tempDir.toString());
    }

    @AfterAll
    static void afterAll() {
        System.clearProperty("org.eclipse.store.storage.my_storage.storage-directory");
    }

    @Test
    void testInjectedInstances() {
        assertThat(storage, notNullValue());
        assertThat(storage.isRunning(), equalTo(true));
    }

}
