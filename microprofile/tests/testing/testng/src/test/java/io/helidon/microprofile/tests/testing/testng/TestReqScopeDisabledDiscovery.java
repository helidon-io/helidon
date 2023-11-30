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
package io.helidon.microprofile.tests.testing.testng;


import io.helidon.microprofile.testing.testng.*;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertEquals;

@HelidonTest
@DisableDiscovery

// JAX-RS Request scope
@AddJaxRs
@AddBean(TestReqScopeDisabledDiscovery.MyController.class)
class TestReqScopeDisabledDiscovery {

    @Inject
    private WebTarget target;

    @Test
    void testGet() {
        assertEquals("Hallo!", target
                .path("/greeting")
                .request()
                .get(String.class));
    }

    @Path("/greeting")
    @RequestScoped
    public static class MyController {
        @GET
        public Response get() {
            return Response.ok("Hallo!").build();
        }
    }
}