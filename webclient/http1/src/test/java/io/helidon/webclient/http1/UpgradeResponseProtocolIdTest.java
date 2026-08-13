/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.time.Duration;

import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.ClientResponseTrailers;
import io.helidon.http.Status;
import io.helidon.http.media.ReadableEntity;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.HttpClientResponse;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.sameInstance;

class UpgradeResponseProtocolIdTest {
    @Test
    void successfulUpgradeResponseDelegatesProtocolWithoutClosingDelegate() {
        var delegate = new Http2Response();
        var connection = new TestConnection();

        UpgradeResponse upgrade = UpgradeResponse.success(delegate, connection);

        assertThat(upgrade.isUpgraded(), is(true));
        assertThat(upgrade.connection(), sameInstance(connection));
        assertThat(upgrade.response().protocolId(), is("h2"));

        upgrade.response().close();
        assertThat(delegate.closed, is(false));
    }

    private static final class Http2Response implements HttpClientResponse {
        private boolean closed;

        @Override
        public String protocolId() {
            return "h2";
        }

        @Override
        public Status status() {
            throw new AssertionError("Status should not be requested");
        }

        @Override
        public ClientResponseHeaders headers() {
            throw new AssertionError("Headers should not be requested");
        }

        @Override
        public ClientResponseTrailers trailers() {
            throw new AssertionError("Trailers should not be requested");
        }

        @Override
        public ClientUri lastEndpointUri() {
            throw new AssertionError("Endpoint should not be requested");
        }

        @Override
        public ReadableEntity entity() {
            throw new AssertionError("Entity should not be requested");
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class TestConnection implements ClientConnection {
        @Override
        public DataReader reader() {
            throw new AssertionError("Reader should not be requested");
        }

        @Override
        public DataWriter writer() {
            throw new AssertionError("Writer should not be requested");
        }

        @Override
        public String channelId() {
            throw new AssertionError("Channel ID should not be requested");
        }

        @Override
        public HelidonSocket helidonSocket() {
            throw new AssertionError("Socket should not be requested");
        }

        @Override
        public void readTimeout(Duration readTimeout) {
            throw new AssertionError("Read timeout should not be changed");
        }

        @Override
        public void closeResource() {
            throw new AssertionError("Connection should not be closed");
        }
    }
}
