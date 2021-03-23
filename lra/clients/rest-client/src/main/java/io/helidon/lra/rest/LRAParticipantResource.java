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
 */
package io.helidon.lra.rest;

import org.eclipse.microprofile.lra.annotation.*;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;

import static io.helidon.lra.rest.LRAConstants.*;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.*;

@ApplicationScoped
@Path(LRAParticipantResource.RESOURCE_PATH)
public class LRAParticipantResource {

    static final String RESOURCE_PATH = "lra-participant-proxy";

    @Inject
    private LRAParticipantRegistry lraParticipantRegistry;

    @PUT
    @Path("{participantId}/" + COMPENSATE)
    @Produces(MediaType.TEXT_PLAIN)
    @Compensate
    public Response compensate(@PathParam("participantId") String participantId,
                               @HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId,
                               @HeaderParam(LRA_HTTP_PARENT_CONTEXT_HEADER) String parentId) {
        return getParticipant(participantId).compensate(createURI(lraId), createURI(parentId));
    }

    @PUT
    @Path("{participantId}/" + COMPLETE)
    @Produces(MediaType.TEXT_PLAIN)
    @Complete
    public Response complete(@PathParam("participantId") String participantId,
                             @HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId,
                             @HeaderParam(LRA_HTTP_PARENT_CONTEXT_HEADER) String parentId) {
        return getParticipant(participantId).complete(createURI(lraId), createURI(parentId));
    }

    @GET
    @Path("{participantId}/" + STATUS)
    @Produces(MediaType.TEXT_PLAIN)
    @Status
    public Response status(@PathParam("participantId") String participantId,
                           @HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId,
                           @HeaderParam(LRA_HTTP_PARENT_CONTEXT_HEADER) String parentId) {
        return getParticipant(participantId).status(createURI(lraId), createURI(parentId));
    }

    @DELETE
    @Path("{participantId}/" + FORGET)
    @Produces(MediaType.TEXT_PLAIN)
    @Forget
    public Response forget(@PathParam("participantId") String participantId,
                           @HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId,
                           @HeaderParam(LRA_HTTP_PARENT_CONTEXT_HEADER) String parentId) {
        return getParticipant(participantId).forget(createURI(lraId), createURI(parentId));
    }

    @PUT
    @Path("{participantId}/" + AFTER)
    @AfterLRA
    public Response afterLRA(@PathParam("participantId") String participantId,
                         @HeaderParam(LRA_HTTP_ENDED_CONTEXT_HEADER) URI lraId,
                         LRAStatus lraStatus) {
        return getParticipant(participantId).afterLRA(lraId, lraStatus);
    }

    private LRAParticipant getParticipant(String participantId) {
        LRAParticipant participant = lraParticipantRegistry.getParticipant(participantId);
        if (participant == null) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).
                    entity(participantId + ": Cannot find participant in LRA registry").build());
        }
        return participant;
    }

    private URI createURI(String value) {
        return value != null ? URI.create(value) : null;
    }
}
