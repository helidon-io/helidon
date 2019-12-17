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
package io.helidon.tests.functional.jaxrs.subresource;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

/**
 * A sub-resource does not have a Path annotation.
 */
@RolesAllowed("sub2")
public class MpSubResource {
    private final String name;

    MpSubResource(String name) {
        this.name = name;
    }

    @Path("/sub")
    @RolesAllowed("subsub")
    public MpSubSubResource getSub() {
        return new MpSubSubResource();
    }

    @GET
    @PermitAll
    public String getNoSecurity() {
        return "Unsecured " + name;
    }

    @Path("/parent")
    @GET
    public String getParentSecurity() {
        return "Secured by parent " + name;
    }

    @Path("/secure")
    @GET
    @RolesAllowed("admin")
    public String getHelloWorld() {
        return "Secured " + name;
    }
}
