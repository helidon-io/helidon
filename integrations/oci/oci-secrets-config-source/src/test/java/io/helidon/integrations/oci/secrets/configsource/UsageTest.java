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
package io.helidon.integrations.oci.secrets.configsource;

import java.nio.file.Files;
import java.nio.file.Paths;

import io.helidon.config.Config;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class UsageTest {

    @Test
    void testUsage() {
        // Get a Config object. Because src/test/resources/meta-config.yaml exists, and because it will be processed
        // according to the Helidon rules, an
        // io.helidon.integrations.oci.secrets.configsource.OciSecretsConfigSourceProvider will be created and any
        // ConfigSources it creates will become part of the assembled Config object.
        Config c = Config.create();

        // Make sure non-existent properties don't cause the Vault to get involved.
        assertThat(c.get("bogus").asNode().orElse(null), nullValue());

        // Make sure properties that have nothing to do with the OCI Secrets Retrieval or Vault APIs are handled by some
        // other (default) ConfigSource, e.g., System properties, etc. (The OCI Secrets Retrieval API should never be
        // consulted for java.home, in other words.)
        assertThat(c.get("java.home").asString().orElse(null), is(System.getProperty("java.home")));

        // Do the rest of this test only if the following assumptions hold. To avoid skipping the rest of this test:
        //
        // 1. Set up a ${HOME}/.oci/config file following
        //    https://docs.oracle.com/en-us/iaas/Content/API/Concepts/sdkconfig.htm or similar
        //
        // 2. Run Maven with all of the following properties:
        //
        //    -Dcompartment-ocid=ocid1.compartment.oci1.iad.123xyz (a valid OCI Compartment OCID)
        //    -Dvault-ocid=ocid1.vault.oci1.iad.123xyz (a valid OCI Vault OCID)
        //    -DFrancqueSecret.expectedValue='Some Value' (some value for a secret named FrancqueSecret in that vault)
        //
        assumeTrue(Files.exists(Paths.get(System.getProperty("user.home"), ".oci", "config"))); // condition 1
        assumeFalse(System.getProperty("compartment-ocid", "").isBlank()); // condition 2
        assumeFalse(System.getProperty("vault-ocid", "").isBlank()); // condition 2
        String expectedValue = System.getProperty("FrancqueSecret.expectedValue", "");
        assumeFalse(expectedValue.isBlank()); // condition 2

        //
        // (Code below this line executes only if the above JUnit assumptions passed. Otherwise control flow stops above.)
        //

        // For this test to pass, all of the following must hold:
        //
        // 1. The vault designated by the vault OCID must hold a secret named FrancqueSecret
        //
        // 2. The secret named FrancqueSecret must have a value equal to the expected value
        assertThat(c.get("FrancqueSecret").asString().orElse(null), is(expectedValue));
    }

}
