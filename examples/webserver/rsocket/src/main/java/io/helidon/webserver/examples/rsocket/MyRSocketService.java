package io.helidon.webserver.examples.rsocket;

import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.webserver.rsocket.server.RSocketRouting;
import io.helidon.webserver.rsocket.server.RSocketService;
import io.rsocket.Payload;
import io.rsocket.util.ByteBufPayload;

public class MyRSocketService implements RSocketService {

    @Override
    public void update(RSocketRouting.Rules rules) {
        rules.fireAndForget("print",this::printPayload);
        rules.requestResponse("print",this::printAndRespond);
        rules.requestStream("print",this::printStream);
        rules.requestChannel("print",this::printChannel);
    }


    private Single<Void> printPayload(Payload payload) {
        System.out.println("Payload: " + payload.getDataUtf8());
        return Single.empty();
    }

    private Single<Payload> printAndRespond(Payload payload){
        System.out.println("received: " +payload.getDataUtf8());
        return Single.just(ByteBufPayload.create("backfire!"));
    }

    private Multi<Payload> printStream(Payload payload){
        String data = payload.getDataUtf8();
        return Multi.range(1,10).map(e->ByteBufPayload.create(e+": "+data));
    }

    private Multi<Payload> printChannel(Multi<Payload> payloads) {
        System.out.println("Hello!");
        return payloads.map(Payload::getDataUtf8).log()
                .onCompleteResumeWith(Multi.range(1,10)
                        .map(Object::toString)).map(e->ByteBufPayload.create(""+e));
    }

}
