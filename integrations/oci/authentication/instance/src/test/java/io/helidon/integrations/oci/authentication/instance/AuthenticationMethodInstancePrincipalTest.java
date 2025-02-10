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

package io.helidon.integrations.oci.authentication.instance;

import java.util.Properties;

import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryManager;

import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class AuthenticationMethodInstancePrincipalTest {
    private static ServiceRegistryManager registryManager;
    private static ServiceRegistry registry;

    void setUp(Properties p) {
        p.put("helidon.oci.authentication-method", "instance-principal");
        p.put("helidon.oci.imds-timeout", "PT1S");
        p.put("helidon.oci.imds-detect-retries", "0");
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
    public void testInstancePrincipalConfigurationAndInstantiation() {
        final String IMDS_BASE_URI = "http://localhost:8000/opc/v2/";
        final String FEDERATION_ENDPOINT = "https://auth.us-myregion-1.oraclecloud.com";
        final String TENANT_ID = "ocid1.tenancy.oc1..mytenancyid";

        Properties p = System.getProperties();
        p.put("helidon.oci.imds-base-uri", IMDS_BASE_URI);
        p.put("helidon.oci.federation-endpoint", FEDERATION_ENDPOINT);
        p.put("helidon.oci.tenant-id", TENANT_ID);
        setUp(p);

        var builder = registry.get(InstancePrincipalsAuthenticationDetailsProvider.InstancePrincipalsAuthenticationDetailsProviderBuilder.class);
        // The following validation indicates that the instance principal provider has been configured properly
        assertThat(builder.getMetadataBaseUrl(), is(IMDS_BASE_URI));
        assertThat(builder.getFederationEndpoint(), is(FEDERATION_ENDPOINT));
        assertThat(builder.getTenancyId(), is(TENANT_ID));
    }
}
