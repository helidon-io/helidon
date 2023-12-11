/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.webclient.tests;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLHandshakeException;

import io.helidon.common.tls.Tls;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.HeaderValues;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static io.helidon.http.HeaderNames.X_HELIDON_CN;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * This test is testing ability to set mutual TLS on WebClient and WebServer.
 */
@ServerTest
class MutualTlsTest {

    private static final Config CONFIG = Config.just(() -> ConfigSources.classpath("application.yaml").build());

    private final WebServer server;

    public MutualTlsTest(WebServer server) {
        this.server = server;
        server.context().register(server);
    }

    @SetUpServer
    public static void setup(WebServerConfig.Builder server) {
        server.config(CONFIG.get("server"))
                .routing(MutualTlsTest::plainRouting)
                .putSocket("secured", socket -> socket
                        .from(server.sockets().get("secured"))
                        .routing(MutualTlsTest::mTlsRouting))
                .putSocket("invalid-server-cert", socket -> socket
                        .from(server.sockets().get("invalid-server-cert"))
                        .routing(MutualTlsTest::mTlsRouting))
                .putSocket("client-no-ca", socket -> socket
                        .from(server.sockets().get("client-no-ca"))
                        .routing(MutualTlsTest::mTlsRouting))
                .putSocket("optional", socket -> socket
                        .from(server.sockets().get("optional"))
                        .routing(MutualTlsTest::mTlsRouting));
    }

    @Test
    public void testAccessSuccessful() {
        Http1Client webClient = createWebClient(CONFIG.get("success"));
        String result = webClient.get()
                .uri("http://localhost:" + server.port())
                .requestEntity(String.class);

        assertThat(result, is("Hello world unsecured!"));
        assertThat(exec(webClient, "https", server.port("secured")), is("Hello Helidon-client!"));
    }

    @Test
    public void testNoClientCert() {
        Http1Client client = createWebClient(CONFIG.get("no-client-cert"));

        // as the client does not have client certificate configured, it is not aware it should wait for the application
        // data from server during SSL handshake, as a result, handshake is OK, but we fail when we try to communicate
        // (as the client becomes aware of the problem only when it tries to read data from TLS, and by that time the
        // server may have terminated the connection)
        // so sometimes we get correct fatal alert about certificate, sometimes connection closed (Broken pipe or similar)
        // it just must always fail with a socket exception or SSLHandshakeException
        UncheckedIOException ex = assertThrows(UncheckedIOException.class, () -> exec(client, "https", server.port("secured")));

        IOException cause = ex.getCause();
        if (cause instanceof SSLHandshakeException) {
            assertThat(cause.getMessage(), containsString("Received fatal alert: bad_certificate"));
        } else {
            if (!(cause instanceof SocketException)) {
                fail("Call must fail with either SSLHandshakeException or SocketException", cause);
            }
        }
    }

    @Test
    public void testOptionalAuthentication() {
        Http1Client client = createWebClient(CONFIG.get("no-client-cert"));

        assertThat(exec(client, "https", server.port("optional")), is("Hello Unknown CN!"));

        client = createWebClient(CONFIG.get("success"));
        assertThat(exec(client, "https", server.port("optional")), is("Hello Helidon-client!"));
    }

    @Test
    public void testServerCertInvalidCn() {
        int port = server.port("invalid-server-cert");
        Http1Client clientOne = createWebClient(CONFIG.get("server-cert-invalid-cn"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> exec(clientOne, "https", port));
        assertThat(ex.getCause().getMessage(), is("No name matching localhost found"));

        Http1Client webClientTwo = createWebClient(CONFIG.get("client-disable-hostname-verification"));
        assertThat(exec(webClientTwo, "https", port), is("Hello Helidon-client!"));
    }

    @Test
    public void testClientNoCa() {
        int port = server.port("client-no-ca");
        Http1Client clientOne = createWebClient(CONFIG.get("client-no-ca"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> exec(clientOne, "https", port));
        assertThat(ex.getCause().getMessage(), endsWith("unable to find valid certification path to requested target"));

        Http1Client webClientTwo = createWebClient(CONFIG.get("client-trust-all"));
        assertThat(exec(webClientTwo, "https", port), is("Hello Helidon-client!"));
    }

    @Test
    public void testClientAndServerUpdateTls() {
        int portSecured = server.port("secured");
        int portDefault = server.port();
        Http1Client clientFirst = createWebClient(CONFIG.get("success"));
        Http1Client clientSecond = createWebClient(CONFIG.get("client-second-valid"));

        assertThat(exec(clientFirst, "https", portSecured), is("Hello Helidon-client!"));

        UncheckedIOException ex = assertThrows(UncheckedIOException.class, () -> exec(clientSecond, "https", portSecured));
        assertThat(ex.getCause().getMessage(), endsWith("unable to find valid certification path to requested target"));

        String response = clientFirst.get().uri("http://localhost:" + portDefault + "/reload").requestEntity(String.class);
        assertThat(response, is("SslContext reloaded. Affected named socket: secured"));

        assertThat(exec(clientSecond, "https", portSecured), is("Hello Oracle-client!"));

        ex = assertThrows(UncheckedIOException.class, () -> exec(clientFirst, "https", portSecured));
        assertThat(ex.getCause().getMessage(), endsWith("unable to find valid certification path to requested target"));

        response = clientFirst.get().uri("http://localhost:" + portDefault + "/reload").requestEntity(String.class);
        assertThat(response, is("SslContext reloaded. Affected named socket: secured"));

        assertThat(exec(clientFirst, "https", portSecured), is("Hello Helidon-client!"));

        ex = assertThrows(UncheckedIOException.class, () -> exec(clientSecond, "https", portSecured));
        assertThat(ex.getCause().getMessage(), endsWith("unable to find valid certification path to requested target"));
    }

    private Http1Client createWebClient(Config config) {
        return Http1Client.builder()
                .config(config)
                .keepAlive(false)
                .build();
    }

    private String exec(Http1Client webClient, String scheme, int port) {
        return webClient.get(scheme + "://localhost:" + port).requestEntity(String.class);
    }

    private static void plainRouting(HttpRouting.Builder routing) {
        AtomicBoolean atomicBoolean = new AtomicBoolean(true);
        routing.get("/", (req, res) -> res.send("Hello world unsecured!"))
                .get("/reload",
                        (req, res) -> {
                            String configName = atomicBoolean.getAndSet(!atomicBoolean.get())
                                    ? "server-second-valid.tls"
                                    : "server.sockets.0.tls";
                            WebServer server = req.context().get(WebServer.class).orElseThrow();
                            server.reloadTls("secured", Tls.create(CONFIG.get(configName)));
                            res.send("SslContext reloaded. Affected named socket: secured");
                        })
                .build();
    }

    private static void mTlsRouting(HttpRouting.Builder routing) {
        routing.get("/", (req, res) -> {

            // close to avoid re-using cached connections on the client side
            res.header(HeaderValues.CONNECTION_CLOSE);
            res.send("Hello " + req.headers().value(X_HELIDON_CN).orElse("Unknown CN") + "!");
        });
    }

}
