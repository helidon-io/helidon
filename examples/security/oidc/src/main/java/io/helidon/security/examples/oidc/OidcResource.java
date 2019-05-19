/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.examples.oidc;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;

import io.helidon.security.SecurityContext;
import io.helidon.security.annotations.Authenticated;

/**
 * A simple JAX-RS resource with a single GET method.
 */
@Path("/test")
public class OidcResource {

    /**
     * Hello world using security context.
     * @param securityContext context as established during login
     * @return a string with current username
     */
    @Authenticated
    @GET
    public String getIt(@Context SecurityContext securityContext) {
        return "Hello " + securityContext.userName();
    }

}
