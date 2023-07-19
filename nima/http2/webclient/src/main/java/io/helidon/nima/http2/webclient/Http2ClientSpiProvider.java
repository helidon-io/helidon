package io.helidon.nima.http2.webclient;

import io.helidon.nima.webclient.api.WebClient;
import io.helidon.nima.webclient.spi.HttpClientSpi;
import io.helidon.nima.webclient.spi.HttpClientSpiProvider;

public class Http2ClientSpiProvider implements HttpClientSpiProvider<Http2ClientProtocolConfig> {
    @Override
    public String protocolId() {
        return Http2Client.PROTOCOL_ID;
    }

    @Override
    public Class<Http2ClientProtocolConfig> configType() {
        return Http2ClientProtocolConfig.class;
    }

    @Override
    public Http2ClientProtocolConfig defaultConfig() {
        return Http2ClientProtocolConfig.create();
    }

    @Override
    public HttpClientSpi protocol(WebClient client, Http2ClientProtocolConfig config) {
        return new Http2ClientImpl(client,
                                   Http2ClientConfig.builder()
                                           .from(client.prototype())
                                           .protocolConfig(config)
                                           .buildPrototype());
    }
}
