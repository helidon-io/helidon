package io.helidon.nima.http2.webclient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import io.helidon.common.configurable.LruCache;
import io.helidon.nima.webclient.api.ClientUri;
import io.helidon.nima.webclient.api.ConnectionKey;
import io.helidon.nima.webclient.api.WebClient;
import io.helidon.nima.webclient.http1.Http1ClientRequest;
import io.helidon.nima.webclient.http1.Http1ClientResponse;

class ConnectionCache {
    //todo Gracefully close connections in channel cache
    private static final LruCache<ConnectionKey, Boolean> HTTP2_SUPPORTED = LruCache.<ConnectionKey, Boolean>builder()
            .capacity(1000)
            .build();
    private static final Map<ConnectionKey, Http2ClientConnectionHandler> CHANNEL_CACHE = new ConcurrentHashMap<>();

    static boolean supports(ConnectionKey ck) {
        return HTTP2_SUPPORTED.get(ck).isPresent();
    }

    static void clear() {
        HTTP2_SUPPORTED.clear();
        CHANNEL_CACHE.forEach((c, c2) -> c2.close());
    }

    static Http2ConnectionAttemptResult newStream(WebClient webClient,
                                                  Http2ClientProtocolConfig protocolConfig,
                                                  ConnectionKey connectionKey,
                                                  Http2ClientRequestImpl request,
                                                  ClientUri initialUri,
                                                  Function<Http1ClientRequest, Http1ClientResponse> http1EntityHandler) {

        // this statement locks all threads - must not do anything complicated (just create a new instance)
        Http2ConnectionAttemptResult result = CHANNEL_CACHE.computeIfAbsent(connectionKey,
                                             Http2ClientConnectionHandler::new)
                // this statement may block a single connection key
                .newStream(webClient,
                           protocolConfig,
                           request,
                           initialUri,
                           http1EntityHandler);
        if (result.result() == Http2ConnectionAttemptResult.Result.HTTP_2) {
            HTTP2_SUPPORTED.put(connectionKey, true);
        }
        return result;
    }
}
