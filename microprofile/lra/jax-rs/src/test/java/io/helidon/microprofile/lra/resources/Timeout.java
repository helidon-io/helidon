/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.lra.resources;

import java.net.URI;
import java.time.temporal.ChronoUnit;

import io.helidon.microprofile.lra.LoadBalancedCoordinatorTest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.lra.LRAResponse;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_RECOVERY_HEADER;

@ApplicationScoped
@Path(Timeout.PATH_BASE)
public class Timeout {

    public static final String PATH_BASE = "timeout";
    public static final String PATH_START_LRA = "start";
    public static final String PATH_COMPENSATE = "compensate";
    public static final String CS_START_LRA = PATH_BASE + PATH_START_LRA;
    public static final String CS_COMPENSATE = PATH_BASE + PATH_COMPENSATE;

    @Inject
    LoadBalancedCoordinatorTest basicTest;

    @PUT
    @Path(Timeout.PATH_START_LRA)
    @LRA(value = LRA.Type.REQUIRES_NEW, timeLimit = 500, timeUnit = ChronoUnit.MILLIS)
    public Response startLRA(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        basicTest.getCompletable(Timeout.CS_START_LRA).complete(lraId);
        return Response.ok().build();
    }

    @PUT
    @Path(Timeout.PATH_COMPENSATE)
    @Produces(MediaType.APPLICATION_JSON)
    @Compensate
    public Response compensateWork(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                                   @HeaderParam(LRA_HTTP_RECOVERY_HEADER) URI recoveryId) {
        basicTest.getCompletable(Timeout.CS_COMPENSATE).complete(lraId);

        return LRAResponse.compensated();
    }
}
