/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.authentication.okeworkload;

import java.util.Properties;

import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryManager;

import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AuthenticationMethodOkeWorkloadTest {
    private static ServiceRegistryManager registryManager;
    private static ServiceRegistry registry;

    void setUp(Properties p) {
        p.put("helidon.oci.authentication-method", "oke-workload-identity");
        System.setProperties(p);

        registryManager = ServiceRegistryManager.create();
        registry = registryManager.registry();
    }

    @AfterEach
    void cleanUp() {
        registry = null;
        if (registryManager != null) {
            registryManager.shutdown();
        }
    }

    @Test
    public void testOkeWorkloadIdentityConfigurationAndInstantiation() {
        final String FEDERATION_ENDPOINT = "https://auth.us-myregion-1.oraclecloud.com";
        final String TENANT_ID = "ocid1.tenancy.oc1..mytenancyid";

        Properties p = System.getProperties();
        p.put("helidon.oci.federation-endpoint", FEDERATION_ENDPOINT);
        p.put("helidon.oci.tenant-id", TENANT_ID);
        setUp(p);

        // This error indicates that the oke-workload-identity provider has been instantiated
        var thrown = assertThrows(IllegalArgumentException.class,
                                                          () -> registry.get(BasicAuthenticationDetailsProvider.class));
        assertThat(thrown.getMessage(), containsString("Invalid Kubernetes ca certification"));
        // The following validation indicates that the oke-workload-identity provider has been configured properly
        assertThat(MockedAuthenticationMethodOkeWorkload.getBuilder().getFederationEndpoint(), is(FEDERATION_ENDPOINT));
        assertThat(MockedAuthenticationMethodOkeWorkload.getBuilder().getTenancyId(), is(TENANT_ID));
    }
}
