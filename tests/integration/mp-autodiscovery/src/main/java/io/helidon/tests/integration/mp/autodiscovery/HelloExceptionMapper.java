/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.mp.autodiscovery;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import static javax.ws.rs.core.Response.Status.CREATED;

/**
 * Simple CDI-managed exception mapper that should be discovered,
 * configured and registered automatically.
 */
@ApplicationScoped
@Provider
public class HelloExceptionMapper implements ExceptionMapper<HelloException> {
    @Inject
    @ConfigProperty(name = "exception.upercase", defaultValue = "true")
    private boolean fUppercase;

    @Override
    public Response toResponse(HelloException exception) {
        String message = fUppercase
                ? exception.getMessage().toUpperCase()
                : exception.getMessage();

        return Response.status(CREATED).entity(message).build();
    }
}
