/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.integrations.oci.secrets.mp.configsource;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class UsageTest {

    private UsageTest() {
        super();
    }

    @Test
    final void testSpike() {
        Config c = ConfigProvider.getConfig();

        // Make sure non-existent, and therefore non-secret, properties are rejected idiomatically.
        assertNull(c.getOptionalValue("bogus", String.class).orElse(null));
        
        // Make sure non-secret properties are handled by, e.g., System properties etc.
        assertEquals(System.getProperty("java.home"), c.getValue("java.home", String.class));

        // Do the rest of this test only if the following assumptions hold.
        String vaultId = System.getProperty("vault-ocid");
        assumeTrue(vaultId != null && !vaultId.isBlank());
        String expectedValue = System.getProperty("FrancqueSecret.expectedValue");
        assumeTrue(expectedValue != null && !expectedValue.isBlank());
        assumeTrue(Files.exists(Paths.get(System.getProperty("user.home"), ".oci", "config")));

        // The vault designated by the vault OCID must hold a secret named FrancqueSecret, and its value must be equal
        // to the expected value.
        assertEquals(expectedValue, c.getValue("FrancqueSecret", String.class));
    }

}
