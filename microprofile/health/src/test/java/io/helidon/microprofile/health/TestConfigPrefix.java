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
package io.helidon.microprofile.health;

import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import org.junit.jupiter.api.Test;

/**
 * Tests the config prefix.
 * <p>
 * We specify 0 values to force the statuses to DOWN. The default config settings cause the checks to return UP, so if we
 * detect DOWN statuses we know the config has been found and applied correctly.
 */
@HelidonTest
@AddConfig(key = "health.checks.diskSpace.thresholdPercent", value = "0.0")
@AddConfig(key = "health.checks.heapMemory.thresholdPercent", value = "0.0")
class TestConfigPrefix {

    @Inject
    private WebTarget webTarget;

    @Test
    void testConfig() {
        TestUtils.checkForFailure(webTarget);
    }
}
