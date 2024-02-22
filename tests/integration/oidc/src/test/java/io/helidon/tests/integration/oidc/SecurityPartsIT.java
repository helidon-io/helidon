/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.jersey.connector.HelidonConnectorProvider;
import io.helidon.jersey.connector.HelidonProperties;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.security.providers.oidc.common.OidcConfig;

import jakarta.json.JsonObject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.security.providers.oidc.common.OidcConfig.DEFAULT_COOKIE_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class SecurityPartsIT extends CommonLoginBase {

    private static final ClientConfig CONFIG_CLIENT_2 = new ClientConfig()
            .connectorProvider(new HelidonConnectorProvider())
            .property(ClientProperties.CONNECT_TIMEOUT, 10000000)
            .property(ClientProperties.READ_TIMEOUT, 10000000)
            .property(ClientProperties.FOLLOW_REDIRECTS, true)
            .property(HelidonProperties.CONFIG, Config.builder()
                    .addSource(ConfigSources.classpath("application-no-cookie.yaml"))
                    .build()
                    .get("client"));

    private Client client2;

    @BeforeEach
    public void beforeEach2() {
        client2 = ClientBuilder.newClient(CONFIG_CLIENT_2);
    }

    @AfterEach
    public void afterEach2() {
        client2.close();
    }

    @Test
    public void testMissingStateCookie(WebTarget webTarget) {

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
            assertThat(response.getStatus(), is(Response.Status.FOUND.getStatusCode()));
            redirectHelidonUrl = response.getStringHeaders().getFirst(HttpHeaders.LOCATION);
        }

        try (Response response = client2.target(redirectHelidonUrl)
                .request()
                .property(ClientProperties.FOLLOW_REDIRECTS, false)
                .get()) {
            //state cookie was missing
            assertThat(response.getStatus(), is(Response.Status.UNAUTHORIZED.getStatusCode()));
        }
    }

    @Test
    @AddConfig(key = "security.providers.1.oidc.cookie-encryption-state-enabled", value = "false")
    public void testNotMatchingStateCode(WebTarget webTarget) {

        JsonObject stateObject;
        String keyCloakUri;
        //greet endpoint is protected, and we need to get JWT token out of the Keycloak. We will get redirected to the Keycloak.
        try (Response response = client.target(webTarget.getUri())
                .path("/test")
                .property(ClientProperties.FOLLOW_REDIRECTS, false)
                .request()
                .get()) {
            assertThat(response.getStatus(), is(Response.Status.TEMPORARY_REDIRECT.getStatusCode()));
            assertThat(response.getHeaderString(HttpHeaders.SET_COOKIE), is(not(nullValue())));

            String stateCookieValue = response.getHeaderString(HttpHeaders.SET_COOKIE);
            stateCookieValue = stateCookieValue.substring(stateCookieValue.indexOf("=") + 1, stateCookieValue.indexOf(";"));
            stateCookieValue = new String(Base64.getDecoder().decode(stateCookieValue));
            stateObject = JSON_READER_FACTORY.createReader(new StringReader(stateCookieValue)).readObject();

            keyCloakUri = response.getHeaderString(HttpHeaders.LOCATION);
        }

        String formUri;
        try (Response response = client.target(keyCloakUri)
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
            assertThat(response.getStatus(), is(Response.Status.FOUND.getStatusCode()));
            redirectHelidonUrl = response.getStringHeaders().getFirst(HttpHeaders.LOCATION);
        }

        JsonObject stateJson = JSON_OBJECT_BUILDER_FACTORY.createObjectBuilder(stateObject)
                .add("state", "someNonMatchingStateString")
                .build();

        String stateCookieValue = Base64.getEncoder().encodeToString(stateJson.toString().getBytes());
        try (Response response = client2.target(redirectHelidonUrl)
                .request()
                .property(ClientProperties.FOLLOW_REDIRECTS, false)
                .header(HttpHeaders.COOKIE, OidcConfig.DEFAULT_STATE_COOKIE_NAME + "=" + stateCookieValue)
                .get()) {
            //state cookie did not have matching nonce
            assertThat(response.getStatus(), is(Response.Status.UNAUTHORIZED.getStatusCode()));
        }
    }

    @Test
    @AddConfig(key = "security.providers.1.oidc.cookie-encryption-state-enabled", value = "false")
    public void testNotMatchingNonce(WebTarget webTarget) {

        JsonObject stateObject;
        String keyCloakUri;
        //greet endpoint is protected, and we need to get JWT token out of the Keycloak. We will get redirected to the Keycloak.
        try (Response response = client.target(webTarget.getUri())
                .path("/test")
                .property(ClientProperties.FOLLOW_REDIRECTS, false)
                .request()
                .get()) {
            assertThat(response.getStatus(), is(Response.Status.TEMPORARY_REDIRECT.getStatusCode()));
            assertThat(response.getHeaderString(HttpHeaders.SET_COOKIE), is(not(nullValue())));

            String stateCookieValue = response.getHeaderString(HttpHeaders.SET_COOKIE);
            stateCookieValue = stateCookieValue.substring(stateCookieValue.indexOf("=") + 1, stateCookieValue.indexOf(";"));
            stateCookieValue = new String(Base64.getDecoder().decode(stateCookieValue));
            stateObject = JSON_READER_FACTORY.createReader(new StringReader(stateCookieValue)).readObject();

            keyCloakUri = response.getHeaderString(HttpHeaders.LOCATION);
        }

        String formUri;
        try (Response response = client.target(keyCloakUri)
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
            assertThat(response.getStatus(), is(Response.Status.FOUND.getStatusCode()));
            redirectHelidonUrl = response.getStringHeaders().getFirst(HttpHeaders.LOCATION);
        }

        JsonObject stateJson = JSON_OBJECT_BUILDER_FACTORY.createObjectBuilder(stateObject)
                .add("nonce", "someNonMatchingNonceString")
                .build();

        String stateCookieValue = Base64.getEncoder().encodeToString(stateJson.toString().getBytes());
        try (Response response = client2.target(redirectHelidonUrl)
                .request()
                .property(ClientProperties.FOLLOW_REDIRECTS, false)
                .header(HttpHeaders.COOKIE, OidcConfig.DEFAULT_STATE_COOKIE_NAME + "=" + stateCookieValue)
                .get()) {
            //state cookie did not have matching nonce
            assertThat(response.getStatus(), is(Response.Status.UNAUTHORIZED.getStatusCode()));
        }
    }

    @Test
    @AddConfig(key = "security.providers.1.oidc.cookie-encryption-enabled", value = "false")
    public void testAccessTokenIssuedIp(WebTarget webTarget) {
        List<String> setCookies = obtainCookies(webTarget);

        //Ignore ID token cookie
        Invocation.Builder request = client2.target(webTarget.getUri()).path("/test").request();
        for (String setCookie : setCookies) {
            if (!setCookie.startsWith(DEFAULT_COOKIE_NAME + "=")) {
                request.header(HttpHeaders.COOKIE, setCookie);
            } else {
                String encodedJson = setCookie.substring(setCookie.indexOf("=") + 1, setCookie.indexOf(";"));
                String json = new String(Base64.getDecoder().decode(encodedJson), StandardCharsets.UTF_8);
                JsonObject jsonObject = JSON_READER_FACTORY.createReader(new StringReader(json)).readObject();
                JsonObject recreatedJsonObject = JSON_OBJECT_BUILDER_FACTORY.createObjectBuilder(jsonObject)
                        .add("remotePeer", "1.1.1.1") //some other address than localhost
                        .build();
                String base64 = Base64.getEncoder()
                        .encodeToString(recreatedJsonObject.toString().getBytes(StandardCharsets.UTF_8));
                request.header(HttpHeaders.COOKIE, DEFAULT_COOKIE_NAME + "=" + base64);
            }
        }

        try (Response response = request
                .property(ClientProperties.FOLLOW_REDIRECTS, false)
                .get()) {
            //This means, remote peer was not the same as our IP. We are getting redirected to key cloak again
            assertThat(response.getStatus(), is(Response.Status.TEMPORARY_REDIRECT.getStatusCode()));
        }
    }

    @Test
    @AddConfig(key = "security.providers.1.oidc.cookie-encryption-enabled", value = "false")
    @AddConfig(key = "security.providers.1.oidc.access-token-ip-check", value = "false")
    public void testDisabledAccessTokenIssuedIp(WebTarget webTarget) {
        List<String> setCookies = obtainCookies(webTarget);

        //Ignore ID token cookie
        Invocation.Builder request = client2.target(webTarget.getUri()).path("/test").request();
        for (String setCookie : setCookies) {
            if (!setCookie.startsWith(DEFAULT_COOKIE_NAME + "=")) {
                request.header(HttpHeaders.COOKIE, setCookie);
            } else {
                String encodedJson = setCookie.substring(setCookie.indexOf("=") + 1, setCookie.indexOf(";"));
                String json = new String(Base64.getDecoder().decode(encodedJson), StandardCharsets.UTF_8);
                JsonObject jsonObject = JSON_READER_FACTORY.createReader(new StringReader(json)).readObject();
                JsonObject recreatedJsonObject = JSON_OBJECT_BUILDER_FACTORY.createObjectBuilder(jsonObject)
                        .add("remotePeer", "1.1.1.1") //some other address than localhost
                        .build();
                String base64 = Base64.getEncoder()
                        .encodeToString(recreatedJsonObject.toString().getBytes(StandardCharsets.UTF_8));
                request.header(HttpHeaders.COOKIE, DEFAULT_COOKIE_NAME + "=" + base64);
            }
        }

        try (Response response = request
                .property(ClientProperties.FOLLOW_REDIRECTS, false)
                .get()) {
            //This means, remote peer was not the same as our IP. We are getting redirected to key cloak again
            assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
        }
    }

}
