package io.helidon.nima.http2.webclient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.nima.http2.Http2Headers;
import io.helidon.nima.webclient.api.ClientRequest;
import io.helidon.nima.webclient.api.ClientUri;
import io.helidon.nima.webclient.api.HttpClientConfig;
import io.helidon.nima.webclient.api.WebClient;
import io.helidon.nima.webclient.api.WebClientServiceRequest;
import io.helidon.nima.webclient.api.WebClientServiceResponse;

class Http2CallOutputStreamChain extends Http2CallChainBase {
    private final CompletableFuture<WebClientServiceRequest> whenSent;
    private final ClientRequest.OutputStreamHandler streamHandler;

    Http2CallOutputStreamChain(WebClient webClient,
                               Http2ClientRequestImpl http2ClientRequest,
                               HttpClientConfig clientConfig,
                               Http2ClientProtocolConfig protocolConfig,
                               CompletableFuture<WebClientServiceRequest> whenSent,
                               CompletableFuture<WebClientServiceResponse> whenComplete,
                               ClientRequest.OutputStreamHandler streamHandler) {
        super(webClient,
              clientConfig,
              protocolConfig,
              http2ClientRequest,
              whenComplete,
              req -> req.outputStream(streamHandler));

        this.whenSent = whenSent;
        this.streamHandler = streamHandler;
    }

    @Override
    protected WebClientServiceResponse doProceed(WebClientServiceRequest serviceRequest,
                                                 ClientRequestHeaders headers,
                                                 Http2ClientStream stream) {

        ClientUri uri = serviceRequest.uri();
        Http2Headers http2Headers = prepareHeaders(serviceRequest.method(), headers, uri);

        stream.write(http2Headers, false);
        whenSent.complete(serviceRequest);

        Http2ClientStream.ClientOutputStream outputStream;
        try {
            outputStream = stream.outputStream();
            streamHandler.handle(outputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (!outputStream.closed()) {
            throw new IllegalStateException("Output stream was not closed in handler");
        }

        return readResponse(serviceRequest, stream);
    }
}
