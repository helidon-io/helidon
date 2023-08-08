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
package io.helidon.integrations.oci.sdk;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import static io.helidon.integrations.oci.sdk.AdpSuppliers.adpSupplier;
import static io.helidon.integrations.oci.sdk.AdpSuppliers.configFile;
import static io.helidon.integrations.oci.sdk.AdpSuppliers.instancePrincipals;

class TestAdpSuppliers {

    private final Function<String, Optional<String>> c;

    private TestAdpSuppliers() {
        super();
        this.c = pn -> Optional.ofNullable(System.getProperty(pn));
    }

    @Test
    final void testConfigFile() throws IOException {
        // Run this test only when there's an OCI configuration file.
        assumeTrue(configFileExists());

        ConfigFileAuthenticationDetailsProvider adp = (ConfigFileAuthenticationDetailsProvider) adpSupplier(c).get();
        assertNotNull(adp);
    }

    @Test
    final void testInstancePrincipals() throws IOException {
        // Run this test only when IMDS is available.
        assumeTrue(imdsAvailable());

        InstancePrincipalsAuthenticationDetailsProvider adp =
            (InstancePrincipalsAuthenticationDetailsProvider) adpSupplier(instancePrincipals(c), configFile(c)).get();
        assertNotNull(adp);
    }

    /*
     * Static methods.
     */


    private static final boolean configFileExists() throws IOException {
        String path = System.getProperty("oci.config.file");
        if (path == null || path.isBlank()) {
            path = "~/.oci/config";
        }
        String profile = System.getProperty("oci.auth.profile");
        if (profile != null && profile.isBlank()) {
            profile = null;
        }
        try {
            return ConfigFileReader.parse(path, profile) != null;
        } catch (final FileNotFoundException ignored) {
            return false;
        }
    }

    private static final boolean imdsAvailable() {
        String hostname = System.getProperty("oci.imds.hostname");
        if (hostname == null || hostname.isBlank()) {
            hostname = "169.254.169.254";
        }
        String timeout = System.getProperty("oci.imds.timeout");
        if (timeout == null || timeout.isBlank()) {
            timeout = "100";
        }
        try {
            return InetAddress.getByName(hostname).isReachable(Integer.parseInt(timeout));
        } catch (final IOException ignored) {
            return false;
        }
    }

}
