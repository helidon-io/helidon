/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.webclient;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerTls;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * This test is testing ability to set mutual TLS on WebClient and WebServer.
 */
public class MutualTlsTest {

    private static final Config CONFIG = Config
            .just(() -> ConfigSources.classpath("application-test.yaml").build());

    private static WebServer webServer;

    @BeforeAll
    public static void setUpServer() throws InterruptedException {
        webServer = startWebServer(CONFIG.get("server"));
    }

    @AfterAll
    public static void killServer() throws InterruptedException, ExecutionException, TimeoutException {
        if (webServer != null) {
            webServer.shutdown()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testAccessSuccessful() {
        WebClient webClient = createWebClient(CONFIG.get("success"));
        String result = webClient.get()
                .uri("http://localhost:" + webServer.port())
                .request(String.class)
                .await();

        assertThat(result, is("Hello world unsecured!"));
        assertThat(executeRequest(webClient, "https", webServer.port("secured")), is("Hello Helidon-client!"));
    }

    @Test
    public void testNoClientCert() {
        WebClient webClient = createWebClient(CONFIG.get("no-client-cert"));

        CompletionException exception = assertThrows(CompletionException.class,
                                                     () -> executeRequest(webClient, "https",
                                                                          webServer.port("secured")));
        assertThat(exception.getCause().getMessage(), containsString("Received fatal alert: bad_certificate"));
    }

    @Test
    public void testOptionalAuthentication() {
        WebClient webClient = createWebClient(CONFIG.get("no-client-cert"));

        assertThat(executeRequest(webClient, "https",
                                  webServer.port("optional")), is("Hello Unknown CN!"));

        webClient = createWebClient(CONFIG.get("success"));
        assertThat(executeRequest(webClient, "https",
                                  webServer.port("optional")), is("Hello Helidon-client!"));
    }

    @Test
    public void testServerCertInvalidCn() {
        int port = webServer.port("invalid-server-cert");
        WebClient webClientOne = createWebClient(CONFIG.get("server-cert-invalid-cn"));

        CompletionException exception = assertThrows(CompletionException.class,
                                                     () -> executeRequest(webClientOne, "https", port));
        assertThat(exception.getCause().getMessage(), is("No name matching localhost found"));

        WebClient webClientTwo = createWebClient(CONFIG.get("client-disable-hostname-verification"));
        assertThat(executeRequest(webClientTwo, "https", port), is("Hello Helidon-client!"));
    }

    @Test
    public void testClientNoCa() {
        int port = webServer.port("client-no-ca");
        WebClient webClientOne = createWebClient(CONFIG.get("client-no-ca"));

        CompletionException exception = assertThrows(CompletionException.class,
                                                     () -> executeRequest(webClientOne, "https", port));
        assertThat(exception.getCause().getMessage(), endsWith("unable to find valid certification path to requested target"));

        WebClient webClientTwo = createWebClient(CONFIG.get("client-trust-all"));
        assertThat(executeRequest(webClientTwo, "https", port), is("Hello Helidon-client!"));
    }

    @Test
    public void testClientAndServerUpdateTls() {
        int portSecured = webServer.port("secured");
        int portDefault = webServer.port();
        WebClient webClientFirst = createWebClient(CONFIG.get("success"));
        WebClient webClientSecond = createWebClient(CONFIG.get("client-second-valid"));

        assertThat(executeRequest(webClientFirst, "https", portSecured), is("Hello Helidon-client!"));
        CompletionException exception = assertThrows(CompletionException.class,
                                                     () -> executeRequest(webClientSecond, "https", portSecured));
        assertThat(exception.getCause().getMessage(), endsWith("unable to find valid certification path to requested target"));
        String response = webClientFirst.get().uri("http://localhost:" + portDefault + "/reload").request(String.class).await();
        assertThat(response, is("SslContext reloaded. Affected named socket: secured"));

        assertThat(executeRequest(webClientSecond, "https", portSecured), is("Hello Oracle-client!"));

        exception = assertThrows(CompletionException.class,
                                 () -> executeRequest(webClientFirst, "https", portSecured));
        assertThat(exception.getCause().getMessage(), endsWith("unable to find valid certification path to requested target"));

        response = webClientFirst.get().uri("http://localhost:" + portDefault + "/reload").request(String.class).await();
        assertThat(response, is("SslContext reloaded. Affected named socket: secured"));

        assertThat(executeRequest(webClientFirst, "https", portSecured), is("Hello Helidon-client!"));
        exception = assertThrows(CompletionException.class,
                                                     () -> executeRequest(webClientSecond, "https", portSecured));
        assertThat(exception.getCause().getMessage(), endsWith("unable to find valid certification path to requested target"));
    }

    private WebClient createWebClient(Config config) {
        return WebClient.builder()
                .config(config)
                .keepAlive(false)
                .build();
    }

    private String executeRequest(WebClient webClient, String schema, int port) {
        return webClient.get()
                .uri(schema + "://localhost:" + port)
                .request(String.class)
                .await();
    }

    private static WebServer startWebServer(Config config) throws InterruptedException {
        WebServer webServer = createWebServer(config);
        long timeout = 2000; // 2 seconds should be enough to start the server
        long now = System.currentTimeMillis();

        while (!webServer.isRunning()) {
            Thread.sleep(100);
            if ((System.currentTimeMillis() - now) > timeout) {
                Assertions.fail("Failed to start webserver");
            }
        }

        return webServer;
    }

    private static WebServer createWebServer(Config config) {
        WebServer webServer = WebServer.builder(createPlainRouting())
                .config(config)
                .addNamedRouting("secured", createMtlsRouting())
                .addNamedRouting("invalid-server-cert", createMtlsRouting())
                .addNamedRouting("client-no-ca", createMtlsRouting())
                .addNamedRouting("optional", createMtlsRouting())
                .build();
        webServer.start()
                .thenAccept(ws -> {
                    System.out.println("WebServer is up!");
                    ws.whenShutdown().thenRun(() -> System.out.println("WEB server is DOWN. Good bye!"));
                })
                .exceptionally(t -> {
                    System.err.println("Startup failed: " + t.getMessage());
                    t.printStackTrace(System.err);
                    return null;
                });
        return webServer;
    }

    private static Routing createPlainRouting() {
        AtomicBoolean atomicBoolean = new AtomicBoolean(true);
        return Routing.builder()
                .get("/", (req, res) -> res.send("Hello world unsecured!"))
                .get("/reload",
                     (req, res) -> {
                         String configName = atomicBoolean.getAndSet(!atomicBoolean.get())
                                 ? "server-second-valid.tls"
                                 : "server.sockets.0.ssl";
                         req.webServer().updateTls(WebServerTls.create(CONFIG.get(configName)),
                                                   "secured");
                         res.send("SslContext reloaded. Affected named socket: secured");
                     })
                .build();
    }

    private static Routing createMtlsRouting() {
        return Routing.builder()
                .get("/", (req, res) -> {
                    String cn = req.headers().first(Http.Header.X_HELIDON_CN).orElse("Unknown CN");
                    res.send("Hello " + cn + "!");
                })
                .build();
    }

}
