/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.tests.functional.context.injection;

import java.util.Objects;

import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

/**
 * Verifies that {@code ServerRequest} and {@code ServerResponse} are injectable
 * both via {@code @Context} and {@code @Inject}.
 */
@Path("/check")
public class CheckInjectionResource {

    @Context
    private ServerRequest serverRequest;

    @Context
    private ServerResponse serverResponse;

    @Inject
    private ServerRequest serverRequestCdi;

    @Inject
    private ServerResponse serverResponseCdi;

    @GET
    public Response checkInjection() {
        Objects.requireNonNull(serverRequest);
        Objects.requireNonNull(serverResponse);
        Objects.requireNonNull(serverRequestCdi);
        Objects.requireNonNull(serverResponseCdi);
        if (!serverRequestCdi.path().equals(serverRequest.path())
                || !serverResponseCdi.status().equals(serverResponse.status())) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok().build();
    }
}
