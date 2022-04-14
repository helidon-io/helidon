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

package io.helidon.security.integration.jersey;

import io.helidon.common.context.Contexts;
import io.helidon.security.SecurityResponse;

import jakarta.ws.rs.core.Response;

/**
 * A {@link SecurityResponse} mapper that is called when a security error is
 * encountered. Gives a chance for applications to craft a more informative
 * response to the user as to the cause of the error.
 */
@FunctionalInterface
public interface SecurityResponseMapper {

    /**
     * Called when a security response is aborted due to a security problem (e.g. authentication
     * failure). Handles control to the application to construct the response returned to
     * the client. Security providers can provide context to mappers using the Helidon
     * context mechanism.
     *
     * @param securityResponse the security response
     * @param responseBuilder the response builder
     * @see Contexts#context()
     */
    void aborted(SecurityResponse securityResponse, Response.ResponseBuilder responseBuilder);
}
