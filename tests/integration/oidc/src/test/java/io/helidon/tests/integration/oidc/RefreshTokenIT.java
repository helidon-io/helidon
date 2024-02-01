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

import java.time.Instant;
import java.util.List;

import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.jwt.jwk.Jwk;
import io.helidon.security.providers.oidc.common.OidcConfig;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.oidc.TestResource.EXPECTED_POST_LOGOUT_TEST_MESSAGE;
import static io.helidon.tests.integration.oidc.TestResource.EXPECTED_TEST_MESSAGE;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;

@AddConfig(key = "security.providers.1.oidc.token-signature-validation", value = "false")
class RefreshTokenIT extends CommonLoginBase {

    @Test
    public void testRefreshToken(WebTarget webTarget) {
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

        //next request should have cookie set, and we do not need to authenticate again
        try (Response response = client.target(webTarget.getUri()).path("/test").request().get()) {
            assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
            assertThat(response.readEntity(String.class), is(EXPECTED_TEST_MESSAGE));
            assertThat(response.getHeaderString(HttpHeaders.SET_COOKIE), nullValue());
        }

        //Since access token validation is disabled, it is enough to just create some invalid one in terms of date.
        Jwt jwt = Jwt.builder()
                .issueTime(Instant.ofEpochMilli(1))
                .expirationTime(Instant.ofEpochMilli(1))
                .notBefore(Instant.ofEpochMilli(1))
                .build();
        SignedJwt signedJwt = SignedJwt.sign(jwt, Jwk.NONE_JWK);

        try (Response response = client
                .target(webTarget.getUri())
                .path("/test")
                .request()
                .header(HttpHeaders.COOKIE, OidcConfig.DEFAULT_COOKIE_NAME + "=" + signedJwt.tokenContent())
                .get()) {
            assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
            assertThat(response.readEntity(String.class), is(EXPECTED_TEST_MESSAGE));
            //Since invalid access token has been provided, this means that the new one has been obtained
            List<String> cookies = response.getStringHeaders().get(HttpHeaders.SET_COOKIE);
            assertThat(cookies, not(empty()));
            assertThat(cookies, hasItem(startsWith(OidcConfig.DEFAULT_COOKIE_NAME)));
        }

        //next request should have cookie set, and we do not need to authenticate again
        try (Response response = client.target(webTarget.getUri()).path("/test").request().get()) {
            assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
            assertThat(response.readEntity(String.class), is(EXPECTED_TEST_MESSAGE));
            assertThat(response.getHeaderString(HttpHeaders.SET_COOKIE), nullValue());
        }

    }

}
