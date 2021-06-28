package io.helidon.microprofile.examples.rsocket;

import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.microprofile.rsocket.cdi.FireAndForget;
import io.helidon.microprofile.rsocket.cdi.RSocket;
import io.helidon.microprofile.rsocket.cdi.RequestChannel;
import io.helidon.microprofile.rsocket.cdi.RequestResponse;
import io.helidon.microprofile.rsocket.cdi.RequestStream;
import io.rsocket.Payload;
import io.rsocket.util.ByteBufPayload;

import javax.enterprise.context.ApplicationScoped;

@RSocket(path = "/mypath")
@ApplicationScoped
public class MyRSocketService {

    @FireAndForget(route = "print")
    public Single<Void> printPayload(Payload payload) {
        System.out.println("Payload: " + payload.getDataUtf8());
        return Single.empty();
    }

    @FireAndForget(route = "print2")
    public Single<Void> printPayload2(Payload payload) {
        System.out.println("Second Payload: " + payload.getDataUtf8());
        return Single.empty();
    }

    @RequestResponse(route = "print")
    public Single<Payload> printAndRespond(Payload payload){
        System.out.println("received: " +payload.getDataUtf8());
        return Single.just(ByteBufPayload.create("backfire!"));
    }

    @RequestStream(route = "print")
    public Multi<Payload> printStream(Payload payload){
        String data = payload.getDataUtf8();
        return Multi.range(1,10).map(e->ByteBufPayload.create(e+": "+data));
    }

    @RequestChannel(route = "print")
    public Multi<Payload> printChannel(Multi<Payload> payloads) {
        System.out.println("Hello!");
        return payloads.map(Payload::getDataUtf8).log()
                .onCompleteResumeWith(Multi.range(1,10)
                        .map(Object::toString)).map(e->ByteBufPayload.create(""+e));
    }

}
