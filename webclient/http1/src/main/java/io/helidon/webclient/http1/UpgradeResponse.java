/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webclient.http1;

import java.util.NoSuchElementException;

import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.ClientResponseTrailers;
import io.helidon.http.Status;
import io.helidon.http.media.ReadableEntity;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.HttpClientResponse;

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

    /**
     * Create an upgrade success response.
     *
     * @param response the HTTP/1.1 response that was returned (successful upgrade)
     * @param connection client connection, now upgraded to the requested protocol on server side
     * @return upgrade response that was successful
     */
    public static UpgradeResponse success(HttpClientResponse response, ClientConnection connection) {
        return new UpgradeResponse(true, connection, new NoCloseResponse(response));
    }

    /**
     * Create an upgrade failure response.
     *
     * @param response the HTTP/1.1 client response that was returned instead of upgrading the connection
     * @return upgrade response that failed to upgrade
     */
    public static UpgradeResponse failure(HttpClientResponse response) {
        return new UpgradeResponse(false, null, response);
    }

    @Override
    public String toString() {
        return response.status() + ": " + response.headers();
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
        public Status status() {
            return delegate.status();
        }

        @Override
        public ClientResponseHeaders headers() {
            return delegate.headers();
        }

        @Override
        public ClientResponseTrailers trailers() {
            return delegate.trailers();
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

