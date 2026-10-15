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

import java.net.BindException;
import java.net.InetAddress;
import java.net.URI;
import java.util.function.Consumer;

import io.helidon.common.tls.Tls;
import io.helidon.webserver.TcpTransportConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http3.Http3RawTestServer;

final class Http3FallbackTestServer implements AutoCloseable {
    private static final int MAX_BIND_ATTEMPTS = 10;

    private final Http3RawTestServer http3Server;
    private final WebServer http1Server;
    private final Http3TlsSupport.Http3TlsMaterials tlsMaterials;

    private Http3FallbackTestServer(Http3RawTestServer http3Server,
                                    WebServer http1Server,
                                    Http3TlsSupport.Http3TlsMaterials tlsMaterials) {
        this.http3Server = http3Server;
        this.http1Server = http1Server;
        this.tlsMaterials = tlsMaterials;
    }

    static Http3FallbackTestServer create(Http3RawTestServer.Handler handler,
                                          Consumer<HttpRouting.Builder> routing) throws Exception {
        Http3TlsSupport.Http3TlsMaterials tlsMaterials = Http3TlsSupport.load();
        RuntimeException collision = null;
        for (int attempt = 1; attempt <= MAX_BIND_ATTEMPTS; attempt++) {
            Http3RawTestServer http3Server = Http3RawTestServer.create(handler);
            WebServer http1Server = null;
            boolean startAttempted = false;
            try {
                http1Server = WebServer.builder()
                        .address(InetAddress.getLoopbackAddress())
                        .port(URI.create(http3Server.baseUri()).getPort())
                        .bindingsDiscoverServices(false)
                        .addBinding(TcpTransportConfig.create())
                        .protocolsDiscoverServices(false)
                        .tls(tlsMaterials.serverTls())
                        .addProtocol(Http1Config.create())
                        .routing(routing)
                        .build();
                startAttempted = true;
                http1Server.start();
                return new Http3FallbackTestServer(http3Server, http1Server, tlsMaterials);
            } catch (RuntimeException | Error e) {
                closeFailedStart(http3Server, http1Server, e);
                if (!startAttempted || !(e instanceof RuntimeException runtimeException)
                        || !isPortCollision(runtimeException)) {
                    throw e;
                }
                // An ephemeral UDP port can already be occupied by a TCP listener.
                collision = runtimeException;
            }
        }
        throw new IllegalStateException("Failed to bind HTTP/3 and HTTP/1 to the same port after "
                                                + MAX_BIND_ATTEMPTS + " attempts", collision);
    }

    String baseUri() {
        return http3Server.baseUri();
    }

    Tls clientTls() {
        return tlsMaterials.clientTls();
    }

    @Override
    public void close() {
        try {
            http1Server.stop();
        } finally {
            http3Server.close();
        }
    }

    private static void closeFailedStart(Http3RawTestServer http3Server, WebServer http1Server, Throwable failure) {
        try {
            if (http1Server != null) {
                http1Server.stop();
            }
        } catch (RuntimeException | Error e) {
            failure.addSuppressed(e);
        }
        try {
            http3Server.close();
        } catch (RuntimeException | Error e) {
            failure.addSuppressed(e);
        }
    }

    private static boolean isPortCollision(RuntimeException failure) {
        if (Thread.currentThread().isInterrupted()) {
            return false;
        }
        boolean bindFailure = false;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause.getSuppressed().length != 0) {
                return false;
            }
            if (cause instanceof BindException) {
                bindFailure = true;
            }
        }
        return bindFailure;
    }
}
