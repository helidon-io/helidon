/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

import io.helidon.microprofile.tests.junit5.AddConfig;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.oidc.TestResource.EXPECTED_POST_LOGOUT_TEST_MESSAGE;
import static io.helidon.tests.integration.oidc.TestResource.EXPECTED_TEST_MESSAGE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class CookieBasedLoginIT extends CommonLoginBase {

    @Test
    public void testSuccessfulLogin(WebTarget webTarget) {
        String formUri;
        //greet endpoint is protected, and we need to get JWT token out of the Keycloak. We will get redirected to the Keycloak.
        try (Response response = client.target(webTarget.getUri())
                .path("/test")
                .request()
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
        try (Response response = client.target(formUri).request().post(form)) {
            assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
            assertThat(response.readEntity(String.class), is(EXPECTED_TEST_MESSAGE));
        }

        //next request should have cookie set, and we do not need to authenticate again
        try (Response response = client.target(webTarget.getUri()).path("/test").request().get()) {
            assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
            assertThat(response.readEntity(String.class), is(EXPECTED_TEST_MESSAGE));
        }
    }

    @Test
    public void testUnknownTenantRejected(WebTarget webTarget) {
        try (Response response = client.target(webTarget.getUri()).path("/test")
                .request()
                .header("helidon-tenant", "nonexistent")
                .get()) {
            assertThat(response.getStatus(), is(Response.Status.UNAUTHORIZED.getStatusCode()));
        }
    }

    @Test
    @AddConfig(key = "security.providers.1.oidc.fallback-to-default-tenant-enabled", value = "true")
    public void testUnknownTenantFallbackToDefaultEnabled(WebTarget webTarget) {
        String formUri;

        try (Response response = client.target(webTarget.getUri()).path("/test")
                .request()
                .header("helidon-tenant", "nonexistent")
                .get()) {
            assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
            formUri = getRequestUri(response.readEntity(String.class));
        }

        Entity<Form> form = Entity.form(new Form().param("username", "userone")
                                                .param("password", "12345")
                                                .param("credentialId", ""));
        try (Response response = client.target(formUri).request().post(form)) {
            assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
            assertThat(response.readEntity(String.class), is(EXPECTED_TEST_MESSAGE));
        }

        try (Response response = client.target(webTarget.getUri()).path("/oidc/logout")
                .request()
                .get()) {
            assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
            assertThat(response.readEntity(String.class), is(EXPECTED_POST_LOGOUT_TEST_MESSAGE));
        }
    }

    @Test
    public void testDefaultTenantUsage(WebTarget webTarget) {
        String formUri;

        //greet endpoint is protected, and we need to get JWT token out of the Keycloak. We will get redirected to the Keycloak.
        try (Response response = client.target(webTarget.getUri()).path("/test")
                .request()
                .get()) {
            assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
            //We need to get form URI out of the HTML
            formUri = getRequestUri(response.readEntity(String.class));
        }

        //Sending authentication to the Keycloak and getting redirected back to the running Helidon app.
        Entity<Form> form = Entity.form(new Form().param("username", "userone")
                                   .param("password", "12345")
                                   .param("credentialId", ""));
        try (Response response = client.target(formUri).request().post(form)) {
            assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
            assertThat(response.readEntity(String.class), is(EXPECTED_TEST_MESSAGE));
        }
    }


    @Test
    public void testLogoutFunctionality(WebTarget webTarget) {
        String formUri;

        //greet endpoint is protected, and we need to get JWT token out of the Keycloak. We will get redirected to the Keycloak.
        try (Response response = client.target(webTarget.getUri()).path("/test")
                .request()
                .get()) {
            assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
            //We need to get form URI out of the HTML
            formUri = getRequestUri(response.readEntity(String.class));
        }

        //Sending authentication to the Keycloak and getting redirected back to the running Helidon app.
        Entity<Form> form = Entity.form(new Form().param("username", "userone")
                                                .param("password", "12345")
                                                .param("credentialId", ""));
        try (Response response = client.target(formUri).request().post(form)) {
            assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
            assertThat(response.readEntity(String.class), is(EXPECTED_TEST_MESSAGE));
        }

        try (Response response = client.target(webTarget.getUri()).path("/oidc/logout")
                .request()
                .get()) {
            assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
            assertThat(response.readEntity(String.class), is(EXPECTED_POST_LOGOUT_TEST_MESSAGE));
        }

        try (Response response = client.target(webTarget.getUri()).path("/oidc/logout")
                .request()
                .get()) {
            //There should be not token present among the cookies since it was cleared by the previous call
            assertThat(response.getStatus(), is(Response.Status.FORBIDDEN.getStatusCode()));
        }
    }

    private String getRequestUri(String html) {
        Document document = Jsoup.parse(html);
        return document.getElementById("kc-form-login").attr("action");
    }

}
