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

package io.helidon.microprofile.server;

import java.lang.System.Logger.Level;

import io.helidon.webserver.http.ServerRequest;

import jakarta.annotation.Priority;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Mapper for exceptions that are not caught.
 *
 * It is not configured by default in Helidon but it is provided for explicit usage.
 */
@Provider
@Priority(5000)
public class CatchAllExceptionMapper implements ExceptionMapper<Exception> {

    private static final System.Logger LOGGER = System.getLogger(CatchAllExceptionMapper.class.getName());

    @Context
    private ServerRequest serverRequest;

    /**
     * Default empty constructor.
     */
    public CatchAllExceptionMapper() {
    }

    @Override
    public Response toResponse(Exception exception) {
        serverRequest.context().register("unmappedException", exception);
        if (exception instanceof WebApplicationException wae) {
            return wae.getResponse();
        } else {
            LOGGER.log(Level.WARNING, () -> "Internal server error", exception);
            return Response.serverError().build();
        }
    }
}
