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

package io.helidon.microprofile.examples.rsocket.client;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

import io.helidon.common.reactive.Single;
import io.helidon.microprofile.rsocket.client.CustomRSocket;
import io.helidon.rsocket.client.RSocketClient;

import io.rsocket.Payload;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;



/**
 * A simple JAX-RS resource to call RSocket.
 */
@Path("/rs")
@RequestScoped
public class RSocketResource {

    private RSocketClient client;
    private RSocketClient anotherClient;

    /**
     * Construct RSocket resource.
     * @param client client
     * @param anotherClient one more client
     */
    @Inject
    public RSocketResource(RSocketClient client, @CustomRSocket("custom") RSocketClient anotherClient) {
        this.client = client;
        this.anotherClient = anotherClient;
    }

    /**
     * Return a worldly greeting message.
     * @return text
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getDefaultMessage() throws ExecutionException, InterruptedException {
        Single<Payload> payload = client.requestResponse(
                Single.just(ByteBuffer.wrap("Hello World!".getBytes(StandardCharsets.UTF_8))));
        return payload.get().getDataUtf8();
    }

    /**
     * Return a worldly greeting message.
     *
     * @return text
     */
    @GET
    @Path("/another")
    @Produces(MediaType.TEXT_PLAIN)
    public String getAnotherMessage() throws ExecutionException, InterruptedException {
        Single<Payload> payload = anotherClient.requestResponse(
                Single.just(ByteBuffer.wrap("Hello Another World!".getBytes(StandardCharsets.UTF_8))));
        return payload.get().getDataUtf8();
    }
}
