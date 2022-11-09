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

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.jersey.connector.HelidonProperties;
import io.helidon.microprofile.server.Server;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

@Testcontainers
public class QueryBasedLoginIT {

    private static final String EXPECTED_TEST_MESSAGE = "Hello world";

    private static final ClientConfig CONFIG = new ClientConfig()
            .property(ClientProperties.FOLLOW_REDIRECTS, Boolean.FALSE)
            .property(ClientProperties.CONNECT_TIMEOUT, 10000000)
            .property(ClientProperties.READ_TIMEOUT, 10000000)
            .property(HelidonProperties.CONFIG, Config.builder()
                    .addSource(ConfigSources.classpath("/query-param-login.yaml"))
                    .build()
                    .get("client"));
    private Client client;

    @Container
    public static KeycloakContainer keycloakContainer = new KeycloakContainer()
            .withRealmImportFiles("/test-realm.json", "/test2-realm.json")
            // this enables KeycloakContainer to be reused across tests
            .withReuse(true);

    private static final int PORT = 7777;
    private static Server server;

    @BeforeAll
    public static void beforeAll() {
        System.setProperty("security.providers.1.oidc.identity-uri",
                           keycloakContainer.getAuthServerUrl() + "realms/test/");
        System.setProperty("security.providers.1.oidc.tenants.0.identity-uri",
                           keycloakContainer.getAuthServerUrl() + "realms/test2/");
        System.setProperty("security.providers.1.oidc.frontend-uri", "http://localhost:" + PORT);
        //Needs to be done here because of the system property update above.
        Config serverConfig = Config.builder()
                .addSource(ConfigSources.classpath("/query-param-login.yaml"))
                .build();
        server = Server.builder()
                .port(PORT)
                .addResourceClass(TestResource.class)
                .config(serverConfig)
                .build()
                .start();
    }

    @BeforeEach
    public void beforeEach() {
        client = ClientBuilder.newClient(CONFIG);
    }

    @AfterAll
    public static void afterAll() {
        server.stop();
    }

    @Test
    public void testSuccessfulLogin() {
        String formUri;
        WebTarget webserverTarget = client.target("http://localhost:" + server.port());

        //greet endpoint is protected, and we need to get JWT token out of the Keycloak. We will get redirected to the Keycloak.
        try (Response response = webserverTarget.path("/test")
                .request()
                .property(ClientProperties.FOLLOW_REDIRECTS, Boolean.TRUE)
                .header("helidon-tenant", "my-super-tenant")
                .get()) {
            assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
            //We need to get form URI out of the HTML
            formUri = getRequestUri(response.readEntity(String.class));
        }

        //Sending authentication to the Keycloak and getting redirected back to the running Helidon app.
        Entity<Form> form = Entity.form(new Form().param("username", "userthree")
                                                .param("password", "12345")
                                                .param("credentialId", ""));
        String redirectUri;
        try (Response response = client.target(formUri).request().post(form)) {
            assertThat(response.getStatus(), is(Response.Status.FOUND.getStatusCode()));
            redirectUri = response.getHeaderString(HttpHeaders.LOCATION);
        }

        try (Response response = client.target(redirectUri).request().get()) {
            assertThat(response.getStatus(), is(Response.Status.TEMPORARY_REDIRECT.getStatusCode()));
            redirectUri = "http://localhost:" + PORT + response.getHeaderString(HttpHeaders.LOCATION);
        }

        //Request with query parameters should pass
        try (Response response = client.target(redirectUri).request().get()) {
            assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
            assertThat(response.readEntity(String.class), is(EXPECTED_TEST_MESSAGE));
        }

        //Another request with query parameters should pass as well
        try (Response response = client.target(redirectUri).request().get()) {
            assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
            assertThat(response.readEntity(String.class), is(EXPECTED_TEST_MESSAGE));
        }

        //Request without query parameters should be redirected to the identity server again
        try (Response response = webserverTarget.path("/test").request().get()) {
            assertThat(response.getStatus(), is(Response.Status.TEMPORARY_REDIRECT.getStatusCode()));
        }
    }

    @Test
    public void testFallbackToDefaultIfTenantNotFound() {
        String formUri;
        WebTarget webserverTarget = client.property(ClientProperties.FOLLOW_REDIRECTS, Boolean.TRUE)
                .target("http://localhost:" + server.port());

        //greet endpoint is protected, and we need to get JWT token out of the Keycloak. We will get redirected to the Keycloak.
        try (Response response = webserverTarget.path("/test")
                .request()
                .header("helidon-tenant", "nonexistent")
                .get()) {
            assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
            //We need to get form URI out of the HTML
            formUri = getRequestUri(response.readEntity(String.class));
        }

        //This user is defined in realm Test 1 which uses tenant "localhost",
        //tenant which does not exist falls back to the default configuration.
        //Default tenant uses realm Test 2, and it only has defined userone and usertwo.
        Entity<Form> form = Entity.form(new Form().param("username", "userthree")
                                                .param("password", "12345")
                                                .param("credentialId", ""));
        try (Response response = client.target(formUri).request().post(form)) {
            //Keycloak for some reason sends 200 OK even if login failed
            assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
            //Now we should not have our target endpoint response since authentication failed
            String content = response.readEntity(String.class);
            assertThat(content, not(EXPECTED_TEST_MESSAGE));
            //We need to update form uri, since it has changed due to unsuccessful login
            formUri = getRequestUri(content);
        }

        //Sending authentication to the Keycloak and getting redirected back to the running Helidon app.
        form = Entity.form(new Form().param("username", "userone")
                                   .param("password", "12345")
                                   .param("credentialId", ""));
        try (Response response = client.target(formUri).request().post(form)) {
            assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
            assertThat(response.readEntity(String.class), is(EXPECTED_TEST_MESSAGE));
        }
    }

    private String getRequestUri(String html) {
        Document document = Jsoup.parse(html);
        return document.getElementById("kc-form-login").attr("action");
    }

}
