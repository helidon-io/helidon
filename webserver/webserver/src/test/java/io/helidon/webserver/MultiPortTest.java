/*
 * Copyright (c) 2017, 2022 Oracle and/or its affiliates.
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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import io.helidon.common.configurable.Resource;
import io.helidon.common.http.Http;
import io.helidon.common.pki.KeyConfig;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientTls;

import org.hamcrest.Matcher;
import org.hamcrest.core.AllOf;
import org.hamcrest.core.StringContains;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The MultiPortTest.
 */
public class MultiPortTest {

    private static WebClient webClient;
    private Handler commonHandler;
    private WebServer webServer;
    private WebServerTls webServerTls;
    private static final Duration TIMEOUT = Duration.ofSeconds(25);

    @BeforeAll
    public static void createClientAcceptingAllCertificates() {
        webClient = WebClient.builder()
                .followRedirects(true)
                .tls(WebClientTls.builder().trustAll(true).build())
                .build();
    }

    @BeforeEach
    public void init() throws Exception {

        commonHandler = new Handler() {

            private final AtomicLong counter = new AtomicLong();

            @Override
            public void accept(ServerRequest req, ServerResponse res) {
                res.send("Root! " + counter.incrementAndGet());
            }
        };

        webServerTls = WebServerTls.builder()
                .privateKey(KeyConfig.keystoreBuilder()
                                    .keystore(Resource.create("ssl/certificate.p12"))
                                    .keystorePassphrase("helidon".toCharArray())
                                    .build())
                .build();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (webServer != null) {
            webServer.shutdown();
        }
    }

