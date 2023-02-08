package io.helidon.nima.sse.webserver;

import java.util.function.Consumer;

import io.helidon.common.GenericType;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.nima.webserver.http.spi.Sink;
import io.helidon.nima.webserver.http.spi.SinkProvider;
import io.helidon.nima.webserver.http1.Http1ServerResponse;

public class SseSinkProvider implements SinkProvider<SseEvent, SseSink> {

    @Override
    public boolean supports(GenericType<? extends Sink<SseEvent>> type) {
        return type == SseSink.TYPE;
    }

    @Override
    public SseSink create(ServerResponse response, Consumer<Object> eventConsumer, Runnable closeRunnable) {
        if (response instanceof Http1ServerResponse res) {
            return new SseSink(res, eventConsumer, closeRunnable);
        }
        throw new IllegalArgumentException("SseSink can only be created from an HTTP/1 response");
    }
}
