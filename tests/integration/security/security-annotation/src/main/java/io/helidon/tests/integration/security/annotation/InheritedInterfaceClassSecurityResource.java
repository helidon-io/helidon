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
import io.helidon.security.annotations.Authorized;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/**
 * Interface-defined resource whose handler inherits Helidon class security.
 */
@Path("/inherited-interface-class-security")
public interface InheritedInterfaceClassSecurityResource {

    /**
     * Returns an authenticated response.
     *
     * @return endpoint response
     */
    @GET
    String authenticated();

    /**
     * Returns a response with authorization disabled by the inherited handler configuration.
     *
     * @return endpoint response
     */
    @GET
    @Path("authorization-disabled")
    String authorizationDisabled();

    /**
     * Base handler supplying inherited security to concrete overrides.
     */
    @Authenticated
    @Authorized(false)
    abstract class SecurityBase {
    }
}
