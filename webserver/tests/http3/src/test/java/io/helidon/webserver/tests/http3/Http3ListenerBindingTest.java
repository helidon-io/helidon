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

package io.helidon.webserver.tests.http3;

import java.net.BindException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import io.helidon.webserver.TcpTransportConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.quic.QuicTransportConfig;

import org.junit.jupiter.api.Test;

import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

class Http3ListenerBindingTest {
    private static final String ENTITY = "listener-binding";
    private static final int MAX_BIND_ATTEMPTS = 10;

    @Test
    void shouldKeepDefaultTcpOverlayOnConfiguredFixedPort() throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        for (int attempt = 1; ; attempt++) {
            int port;
            Http3TestSupport.TestEnvironment environment;
            try {
                try (ServerSocket tcp = new ServerSocket(0, 1, loopback);
                     DatagramSocket _ = new DatagramSocket(new InetSocketAddress(loopback, tcp.getLocalPort()))) {
                    port = tcp.getLocalPort();
                }

                environment = Http3TestSupport.sharedListener(builder -> builder.port(port)
                        .bindingsDiscoverServices(false)
                        .addBinding(QuicTransportConfig.create()), Http3ListenerBindingTest::routing);
            } catch (Exception e) {
                if (attempt >= MAX_BIND_ATTEMPTS || !isRetryableBindFailure(e)) {
                    throw e;
                }
                continue;
            }

            try (environment) {
                assertThat(environment.port(), is(port));
                assertServesHttp1AndHttp3(environment);
            }
            return;
        }
    }

    @Test
    void shouldConvergeOnEphemeralPortWithProgrammaticTcpFirst() throws Exception {
        try (Http3TestSupport.TestEnvironment environment =
                     Http3TestSupport.sharedListener(builder -> builder.bindingsDiscoverServices(false)
                             .addBinding(TcpTransportConfig.create())
                             .addBinding(QuicTransportConfig.create()), Http3ListenerBindingTest::routing)) {
            assertThat(environment.port(), is(greaterThan(0)));
            assertServesHttp1AndHttp3(environment);
        }
    }

    @Test
    void shouldConvergeOnEphemeralPortWithProgrammaticQuicFirst() throws Exception {
        try (Http3TestSupport.TestEnvironment environment =
                     Http3TestSupport.sharedListener(builder -> builder.bindingsDiscoverServices(false)
                             .addBinding(QuicTransportConfig.create())
                             .addBinding(TcpTransportConfig.create()), Http3ListenerBindingTest::routing)) {
            assertThat(environment.port(), is(greaterThan(0)));
            assertServesHttp1AndHttp3(environment);
        }
    }

    private static boolean isRetryableBindFailure(Throwable failure) {
        if (Thread.currentThread().isInterrupted()) {
            return false;
        }
        boolean bindFailure = false;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof InterruptedException || cause.getSuppressed().length != 0) {
                return false;
            }
            if (cause instanceof BindException) {
                bindFailure = true;
            }
        }
        return bindFailure;
    }

    private static void routing(HttpRouting.Builder routing) {
        routing.get("/binding", (_, res) -> res.send(ENTITY));
    }

    private static void assertServesHttp1AndHttp3(Http3TestSupport.TestEnvironment environment) throws Exception {
        try (HttpClient http1Client = environment.http1Client();
             HttpClient http3Client = environment.http3Client()) {
            HttpResponse<String> http1Response =
                    http1Client.send(Http3TestSupport.http1Get(environment.port(), "/binding"), ofString());
            HttpResponse<String> http3Response =
                    http3Client.send(Http3TestSupport.http3Get(environment.port(), "/binding"), ofString());

            assertThat(http1Response.statusCode(), is(200));
            assertThat(http1Response.version(), is(HTTP_1_1));
            assertThat(http1Response.body(), is(ENTITY));
            assertThat(http3Response.statusCode(), is(200));
            assertThat(http3Response.version(), is(HTTP_3));
            assertThat(http3Response.body(), is(ENTITY));
        }
    }
}
