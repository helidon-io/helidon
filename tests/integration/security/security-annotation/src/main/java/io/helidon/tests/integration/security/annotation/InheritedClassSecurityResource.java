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

import io.helidon.security.annotations.Authenticated;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/**
 * Class-defined resource whose handler inherits Helidon class security.
 */
@Path("/inherited-class-security")
public abstract class InheritedClassSecurityResource {

    /**
     * Returns a response from an inherited endpoint method.
     *
     * @return endpoint response
     */
    @GET
    public String inherited() {
        return "inherited class authentication";
    }

    /**
     * Returns a response from a concrete override.
     *
     * @return endpoint response
     */
    @GET
    @Path("overridden")
    public String overridden() {
        return "base";
    }

    /**
     * Intermediate handler supplying inherited security.
     */
    @Authenticated
    public abstract static class SecurityBase extends InheritedClassSecurityResource {
    }
}
