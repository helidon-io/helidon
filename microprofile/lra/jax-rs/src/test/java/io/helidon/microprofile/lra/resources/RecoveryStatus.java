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

import org.eclipse.microprofile.lra.LRAResponse;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;
import org.eclipse.microprofile.lra.annotation.Status;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.helidon.microprofile.lra.LoadBalancedCoordinatorTest;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_RECOVERY_HEADER;

@ApplicationScoped
@Path(RecoveryStatus.PATH_BASE)
public class RecoveryStatus {

    public static final String PATH_BASE = "recovery-status";
    public static final String PATH_START_LRA = "start-compensate";
    public static final String CS_START_LRA = PATH_BASE + PATH_START_LRA;
    public static final String CS_COMPENSATE_FIRST = CS_START_LRA + "compensate-first";
    public static final String CS_COMPENSATE_SECOND = CS_START_LRA + "compensate-second";
    public static final String CS_STATUS = CS_START_LRA + "status";
    public static final String CS_EXPECTED_STATUS = CS_STATUS + "expected";

    @Inject
    LoadBalancedCoordinatorTest basicTest;

    @PUT
    @Path(PATH_START_LRA)
    @LRA(value = LRA.Type.REQUIRES_NEW)
    public Response startCompensateLRA(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                                       @HeaderParam(LRA_HTTP_RECOVERY_HEADER) String recoveryId,
                                       ParticipantStatus reportStatus) {
        basicTest.getCompletable(CS_START_LRA, lraId).complete(lraId);
        basicTest.getCompletable(CS_EXPECTED_STATUS, lraId).complete(reportStatus);
        // Force to compensate
        return Response.serverError()
                .header(LRA_HTTP_CONTEXT_HEADER, lraId.toASCIIString())
                .header(LRA_HTTP_RECOVERY_HEADER, recoveryId)
                .build();
    }

    @PUT
    @Path("/compensate")
    @Produces(MediaType.APPLICATION_JSON)
    @Compensate
    public Response compensateLra(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                                  @HeaderParam(LRA_HTTP_RECOVERY_HEADER) URI recoveryId) {

        CompletableFuture<URI> completable = basicTest.getCompletable(CS_COMPENSATE_FIRST, lraId);
        boolean secondCall = completable.isDone();
        completable.complete(lraId);
        if (secondCall) {
            basicTest.getCompletable(CS_COMPENSATE_SECOND, lraId).complete(lraId);
        } else {
            try {
                // sleep longer than coordinator waits for compensate response
                // to force it to use @Status
                // TODO: get timeout from mock coordinator
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return LRAResponse.failedToComplete();
        }
        return LRAResponse.compensated();
    }

    @Status
    public ParticipantStatus status(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
        basicTest.getCompletable(CS_STATUS, lraId).complete(lraId);
        // we slept thru the first compensate call, let coordinator know if we want it to try compensate again
        // retrieve saved status from #startCompensateLRA
        return basicTest.await(CS_EXPECTED_STATUS, lraId);
    }

}
