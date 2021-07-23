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
package io.helidon.microprofile.examples.rsocket.server;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;

import io.helidon.common.reactive.Multi;
import io.helidon.microprofile.rsocket.server.FireAndForget;
import io.helidon.microprofile.rsocket.server.RSocket;
import io.helidon.microprofile.rsocket.server.RequestChannel;
import io.helidon.microprofile.rsocket.server.RequestResponse;
import io.helidon.microprofile.rsocket.server.RequestStream;

import io.rsocket.Payload;
import io.rsocket.util.ByteBufPayload;


/**
 * Example RSocket endpoint.
 */
@RSocket("/another")
@ApplicationScoped
public class AnotherRSocketService {

    /**
     * Fire and Forget sample.
     * @param payload Payload
     * @return CompletableFuture
     */
    @FireAndForget("print")
    public CompletableFuture<Void> printPayload(Payload payload) {
        System.out.println("Payload from another: " + payload.getDataUtf8());
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Fire and Forget sample with different routing.
     * @param payload Payload
     * @return CompletableFuture
     */
    @FireAndForget("print2")
    public CompletableFuture<Void> printPayload2(Payload payload) {
        System.out.println("Second Payload from another: " + payload.getDataUtf8());
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Request Response sample.
     * @param payload Payload
     * @return CompletableFuture
     */
    @RequestResponse("print")
    public CompletableFuture<Payload> printAndRespond(Payload payload){
        System.out.println("Received from another: " + payload.getDataUtf8());
        return CompletableFuture.supplyAsync(()->ByteBufPayload.create("Another backfire!"));
    }

    /**
     * Request Response sample without routing.
     * @param payload
     * @return CompletableFuture
     */
    @RequestResponse()
    public CompletableFuture<Payload> requestNoRoute(Payload payload){
        System.out.println("Request no route: " + payload.getDataUtf8());
        return CompletableFuture.supplyAsync(()->ByteBufPayload.create("Another backfire!"));
    }

    /**
     * Request Stream sample.
     * @param payload Payload
     * @return CompletableFuture
     */
    @RequestStream("stream")
    public Stream<Payload> printStream(Payload payload){
        String data = payload.getDataUtf8();
        return IntStream.range(1, 10).mapToObj(e->ByteBufPayload.create(e + ": " + data));
    }

    /**
     * Request Channel sample.
     * @param payloads
     * @return CompletableFuture
     */
    //There is no good way to convert to streams. Flow.Publisher used.
    @RequestChannel("channel")
    public Flow.Publisher<Payload> printChannel(Flow.Publisher<Payload> payloads) {
        return Multi.create(payloads).map(Payload::getDataUtf8).log()
                .onCompleteResumeWith(Multi.range(1, 10)
                        .map(Object::toString)).map(e->ByteBufPayload.create("" + e));
    }

}
