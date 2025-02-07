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

import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryManager;

import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ServerTest
public class AuthenticationMethodInstancePrincipalTest {
    private static ServiceRegistryManager registryManager;
    private static ServiceRegistry registry;
    private static String imdsBaseUri;

    AuthenticationMethodInstancePrincipalTest(WebServer server) {
        imdsBaseUri = "http://localhost:%d/opc/v2/".formatted(server.port());
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/opc/v2/instance", ImdsEmulator::emulateImdsInstance);
    }

    void setUp(Properties p) {
        p.put("helidon.oci.authentication-method", "instance-principal");
        p.put("helidon.oci.imds-timeout", "PT1S");
        p.put("helidon.oci.imds-detect-retries", "0");
        p.put("helidon.oci.imds-base-uri", imdsBaseUri);
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
        final String FEDERATION_ENDPOINT = "https://auth.us-myregion-1.oraclecloud.com";
        final String TENANT_ID = "ocid1.tenancy.oc1..mytenancyid";

        Properties p = System.getProperties();
        p.put("helidon.oci.federation-endpoint", FEDERATION_ENDPOINT);
        p.put("helidon.oci.tenant-id", TENANT_ID);
        setUp(p);

        // This error indicates that the instance principal provider has been instantiated
        var thrown = assertThrows(IllegalArgumentException.class,
                                  () -> registry.get(BasicAuthenticationDetailsProvider.class));
        assertThat(thrown.getMessage(),
                   containsString(MockedInstancePrincipalBuilderProvider.INSTANCE_PRINCIPAL_INSTANTIATION_MESSAGE));

        // The following validation indicates that the instance principal provider has been configured properly
        assertThat(MockedAuthenticationMethodInstancePrincipal.getBuilder().getMetadataBaseUrl(), is(imdsBaseUri));
        assertThat(MockedAuthenticationMethodInstancePrincipal.getBuilder().getFederationEndpoint(), is(FEDERATION_ENDPOINT));
        assertThat(MockedAuthenticationMethodInstancePrincipal.getBuilder().getTenancyId(), is(TENANT_ID));
    }

    public static class ImdsEmulator {
        // This will allow HelidonOci.imdsAvailable() to be tested by making the server that simulates IMDS to return a JSON value
        // when instance property is queried
        private static final String IMDS_INSTANCE_RESPONSE = """
                {
                 "displayName": "helidon-server"
                }
                """;

        private static void emulateImdsInstance(ServerRequest req, ServerResponse res) {
            res.send(IMDS_INSTANCE_RESPONSE);
        }
    }
}
