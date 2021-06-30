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

@RSocket(path = "/another")
@ApplicationScoped
public class AnotherRSocketService {

    @FireAndForget(route = "print")
    public Single<Void> printPayload(Payload payload) {
        System.out.println("Payload from another: " + payload.getDataUtf8());
        return Single.empty();
    }

    @FireAndForget(route = "print2")
    public Single<Void> printPayload2(Payload payload) {
        System.out.println("Second Payload from another: " + payload.getDataUtf8());
        return Single.empty();
    }

    @RequestResponse(route = "print")
    public Single<Payload> printAndRespond(Payload payload){
        System.out.println("received from another: " +payload.getDataUtf8());
        return Single.just(ByteBufPayload.create("Another backfire!"));
    }

    @RequestStream(route = "stream")
    public Multi<Payload> printStream(Payload payload){
        String data = payload.getDataUtf8();
        return Multi.range(1,10).map(e->ByteBufPayload.create(e+": "+data));
    }

    @RequestChannel(route = "channel")
    public Multi<Payload> printChannel(Multi<Payload> payloads) {
        return payloads.map(Payload::getDataUtf8).log()
                .onCompleteResumeWith(Multi.range(1,10)
                        .map(Object::toString)).map(e->ByteBufPayload.create(""+e));
    }

}
