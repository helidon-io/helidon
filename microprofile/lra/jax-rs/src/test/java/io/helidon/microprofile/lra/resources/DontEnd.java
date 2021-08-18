/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.helidon.microprofile.lra.resources;

import java.net.URI;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import io.helidon.microprofile.lra.LoadBalancedCoordinatorTest;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;

@ApplicationScoped
@Path(DontEnd.PATH_BASE)
public class DontEnd extends CommonAfter {

    public static final String PATH_BASE = "dont-end";
    public static final String PATH_START_LRA = "start";
    public static final String PATH_START_SECOND_LRA = "start-second";
    public static final String CS_START_LRA = PATH_BASE + PATH_START_LRA;
    public static final String CS_START_SECOND_LRA = PATH_BASE + PATH_START_LRA;

    @Inject
    LoadBalancedCoordinatorTest basicTest;

    @PUT
    @Path(PATH_START_LRA)
    @LRA(value = LRA.Type.REQUIRES_NEW, end = false)
    public Response startDontEndLRA(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
        basicTest.getCompletable(CS_START_LRA).complete(lraId);
        return Response.ok().build();
    }

    @PUT
    @Path(PATH_START_SECOND_LRA)
    @LRA(value = LRA.Type.MANDATORY, end = true)
    public Response endLRA(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
        basicTest.getCompletable(CS_START_SECOND_LRA).complete(lraId);
        return Response.ok().build();
    }

}
