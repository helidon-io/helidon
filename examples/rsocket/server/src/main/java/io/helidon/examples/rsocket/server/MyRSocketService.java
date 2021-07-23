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

package io.helidon.examples.rsocket.server;

import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.rsocket.server.RSocketRouting;
import io.helidon.rsocket.server.RSocketService;

import io.rsocket.Payload;
import io.rsocket.util.ByteBufPayload;

/**
 * Sample RSocket service.
 */
public class MyRSocketService implements RSocketService {

    @Override
    public void update(RSocketRouting.Rules rules) {
        rules.fireAndForget("print", this::printPayload);
        rules.requestResponse("print", this::printAndRespond);
        rules.requestStream("print", this::printStream);
        rules.requestChannel("print", this::printChannel);
        rules.requestResponse(this::printAndRespondNoRoute);
    }


    private Single<Void> printPayload(Payload payload) {
        System.out.println("Payload: " + payload.getDataUtf8());
        return Single.empty();
    }

    private Single<Payload> printAndRespond(Payload payload){
        System.out.println("Received: " + payload.getDataUtf8());
        return Single.just(ByteBufPayload.create("backfire!"));
    }

    private Single<Payload> printAndRespondNoRoute(Payload payload){
        System.out.println("Received no route: " + payload.getDataUtf8());
        return Single.just(ByteBufPayload.create("backfire no route!"));
    }

    private Multi<Payload> printStream(Payload payload){
        System.out.println("Print Stream");
        String data = payload.getDataUtf8();
        return Multi.range(1, 10).map(e->ByteBufPayload.create(e + ": " + data));
    }

    private Multi<Payload> printChannel(Multi<Payload> payloads) {
        System.out.println("Print Channel");
        return payloads.map(Payload::getDataUtf8).log()
                .onCompleteResumeWith(Multi.range(1, 10)
                        .map(Object::toString)).map(e->ByteBufPayload.create("" + e));
    }
}
