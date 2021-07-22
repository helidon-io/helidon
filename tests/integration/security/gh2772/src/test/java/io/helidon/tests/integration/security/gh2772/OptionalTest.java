/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.security.gh2772;

import java.util.Base64;

import javax.inject.Inject;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for gh2772.
 */
@HelidonTest
class OptionalTest {
    @Inject
    private WebTarget webTarget;

    /*
     * Both auth provider will abstain since we are not passing Auth headers,
     * and with all abstain, composite will return 401.
     */
    @Test
    void testResourceEndpoint() {
        Response response = webTarget
                .path("/greet")
                .request()
                .get(Response.class);
        assertThat(response.getStatus(), is(Response.Status.UNAUTHORIZED.getStatusCode()));
    }

    /*
     * Digest auth provider will abstain but basic will work and we get e2e call done.
     */
    @Test
    void testResourceEndpointBasic() {
        Response response = webTarget
                .path("/greet")
                .request()
                .header("Authorization", basic("basic"))
                .get(Response.class);
        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(response.readEntity(String.class), is("Hello World"));
    }

    /*
     * We are passing Auth headers with wrong user info for basic auth so both provider will abastain,
     * and with all abstain, composite will return 401.
     */
    @Test
    void testResourceEndpointBasicFail() {
        Response response = webTarget
                .path("/greet")
                .request()
                .header("Authorization", basic("fail"))
                .get(Response.class);
        assertThat(response.getStatus(), is(Response.Status.UNAUTHORIZED.getStatusCode()));
    }

    private String basic(String user) {
        String uap = user + ":basic";
        return "basic " + Base64.getEncoder().encodeToString(uap.getBytes());
    }
}