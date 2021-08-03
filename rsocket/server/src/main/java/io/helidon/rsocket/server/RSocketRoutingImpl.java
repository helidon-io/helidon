/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import java.util.Map;

/**
 * Implementation of RSocketRouting.
 */
public class RSocketRoutingImpl implements RSocketRouting {

    private final Map<String, RequestResponseHandler> requestResponseRoutes;
    private final Map<String, FireAndForgetHandler> fireAndForgetRoutes;
    private final Map<String, RequestStreamHandler> requestStreamRoutes;
    private final Map<String, RequestChannelHandler> requestChannelRoutes;

    /**
     * Constructor for RSocketRouting.
     *
     * @param requestResponseRoutes Map
     * @param fireAndForgetRoutes Map
     * @param requestStreamRoutes Map
     * @param requestChannelRoutes Map
     */
    RSocketRoutingImpl(Map<String, RequestResponseHandler> requestResponseRoutes,
                              Map<String, FireAndForgetHandler> fireAndForgetRoutes,
                              Map<String, RequestStreamHandler> requestStreamRoutes,
                              Map<String, RequestChannelHandler> requestChannelRoutes) {
        this.requestResponseRoutes = requestResponseRoutes;
        this.fireAndForgetRoutes = fireAndForgetRoutes;
        this.requestStreamRoutes = requestStreamRoutes;
        this.requestChannelRoutes = requestChannelRoutes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, RequestResponseHandler> requestResponseRoutes() {
        return requestResponseRoutes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, FireAndForgetHandler> fireAndForgetRoutes() {
        return fireAndForgetRoutes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, RequestStreamHandler> requestStreamRoutes() {
        return requestStreamRoutes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, RequestChannelHandler> requestChannelRoutes() {
        return requestChannelRoutes;
    }

}
