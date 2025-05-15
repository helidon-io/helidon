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

import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

import io.helidon.security.annotations.Authenticated;
import io.helidon.webclient.api.WebClient;

/**
 * Test resource.
 */
@Path("/test")
@RequestScoped
public class TestResource {

    public static final String EXPECTED_TEST_MESSAGE = "Hello world";
    public static final String EXPECTED_POST_LOGOUT_TEST_MESSAGE = "Post logout endpoint reached with no cookies";

    /**
     * Return hello world message.
     *
     * @return hello world
     */
    @GET
    @Authenticated
    @Produces(MediaType.TEXT_PLAIN)
    public String getDefaultMessage() {
        return EXPECTED_TEST_MESSAGE;
    }

    @Path("/postLogout")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String postLogout(@Context HttpHeaders httpHeaders) {
        if (httpHeaders.getCookies().isEmpty()) {
            return EXPECTED_POST_LOGOUT_TEST_MESSAGE;
        }
        return "Cookies are not cleared!";
    }


    @GET
    @Authenticated
    @Path("/outbound")
    @Produces(MediaType.TEXT_PLAIN)
    public String outbound(@Context UriInfo uri) {
        try (Client client = ClientBuilder.newClient()) {
            return client.target(uri.getBaseUri())
                    .path("/test/redirected")
                    .request()
                    .get(String.class);
        }
    }

    @GET
    @Authenticated
    @Path("/redirected")
    @Produces(MediaType.TEXT_PLAIN)
    public String redirected(@Context HttpHeaders httpHeaders) {
        return EXPECTED_TEST_MESSAGE;
    }

}

