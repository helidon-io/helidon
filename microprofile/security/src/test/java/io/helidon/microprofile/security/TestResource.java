/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.microprofile.security;

import io.helidon.security.SecurityContext;
import io.helidon.security.annotations.Authenticated;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

/**
 * Testing resource (JAX-RS).
 */
@Path("/")
public class TestResource {
    /**
     * Uses default provider - automatically authenticated and authorized.
     *
     * @return subject information
     */
    @Authenticated
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String defaultProvider(@Context SecurityContext securityContext) {
        return "Basic provider\n"
                + " user: " + securityContext.userName() + "\n"
                + " subject: " + securityContext.user();
    }

    @Authenticated(provider = "http-digest-auth")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/digest")
    public String digest(@Context SecurityContext securityContext) {
        return "Digest provider\n"
                + " user: " + securityContext.userName() + "\n"
                + " subject: " + securityContext.user();
    }

    @Authenticated(provider = "digest_old")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/digest_old")
    public String digestOld(@Context SecurityContext securityContext) {
        return "Digest provider\n"
                + " user: " + securityContext.userName() + "\n"
                + " subject: " + securityContext.user();
    }
}
