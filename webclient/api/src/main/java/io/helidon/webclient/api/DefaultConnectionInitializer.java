package io.helidon.webclient.api;

/**
 * The default {@link ConnectionInitializer} implementation, which does nothing.
 */
class DefaultConnectionInitializer implements ConnectionInitializer {
    @Override
    public void initializeConnectedSocket(final ConnectedSocket socket) {
        // No-op
    }

    @Override
    public void initializeConnectedSocket(final ConnectedSocketChannel socket) {
        // No-op
    }
}
