/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.oidc;

import io.helidon.config.Config;
import io.helidon.jersey.connector.HelidonConnectorProvider;
import io.helidon.jersey.connector.HelidonProperties;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@HelidonTest(resetPerTest = true)
@AddBean(TestResource.class)
class CommonLoginBase {

    @Container
    static final KeycloakContainer KEYCLOAK_CONTAINER = new KeycloakContainer()
            .withRealmImportFiles("/test-realm.json", "/test2-realm.json")
            // this enables KeycloakContainer to be reused across tests
            .withReuse(true);

    private static final ClientConfig CONFIG = new ClientConfig()
            .connectorProvider(new HelidonConnectorProvider())
            .property(ClientProperties.CONNECT_TIMEOUT, 10000000)
            .property(ClientProperties.READ_TIMEOUT, 10000000)
            .property(HelidonProperties.CONFIG, Config.create().get("client"));

    Client client;

    @BeforeAll
    public static void beforeAll() {
        System.setProperty("security.providers.1.oidc.identity-uri",
                           KEYCLOAK_CONTAINER.getAuthServerUrl() + "realms/test/");
        System.setProperty("security.providers.1.oidc.tenants.0.identity-uri",
                           KEYCLOAK_CONTAINER.getAuthServerUrl() + "realms/test2/");
    }

    @BeforeEach
    public void beforeEach() {
        client = ClientBuilder.newClient(CONFIG);
    }

}
