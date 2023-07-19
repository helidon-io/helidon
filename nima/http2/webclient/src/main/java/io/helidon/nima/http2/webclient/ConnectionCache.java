package io.helidon.nima.http2.webclient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import io.helidon.common.socket.SocketOptions;
import io.helidon.nima.webclient.api.ClientUri;
import io.helidon.nima.webclient.api.ConnectionKey;

class ConnectionCache {
    //todo Gracefully close connections in channel cache
    private static final Map<ConnectionKey, Http2ClientConnectionHandler> CHANNEL_CACHE = new ConcurrentHashMap<>();

    static Http2ClientStream newStream(ExecutorService executorService,
                                       ConnectionKey connectionKey,
                                       boolean priorKnowledge,
                                       ClientUri uri) {
        // this statement locks all threads - must not do anything complicated (just create a new instance)
        return CHANNEL_CACHE.computeIfAbsent(connectionKey,
                                             key -> new Http2ClientConnectionHandler(executorService,
                                                                                     SocketOptions.builder().build(),
                                                                                     uri.path(),
                                                                                     key))
                // this statement may block a single connection key
                .newStream(new ConnectionContext(priority,
                                                 priorKnowledge,
                                                 initialWindowSize,
                                                 maxFrameSize,
                                                 maxHeaderListSize,
                                                 connectionPrefetch,
                                                 requestPrefetch,
                                                 flowControlTimeout,
                                                 timeout));
    }
}
