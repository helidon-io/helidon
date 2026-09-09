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

import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/**
 * Resource used to verify that class security is analyzed once for multiple methods.
 */
@Path("/class-security-cache")
@RequestScoped
public class ClassSecurityCacheResource {

    /**
     * Returns the first response.
     *
     * @return first response
     */
    @GET
    @Path("first")
    public String first() {
        return "first";
    }

    /**
     * Returns the second response.
     *
     * @return second response
     */
    @GET
    @Path("second")
    public String second() {
        return "second";
    }
}
