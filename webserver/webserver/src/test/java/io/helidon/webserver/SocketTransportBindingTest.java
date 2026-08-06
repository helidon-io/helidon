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

package io.helidon.webserver;

import java.net.InetSocketAddress;
import java.util.Timer;

import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.tls.Tls;
import io.helidon.webserver.spi.TransportBinding.Security;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SocketTransportBindingTest {
    @Test
    void tcpTransportBindingUsesListenerTlsContext() {
        Tls listenerTls = tls();
        Limit requestLimit = mock(Limit.class);
        Limit connectionLimit = mock(Limit.class);
        Timer idleConnectionTimer = mock(Timer.class);
        ListenerConfig listenerConfig = ListenerConfig.builder()
                .tls(listenerTls)
                .useNio(false)
                .addVirtualHost(virtualHost -> virtualHost.host("api.example.com")
                        .tls(tls()))
                .build();
        ListenerContext listenerContext = mock(ListenerContext.class);
        ListenerTlsContext listenerTlsContext = mock(ListenerTlsContext.class);
        TransportBindingContext transportContext = mock(TransportBindingContext.class);
        when(listenerContext.config()).thenReturn(listenerConfig);
        when(listenerTlsContext.tls()).thenReturn(listenerTls);
        when(transportContext.listenerContext()).thenReturn(listenerContext);
        when(transportContext.listenerTls()).thenReturn(listenerTlsContext);
        when(transportContext.requestLimit()).thenReturn(requestLimit);
        when(transportContext.connectionLimit()).thenReturn(connectionLimit);
        when(transportContext.configuredAddress()).thenReturn(new InetSocketAddress(0));

        TcpTransportBinding binding = new TcpTransportBinding(transportContext, idleConnectionTimer);

        assertThat(binding.security(), is(Security.TLS));
        assertThat(binding.holdsIdleConnectionPermit(), is(true));
        verify(transportContext).listenerTls();
        verify(transportContext).connectionLimit();
        verify(listenerTlsContext).tls();
    }

    private static Tls tls() {
        Tls tls = mock(Tls.class);
        when(tls.enabled()).thenReturn(true);
        return tls;
    }
}
