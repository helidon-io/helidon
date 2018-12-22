/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.examples.idcs;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import io.helidon.security.SecurityContext;
import io.helidon.security.abac.scope.ScopeValidator;
import io.helidon.security.annot.Authenticated;

/**
 * JAX-RS Resource.
 */
@Path("/")
public class JerseyResource {
    /**
     * Returns a hello world message and current subject.
     *
     * @param securityContext Security component's context
     * @return returns hello and subject.
     */
    //this is the important annotation for authentication to kick in
    @Authenticated
    @ScopeValidator.Scope("first_scope")
    @ScopeValidator.Scope("second_scope")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getHelloName(@Context SecurityContext securityContext) {
        return "Hello, your current subject: " + securityContext.getUser().orElse(SecurityContext.ANONYMOUS);
    }
}
