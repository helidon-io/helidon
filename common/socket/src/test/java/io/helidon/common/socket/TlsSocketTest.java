/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import java.security.Principal;
import java.util.Optional;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TlsSocketTest {

    private final SSLSocket sslSocket;
    private final SSLSession sslSession1;
    private final SSLSession sslSession2;

    public TlsSocketTest() throws SSLPeerUnverifiedException {
        sslSocket = mock(SSLSocket.class);
        sslSession1 = mock(SSLSession.class);
        sslSession2 = mock(SSLSession.class);

        Principal principal1 = mock(Principal.class);
        Principal principal2 = mock(Principal.class);

        when(principal1.getName()).thenReturn("Frank");
        when(principal2.getName()).thenReturn("Jack");

        when(sslSession1.getId()).thenReturn(new byte[] {'a', 'b', 'c'});
        when(sslSession2.getId()).thenReturn(new byte[] {'d', 'e', 'f'});

        when(sslSession1.getPeerPrincipal()).thenReturn(principal1);
        when(sslSession2.getPeerPrincipal()).thenReturn(principal2);
    }

    @Test
    void renegotiationDetectionBySslSessionId() {
        when(sslSocket.getSession()).thenReturn(sslSession1);
        TlsSocket serverSocket = TlsSocket.server(sslSocket, "test1", "test2");

        assertFalse(serverSocket.renegotiated());

        // renegotiate
        when(sslSocket.getSession()).thenReturn(sslSession2);

        assertTrue(serverSocket.renegotiated());

        // detection is not reentrant
        assertFalse(serverSocket.renegotiated());
    }

    @Test
    void lazyPeerInfo() {
        when(sslSocket.getSession()).thenReturn(sslSession1);
        TlsSocket serverSocket = TlsSocket.server(sslSocket, "test1", "test2");

        assertPrincipal(serverSocket.remotePeer().tlsPrincipal(), "Frank");
        assertPrincipal(serverSocket.remotePeer().tlsPrincipal(), "Frank");
    }

    @Test
    void lazyPeerInfoRenegotiated() {
        when(sslSocket.getSession()).thenReturn(sslSession1);
        TlsSocket serverSocket = TlsSocket.server(sslSocket, "test1", "test2");

        assertPrincipal(serverSocket.remotePeer().tlsPrincipal(), "Frank");
        assertPrincipal(serverSocket.remotePeer().tlsPrincipal(), "Frank");

        // renegotiate
        when(sslSocket.getSession()).thenReturn(sslSession2);

        assertPrincipal(serverSocket.remotePeer().tlsPrincipal(), "Jack");
        assertPrincipal(serverSocket.remotePeer().tlsPrincipal(), "Jack");
    }

    private void assertPrincipal(Optional<Principal> act, String expectedName) {
        assertTrue(act.isPresent());
        assertThat(act.get().getName(), is(expectedName));
    }
}