    private void assertResponse(final String protocol, int port, String path, Matcher<String> matcher) {
        try {
            webClient.get()
                    .uri(protocol + "://localhost:" + port)
                    .path(path)
                    .request(String.class)
                    .thenAccept(it -> assertThat("Unexpected response: " + it, it, matcher))
                    .await(TIMEOUT);
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    public void programmaticNoCompound() throws Exception {

        WebServer webServerPlain = WebServer.create(
                Routing.builder()
                        .get("/", commonHandler)
                        .get("/variable", (req, res) -> res.send("Variable 8080")));

        WebServer webServerTls = WebServer.builder(
                Routing.builder()
                        .get("/", commonHandler)
                        .get("/variable", (req, res) -> res.send("Variable 8443")))
                .tls(this.webServerTls)
                .build();

        try {
            webServerPlain.start().await(TIMEOUT);
            webServerTls.start().await(TIMEOUT);

            assertResponse("https", webServerTls.port(), "/", is("Root! 1"));
            assertResponse("http", webServerPlain.port(), "/", is("Root! 2"));
            assertResponse("https", webServerTls.port(), "/", is("Root! 3"));
            assertResponse("http", webServerPlain.port(), "/", is("Root! 4"));

            assertResponse("https", webServerTls.port(), "/variable", is("Variable 8443"));
            assertResponse("http", webServerPlain.port(), "/variable", is("Variable 8080"));
        } finally {
            try {
                webServerPlain.shutdown().await(TIMEOUT);
            } finally {
                webServerTls.shutdown().await(TIMEOUT);
            }
        }
    }

    @Test
    public void compositeInlinedWebServer() throws Exception {
        // start all of the servers
        webServer = WebServer.builder()
                .defaultSocket(s -> s
                        .tls(webServerTls)
                ).routing(r -> r
                        .get("/overridden", (req, res) -> res.send("Overridden 8443"))
                        .get("/", commonHandler)
                        .get("/variable", (req, res) -> res.send("Variable 8443"))
                )
                .socket("plain", (socket, router) -> router
                        .addRouting(() -> Routing.builder()
                                .get("/overridden", (req, res) -> res.send("Overridden 8080"))
                                .get("/", commonHandler)
                                .get("/variable", (req, res) -> res.send("Variable 8080"))
                                .build()
                        )
                )
                .build();

        webServer.start()
                .await(TIMEOUT);

        assertResponse("https", webServer.port(), "/", is("Root! 1"));
        assertResponse("http", webServer.port("plain"), "/", is("Root! 2"));
        assertResponse("https", webServer.port(), "/", is("Root! 3"));
        assertResponse("http", webServer.port("plain"), "/", is("Root! 4"));

        assertResponse("https", webServer.port(), "/variable", is("Variable 8443"));
        assertResponse("http", webServer.port("plain"), "/variable", is("Variable 8080"));
    }

    @Test
    public void compositeSingleRoutingWebServer() throws Exception {
        // start all of the servers
        webServer = WebServer.builder()
                .routing(r -> r
                        .get("/overridden", (req, res) -> res.send("Overridden BOTH"))
                        .get("/", commonHandler)
                        .get("/variable", (req, res) -> res.send("Variable BOTH"))
                )
                .socket("secured", s -> s
                        .name("secured")
                        .tls(webServerTls)
                )
                .build();

        webServer.start()
                .await(TIMEOUT);

        assertResponse("https", webServer.port("secured"), "/", is("Root! 1"));
        assertResponse("http", webServer.port(), "/", is("Root! 2"));
        assertResponse("https", webServer.port("secured"), "/", is("Root! 3"));
        assertResponse("http", webServer.port(), "/", is("Root! 4"));

        assertResponse("https", webServer.port("secured"), "/variable", is("Variable BOTH"));
        assertResponse("http", webServer.port(), "/variable", is("Variable BOTH"));
    }

    @Test
    public void compositeRedirectWebServer() throws Exception {

        // start all of the servers
        webServer = WebServer.builder()
                .defaultSocket(socket -> socket
                        .tls(webServerTls)
                )
                .routing(r -> r.get("/foo", commonHandler))
                .socket("redirect", (socket, router) -> router
                        .addRouting(Routing.builder()
                                .any((req, res) -> {
                                    res.status(Http.Status.MOVED_PERMANENTLY_301)
                                            .headers()
                                            .add(Http.Header.LOCATION,
                                                    String.format("https://%s:%d%s",
                                                            req.headers()
                                                                    .first(Http.Header.HOST)
                                                                    .map(s -> s.contains(":") ? s
                                                                            .subSequence(0, s.indexOf(":")) : s)
                                                                    .orElseThrow(() -> new IllegalStateException(
                                                                            "Header 'Host' not found!")),
                                                            req.webServer().port(),
                                                            req.path()));
                                    res.send();
                                })))
                .build();

        webServer.start()
                .await(TIMEOUT);

        WebClient webClient = WebClient.builder()
                .tls(WebClientTls.builder().trustAll(true).build())
                .build();

        assertResponse("https", webServer.port(), "/foo", is("Root! 1"));
        webClient.get()
                .uri("http://localhost:" + webServer.port("redirect"))
                .path("/foo")
                .request()
                .thenApply(it -> {
                    assertThat("Unexpected response: " + it,
                               it.headers().first(Http.Header.LOCATION).get(),
                               AllOf.allOf(StringContains.containsString("https://localhost:"),
                                           StringContains.containsString("/foo")));
                    assertThat("Unexpected response: " + it, it.status(), is(Http.Status.MOVED_PERMANENTLY_301));
                    return it;
                })
                .thenCompose(it -> webClient.get()
                        .uri(it.headers().first(Http.Header.LOCATION).get())
                        .request(String.class))
                .thenAccept(it -> assertThat("Unexpected response: " + it, it, is("Root! 2")))
                .await(TIMEOUT);
    }

    @Test
    public void compositeFromConfig() throws Exception {
        Config config = Config.create(ConfigSources.classpath("multiport/application.yaml"));
        webServer = WebServer.builder()
                .defaultSocket(s -> s
                        .host("localhost")
                )
                .routing(r -> r.get("/", (req, res) -> res.send("Plain!")))
                .config(config.get("webserver"))
                .socket("secured", (s, r) -> r.addRouting(Routing.builder()
                        .get("/", (req, res) -> res.send("Secured!")))
                )
                .build();

        webServer.start()
                .await(TIMEOUT);

        assertResponse("http", webServer.port(), "/", is("Plain!"));
        assertResponse("https", webServer.port("secured"), "/", is("Secured!"));
    }
}
