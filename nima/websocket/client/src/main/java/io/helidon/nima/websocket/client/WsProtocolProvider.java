package io.helidon.nima.websocket.client;

import io.helidon.nima.webclient.api.WebClient;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.spi.ProtocolProvider;

public class WsProtocolProvider implements ProtocolProvider<WsClient, WsClientProtocolConfig> {
    static final String CONFIG_KEY = "websocket";

    @Override
    public String protocolId() {
        return "ws";
    }

    @Override
    public Class<WsClientProtocolConfig> configType() {
        return WsClientProtocolConfig.class;
    }

    @Override
    public WsClientProtocolConfig defaultConfig() {
        return WsClientProtocolConfig.create();
    }

    @Override
    public WsClient protocol(WebClient client, WsClientProtocolConfig config) {
        return new WsClientImpl(client,
                                client.client(Http1Client.PROTOCOL),
                                WsClientConfig.builder().from(client.prototype())
                                        .protocolConfig(config)
                                        .buildPrototype());
    }
}
