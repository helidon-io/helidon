/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.examples.webserver.mtls;

import java.io.UncheckedIOException;
import java.nio.file.Path;

import javax.net.ssl.SSLHandshakeException;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.http.ContentDisposition;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test of mutual TLS example.
 */
@ServerTest
public class RevocationConfigTest {

    private static Config config;
    private final WebServer server;
    private final Http1Client client;

    public RevocationConfigTest(WebServer server) {
        this.server = server;
        this.client = Http1Client.builder().config(config.get("client")).build();
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder server) {
        config = Config.create();
        server.config(config)
                .port(8080)
                .routing(RevocationConfigTest::plainRouting)
                .putSocket("secured", socket -> socket
                        .from(server.sockets().get("secured"))
                        .routing(routing -> routing
                                .get("/", (req, res) -> res.send("Hello world secured!"))));
    }

    private static void plainRouting(HttpRouting.Builder routing) {
        routing.get("/", (req, res) -> res.send("Hello world unsecured!"))
                .get("/ca.crl", (req, res) -> {
                    Path filePath = Path.of(RevocationConfigTest.class.getResource("/ca.crl").getPath());
                    ServerResponseHeaders headers = res.headers();
                    headers.contentType(MediaTypes.APPLICATION_OCTET_STREAM);
                    headers.set(ContentDisposition.builder()
                                        .filename(filePath.getFileName().toString())
                                        .build());
                    res.send(filePath);
                });
    }

    @Test
    public void testEnabledRevocationConfigWithCrl() {
        //Exception happens on the server, this connection just fails with HandshakeException
        UncheckedIOException exception = assertThrows(UncheckedIOException.class, this::executeRequest);
        assertThat(exception.getCause(), instanceOf(SSLHandshakeException.class));
        assertThat(exception.getCause().getMessage(), is("Received fatal alert: certificate_unknown"));
    }

    private void executeRequest() {
        client.get("https://localhost:" + server.port("secured") + "/").request();
    }
}