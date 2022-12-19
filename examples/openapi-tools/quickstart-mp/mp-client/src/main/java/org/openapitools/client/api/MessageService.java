package org.openapitools.client.api;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.openapitools.client.model.Message;

@Path("/greet")
@ApplicationScoped
public class MessageService {

    @Inject
    @RestClient
    private MessageApi messageApi;


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
