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
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class UsageTest {

    private UsageTest() {
        super();
    }

    @Test
    final void testUsage() {
        Config c = ConfigProvider.getConfig();

        // Make sure non-existent, and therefore non-secret, properties are rejected idiomatically.
        assertNull(c.getOptionalValue("bogus", String.class).orElse(null));

        // Make sure non-secret properties are handled by, e.g., System properties etc.
        assertEquals(System.getProperty("java.home"), c.getValue("java.home", String.class));

        // Do the rest of this test only if the following assumptions hold. To avoid skipping the rest of this test:
        //
        // 1. Set up a ${HOME}/.oci/config file following
        //    https://docs.oracle.com/en-us/iaas/Content/API/Concepts/sdkconfig.htm or similar
        //
        // 2. Run Maven with all of the following properties:
        //
        //    -Dvault-ocid=ocid1.vault.oci1.iad.123xyz (a valid OCI Vault OCID)
        //    -DFrancqueSecret.expectedValue='Some Value' (some value for a secret named FrancqueSecret in that vault)
        //
        assumeTrue(Files.exists(Paths.get(System.getProperty("user.home"), ".oci", "config")));
        assumeFalse(System.getProperty("vault-ocid", "").isBlank());
        String expectedValue = System.getProperty("FrancqueSecret.expectedValue", "");
        assumeFalse(expectedValue.isBlank());

        // The vault designated by the vault OCID must hold a secret named FrancqueSecret, and its value must be equal
        // to the expected value.
        assertEquals(expectedValue, c.getValue("FrancqueSecret", String.class));
    }

}
