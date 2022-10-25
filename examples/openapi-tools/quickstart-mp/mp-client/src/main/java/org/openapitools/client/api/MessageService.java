package org.openapitools.client.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.openapitools.client.model.Message;

@Path("/greet")
@ApplicationScoped
public class MessageService {

    @Inject
    @RestClient
    MessageApi messageApi;


    @GET
    @Produces({"application/json"})
    public Message getDefaultMessage() throws ApiException {
        return messageApi.getDefaultMessage();
    }

    @GET
    @Path("/{name}")
    @Produces({"application/json"})
    public Message getMessage(@PathParam("name") String name) throws ApiException {
        return messageApi.getMessage(name);
    }

    @PUT
    @Path("/greeting")
    @Consumes({"application/json"})
    public void updateGreeting(Message message) throws ApiException {
        messageApi.updateGreeting(message);
    }
}