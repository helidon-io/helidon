/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import io.helidon.common.configurable.Resource;
import io.helidon.common.http.Http;
import io.helidon.common.pki.KeyConfig;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.webclient.Ssl;
import io.helidon.webclient.WebClient;

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

    private static final Logger LOGGER = Logger.getLogger(MultiPortTest.class.getName());

    private static WebClient webClient;
    private Handler commonHandler;
    private SSLContext ssl;
    private WebServer webServer;

    @BeforeAll
    public static void createClientAcceptingAllCertificates() throws Exception {
        webClient = WebClient.builder()
                .followRedirects(true)
                .ssl(Ssl.builder().trustAll(true).build())
                .build();
    }

    @BeforeEach
    public void init() throws Exception {

        commonHandler = new Handler() {

            private volatile int counter = 0;

            @Override
            public void accept(ServerRequest req, ServerResponse res) {
                res.send("Root! " + ++counter);
            }
        };

        ssl = SSLContextBuilder.create(KeyConfig.keystoreBuilder()
                                                 .keystore(Resource.create("ssl/certificate.p12"))
                                                 .keystorePassphrase(new char[] {'h', 'e', 'l', 'i', 'd', 'o', 'n'})
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
                    .toCompletableFuture()
                    .get();
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    public void programmaticNoCompound() throws Exception {

        WebServer webServer8080 = WebServer.create(
                ServerConfiguration.builder()
                                   .build(),
                Routing.builder()
                       .get("/", commonHandler)
                       .get("/variable", (req, res) -> res.send("Variable 8080")));

        WebServer webServer8443 = WebServer.create(
                ServerConfiguration.builder()
                                   .ssl(ssl),
                Routing.builder()
                       .get("/", commonHandler)
                       .get("/variable", (req, res) -> res.send("Variable 8443")));

        try {
            webServer8080.start().toCompletableFuture().join();
            webServer8443.start().toCompletableFuture().join();

            System.out.println("Webserver started on port: " + webServer8080.port());
            System.out.println("Webserver started on port: " + webServer8443.port());

            assertResponse("https", webServer8443.port(), "/", is("Root! 1"));
            assertResponse("http", webServer8080.port(), "/", is("Root! 2"));
            assertResponse("https", webServer8443.port(), "/", is("Root! 3"));
            assertResponse("http", webServer8080.port(), "/", is("Root! 4"));

            assertResponse("https", webServer8443.port(), "/variable", is("Variable 8443"));
            assertResponse("http", webServer8080.port(), "/variable", is("Variable 8080"));
        } finally {
            try {
                webServer8080.shutdown().toCompletableFuture().join();
            } finally {
                webServer8443.shutdown().toCompletableFuture().join();
            }
        }
    }

    @Test
    public void compositeInlinedWebServer() throws Exception {
        // start all of the servers
        webServer = WebServer.builder(
                Routing.builder()
                       .get("/overridden", (req, res) -> res.send("Overridden 8443"))
                       .get("/", commonHandler)
                       .get("/variable", (req, res) -> res.send("Variable 8443"))

                       .build())
                             .config(ServerConfiguration.builder()
                                                               .ssl(ssl)
                                                               .addSocket("plain", SocketConfiguration.builder()))
                             .addNamedRouting("plain",
                                              Routing.builder()
                                                     .get("/overridden", (req, res) -> res.send("Overridden 8080"))
                                                     .get("/", commonHandler)
                                                     .get("/variable", (req, res) -> res.send("Variable 8080")))
                             .build();

        webServer.start()
                 .toCompletableFuture()
                 .join();

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
        webServer = WebServer.create(
                ServerConfiguration.builder()
                                   .addSocket("secured",
                                              SocketConfiguration.builder()
                                                                 .ssl(ssl)),
                Routing.builder()
                       .get("/overridden", (req, res) -> res.send("Overridden BOTH"))
                       .get("/", commonHandler)
                       .get("/variable", (req, res) -> res.send("Variable BOTH")));

        webServer.start()
                 .toCompletableFuture()
                 .join();

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
        webServer = WebServer.builder(Routing.builder()
                                             .get("/foo", commonHandler))
                             .config(
                                     ServerConfiguration.builder()
                                                        .ssl(ssl)
                                                        .addSocket("redirect", SocketConfiguration.builder()))
                             .addNamedRouting("redirect",
                                              Routing.builder()
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
                                                     })
                                             )
                             .build();

        webServer
                .start()
                .toCompletableFuture()
                .join();

        WebClient webClient = WebClient.builder()
                .ssl(Ssl.builder().trustAll(true).build())
                .build();

//        Response response = client.target("http://localhost:" + webServer.port("redirect")).path("/foo").request().get();
//        assertThat("Unexpected response: " + response, response.getHeaderString("Location"),
//                   AllOf.allOf(StringContains.containsString("https://localhost:"), StringContains.containsString("/foo")));
//        assertThat("Unexpected response: " + response, response.getStatus(), is(Http.Status.MOVED_PERMANENTLY_301.code()));
//
//        assertResponse("https", webServer.port(), "/foo", is("Root! 1"));
//
//        Response responseRedirected = client.target(response.getHeaderString("Location")).request().get();
//        assertThat("Unexpected response: " + responseRedirected, responseRedirected.readEntity(String.class), is("Root! 2"));
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
                .toCompletableFuture()
                .get();
    }

    @Test
    public void compositeFromConfig() throws Exception {
        Config config = Config.create(ConfigSources.classpath("multiport/application.yaml"));
        webServer = WebServer.builder(Routing.builder()
                                             .get("/", (req, res) -> res.send("Plain!")))
                             .config(ServerConfiguration.create(config.get("webserver")))
                             .addNamedRouting("secured",
                                              Routing.builder()
                                                     .get("/", (req, res) -> res.send("Secured!")))
                             .build();

        webServer.start()
                 .toCompletableFuture()
                 .join();

        assertResponse("http", webServer.port(), "/", is("Plain!"));
        assertResponse("https", webServer.port("secured"), "/", is("Secured!"));
    }
}
