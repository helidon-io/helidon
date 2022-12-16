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

import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.helidon.security.annotations.Authenticated;

/**
 * Test resource.
 */
@Path("/test")
@RequestScoped
public class TestResource {

    public static final String EXPECTED_TEST_MESSAGE = "Hello world";

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

}

