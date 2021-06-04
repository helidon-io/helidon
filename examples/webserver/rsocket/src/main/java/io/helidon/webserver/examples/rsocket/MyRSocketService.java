package io.helidon.webserver.examples.rsocket;

import io.helidon.common.reactive.Single;
import io.helidon.webserver.rsocket.server.RSocketRouting;
import io.helidon.webserver.rsocket.server.RSocketService;
import io.netty.buffer.ByteBuf;
import io.rsocket.Payload;
import io.rsocket.util.ByteBufPayload;

public class MyRSocketService implements RSocketService {

    @Override
    public void update(RSocketRouting.Rules rules) {
        rules.fireAndForget("print",this::printPayload);
    }


    private Single<Void> printPayload(Payload payload) {
        System.out.println("Payload: " + payload.getDataUtf8());
        return Single.empty();
    }

}
