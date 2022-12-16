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

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.oidc.TestResource.EXPECTED_TEST_MESSAGE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
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
    public void testFallbackToDefaultIfTenantNotFound(WebTarget webTarget) {
        String formUri;

        //greet endpoint is protected, and we need to get JWT token out of the Keycloak. We will get redirected to the Keycloak.
        try (Response response = client.target(webTarget.getUri()).path("/test")
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
