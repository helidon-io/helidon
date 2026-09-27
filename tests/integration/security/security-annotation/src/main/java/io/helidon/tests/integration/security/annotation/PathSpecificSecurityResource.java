/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.security.annotation;

import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.security.SecurityContext;
import io.helidon.security.annotations.Authenticated;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;

/**
 * Resource used for path specific security checks.
 */
@Path("/path-specific")
public class PathSpecificSecurityResource {
    private static final AtomicInteger SECRET_ACCESSES = new AtomicInteger();

    static void resetSecretAccesses() {
        SECRET_ACCESSES.set(0);
    }

    static int secretAccesses() {
        return SECRET_ACCESSES.get();
    }

    /**
     * Single method whose security is controlled by path specific endpoint configuration.
     *
     * @param path path captured by this method
     * @param securityContext security context
     * @return authenticated user name and path
     */
    @GET
    @Authenticated
    @Path("{path: .*}")
    public String get(@PathParam("path") String path, @Context SecurityContext securityContext) {
        if ("private/secret".equals(path)) {
            SECRET_ACCESSES.incrementAndGet();
        }

        String userName = securityContext.userName();
        return (userName == null ? "anonymous" : userName) + ":" + path;
    }
}
