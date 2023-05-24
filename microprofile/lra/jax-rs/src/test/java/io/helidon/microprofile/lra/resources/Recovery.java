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
import java.util.concurrent.CompletableFuture;

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
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_RECOVERY_HEADER;

@ApplicationScoped
@Path(Recovery.PATH_BASE)
public class Recovery {

    public static final String PATH_BASE = "recovery";
    public static final String PATH_START_COMPENSATE_LRA = "start-compensate";
    public static final String PATH_START_COMPLETE_LRA = "start-complete";
    public static final String PATH_COMPLETE = "complete";
    public static final String PATH_COMPENSATE = "compensate";
    public static final String CS_START_COMPENSATE_LRA = PATH_BASE + PATH_START_COMPENSATE_LRA;
    public static final String CS_START_COMPLETE_LRA = PATH_BASE + PATH_START_COMPLETE_LRA;
    public static final String CS_COMPLETE_FIRST = PATH_BASE + PATH_COMPLETE + "first";
    public static final String CS_COMPLETE_SECOND = PATH_BASE + PATH_COMPLETE + "second";
    public static final String CS_COMPENSATE_FIRST = PATH_BASE + PATH_COMPENSATE + "first";
    public static final String CS_COMPENSATE_SECOND = PATH_BASE + PATH_COMPENSATE + "second";

    @Inject
    LoadBalancedCoordinatorTest basicTest;

    @PUT
    @Path(Recovery.PATH_START_COMPENSATE_LRA)
    @LRA(value = LRA.Type.REQUIRES_NEW, timeLimit = 500, timeUnit = ChronoUnit.MILLIS)
    public Response startCompensateLra(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                                       @HeaderParam(LRA_HTTP_RECOVERY_HEADER) String recoveryId) {
        basicTest.getCompletable(CS_START_COMPENSATE_LRA).complete(lraId);
        // Force to compensate
        return Response.serverError()
                .header(LRA_HTTP_CONTEXT_HEADER, lraId.toASCIIString())
                .header(LRA_HTTP_RECOVERY_HEADER, recoveryId)
                .build();
    }

    @PUT
    @Path(Recovery.PATH_START_COMPLETE_LRA)
    @LRA(value = LRA.Type.REQUIRES_NEW, timeLimit = 500, timeUnit = ChronoUnit.MILLIS)
    public Response startCompleteLra(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                                     @HeaderParam(LRA_HTTP_RECOVERY_HEADER) String recoveryId) {
        basicTest.getCompletable(CS_START_COMPLETE_LRA).complete(lraId);
        return Response.ok()
                .header(LRA_HTTP_CONTEXT_HEADER, lraId.toASCIIString())
                .header(LRA_HTTP_RECOVERY_HEADER, recoveryId)
                .build();
    }

    @PUT
    @Path(Recovery.PATH_COMPENSATE)
    @Produces(MediaType.APPLICATION_JSON)
    @Compensate
    public Response compensateLra(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                                  @HeaderParam(LRA_HTTP_RECOVERY_HEADER) URI recoveryId) {

        CompletableFuture<URI> completable = basicTest.getCompletable(CS_COMPENSATE_FIRST);
        boolean secondCall = completable.isDone();
        completable.complete(lraId);
        if (secondCall) {
            basicTest.getCompletable(CS_COMPENSATE_SECOND).complete(lraId);
        } else {
            return LRAResponse.failedToCompensate();
        }
        return LRAResponse.compensated();
    }

    @PUT
    @Path(Recovery.PATH_COMPLETE)
    @Produces(MediaType.APPLICATION_JSON)
    @Complete
    public Response completeLra(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                                @HeaderParam(LRA_HTTP_RECOVERY_HEADER) URI recoveryId) {

        CompletableFuture<URI> completable = basicTest.getCompletable(CS_COMPLETE_FIRST);
        boolean secondCall = completable.isDone();
        completable.complete(lraId);
        if (secondCall) {
            basicTest.getCompletable(CS_COMPLETE_SECOND).complete(lraId);
        } else {
            return LRAResponse.failedToComplete();
        }
        return LRAResponse.completed();
    }
}
