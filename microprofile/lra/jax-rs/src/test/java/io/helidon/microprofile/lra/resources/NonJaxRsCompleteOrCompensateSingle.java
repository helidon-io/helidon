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
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import io.helidon.common.reactive.Single;
import io.helidon.microprofile.lra.LoadBalancedCoordinatorTest;

import org.eclipse.microprofile.lra.annotation.AfterLRA;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;
import org.eclipse.microprofile.lra.annotation.Status;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

@Path(NonJaxRsCompleteOrCompensateSingle.PATH_BASE)
public class NonJaxRsCompleteOrCompensateSingle {

    public static final String PATH_BASE = "non-jax-rs-complete-cancel-single";
    public static final String PATH_START_LRA = "start";
    public static final String PATH_COMPLETE = "complete";
    public static final String PATH_COMPENSATE = "compensate";
    public static final String PATH_AFTER_LRA = "after-lra";
    public static final String PATH_STATUS = "status";
    public static final String CS_START_LRA = PATH_BASE + PATH_START_LRA;
    public static final String CS_COMPLETE = PATH_BASE + PATH_COMPLETE;
    public static final String CS_COMPENSATE = PATH_BASE + PATH_COMPENSATE;
    public static final String CS_AFTER_LRA = PATH_BASE + PATH_AFTER_LRA;
    public static final String CS_STATUS = PATH_BASE + PATH_STATUS;

    Set<URI> lras = new HashSet<>();

    @Inject
    LoadBalancedCoordinatorTest basicTest;

    @PUT
    @LRA(value = LRA.Type.REQUIRES_NEW)
    @Path(NonJaxRsCompleteOrCompensateSingle.PATH_START_LRA)
    public void start(
            @HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
            @HeaderParam(Work.HEADER_KEY) Work work
    ) {
        basicTest.getCompletable(CS_START_LRA).complete(lraId);
        work.doWork(lraId);
    }

    @Complete
    public Single<Response> complete(URI lraId) {
        if(lras.add(lraId)){
            // Force status call
            return Single.error(new RuntimeException("Try again"));
        }
        basicTest.getCompletable(CS_COMPLETE).complete(lraId);
        return Single.just(Response.ok(ParticipantStatus.Completed.name()).build());
    }

    @Compensate
    public Single<Response> compensate(URI lraId) {
        if(lras.add(lraId)){
            // Force status call
            return Single.error(new RuntimeException("Try again"));
        }
        basicTest.getCompletable(CS_COMPENSATE).complete(lraId);
        return Single.just(Response.ok(ParticipantStatus.Compensated.name()).build());
    }

    @Status
    public ParticipantStatus status(URI lraId) {
        basicTest.getCompletable(CS_STATUS).complete(lraId);
        return ParticipantStatus.Active;
    }

    @AfterLRA
    public void after(URI lraId) {
        basicTest.getCompletable(CS_AFTER_LRA).complete(lraId);
    }
}
