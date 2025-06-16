/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.jersey.connector.HelidonConnectorProvider;
import io.helidon.jersey.connector.HelidonProperties;
import io.helidon.logging.common.LogConfig;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonReaderFactory;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static io.helidon.security.providers.oidc.common.OidcConfig.DEFAULT_ID_COOKIE_NAME;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

@Testcontainers(disabledWithoutDocker = true)
@HelidonTest(resetPerTest = true)
@AddBean(TestResource.class)
class CommonLoginBase {
    static {
        LogConfig.initClass();
    }

    static final JsonBuilderFactory JSON_OBJECT_BUILDER_FACTORY = Json.createBuilderFactory(Map.of());
    static final JsonReaderFactory JSON_READER_FACTORY = Json.createReaderFactory(Map.of());

    @Container
    static final KeycloakContainer KEYCLOAK_CONTAINER = new KeycloakContainer("quay.io/keycloak/keycloak:19.0.3")
            .withRealmImportFiles("/test-realm.json", "/test2-realm.json")
            // this enables KeycloakContainer to be reused across tests
            .withReuse(true);

    private static final ClientConfig CONFIG = new ClientConfig()
            .connectorProvider(new HelidonConnectorProvider())
            .property(ClientProperties.CONNECT_TIMEOUT, 10000000)
            .property(ClientProperties.READ_TIMEOUT, 10000000)
            .property(ClientProperties.FOLLOW_REDIRECTS, true)
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

    @AfterEach
    public void afterEach() {
        client.close();
    }


    String getRequestUri(String html) {
        Document document = Jsoup.parse(html);
        return document.getElementById("kc-form-login").attr("action");
    }

    List<String> obtainCookies(WebTarget webTarget) {
        String formUri;

        //greet endpoint is protected, and we need to get JWT token out of the Keycloak. We will get redirected to the Keycloak.
        try (Response response = client.target(webTarget.getUri())
                .path("/test")
                .request()
                .get()) {
            assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
            //We need to get form URI out of the HTML
            formUri = getRequestUri(response.readEntity(String.class));
        }

        String redirectHelidonUrl;
        //Sending authentication to the Keycloak and getting redirected back to the running Helidon app.
        //Redirection needs to be disabled, so we can get Set-Cookie header from Helidon redirect endpoint
        Entity<Form> form = Entity.form(new Form().param("username", "userone")
                                                .param("password", "12345")
                                                .param("credentialId", ""));
        try (Response response = client.target(formUri)
                .request()
                .property(ClientProperties.FOLLOW_REDIRECTS, false)
                .header("Connection", "close")
                .post(form)) {
            if (response.getStatus() == Response.Status.BAD_REQUEST.getStatusCode()) {
                String sss = response.readEntity(String.class);
                System.out.println();
            }
            assertThat(response.getStatus(), is(Response.Status.FOUND.getStatusCode()));
            redirectHelidonUrl = response.getStringHeaders().getFirst(HttpHeaders.LOCATION);
        }

        List<String> setCookies;
        //Helidon OIDC redirect endpoint -> Sends back Set-Cookie header
        try (Response response = client.target(redirectHelidonUrl)
                .request()
                .property(ClientProperties.FOLLOW_REDIRECTS, false)
                .get()) {
            assertThat(response.getStatus(), is(Response.Status.TEMPORARY_REDIRECT.getStatusCode()));
            //Since invalid access token has been provided, this means that the new one has been obtained
            setCookies = response.getStringHeaders().get(HttpHeaders.SET_COOKIE);
            assertThat(setCookies, not(empty()));
            assertThat(setCookies, hasItem(startsWith(DEFAULT_ID_COOKIE_NAME)));
        }

        return setCookies;
    }

}
