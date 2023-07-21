package io.helidon.nima.http2.webclient;

import io.helidon.nima.webclient.api.ClientUri;
import io.helidon.nima.webclient.api.ConnectionKey;
import io.helidon.nima.webclient.api.HttpClientConfig;
import io.helidon.nima.webclient.api.HttpClientResponse;
import io.helidon.nima.webclient.api.WebClient;
import io.helidon.nima.webclient.api.WebClientServiceRequest;
import io.helidon.nima.webclient.api.WebClientServiceResponse;
import io.helidon.nima.webclient.spi.WebClientService;

abstract class Http2CallChainBase implements WebClientService.Chain {
    private final WebClient webClient;
    private final HttpClientConfig clientConfig;
    private final Http2ClientProtocolConfig protocolConfig;
    private final Http2ClientRequestImpl clientRequest;

    Http2CallChainBase(WebClient webClient,
                       HttpClientConfig clientConfig,
                       Http2ClientProtocolConfig protocolConfig,
                       Http2ClientRequestImpl clientRequest) {

        this.webClient = webClient;
        this.clientConfig = clientConfig;
        this.protocolConfig = protocolConfig;
        this.clientRequest = clientRequest;
    }

    @Override
    public WebClientServiceResponse proceed(WebClientServiceRequest serviceRequest) {
        /*
        if (explicitConnection) {
          use connection cache with explicit connection (e.g. upgrade/prior-knowledge/TLS ALPN)
        } else {
          use connection cache
          if upgrade & fails - use response from HTTP/1.1, and just wrap it
          else store stream
        }
       */
        Http2ConnectionAttemptResult result = ConnectionCache.newStream(webClient,
                                                                        protocolConfig,
                                                                        connectionKey(serviceRequest),
                                                                        clientRequest,
                                                                        serviceRequest.uri());

        if (result.result() == Http2ConnectionAttemptResult.Result.HTTP_2) {
            return doProceed(serviceRequest, result.stream());
        } else {
            return doProceed(serviceRequest, result.response());
        }
    }

    protected abstract WebClientServiceResponse doProceed(WebClientServiceRequest serviceRequest, HttpClientResponse response);

    protected abstract WebClientServiceResponse doProceed(WebClientServiceRequest serviceRequest, Http2ClientStream stream);

    private ConnectionKey connectionKey(WebClientServiceRequest serviceRequest) {
        ClientUri uri = serviceRequest.uri();
        return new ConnectionKey(uri.scheme(),
                                 uri.host(),
                                 uri.port(),
                                 clientRequest.tls(),
                                 clientConfig.dnsResolver(),
                                 clientConfig.dnsAddressLookup(),
                                 clientRequest.proxy());
    }
}
