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

import io.helidon.lra.rest.ClientLRARequestFilter;
import io.helidon.lra.rest.ClientLRAResponseFilter;
import org.eclipse.microprofile.lra.annotation.*;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.*;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_PARENT_CONTEXT_HEADER;

@Path("/")
@ApplicationScoped
public class RestOrderResource {

    private ParticipantStatus participantStatus;
    private Client client;

    public RestOrderResource() {
        client = ClientBuilder.newBuilder()
                .register(ClientLRARequestFilter.class)
                .register(ClientLRAResponseFilter.class)
                .build();
    }

    @Path("/placeOrder")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(value = LRA.Type.REQUIRES_NEW)
    public Response placeOrder(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId)  {
        System.out.println("------>RestOrderResource.placeOrder received. " +
                "Will send message to inventory service to check availability. lraId:" + lraId);
        participantStatus = ParticipantStatus.Active;
        Response response = client.target(System.getProperty("inventory.service.url", "http://localhost:8092/inventory/reserveInventory")).request().get();
        String entity = response.readEntity(String.class);
        boolean isInventoryExists = entity.equals("inventoryexists");
        System.out.println("------>RestOrderResource.placeOrder reply received from Inventory service response:" + response + " entity:" + entity);
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if(!isInventoryExists) return Response.serverError()
                .entity("isCancel")
                .build();
        else return  Response.ok()
                .entity("isClose")
                .build();
    }

    @Path("/compensateMethod")
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Compensate
    public Response cancelInventory(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId) throws NotFoundException {
        participantStatus = ParticipantStatus.Compensating;
        System.out.println("------>RestOrderResource.cancelInventory put inventory back if any lraId:" + lraId);
        participantStatus = ParticipantStatus.Compensated;
        return Response.status(Response.Status.OK).entity(participantStatus.name()).build();
    }

    @PUT
    @Path("/completeMethod")
    @Produces(MediaType.APPLICATION_JSON)
    @Complete
    public Response completeInventory(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId) throws NotFoundException {
        participantStatus = ParticipantStatus.Completing;
        System.out.println("------>RestOrderResource.completeInventory prepare item for shipping lraId:" + lraId);
        participantStatus = ParticipantStatus.Completed;
        return Response.status(Response.Status.OK).entity(participantStatus.name()).build();
    }

    @PUT
    @Path("/afterLRAMethod")
    @AfterLRA
    public Response afterLRA(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                           @HeaderParam(LRA_HTTP_PARENT_CONTEXT_HEADER) URI parent) {
        System.out.println("------>RestOrderResource.afterLRA participantStatus:" + participantStatus + " lraId:" + lraId);
        return Response.ok().entity(participantStatus).build();
    }

    @PUT
    @Path("/statusMethod")
    @Status
    public Response status(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                           @HeaderParam(LRA_HTTP_PARENT_CONTEXT_HEADER) URI parent) {
        System.out.println("------>RestOrderResource.status participantStatus:" + participantStatus + " lraId:" + lraId);
        return Response.ok().entity(participantStatus).build();
    }

}
