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
package io.helidon.examples.messaging.mp;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.messaging.connectors.jms.JmsMessage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseBroadcaster;
import jakarta.ws.rs.sse.SseEventSink;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.glassfish.jersey.media.sse.OutboundEvent;

/**
 * SSE Jax-Rs resource for message publishing and consuming.
 */
@Path("/frank")
@ApplicationScoped
public class FrankResource {

    @Inject
    @Channel("to-wls")
    private Emitter<String> emitter;
    private SseBroadcaster sseBroadcaster;

    /**
     * Consuming JMS messages from Weblogic and sending them to the client over SSE.
     *
     * @param msg dequeued message
     * @return completion stage marking end of the processing
     */
    @Incoming("from-wls")
    public CompletionStage<Void> receive(JmsMessage<String> msg) {
        if (sseBroadcaster == null) {
            System.out.println("No SSE client subscribed yet: " + msg.getPayload());
            return CompletableFuture.completedStage(null);
        }
        sseBroadcaster.broadcast(new OutboundEvent.Builder().data(msg.getPayload()).build());
        return CompletableFuture.completedStage(null);
    }

    /**
     * Send message to Weblogic JMS queue.
     *
     * @param msg message to be sent
     */
    @POST
    @Path("/send/{msg}")
    public void send(@PathParam("msg") String msg) {
        emitter.send(msg);
    }

    /**
     * Register SSE client to listen for messages coming from Weblogic JMS.
     *
     * @param eventSink client sink
     * @param sse       SSE context
     */
    @GET
    @Path("sse")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void listenToEvents(@Context SseEventSink eventSink, @Context Sse sse) {
        if (sseBroadcaster == null) {
            sseBroadcaster = sse.newBroadcaster();
        }
        sseBroadcaster.register(eventSink);
    }
}
