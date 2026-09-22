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

package io.helidon.webclient.tests.http3;

import java.net.InetAddress;
import java.util.List;
import java.util.function.Consumer;

import javax.net.ssl.SSLContext;

import io.helidon.common.tls.Tls;
import io.helidon.webserver.SniAuthorityPolicy;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http3.Http3Config;

final class TestEnvironment implements AutoCloseable {
    private final WebServer server;
    private final Http3TlsSupport.Http3TlsMaterials tlsMaterials;

    private TestEnvironment(WebServer server, Http3TlsSupport.Http3TlsMaterials tlsMaterials) {
        this.server = server;
        this.tlsMaterials = tlsMaterials;
    }

    static TestEnvironment createSharedListener(Consumer<HttpRouting.Builder> routing) throws Exception {
        return createSharedListener(Http1Config.create(), routing);
    }

    static TestEnvironment createSharedListener(Http1Config http1Config,
                                                Consumer<HttpRouting.Builder> routing) throws Exception {
        return createSharedListener(http1Config, Http3Config.create(), routing);
    }

    static TestEnvironment createSharedListener(Http1Config http1Config,
                                                Http3Config http3Config,
                                                Consumer<HttpRouting.Builder> routing) throws Exception {
        Http3TlsSupport.Http3TlsMaterials tlsMaterials = Http3TlsSupport.load();
        WebServer server = WebServer.builder()
                .address(InetAddress.getLoopbackAddress())
                .port(0)
                .protocolsDiscoverServices(false)
                .tls(tlsMaterials.serverTls())
                .sni(it -> it.authorityMismatch(SniAuthorityPolicy.ALLOW))
                .addProtocol(http1Config)
                .addProtocol(http3Config)
                .routing(routing)
                .build()
                .start();

        return new TestEnvironment(server, tlsMaterials);
    }

    String baseUri() {
        return "https://localhost:" + server.port();
    }

    Tls clientTls() {
        return tlsMaterials.clientTls();
    }

    Tls clientTlsHttp3() {
        return tlsMaterials.clientTlsHttp3();
    }

    Tls clientTlsWithoutTls13() {
        return tlsMaterials.clientTlsWithEnabledProtocols(List.of("TLSv1.2"));
    }

    @Override
    public void close() {
        server.stop();
    }

    SSLContext clientSslContext() {
        return tlsMaterials.clientSslContext();
    }

    Http3TlsSupport.Http3TlsMaterials tlsMaterials() {
        return tlsMaterials;
    }
}
