/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.jersey;

import java.net.InetAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.testsupport.LoggingTestUtils;

import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * The JerseyExampleMain.
 * <p>
 * One of possible runs is:
 * <pre><code>
 *     seq 10000000 | head -c 50000 > /tmp/test.bin
 *     FILE=`mktemp`; for A in `seq 100000`; do cat /tmp/test.bin | curl -X POST -Ssf http://localhost:8080/jersey/first/content
 * --data-binary @- -vvv > $FILE; diff /tmp/test.bin $FILE || break; done
 * </code></pre>
 */
public final class JerseyExampleMain {

    public static final JerseyExampleMain INSTANCE = new JerseyExampleMain();

    private JerseyExampleMain() {
    }

    private volatile Client client;
    private volatile WebServer webServer;

    public static void main(String... args) throws InterruptedException, ExecutionException, TimeoutException {

        LoggingTestUtils.initializeLogging();

        INSTANCE.webServer(false);
    }

    synchronized WebTarget target() throws InterruptedException, ExecutionException, TimeoutException {
        WebServer webServer = webServer(true);

        if (client == null) {
            client = client();
        }

        return client.target("http://localhost:" + webServer.port());
    }

    static Client client() {
        ClientConfig clientConfig = new ClientConfig();
        // Grizzly Connector has issues with large sent data .. we can't use it
        clientConfig.connectorProvider(new ApacheConnectorProvider());
        Client client = ClientBuilder.newClient(clientConfig);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                client.close();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }));
        return client;
    }

    synchronized WebServer webServer(boolean testing) throws InterruptedException, TimeoutException, ExecutionException {
        if (webServer != null) {
            return webServer;
        }

        ServerConfiguration.Builder builder = ServerConfiguration.builder()
                                                                 .bindAddress(InetAddress.getLoopbackAddress());
        if (!testing) {
            // in case we're running as main an not in test, run on a fixed port
            builder.port(8080);
        }

        webServer = WebServer.create(builder.build(),
                                     Routing.builder()
                                            .register("/jersey",
                                                      JerseySupport.builder()
                                                                   .register(JerseyExampleResource.class))
                                            .any("/jersey/second", (req, res) -> {
                                                req.content()
                                                   .as(String.class)
                                                   .thenAccept(s -> {
                                                       res.send("second-content: " + s)
                                                          .exceptionally(throwable -> {
                                                              throwable.printStackTrace();
                                                              fail("Should not fail: " + throwable.getMessage());
                                                              return null;
                                                          });
                                                   })
                                                   .exceptionally(throwable -> {
                                                       req.next(throwable);
                                                       return null;
                                                   });
                                            }));

        webServer.start().toCompletableFuture().get(10, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                webServer.shutdown().toCompletableFuture().get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }));

        if (!testing) {
            System.out.println("WebServer Jersey application started.");
            System.out.println("Hit CTRL+C to stop.");
            Thread.currentThread().join();
        }

        return webServer;
    }
}
