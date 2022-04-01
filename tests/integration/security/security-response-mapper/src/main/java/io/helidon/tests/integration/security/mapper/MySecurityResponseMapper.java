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

package io.helidon.tests.integration.security.mapper;

import jakarta.ws.rs.core.Response;

import io.helidon.common.context.Contexts;
import io.helidon.security.SecurityResponse;
import io.helidon.security.integration.jersey.SecurityResponseMapper;

/**
 * Mapper that intercepts creation of {@link Response} when a security
 * error is encountered.
 */
public class MySecurityResponseMapper implements SecurityResponseMapper {

    @Override
    public void aborted(SecurityResponse securityResponse, Response.ResponseBuilder responseBuilder) {
        responseBuilder.header("MAPPED-BY", "MySecurityResponseMapper");

        // Access data set in RestrictedProvider
        Contexts.context()
                .flatMap(c -> c.get(RestrictedProvider.class, String.class))
                .ifPresent(p -> responseBuilder.header("PROVIDER", p));
    }
}
