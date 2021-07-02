/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Server certificate reloading test.
 */
public class WebServerTlsTest {
    private static final Config CONFIG = Config.builder().sources(ConfigSources.classpath("config-with-ssl2.conf")).build();

    private static final Logger LOGGER = Logger.getLogger(WebServerTlsTest.class.getName());

    private static WebServer webServer;
    private static Client clientFirst;
    private static Client clientSecond;

    /**
     * Start the secured Web Server
     *
     * @throws Exception in case of an error
     */
    @BeforeAll
    private static void startServer() throws Exception {
        webServer = WebServer.builder(createPlainRouting())
                .config(ServerConfiguration.builder(CONFIG.get("webserver")))
                .addNamedRouting("secured", createMtlsRouting())
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
                })
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        LOGGER.info("Started secured server at: https://localhost:" + webServer.port());
    }

    @BeforeAll
    public static void createClientAcceptingAllCertificates() throws Exception {
        SSLContext sc = SSLContextBuilder.create(CONFIG.get("client-ssl-first"));

        clientFirst = ClientBuilder.newBuilder()
                .sslContext(sc)
                .build();
        sc = SSLContextBuilder.create(CONFIG.get("client-ssl-second"));

        clientSecond = ClientBuilder.newBuilder()
                .sslContext(sc)
                .build();
    }

    private static Routing createPlainRouting() {
        AtomicBoolean atomicBoolean = new AtomicBoolean(true);
        return Routing.builder()
                .get("/", (req, res) -> res.send("Hello world unsecured!"))
                .get("/reload",
                     (req, res) -> {
                         String configName = atomicBoolean.getAndSet(!atomicBoolean.get())
                                 ? "ssl-second"
                                 : "webserver.sockets.secured.ssl";
                         req.webServer().updateTls(WebServerTls.create(CONFIG.get(configName)),
                                                   "secured");
                         res.send("SslContext reloaded. Affected named socket: secured");
                     })
                .build();
    }

    private static Routing createMtlsRouting() {
        return Routing.builder()
                .get("/", (req, res) -> res.send("It works!"))
                .build();
    }

    @AfterAll
    public static void close() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
        }
        if (clientFirst != null) {
            clientFirst.close();
        }
        if (clientSecond != null) {
            clientSecond.close();
        }
    }

    @Test
    public void testLoadingFromConfig() {
        WebServerTls webServerTls = WebServerTls.create(CONFIG.get("ssl"));
        assertThat(webServerTls.clientAuth(), is(ClientAuthentication.OPTIONAL));
        assertThat(webServerTls.sslContext(), notNullValue());
    }

    @Test
    public void testReloading() {
        WebTarget target = clientFirst.target("http://localhost:" + webServer.port());
        assertThat(addHeader(target).get().readEntity(String.class), is("Hello world unsecured!"));
        target = clientSecond.target("http://localhost:" + webServer.port());
        assertThat(addHeader(target).get().readEntity(String.class), is("Hello world unsecured!"));
        target = clientFirst.target("https://localhost:" + webServer.port("secured"));
        assertThat(addHeader(target).get().readEntity(String.class), is("It works!"));
        ProcessingException ex = assertThrows(ProcessingException.class,
                                              () -> clientSecond.target("https://localhost:" + webServer.port("secured"))
                                                      .request().header(Http.Header.CONNECTION, "close").get());
        assertThat(ex.getCause(), instanceOf(SSLHandshakeException.class));
        assertThat(ex.getCause().getMessage(), is("PKIX path validation failed: java.security.cert.CertPathValidatorException: "
                                                          + "signature check failed"));

        target = clientFirst.target("http://localhost:" + webServer.port() + "/reload");
        assertThat(addHeader(target).get().readEntity(String.class), is("SslContext reloaded. Affected named socket: secured"));

        target = clientSecond.target("https://localhost:" + webServer.port("secured"));
        assertThat(addHeader(target).get().readEntity(String.class), is("It works!"));
        ex = assertThrows(ProcessingException.class,
                                              () -> clientFirst.target("https://localhost:" + webServer.port("secured"))
                                                      .request().header(Http.Header.CONNECTION, "close").get());
        assertThat(ex.getCause(), instanceOf(SSLHandshakeException.class));
        assertThat(ex.getCause().getMessage(), is("PKIX path validation failed: java.security.cert.CertPathValidatorException: "
                                                          + "signature check failed"));

        target = clientFirst.target("http://localhost:" + webServer.port() + "/reload");
        assertThat(addHeader(target).get().readEntity(String.class), is("SslContext reloaded. Affected named socket: secured"));

        target = clientFirst.target("https://localhost:" + webServer.port("secured"));
        assertThat(addHeader(target).get().readEntity(String.class), is("It works!"));
        ex = assertThrows(ProcessingException.class,
                          () -> clientSecond.target("https://localhost:" + webServer.port("secured"))
                                  .request().header(Http.Header.CONNECTION, "close").get());
        assertThat(ex.getCause(), instanceOf(SSLHandshakeException.class));
        assertThat(ex.getCause().getMessage(), is("PKIX path validation failed: java.security.cert.CertPathValidatorException: "
                                                          + "signature check failed"));
    }

    private Invocation.Builder addHeader(WebTarget webTarget) {
        return webTarget.request().header(Http.Header.CONNECTION, "close");
    }

}
