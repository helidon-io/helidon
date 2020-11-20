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

import javax.inject.Inject;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import io.helidon.microprofile.lra.BasicTest;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;

@Path(CdiCompleteOrCompensate.PATH_BASE)
public class CdiCompleteOrCompensate {

    public static final String PATH_BASE = "cdi-complete-cancel";
    public static final String PATH_START_LRA = "start";
    public static final String PATH_COMPLETE = "complete";
    public static final String PATH_COMPENSATE = "compensate";
    public static final String CS_START_LRA = PATH_BASE + PATH_START_LRA;
    public static final String CS_COMPLETE = PATH_BASE + PATH_COMPLETE;
    public static final String CS_COMPENSATE = PATH_BASE + PATH_COMPENSATE;

    @Inject
    BasicTest basicTest;

    @PUT
    @LRA(value = LRA.Type.REQUIRES_NEW)
    @Path(CdiCompleteOrCompensate.PATH_START_LRA)
    public void start(
            @HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
            @HeaderParam(Work.HEADER_KEY) Work work
    ) {
        basicTest.getCompletable(CS_START_LRA).complete(lraId);
        work.doWork(lraId);
    }

    @Complete
    public Response complete(URI lraId) {
        basicTest.getCompletable(CS_COMPLETE).complete(lraId);
        return Response.ok(ParticipantStatus.Completed.name()).build();
    }

    @Compensate
    public Response compensate(URI lraId) {
        basicTest.getCompletable(CS_COMPENSATE).complete(lraId);
        return Response.ok(ParticipantStatus.Compensated.name()).build();
    }
}
