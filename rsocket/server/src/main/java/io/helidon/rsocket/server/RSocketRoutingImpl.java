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
     * @param requestResponseRoutes
     * @param fireAndForgetRoutes
     * @param requestStreamRoutes
     * @param requestChannelRoutes
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
