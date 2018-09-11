/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.netty;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;

import io.helidon.common.configurable.Resource;
import io.helidon.common.http.Http;
import io.helidon.common.pki.KeyConfig;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.SSLContextBuilder;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.SocketConfiguration;
import io.helidon.webserver.WebServer;

import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.logging.LoggingFeature;
import org.hamcrest.Matcher;
import org.hamcrest.core.AllOf;
import org.hamcrest.core.StringContains;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * The MultiPortTest.
 */
public class MultiPortTest {

    private static final Logger LOGGER = Logger.getLogger(MultiPortTest.class.getName());

    private static Client client;
    private Handler commonHandler;
    private SSLContext ssl;
    private WebServer webServer;

    @BeforeAll
    public static void createClientAcceptingAllCertificates() throws Exception {

        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                    }
                }
        };

        // Install the all-trusting trust manager
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());

        client = ClientBuilder.newBuilder()
                              .register(new LoggingFeature(LOGGER, Level.WARNING, LoggingFeature.Verbosity.PAYLOAD_ANY, 1500))
                              .property(ClientProperties.FOLLOW_REDIRECTS, true)
                              .sslContext(sc)
                              .hostnameVerifier((s, sslSession) -> true)
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
                                                 .keystore(Resource.from("ssl/certificate.p12"))
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
        Response response = client.target(protocol + "://localhost:" + port).path(path).request().get();
        assertThat("Unexpected response: " + response, response.readEntity(String.class), matcher);
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
                             .configuration(ServerConfiguration.builder()
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
                             .configuration(
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
                                                                               req.uri()));
                                                         res.send();
                                                     })
                                             )
                             .build();

        webServer
                .start()
                .toCompletableFuture()
                .join();

        Response response = client.target("http://localhost:" + webServer.port("redirect")).path("/foo").request().get();
        assertThat("Unexpected response: " + response, response.getHeaderString("Location"),
                   AllOf.allOf(StringContains.containsString("https://localhost:"), StringContains.containsString("/foo")));
        assertThat("Unexpected response: " + response, response.getStatus(), is(Http.Status.MOVED_PERMANENTLY_301.code()));

        assertResponse("https", webServer.port(), "/foo", is("Root! 1"));

        Response responseRedirected = client.target(response.getHeaderString("Location")).request().get();
        assertThat("Unexpected response: " + responseRedirected, responseRedirected.readEntity(String.class), is("Root! 2"));
    }

    @Test
    public void compositeFromConfig() throws Exception {
        Config config = Config.from(ConfigSources.classpath("multiport/application.yaml"));
        webServer = WebServer.builder(Routing.builder()
                                             .get("/", (req, res) -> res.send("Plain!")))
                             .configuration(ServerConfiguration.fromConfig(config.get("webserver")))
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
