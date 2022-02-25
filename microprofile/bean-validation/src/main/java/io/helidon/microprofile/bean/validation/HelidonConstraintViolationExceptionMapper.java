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

package io.helidon.microprofile.bean.validation;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;


/**
 * A JAX-RS provider that maps {@link jakarta.validation.ConstraintViolationException} from bean validation
 * to a proper JAX-RS response with {@link jakarta.ws.rs.core.Response.Status#BAD_REQUEST} status.
 * If this provider is not present, validation exception from Validation would end with an internal server
 * error.
 */
@Provider
public class HelidonConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    /**
     * Return Validation Exception, wrapped as a bad request.
     * @param exception Validation exception
     * @return BAR_REQUEST Response.
     */
    @Override
    public Response toResponse(ConstraintViolationException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(exception.getMessage())
                .status(Response.Status.BAD_REQUEST.getStatusCode())
                .build();
    }
}
