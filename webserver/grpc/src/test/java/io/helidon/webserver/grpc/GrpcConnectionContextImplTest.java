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

package io.helidon.webserver.grpc;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.PeerInfo;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.SniContext;
import io.helidon.webserver.SniMatchType;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class GrpcConnectionContextImplTest {
    @Test
    void exposesSniHosts() {
        GrpcConnectionContext context = new GrpcConnectionContextImpl(
                new TestConnectionContext(sniContext("api.example.com", "*.example.com")));

        assertThat(context.sniRequestedHost(), is(Optional.of("api.example.com")));
        assertThat(context.sniMatchedHost(), is(Optional.of("*.example.com")));
    }

    @Test
    void returnsEmptySniHostsWithoutSniContext() {
        GrpcConnectionContext context = new GrpcConnectionContextImpl(new TestConnectionContext(null));

        assertThat(context.sniRequestedHost(), is(Optional.empty()));
        assertThat(context.sniMatchedHost(), is(Optional.empty()));
    }

    private static SniContext sniContext(String presentedHost, String matchedHost) {
        return new SniContext() {
            @Override
            public Optional<String> presentedHost() {
                return Optional.of(presentedHost);
            }

            @Override
            public Optional<String> matchedHost() {
                return Optional.of(matchedHost);
            }

            @Override
            public SniMatchType matchType() {
                return SniMatchType.WILDCARD;
            }

            @Override
            public AuthorityCheck checkAuthority(String authority) {
                return AuthorityCheck.ALLOWED;
            }
        };
    }

    private static final class TestConnectionContext implements ConnectionContext {
        private final SniContext sniContext;

        private TestConnectionContext(SniContext sniContext) {
            this.sniContext = sniContext;
        }

        @Override
        public Optional<SniContext> sniContext() {
            return Optional.ofNullable(sniContext);
        }

        @Override
        public ListenerContext listenerContext() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public ExecutorService executor() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public DataWriter dataWriter() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public DataReader dataReader() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public Router router() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public PeerInfo remotePeer() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public PeerInfo localPeer() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public boolean isSecure() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public String socketId() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public String childSocketId() {
            throw new UnsupportedOperationException("Should not be called");
        }
    }
}
