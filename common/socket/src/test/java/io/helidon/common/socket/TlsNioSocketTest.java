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

package io.helidon.common.socket;

import java.nio.channels.SocketChannel;
import java.security.Principal;
import java.util.Optional;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TlsNioSocketTest {

    @Test
    void retainedPeerInfoSeesRenegotiatedPrincipal() throws Exception {
        Principal principal1 = mock(Principal.class);
        Principal principal2 = mock(Principal.class);
        when(principal1.getName()).thenReturn("Frank");
        when(principal2.getName()).thenReturn("Jack");

        SSLSession session1 = mock(SSLSession.class);
        SSLSession session2 = mock(SSLSession.class);
        when(session1.getId()).thenReturn(new byte[] {'a', 'b', 'c'});
        when(session2.getId()).thenReturn(new byte[] {'d', 'e', 'f'});
        when(session1.getPacketBufferSize()).thenReturn(1);
        when(session1.getApplicationBufferSize()).thenReturn(1);
        when(session1.getPeerPrincipal()).thenReturn(principal1);
        when(session2.getPeerPrincipal()).thenReturn(principal2);
        when(session1.getPeerCertificates()).thenReturn(new java.security.cert.Certificate[0]);
        when(session2.getPeerCertificates()).thenReturn(new java.security.cert.Certificate[0]);

        SSLEngine engine = mock(SSLEngine.class);
        SocketChannel channel = mock(SocketChannel.class);
        when(engine.getSession()).thenReturn(session1);

        TlsNioSocket socket = TlsNioSocket.server(channel, engine, "listener", "server");
        PeerInfo peerInfo = socket.remotePeer();

        assertPrincipal(peerInfo.tlsPrincipal(), "Frank");

        // Renegotiate, but keep using the same PeerInfo instance.
        when(engine.getSession()).thenReturn(session2);
        assertPrincipal(peerInfo.tlsPrincipal(), "Jack");
    }

    @Test
    void peerIdentityIsCachedPerSession() throws Exception {
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("Frank");

        SSLSession session = mock(SSLSession.class);
        when(session.getId()).thenReturn(new byte[] {'a', 'b', 'c'});
        when(session.getPacketBufferSize()).thenReturn(1);
        when(session.getApplicationBufferSize()).thenReturn(1);
        when(session.getPeerPrincipal()).thenReturn(principal);
        when(session.getPeerCertificates()).thenReturn(new java.security.cert.Certificate[0]);

        SSLEngine engine = mock(SSLEngine.class);
        SocketChannel channel = mock(SocketChannel.class);
        when(engine.getSession()).thenReturn(session);

        TlsNioSocket socket = TlsNioSocket.server(channel, engine, "listener", "server");
        PeerInfo peerInfo = socket.remotePeer();

        assertPrincipal(peerInfo.tlsPrincipal(), "Frank");
        assertPrincipal(peerInfo.tlsPrincipal(), "Frank");

        verify(session, times(1)).getPeerPrincipal();
        verify(session, times(1)).getPeerCertificates();
    }

    private void assertPrincipal(Optional<Principal> actual, String expectedName) {
        assertTrue(actual.isPresent());
        assertThat(actual.get().getName(), is(expectedName));
    }
}
