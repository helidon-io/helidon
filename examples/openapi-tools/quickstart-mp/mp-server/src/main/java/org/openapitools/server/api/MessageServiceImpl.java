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

package org.openapitools.server.api;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import org.openapitools.server.model.Message;

/**
 * MessageService implementation.
 */
@ApplicationScoped
@Path("/greet")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaHelidonServerCodegen", date = "2022-10-25T09:58"
        + ":58.439110277+02:00[Europe/Prague]")
public class MessageServiceImpl implements MessageService {

    private final AtomicReference<Message> defaultMessage = new AtomicReference<>();

    public MessageServiceImpl() {
        Message message = new Message();
        message.setMessage("World");
        message.setGreeting("Hello");
        defaultMessage.set(message);
    }

    @GET
    @Produces({"application/json"})
    public Message getDefaultMessage() {
        return defaultMessage.get();
    }

    @GET
    @Path("/{name}")
    @Produces({"application/json"})
    public Message getMessage(@PathParam("name") String name) {
        Message result = new Message();
        return result.message(name).greeting(defaultMessage.get().getGreeting());
    }

    @PUT
    @Path("/greeting")
    @Consumes({"application/json"})
    public void updateGreeting(@Valid @NotNull Message message) {
        defaultMessage.set(message);
        Response.status(Response.Status.NO_CONTENT).build();
    }
}
