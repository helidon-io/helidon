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
import io.helidon.common.tls.TlsMaterial;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebServerTlsReloadTest {
    @Test
    void reloadTlsMaterialDefaultSocketDispatchesToListener() {
        Tls tls = tls();
        TlsMaterial material = material();
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .tls(tls)
                .build();

        server.reloadTls(material);

        verify(tls).reload(material);
    }

    @Test
    void reloadTlsMaterialNamedSocketDispatchesToListener() {
        Tls tls = tls();
        TlsMaterial material = material();
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .putSocket("admin", socket -> socket.tls(tls))
                .build();

        server.reloadTls(material, "admin");

        verify(tls).reload(material);
    }

    @Test
    void reloadTlsMaterialWithoutTlsBindingFails() {
        TlsMaterial material = material();
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .bindingsDiscoverServices(false)
                .addBinding(new TestTransportBindingConfig("test", true))
                .build();

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> server.reloadTls(material));

        assertThat(failure.getMessage(), containsString("TLS is not enabled"));
    }

    @Test
    @SuppressWarnings("removal")
    void reloadDeprecatedTlsDispatchesAsMaterialToBinding() {
        TestTransportBindingProvider.reset();
        Tls listenerTls = tls();
        Tls reloadTls = Tls.builder()
                .trustAll(true)
                .build();
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .tls(listenerTls)
                .bindingsDiscoverServices(false)
                .addBinding(new TestTransportBindingConfig("test", true, false, true))
                .build();

        server.reloadTls(reloadTls);

        assertThat(TestTransportBindingProvider.reloads("test"), is(1));
    }

    @Test
    void reloadTlsMaterialAttemptsRemainingTransportBindingsAfterFailure() {
        TestTransportBindingProvider.reset();
        TlsMaterial material = material();
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .tls(Tls.builder()
                             .trustAll(true)
                             .build())
                .bindingsDiscoverServices(false)
                .addBinding(new TestTransportBindingConfig("failing", true, false, true, true, false, false))
                .addBinding(new TestTransportBindingConfig("remaining", true, false, true))
                .build();

        RuntimeException failure = assertThrows(RuntimeException.class, () -> server.reloadTls(material));

        assertThat(failure.getMessage(), containsString("Failed to reload TLS"));
        assertThat(failure.getCause().getMessage(), containsString("transport binding failing"));
        assertThat(TestTransportBindingProvider.reloads("failing"), is(1));
        assertThat(TestTransportBindingProvider.reloads("remaining"), is(1));
    }

    @Test
    void reloadVirtualHostTlsMaterialAttemptsRemainingTransportBindingsAfterFailure() {
        TestTransportBindingProvider.reset();
        TlsMaterial material = material();
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .tls(Tls.builder()
                             .trustAll(true)
                             .build())
                .addVirtualHost(virtualHost -> virtualHost.host("api.example.com")
                        .tls(Tls.builder()
                                     .trustAll(true)
                                     .build()))
                .bindingsDiscoverServices(false)
                .addBinding(new TestTransportBindingConfig("failing", true, false, true, true, false, true))
                .addBinding(new TestTransportBindingConfig("remaining", true, false, true, false, false, true))
                .build();

        RuntimeException failure = assertThrows(RuntimeException.class,
                                                () -> server.reloadVirtualHostTls(material, "api.example.com"));

        assertThat(failure.getMessage(), containsString("Failed to reload virtual host TLS"));
        assertThat(failure.getCause().getMessage(), containsString("transport binding failing"));
        assertThat(TestTransportBindingProvider.virtualHostReloads("failing"), is(1));
        assertThat(TestTransportBindingProvider.virtualHostReloads("remaining"), is(1));
    }

    @Test
    void reloadVirtualHostTlsMaterialNamedSocketDispatchesToRegistry() {
        Tls defaultTls = tls();
        Tls virtualHostTls = tls();
        TlsMaterial material = material();
        WebServer server = WebServer.builder()
                .shutdownHook(false)
                .putSocket("admin", socket -> socket.tls(defaultTls)
                        .useNio(true)
                        .addVirtualHost(virtualHost -> virtualHost.host("api.example.com")
                                .tls(virtualHostTls)))
                .build();

        server.reloadVirtualHostTls(material, "admin", "api.example.com");

        verify(virtualHostTls).reload(material);
        verify(defaultTls, never()).reload(material);
    }

    private static Tls tls() {
        Tls tls = mock(Tls.class);
        when(tls.enabled()).thenReturn(true);
        return tls;
    }

    private static TlsMaterial material() {
        return TlsMaterial.builder()
                .trustAll(true)
                .build();
    }
}
