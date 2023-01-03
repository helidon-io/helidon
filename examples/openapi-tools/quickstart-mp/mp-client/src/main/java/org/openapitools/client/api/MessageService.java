/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

/**
 * MessageService.
 */
@Path("/greet")
@ApplicationScoped
public class MessageService {

    @Inject
    @RestClient
    private MessageApi messageApi;


    /**
     * Return a worldly greeting message.
     *
     * @return a worldly greeting message.
     * @throws ApiException
     */
    @GET
    @Produces({"application/json"})
    public Message getDefaultMessage() throws ApiException {
        return messageApi.getDefaultMessage();
    }

    /**
     * Return a greeting message using the name that was provided.
     *
     * @param name name for message.
     * @return a greeting message.
     * @throws ApiException
     */
    @GET
    @Path("/{name}")
    @Produces({"application/json"})
    public Message getMessage(@PathParam("name") String name) throws ApiException {
        return messageApi.getMessage(name);
    }

    /**
     * Set the greeting to use in future messages.
     *
     * @param message message to set.
     * @throws ApiException
     */
    @PUT
    @Path("/greeting")
    @Consumes({"application/json"})
    public void updateGreeting(Message message) throws ApiException {
        messageApi.updateGreeting(message);
    }
}
