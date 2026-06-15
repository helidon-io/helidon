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

        TcpTransportBinding binding = new TcpTransportBinding(transportContext, TcpTransportConfig.create());

        assertThat(binding.security(), is(Security.TLS));
        verify(transportContext).listenerTls();
        verify(listenerTlsContext).tls();
    }

    private static Tls tls() {
        Tls tls = mock(Tls.class);
        when(tls.enabled()).thenReturn(true);
        return tls;
    }
}
