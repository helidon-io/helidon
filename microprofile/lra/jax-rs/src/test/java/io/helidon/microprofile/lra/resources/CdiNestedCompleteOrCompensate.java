/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import io.helidon.microprofile.lra.BasicTest;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;

@ApplicationScoped
@Path(CdiNestedCompleteOrCompensate.PATH_BASE)
public class CdiNestedCompleteOrCompensate {
    public static final String PATH_BASE = "cdi-nested";
    public static final String PATH_START_PARENT_LRA = "start-parent";
    public static final String PATH_START_NESTED_LRA = "start-nested";
    public static final String PATH_END_PARENT_LRA = "end-parent";
    public static final String PATH_END_NESTED_LRA = "end-nested";
    public static final String CS_START_PARENT_LRA = PATH_BASE + PATH_START_PARENT_LRA;
    public static final String CS_END_PARENT_LRA = PATH_BASE + PATH_END_PARENT_LRA;
    public static final String CS_START_NESTED_LRA = PATH_BASE + PATH_START_NESTED_LRA;
    public static final String CS_END_NESTED_LRA = PATH_BASE + PATH_END_NESTED_LRA;
    public static final String CS_COMPLETED = PATH_BASE + PATH_END_NESTED_LRA + "completed";
    public static final String CS_COMPENSATED = PATH_BASE + PATH_END_NESTED_LRA + "compensated";

    @Inject
    BasicTest basicTest;

    @PUT
    @Path(PATH_START_PARENT_LRA)
    @LRA(value = LRA.Type.REQUIRES_NEW, end = false)
    public Response startParent(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                                @HeaderParam(Work.HEADER_KEY) Work work) {
        basicTest.getCompletable(CS_START_PARENT_LRA).complete(lraId);
        return work.doWork(lraId);
    }

    @PUT
    @Path(PATH_START_NESTED_LRA)
    @LRA(value = LRA.Type.NESTED, end = false)
    public Response startNested(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                                @HeaderParam(Work.HEADER_KEY) Work work) {
        basicTest.getCompletable(CS_START_NESTED_LRA).complete(lraId);
        return work.doWork(lraId);
    }

    @PUT
    @Path(PATH_END_PARENT_LRA)
    @LRA(value = LRA.Type.MANDATORY, end = true)
    public Response endParent(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                              @HeaderParam(Work.HEADER_KEY) Work work) {
        basicTest.getCompletable(CS_END_PARENT_LRA).complete(lraId);
        return work.doWork(lraId);
    }

    @PUT
    @Path(PATH_END_NESTED_LRA)
    @LRA(value = LRA.Type.MANDATORY, end = true)
    public Response endNested(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                              @HeaderParam(Work.HEADER_KEY) Work work) {
        basicTest.getCompletable(CS_END_NESTED_LRA).complete(lraId);
        return work.doWork(lraId);
    }

    @Complete
    public Response complete(URI lraId, URI parentLraId) {
        basicTest.getCompletable(CS_COMPLETED, lraId).complete(parentLraId);
        return Response.ok(ParticipantStatus.Completed.name()).build();
    }

    @Compensate
    public Response compensate(URI lraId, URI parentLraId) {
        basicTest.getCompletable(CS_COMPENSATED, lraId).complete(parentLraId);
        return Response.ok(ParticipantStatus.Compensated.name()).build();
    }
}
