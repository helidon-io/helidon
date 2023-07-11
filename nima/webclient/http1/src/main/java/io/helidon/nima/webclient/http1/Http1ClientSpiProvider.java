package io.helidon.nima.webclient.http1;

import io.helidon.nima.webclient.api.WebClient;
import io.helidon.nima.webclient.spi.HttpClientSpi;
import io.helidon.nima.webclient.spi.HttpClientSpiProvider;

public class Http1ClientSpiProvider implements HttpClientSpiProvider<Http1ClientProtocolConfig> {
    @Override
    public String protocolId() {
        return Http1ProtocolProvider.PROTOCOL_ID;
    }

    @Override
    public Class<Http1ClientProtocolConfig> configType() {
        return Http1ClientProtocolConfig.class;
    }

    @Override
    public Http1ClientProtocolConfig defaultConfig() {
        return Http1ClientProtocolConfig.create();
    }

    @Override
    public HttpClientSpi protocol(WebClient client, Http1ClientProtocolConfig config) {
        return new Http1ClientImpl(Http1ClientConfig.builder()
                                           .from(client.prototype())
                                           .protocolConfig(config)
                                           .buildPrototype());
    }
}
