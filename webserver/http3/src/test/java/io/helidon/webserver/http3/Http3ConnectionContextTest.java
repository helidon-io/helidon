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

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Optional;

import javax.security.auth.x500.X500Principal;

import io.helidon.common.socket.PeerInfo;
import io.helidon.quic.QuicConnection;
import io.helidon.webserver.SniContext;
import io.helidon.webserver.TransportBindingContext;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Http3ConnectionContextTest {
    @Test
    void delegatesSocketContextToQuicConnection() {
        TransportBindingContext listenerContext = mock(TransportBindingContext.class);

        PeerInfo remotePeer = mock(PeerInfo.class);
        PeerInfo localPeer = mock(PeerInfo.class);
        QuicConnection connection = mock(QuicConnection.class);
        when(connection.socketId()).thenReturn("listener-7");
        when(connection.childSocketId()).thenReturn("connection-41");
        when(connection.remotePeer()).thenReturn(remotePeer);
        when(connection.localPeer()).thenReturn(localPeer);
        when(connection.isSecure()).thenReturn(true);
        when(remotePeer.tlsCertificates()).thenReturn(Optional.empty());

        Http3ConnectionContext context = new Http3ConnectionContext(listenerContext, connection);

        assertThat(context.socketId(), is("listener-7"));
        assertThat(context.childSocketId(), is("connection-41"));
        assertThat(context.remotePeer(), sameInstance(remotePeer));
        assertThat(context.localPeer(), sameInstance(localPeer));
        assertThat(context.isSecure(), is(true));
    }

    @Test
    void rejectsConnectionLevelByteIo() {
        Http3ConnectionContext context = new Http3ConnectionContext(mock(TransportBindingContext.class),
                                                                    connectionWithNoPeerCertificates());

        UnsupportedOperationException readFailure = assertThrows(UnsupportedOperationException.class,
                                                                  context::dataReader);
        UnsupportedOperationException writeFailure = assertThrows(UnsupportedOperationException.class,
                                                                   context::dataWriter);

        assertThat(readFailure.getMessage(), is("HTTP/3 byte I/O is stream-scoped"));
        assertThat(writeFailure.getMessage(), is("HTTP/3 byte I/O is stream-scoped"));
    }

    @Test
    void retainsSelectedSniContext() {
        SniContext sniContext = mock(SniContext.class);
        Http3ConnectionContext context = new Http3ConnectionContext(mock(TransportBindingContext.class),
                                                                    connectionWithNoPeerCertificates(),
                                                                    Optional.of(sniContext));

        assertThat(context.sniContext(), is(Optional.of(sniContext)));
    }

    @Test
    void cachesAbsentTlsCommonNameForConnection() {
        PeerInfo remotePeer = mock(PeerInfo.class);
        QuicConnection connection = mock(QuicConnection.class);
        when(connection.remotePeer()).thenReturn(remotePeer);
        when(remotePeer.tlsCertificates()).thenReturn(Optional.empty());

        Http3ConnectionContext context = new Http3ConnectionContext(mock(TransportBindingContext.class), connection);

        assertThat(context.tlsCommonName(), is(Optional.empty()));
        assertThat(context.tlsCommonName(), is(Optional.empty()));
        verify(remotePeer).tlsCertificates();
    }

    @Test
    void cachesPresentTlsCommonNameForConnection() {
        X509Certificate certificate = mock(X509Certificate.class);
        PeerInfo remotePeer = mock(PeerInfo.class);
        QuicConnection connection = mock(QuicConnection.class);
        when(certificate.getSubjectX500Principal()).thenReturn(new X500Principal("CN=test-client"));
        when(connection.remotePeer()).thenReturn(remotePeer);
        when(remotePeer.tlsCertificates()).thenReturn(Optional.of(new Certificate[] {certificate}));

        Http3ConnectionContext context = new Http3ConnectionContext(mock(TransportBindingContext.class), connection);

        assertThat(context.tlsCommonName(), is(Optional.of("test-client")));
        assertThat(context.tlsCommonName(), is(Optional.of("test-client")));
        verify(remotePeer).tlsCertificates();
        verify(certificate).getSubjectX500Principal();
    }

    private static QuicConnection connectionWithNoPeerCertificates() {
        PeerInfo remotePeer = mock(PeerInfo.class);
        QuicConnection connection = mock(QuicConnection.class);
        when(connection.remotePeer()).thenReturn(remotePeer);
        when(remotePeer.tlsCertificates()).thenReturn(Optional.empty());
        return connection;
    }
}
