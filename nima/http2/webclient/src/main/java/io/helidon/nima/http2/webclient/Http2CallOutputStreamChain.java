package io.helidon.nima.http2.webclient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.http.WritableHeaders;
import io.helidon.nima.http2.Http2Headers;
import io.helidon.nima.webclient.api.ClientRequest;
import io.helidon.nima.webclient.api.HttpClientConfig;
import io.helidon.nima.webclient.api.WebClient;
import io.helidon.nima.webclient.api.WebClientServiceRequest;
import io.helidon.nima.webclient.api.WebClientServiceResponse;

class Http2CallOutputStreamChain extends Http2CallChainBase {
    Http2CallOutputStreamChain(WebClient webClient,
                                      Http2ClientRequestImpl http2ClientRequest,
                                      HttpClientConfig clientConfig,
                                      Http2ClientProtocolConfig protocolConfig,
                                      CompletableFuture<WebClientServiceRequest> whenSent,
                                      CompletableFuture<WebClientServiceResponse> whenComplete,
                                      ClientRequest.OutputStreamHandler streamHandler) {
    }

    @Override
    public WebClientServiceResponse proceed(WebClientServiceRequest clientRequest) {
        // todo validate request ok

        WritableHeaders<?> headers = WritableHeaders.create(explicitHeaders);

        Http2ClientStream stream = reserveStream();

        Http2Headers http2Headers = prepareHeaders(headers);

        stream.write(http2Headers, false);

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

        return readResponse(stream);
    }
}
