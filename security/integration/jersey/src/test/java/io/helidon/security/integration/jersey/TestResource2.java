/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

import java.lang.reflect.Proxy;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.helidon.security.SecurityContext;
import io.helidon.security.annotations.Authenticated;

/**
 * Test resource 2.
 */
@Path("/test2")
public class TestResource2 {
    @Context
    private SecurityContext context;

    @GET
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    public Response getIt() {
        TestResource1.TransferObject response = new TestResource1.TransferObject();
        // we expect this to be a proxy
        boolean field = Proxy.isProxyClass(context.getClass());
        String className = context.getClass().getName();

        response.setSubject(context.user().orElse(SecurityContext.ANONYMOUS).toString());
        response.setField(field);
        response.setFieldClass(className);

        return Response.ok(response).build();
    }
}
