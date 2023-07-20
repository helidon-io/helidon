package io.helidon.nima.webclient.http1;

import java.util.NoSuchElementException;

import io.helidon.common.http.ClientResponseHeaders;
import io.helidon.common.http.Http;
import io.helidon.nima.http.media.ReadableEntity;
import io.helidon.nima.webclient.api.ClientConnection;
import io.helidon.nima.webclient.api.ClientUri;
import io.helidon.nima.webclient.api.HttpClientResponse;

/**
 * Response to an upgrade attempt.
 */
public final class UpgradeResponse {
    private final boolean isUpgraded;
    private final ClientConnection connection;
    private final HttpClientResponse response;

    private UpgradeResponse(boolean isUpgraded,
                            ClientConnection connection,
                            HttpClientResponse response) {

        this.isUpgraded = isUpgraded;
        this.connection = connection;
        this.response = response;
    }

    public static UpgradeResponse success(HttpClientResponse response, ClientConnection connection) {
        return new UpgradeResponse(true, connection, new NoCloseResponse(response));
    }

    public static UpgradeResponse failure(HttpClientResponse response) {
        return new UpgradeResponse(false, null, response);
    }

    /**
     * Whether upgrade succeeded or not.
     *
     * @return whether we upgraded the connection
     */
    public boolean isUpgraded() {
        return isUpgraded;
    }

    /**
     * Upgraded connection. Only available on successful upgrade, will throw an exception otherwise.
     *
     * @return connection
     * @throws java.util.NoSuchElementException in case the upgrade failed
     * @see #isUpgraded()
     */
    public ClientConnection connection() {
        if (connection == null) {
            throw new NoSuchElementException("If upgrade fails, connection cannot be obtained");
        }
        return connection;
    }

    /**
     * The HTTP response we got from the server, always present.
     *
     * @return response as received by the HTTP/1 client
     */
    public HttpClientResponse response() {
        return response;
    }

    private static class NoCloseResponse implements HttpClientResponse {
        private final HttpClientResponse delegate;

        NoCloseResponse(HttpClientResponse response) {
            this.delegate = response;
        }

        @Override
        public Http.Status status() {
            return delegate.status();
        }

        @Override
        public ClientResponseHeaders headers() {
            return delegate.headers();
        }

        @Override
        public ClientUri lastEndpointUri() {
            return delegate.lastEndpointUri();
        }

        @Override
        public ReadableEntity entity() {
            return delegate.entity();
        }

        @Override
        public void close() {
            // do nothing, as the connection was upgraded, and we hand over responsibility for it to the protocol
        }
    }
}

