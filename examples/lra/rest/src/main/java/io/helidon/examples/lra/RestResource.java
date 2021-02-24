/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.examples.lra;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.lra.annotation.*;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_PARENT_CONTEXT_HEADER;

@Path("/inventory")
@ApplicationScoped
public class RestResource {

    private ParticipantStatus participantStatus;
    private boolean isCancel; //technically indicates whether to throw Exception
    private String uriToCall;
    private Map lraStatusMap = new HashMap<String, ParticipantStatus>();

    @Path("/requiresNew")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(value = LRA.Type.REQUIRES_NEW)
    public Response requiresNew(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId)  {
        return getResponse("requiresNew");
    }

    @Path("/required")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(value = LRA.Type.REQUIRED)
    public Response required(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId)  {
        return getResponse("required");
    }

    @Path("/mandatory")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(value = LRA.Type.MANDATORY)
    public Response mandatory(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId)  {
        return getResponse("mandatory");
    }

    @Path("/never")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(value = LRA.Type.NEVER)
    public Response never(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId)  {
        return getResponse("never");
    }

    @Path("/notSupported")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(value = LRA.Type.NOT_SUPPORTED)
    public Response notSupported(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId)  {
        return getResponse("notSupported");
    }

    @Path("/supports")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(value = LRA.Type.SUPPORTS)
    public Response supports(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId)  {
        return getResponse("supports");
    }

    @Path("/nested")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(value = LRA.Type.NESTED)
    public Response nested(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId)  {
        return getResponse("nested");
    }

    @Path("/compensateMethod")
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Compensate
    public Response cancelInventory(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId) throws NotFoundException {
        participantStatus = ParticipantStatus.Compensating;
        System.out.println("InventoryResource.cancelInventory put inventory back if any lraId:" + lraId);
        participantStatus = ParticipantStatus.Compensated;
        return Response.status(Response.Status.OK).entity(participantStatus.name()).build();
    }

    @PUT
    @Path("/completeMethod")
    @Produces(MediaType.APPLICATION_JSON)
    @Complete
    public Response completeInventory(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId) throws NotFoundException {
        participantStatus = ParticipantStatus.Completing;
        System.out.println("InventoryResource.completeInventory prepare item for shipping lraId:" + lraId);
        participantStatus = ParticipantStatus.Completed;
        return Response.status(Response.Status.OK).entity(participantStatus.name()).build();
    }

    @GET
    @Path("/statusMethod")
    @Status
    public Response status(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                           @HeaderParam(LRA_HTTP_PARENT_CONTEXT_HEADER) URI parent) {
        System.out.println("InventoryResource.status participantStatus:" + participantStatus + " lraId:" + lraId);
        return Response.ok().entity(participantStatus).build();
    }




    //methods to set complete/compensate action if/as result of throwing exception

    private Response getResponse(String lraType) {
        participantStatus = ParticipantStatus.Active;
        if(isCancel) return Response.serverError()
                .entity("isCancel")
                .build();
        else return  Response.ok()
                .entity("isClose")
                .build();
    }

    @GET
    @Path("/setCancel")
    public Response setCancel() {
        System.out.println("setCancel called. LRA method will throw exception (resulting in compensation if appropriate)");
        isCancel = true;
        return Response.ok()
                .entity("isCancel = true")
                .build();
    }

    @GET
    @Path("/setClose")
    public Response setClose() {
        System.out.println("setClose called. LRA method will NOT throw exception (resulting in complete if appropriate)");
        isCancel = false;
        return Response.ok()
                .entity("isCancel = false")
                .build();
    }

    @GET
    @Path("/setURIToCall")
    public Response setURIToCall(@QueryParam("uri") String uri) {
        System.out.println("setURIToCall:" + uri);
        uriToCall = uri;
        isCancel = false;
        return Response.ok()
                .entity("setURIToCall:" + uri)
                .build();
    }

}
