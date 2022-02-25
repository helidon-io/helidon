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

import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;


/**
 * A JAX-RS provider that maps {@link javax.validation.ConstraintViolationException} from bean validation
 * to a proper JAX-RS response with {@link javax.ws.rs.core.Response.Status#BAD_REQUEST} status.
 * If this provider is not present, validation exception from Validation would end with an internal server
 * error.
 */
@Provider
public class HelidonConstraintViolationException extends ConstraintViolationException implements ExceptionMapper {

    /**
     * Call super of ConstraintViolationException.
     *
     * @param message Exception message.
     * @param constraintViolations List with violations.
     */
    public HelidonConstraintViolationException(String message, Set<? extends ConstraintViolation<?>> constraintViolations) {
        super(message, constraintViolations);
    }

    /**
     * Call super of ConstraintViolationException.
     *
     * @param constraintViolations List with violations.
     */
    public HelidonConstraintViolationException(Set<? extends ConstraintViolation<?>> constraintViolations) {
        super(constraintViolations);
    }

    /**
     * Return Validation Exception, wrapped as a bad request.
     * @param throwable Validation exception
     * @return BAR_REQUEST Response.
     */
    @Override
    public Response toResponse(Throwable throwable) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(throwable.getMessage())
                .status(Response.Status.BAD_REQUEST.getStatusCode())
                .build();
    }
}
