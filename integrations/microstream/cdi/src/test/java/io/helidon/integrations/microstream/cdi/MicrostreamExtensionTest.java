/*
 * Copyright (c) 2021, 2025 Oracle and/or its affiliates.
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

import java.nio.file.Path;

import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import one.microstream.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnJre;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@HelidonTest
@DisabledOnJre(
        value = JRE.OTHER,
        disabledReason = "https://github.com/helidon-io/helidon/issues/10152")
class MicrostreamExtensionTest {

    @TempDir
    static Path tempDir;

    @Inject
    @MicrostreamStorage(configNode = "one.microstream.storage.my_storage")
    EmbeddedStorageManager storage;

    @BeforeAll
    static void beforeAll() {
        System.setProperty("one.microstream.storage.my_storage.storage-directory", tempDir.toString());
    }

    @AfterAll
    static void afterAll() {
        System.clearProperty("one.microstream.storage.my_storage.storage-directory");
    }

    @Test
    void testInjectedInstances() {
        assertThat(storage, notNullValue());
        assertThat(storage.isRunning(), equalTo(true));
    }

}