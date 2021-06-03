package io.helidon.webserver.rsocket.server;

import io.helidon.webserver.rsocket.FireAndForgetHandler;
import io.helidon.webserver.rsocket.RequestChannelHandler;
import io.helidon.webserver.rsocket.RequestResponseHandler;
import io.helidon.webserver.rsocket.RequestStreamHandler;

import java.util.HashMap;
import java.util.Map;

public interface RSocketRouting {

    static Builder builder() {
        return new Builder();
    }

    interface Rules {

        Rules register(RSocketService service);

        Rules register(String pathParam, RSocketService service);

        Rules fireAndForget(FireAndForgetHandler handler);

        Rules fireAndForget(String pathParam, FireAndForgetHandler handler);

        Rules requestChannel(RequestChannelHandler handler);

        Rules requestChannel(String pathParam, RequestChannelHandler handler);

        Rules requestResponse(RequestResponseHandler handler);

        Rules requestResponse(String pathParam, RequestResponseHandler handler);

        Rules requestStream(RequestStreamHandler handler);

        Rules requestStream(String pathParam, RequestStreamHandler handler);

    }

    class Builder implements Rules, io.helidon.common.Builder<RSocketRouting> {

        private final Map<String, RequestResponseHandler<?>> requestResponseRoutes;
        private final Map<String, FireAndForgetHandler> fireAndForgetRoutes;
        private final Map<String, RequestStreamHandler<?>> requestStreamRoutes;
        private final Map<String, RequestChannelHandler<?>> requestChannelRoutes;


        private Builder() {
            this.requestResponseRoutes = new HashMap<>();
            this.fireAndForgetRoutes = new HashMap<>();
            this.requestStreamRoutes = new HashMap<>();
            this.requestChannelRoutes = new HashMap<>();
        }

        @Override
        public RSocketRouting build() {
            return new RSocketRoutingImpl(requestResponseRoutes,
                    fireAndForgetRoutes,
                    requestStreamRoutes,
                    requestChannelRoutes);
        }

        @Override
        public Rules register(RSocketService service) {
            return null;
        }

        @Override
        public Rules register(String pathParam, RSocketService service) {
            return null;
        }

        @Override
        public Rules fireAndForget(FireAndForgetHandler handler) {
            fireAndForgetRoutes.put("", handler);
            return this;
        }

        @Override
        public Rules fireAndForget(String pathParam, FireAndForgetHandler handler) {
            fireAndForgetRoutes.put(pathParam,handler);
            return this;
        }

        @Override
        public Rules requestChannel(RequestChannelHandler handler) {
            requestChannelRoutes.put("",handler);
            return this;
        }

        @Override
        public Rules requestChannel(String pathParam, RequestChannelHandler handler) {
            requestChannelRoutes.put(pathParam, handler);
            return this;
        }

        @Override
        public Rules requestResponse(RequestResponseHandler handler) {
            requestResponseRoutes.put("",handler);
            return this;
        }

        @Override
        public Rules requestResponse(String pathParam, RequestResponseHandler handler) {
            requestResponseRoutes.put(pathParam,handler);
            return this;
        }

        @Override
        public Rules requestStream(RequestStreamHandler handler) {
            requestStreamRoutes.put("",handler);
            return this;
        }

        @Override
        public Rules requestStream(String pathParam, RequestStreamHandler handler) {
            requestStreamRoutes.put(pathParam,handler);
            return this;
        }
    }
}
