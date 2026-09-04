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

package io.helidon.quic.api;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.quic.QuicBidirectionalStream;
import io.helidon.quic.QuicClient;
import io.helidon.quic.QuicClientTarget;
import io.helidon.quic.QuicConfig;
import io.helidon.quic.QuicReceiveStream;
import io.helidon.quic.QuicServer;
import io.helidon.quic.QuicSession;
import io.helidon.quic.QuicVersion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class QuicClientServerIT {
    private static final String ALPN = "helidon-test-quic";
    private static final char[] KEY_PASSWORD = "changeit".toCharArray();
    private static final String SERVER_KEYSTORE = "io/helidon/quic/server-keystore.p12";
    private static final String CLIENT_TRUSTSTORE = "io/helidon/quic/client-truststore.p12";

    @Test
    @Timeout(30)
    void shouldHandshakeAndEcho() throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            QuicConfig quicConfig = QuicConfig.builder()
                    .availableVersions(List.of(QuicVersion.QUIC_V1))
                    .buildPrototype();
            Tls serverTls = serverTls();
            try (QuicServer server = QuicServer.builder()
                    .executor(executor)
                    .quicConfig(quicConfig)
                    .tls(serverTls)
                    .applicationProtocols(List.of(ALPN))
                    .bindAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                    .build();
                 QuicClient client = QuicClient.builder()
                         .executor(executor)
                         .quicConfig(quicConfig)
                         .tls(clientTls())
                         .build()) {
                server.start();
                Future<QuicSession> serverSessionFuture = executor.submit(() -> echo(server.accept()));
                InetSocketAddress serverAddress = server.localAddress();
                QuicClientTarget target = QuicClientTarget.builder()
                        .peerAddress(serverAddress)
                        .tlsPeerName(serverAddress.getHostString())
                        .applicationProtocols(List.of(ALPN))
                        .buildPrototype();
                QuicSession clientSession = client.connect(target);
                QuicSession serverSession = null;
                try {
                    assertThat(clientSession.applicationProtocol(), is(ALPN));
                    assertThat(clientSession.quicVersion(), is(QuicVersion.QUIC_V1));

                    QuicBidirectionalStream clientStream = clientSession.openBidirectionalStream();
                    byte[] payload = "helidon-quic-e2e".getBytes(StandardCharsets.UTF_8);
                    clientStream.writeFinal(BufferData.create(payload));

                    assertThat(readAll(clientStream), equalTo(payload));
                    serverSession = serverSessionFuture.get();
                    assertThat(serverSession.applicationProtocol(), is(ALPN));
                    assertThat(serverSession.quicVersion(), is(QuicVersion.QUIC_V1));
                } finally {
                    clientSession.close(0);
                    if (serverSession != null) {
                        serverSession.close(0);
                    }
                }
            }
        }
    }

    private static QuicSession echo(QuicSession session) {
        QuicReceiveStream remoteStream = session.acceptStream();
        assertThat(remoteStream, instanceOf(QuicBidirectionalStream.class));
        QuicBidirectionalStream bidirectionalStream = (QuicBidirectionalStream) remoteStream;
        bidirectionalStream.writeFinal(BufferData.create(readAll(bidirectionalStream)));
        return session;
    }

    private static byte[] readAll(QuicReceiveStream stream) {
        BufferData output = BufferData.growing(256);
        for (;;) {
            Optional<BufferData> next = stream.read();
            if (next.isEmpty()) {
                return output.readBytes();
            }
            output.write(next.orElseThrow());
        }
    }

    private static Tls serverTls() throws Exception {
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

    private static Tls clientTls() {
        return Tls.builder()
                .trust(trust -> trust.keystore(store -> store
                        .passphrase(new String(KEY_PASSWORD))
                        .trustStore(true)
                        .keystore(Resource.create(CLIENT_TRUSTSTORE))))
                .build();
    }
}
