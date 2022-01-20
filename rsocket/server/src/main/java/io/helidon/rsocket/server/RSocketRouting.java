/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

        /**
         * Register an RSocket service.
         *
         * @param service {@link RSocketService}
         * @return {@link Rules}
         */
        Rules register(RSocketService service);

        /**
         *  Register an RSocket service.
         *
         * @param pathParam String
         * @param service {@link RSocketService}
         * @return {@link Rules}
         */
        Rules register(String pathParam, RSocketService service);

        /**
         * Assign Fire And Forget handler.
         * @param handler {@link FireAndForgetHandler}
         * @return {@link Rules}
         */
        Rules fireAndForget(FireAndForgetHandler handler);

        /**
         * Assign Fire And Forget handler with path param.
         *
         * @param pathParam String
         * @param handler {@link FireAndForgetHandler}
         * @return {@link Rules}
         */
        Rules fireAndForget(String pathParam, FireAndForgetHandler handler);

        /**
         * Assign Request Channel handler.
         *
         * @param handler {@link RequestChannelHandler}
         * @return {@link Rules}
         */
        Rules requestChannel(RequestChannelHandler handler);

        /**
         * Assign Request Channel handler with path param.
         *
         * @param pathParam String
         * @param handler {@link RequestChannelHandler}
         * @return {@link Rules}
         */
        Rules requestChannel(String pathParam, RequestChannelHandler handler);

        /**
         * Assign Request Response handler.
         *
         * @param handler {@link RequestResponseHandler}
         * @return {@link Rules}
         */
        Rules requestResponse(RequestResponseHandler handler);

        /**
         * Assign Request Response handler with param.
         *
         * @param pathParam String
         * @param handler {@link RequestResponseHandler}
         * @return {@link Rules}
         */
        Rules requestResponse(String pathParam, RequestResponseHandler handler);

        /**
         * Assign Request Stream handler.
         *
         * @param handler {@link RequestStreamHandler}
         * @return {@link Rules}
         */
        Rules requestStream(RequestStreamHandler handler);

        /**
         * Assign Request Stream handler with param.
         *
         * @param pathParam String
         * @param handler {@link RequestStreamHandler}
         * @return {@link Rules}
         */
        Rules requestStream(String pathParam, RequestStreamHandler handler);


    }

    /**
     * Builder class for RSocket Routing.
     */
    class Builder implements Rules, io.helidon.common.Builder {

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
         * @param service RSocketService
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
         * @param pathParam String
         * @param service RSocketService
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
         * @param handler FireAndForgetHandler
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
         * @param handler FireAndForgetHandler
         * @param pathParam String
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
         * @param handler RequestChannelHandler
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
         * @param handler RequestChannelHandler
         * @param pathParam String
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
         * @param handler RequestResponseHandler
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
         * @param handler RequestResponseHandler
         * @param pathParam String
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
         * @param handler RequestStreamHandler
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
         * @param handler RequestStreamHandler
         * @param pathParam String
         * @return Builder
         */
        @Override
        public Builder requestStream(String pathParam, RequestStreamHandler handler) {
            requestStreamRoutes.put(pathParam, handler);
            return this;
        }
    }
}
