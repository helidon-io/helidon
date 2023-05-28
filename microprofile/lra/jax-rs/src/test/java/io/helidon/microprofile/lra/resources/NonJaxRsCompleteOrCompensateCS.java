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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.microprofile.lra.LoadBalancedCoordinatorTest;

import jakarta.inject.Inject;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

@Path(NonJaxRsCompleteOrCompensateCS.PATH_BASE)
public class NonJaxRsCompleteOrCompensateCS {

    public static final String PATH_BASE = "non-jax-rs-complete-cancel-cs";
    public static final String PATH_START_LRA = "start";
    public static final String PATH_COMPLETE = "complete";
    public static final String PATH_COMPENSATE = "compensate";
    public static final String CS_START_LRA = PATH_BASE + PATH_START_LRA;
    public static final String CS_COMPLETE = PATH_BASE + PATH_COMPLETE;
    public static final String CS_COMPENSATE = PATH_BASE + PATH_COMPENSATE;

    @Inject
    LoadBalancedCoordinatorTest basicTest;

    @PUT
    @LRA(value = LRA.Type.REQUIRES_NEW)
    @Path(NonJaxRsCompleteOrCompensateCS.PATH_START_LRA)
    public void start(
            @HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
            @HeaderParam(Work.HEADER_KEY) Work work
    ) {
        basicTest.getCompletable(CS_START_LRA).complete(lraId);
        work.doWork(lraId);
    }

    @Complete
    public CompletionStage<Response> complete(URI lraId) {
        basicTest.getCompletable(CS_COMPLETE).complete(lraId);
        return CompletableFuture.supplyAsync(() -> Response.ok(ParticipantStatus.Completed.name()).build());
    }

    @Compensate
    public CompletionStage<Response> compensate(URI lraId) {
        basicTest.getCompletable(CS_COMPENSATE).complete(lraId);
        return CompletableFuture.supplyAsync(() -> Response.ok(ParticipantStatus.Compensated.name()).build());
    }
}
