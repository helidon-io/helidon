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

package io.helidon.examples.integrations.oci.vault.cdi;

import com.oracle.bmc.model.BmcException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps SDK errors to HTTP errors, as otherwise any exception is manifested as an internal server error.
 * This mapper is not part of integration with OCI SDK, as each application may require a different entity format.
 * This mapper simply uses the response code as HTTP status code, and error message as entity.
 */
@Provider
class ErrorHandlerProvider implements ExceptionMapper<BmcException> {
    @Override
    public Response toResponse(BmcException e) {
        return Response.status(e.getStatusCode())
                .entity(e.getMessage())
                .build();
    }
}
