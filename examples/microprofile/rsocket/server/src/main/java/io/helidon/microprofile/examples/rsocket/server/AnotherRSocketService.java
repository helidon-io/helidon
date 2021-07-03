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

import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.microprofile.rsocket.server.FireAndForget;
import io.helidon.microprofile.rsocket.server.RSocket;
import io.helidon.microprofile.rsocket.server.RequestChannel;
import io.helidon.microprofile.rsocket.server.RequestResponse;
import io.helidon.microprofile.rsocket.server.RequestStream;
import io.rsocket.Payload;
import io.rsocket.util.ByteBufPayload;

import javax.enterprise.context.ApplicationScoped;

/**
 * Example RSocket endpoint.
 */
@RSocket("/another")
@ApplicationScoped
public class AnotherRSocketService {

    @FireAndForget("print")
    public Single<Void> printPayload(Payload payload) {
        System.out.println("Payload from another: " + payload.getDataUtf8());
        return Single.empty();
    }

    @FireAndForget("print2")
    public Single<Void> printPayload2(Payload payload) {
        System.out.println("Second Payload from another: " + payload.getDataUtf8());
        return Single.empty();
    }

    @RequestResponse("request-response")
    public Single<Payload> printAndRespond(Payload payload){
        System.out.println("received from another: " +payload.getDataUtf8());
        return Single.just(ByteBufPayload.create("Another backfire!"));
    }

    @RequestStream("stream")
    public Multi<Payload> printStream(Payload payload){
        String data = payload.getDataUtf8();
        return Multi.range(1,10).map(e->ByteBufPayload.create(e+": "+data));
    }

    @RequestChannel("channel")
    public Multi<Payload> printChannel(Multi<Payload> payloads) {
        return payloads.map(Payload::getDataUtf8).log()
                .onCompleteResumeWith(Multi.range(1,10)
                        .map(Object::toString)).map(e->ByteBufPayload.create(""+e));
    }

}
