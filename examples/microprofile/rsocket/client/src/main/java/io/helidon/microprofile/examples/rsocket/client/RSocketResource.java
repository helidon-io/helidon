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

package io.helidon.microprofile.examples.rsocket.client;

import io.helidon.common.reactive.Single;
import io.helidon.microprofile.rsocket.client.CustomRSocket;
import io.helidon.rsocket.client.RSocketClient;
import io.rsocket.Payload;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * A simple JAX-RS resource to call RSocket.
 */
@Path("/rs")
@RequestScoped
public class RSocketResource {

    private RSocketClient client;
    private RSocketClient anotherClient;

    @Inject
    public RSocketResource(RSocketClient client, @CustomRSocket("custom")RSocketClient anotherClient) {
        this.client = client;
        this.anotherClient = anotherClient;
    }

    /**
     * Return a worldly greeting message.
     *
     * @return text
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getDefaultMessage() throws ExecutionException, InterruptedException {
        Single<Payload> payload = client.requestResponse(Single.just(ByteBuffer.wrap("Hello World!".getBytes(StandardCharsets.UTF_8))));
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
        Single<Payload> payload = anotherClient.requestResponse(Single.just(ByteBuffer.wrap("Hello Another World!".getBytes(StandardCharsets.UTF_8))));
        return payload.get().getDataUtf8();
    }
}
