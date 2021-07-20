package io.helidon.rsocket.server;

import java.util.HashMap;
import java.util.Map;

/**
 * RSocket routing.
 */
public interface RSocketRouting {

    /**
     * Builder for RSocket routing.
     *
     * @return Builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Get all Request Response routes.
     *
     * @return Map with routes.
     */
    Map<String, RequestResponseHandler> requestResponseRoutes();

    /**
     * Get all Fire and Forget routes.
     *
     * @return Map with routes.
     */
    Map<String, FireAndForgetHandler> fireAndForgetRoutes();

    /**
     * Get all Request Stream routes.
     *
     * @return Map with routes.
     */
    Map<String, RequestStreamHandler> requestStreamRoutes();

    /**
     * Get all Request Channel routes.
     *
     * @return Map with routes.
     */
    Map<String, RequestChannelHandler> requestChannelRoutes();


    /**
     * Rules for RSocket routing.
     */
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

    /**
     * Builder class for RSocket Routing.
     */
    class Builder implements Rules, io.helidon.common.Builder<RSocketRouting> {

        private final Map<String, RequestResponseHandler> requestResponseRoutes;
        private final Map<String, FireAndForgetHandler> fireAndForgetRoutes;
        private final Map<String, RequestStreamHandler> requestStreamRoutes;
        private final Map<String, RequestChannelHandler> requestChannelRoutes;


        private Builder() {
            this.requestResponseRoutes = new HashMap<>();
            this.fireAndForgetRoutes = new HashMap<>();
            this.requestStreamRoutes = new HashMap<>();
            this.requestChannelRoutes = new HashMap<>();
        }

        /**
         * Return RSocketRouting implementation.
         *
         * @return {@link RSocketRouting}
         */
        @Override
        public RSocketRouting build() {
            return new RSocketRoutingImpl(requestResponseRoutes,
                    fireAndForgetRoutes,
                    requestStreamRoutes,
                    requestChannelRoutes);
        }

        /**
         * Register {@link RSocketService} service.
         *
         * @param service
         * @return Builder
         */
        @Override
        public Builder register(RSocketService service) {
            service.update(this);
            return this;
        }

        /**
         * Register {@link RSocketService} service.
         *
         * @param pathParam
         * @param service
         * @return Builder
         */
        @Override
        public Builder register(String pathParam, RSocketService service) {
            service.update(this);
            return this;
        }

        /**
         * Register Fire and Forget method handler.
         *
         * @param handler
         * @return Builder
         */
        @Override
        public Builder fireAndForget(FireAndForgetHandler handler) {
            fireAndForgetRoutes.put("", handler);
            return this;
        }

        /**
         * Register Fire and Forget method handler.
         *
         * @param handler
         * @param pathParam
         * @return Builder
         */
        @Override
        public Builder fireAndForget(String pathParam, FireAndForgetHandler handler) {
            fireAndForgetRoutes.put(pathParam, handler);
            return this;
        }

        /**
         * Register Request Channel method handler.
         *
         * @param handler
         * @return Builder
         */
        @Override
        public Builder requestChannel(RequestChannelHandler handler) {
            requestChannelRoutes.put("", handler);
            return this;
        }

        /**
         * Register Request Channel method handler.
         *
         * @param handler
         * @param pathParam
         * @return Builder
         */
        @Override
        public Builder requestChannel(String pathParam, RequestChannelHandler handler) {
            requestChannelRoutes.put(pathParam, handler);
            return this;
        }

        /**
         * Register Request Response method handler.
         *
         * @param handler
         * @return Builder
         */
        @Override
        public Builder requestResponse(RequestResponseHandler handler) {
            requestResponseRoutes.put("", handler);
            return this;
        }

        /**
         * Register Request Response method handler.
         *
         * @param handler
         * @param pathParam
         * @return Builder
         */
        @Override
        public Builder requestResponse(String pathParam, RequestResponseHandler handler) {
            requestResponseRoutes.put(pathParam, handler);
            return this;
        }

        /**
         * Register Request Stream method handler.
         *
         * @param handler
         * @return Builder
         */
        @Override
        public Builder requestStream(RequestStreamHandler handler) {
            requestStreamRoutes.put("", handler);
            return this;
        }

        /**
         * Register Request Stream method handler.
         *
         * @param handler
         * @param pathParam
         * @return Builder
         */
        @Override
        public Builder requestStream(String pathParam, RequestStreamHandler handler) {
            requestStreamRoutes.put(pathParam, handler);
            return this;
        }
    }
}
