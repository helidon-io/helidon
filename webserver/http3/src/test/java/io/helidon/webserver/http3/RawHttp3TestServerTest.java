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

package io.helidon.webserver.http3;

import java.net.InetSocketAddress;
import java.util.List;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.http.http3.Http3ErrorCode;
import io.helidon.http.http3.Http3Protocol;
import io.helidon.quic.QuicConfig;
import io.helidon.quic.QuicServerRuntime;
import io.helidon.quic.QuicVersion;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class RawHttp3TestServerTest {
    private static final char[] KEY_PASSWORD = "changeit".toCharArray();
    private static final String SERVER_KEYSTORE = "io/helidon/webserver/http3/server-keystore.p12";

    @Test
    void shouldUseListenerNameAsQuicServerId() throws Exception {
        try (QuicServerRuntime server = RawHttp3TestServer.createQuicServer("secure-listener",
                                                                            Runnable::run,
                                                                            new InetSocketAddress(0),
                                                                            tls(),
                                                                            quicConfig())) {
            assertThat(server.instanceId(), equalTo("secure-listener"));
            assertThat(server.name(), equalTo("QuicServerRuntime(secure-listener)"));
        }
    }

    @Test
    void shouldExposeHttp3ApplicationErrorsForQuicServer() {
        assertThat(Http3RuntimeSupport.applicationErrors().apply(Http3ErrorCode.REQUEST_CANCELLED.code()),
                   equalTo("H3_REQUEST_CANCELLED"));
    }

    private static QuicConfig quicConfig() {
        return QuicConfig.builder()
                .availableVersions(List.of(QuicVersion.QUIC_V1))
                .buildPrototype();
    }

    private static Tls tls() throws Exception {
        Keys keys = Keys.builder()
                .keystore(store -> store
                        .passphrase(new String(KEY_PASSWORD))
                        .keyAlias("server")
                        .certChainAlias("server")
                        .keystore(Resource.create(SERVER_KEYSTORE)))
                .build();
        return Tls.builder()
                .privateKey(keys.privateKey().orElseThrow())
                .privateKeyCertChain(keys.certChain())
                .build();
    }
}
