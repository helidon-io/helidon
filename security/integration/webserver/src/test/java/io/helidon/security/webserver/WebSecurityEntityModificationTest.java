/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.webserver;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;

import io.helidon.common.reactive.Flow;
import io.helidon.common.reactive.SubmissionPublisher;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.Principal;
import io.helidon.security.Security;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link SecurityHandler} with message modification.
 */
public class WebSecurityEntityModificationTest {
    private static WebServer server;
    private static Client client;
    private static int port;

    @BeforeAll
    public static void initClass() throws IOException, InterruptedException {
        Logger l = Logger.getLogger("AUDIT");

        ConsoleHandler ch = new ConsoleHandler();
        ch.setFormatter(new SimpleFormatter());
        ch.setLevel(Level.FINEST);
        l.addHandler(ch);
        l.setUseParentHandlers(false);
        l.setLevel(Level.FINEST);

        Security security = buildSecurity();

        Routing routing = Routing.builder()
                .register(WebSecurity.from(security))
                // secure post
                .post("/", WebSecurity.authenticate())
                .post("/", (req, res) -> {
                    // simple echo
                    req.content().as(String.class).thenAccept(res::send);
                })
                .build();

        client = ClientBuilder.newClient();
        server = WebServer.create(routing);
        long t = System.currentTimeMillis();
        CountDownLatch cdl = new CountDownLatch(1);
        server.start().thenAccept(webServer -> {
            long time = System.currentTimeMillis() - t;
            System.out.println("Started server on localhost:" + webServer.port() + " in " + time + " millis");
            cdl.countDown();
        });

        //we must wait for server to start, so other tests are not triggered until it is ready!
        assertThat("Timeout while waiting for server to start!", cdl.await(5, TimeUnit.SECONDS), is(true));

        port = server.port();
    }

    private static Security buildSecurity() {
        return Security.builder().addAuthenticationProvider(request -> {
            request.getResponseEntity().ifPresent(message -> {
                // this is a test - just append " Suffix" to response message
                message.filter(inPublisher -> {
                    SubmissionPublisher<ByteBuffer> outPublisher = new SubmissionPublisher<>();

                    return subscriber -> {
                        outPublisher.subscribe(subscriber);

                        inPublisher.subscribe(new Flow.Subscriber<ByteBuffer>() {
                            volatile Flow.Subscription subscription;

                            @Override
                            public void onSubscribe(Flow.Subscription subscription) {
                                (this.subscription = subscription).request(1);
                            }

                            @Override
                            public void onNext(ByteBuffer item) {
                                outPublisher.submit(item);
                                subscription.request(1);
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                outPublisher.closeExceptionally(throwable);
                            }

                            @Override
                            public void onComplete() {
                                outPublisher.submit(ByteBuffer.wrap(" Suffix".getBytes()));
                                outPublisher.close();
                            }
                        });
                    };
                });
            });

            return request.getRequestEntity()
                    .map(message -> {

                        // this message filter will forward the message and once done, publish additional data
                        message.filter(inPublisher -> {
                            SubmissionPublisher<ByteBuffer> outPublisher = new SubmissionPublisher<>(ForkJoinPool.commonPool(),
                                                                                                     10);
                            return subscriber -> {
                                outPublisher.subscribe(subscriber);

                                inPublisher.subscribe(new Flow.Subscriber<ByteBuffer>() {
                                    volatile Flow.Subscription subscription;

                                    @Override
                                    public void onSubscribe(Flow.Subscription subscription) {
                                        (this.subscription = subscription).request(1);
                                    }

                                    @Override
                                    public void onNext(ByteBuffer item) {
                                        subscription.request(1);
                                        outPublisher.submit(item);
                                    }

                                    @Override
                                    public void onError(Throwable throwable) {
                                        outPublisher.closeExceptionally(throwable);
                                    }

                                    @Override
                                    public void onComplete() {
                                        outPublisher.submit(ByteBuffer.wrap(" Worldie".getBytes()));
                                        outPublisher.close();
                                    }
                                });
                            };
                        });

                        //we authenticate immediately, filter message as it goes
                        return CompletableFuture.completedFuture(forUser("user"));
                    })
                    .orElse(CompletableFuture.completedFuture(forUser("user")));
        }).build();
    }

    private static AuthenticationResponse forUser(String user) {
        return AuthenticationResponse.success(Principal.create(user));
    }

    @AfterAll
    public static void afterClass() throws InterruptedException {
        CountDownLatch cdl = new CountDownLatch(1);
        client.close();
        long t = System.currentTimeMillis();
        server.shutdown().thenAccept(webServer -> {
            long time = System.currentTimeMillis() - t;
            System.out.println("Stopped server in " + time + " millis");
            cdl.countDown();
        });
        //we must wait until server is shutdown, so another test class doesn't try to use the same port
        assertThat("Timeout while waiting for server to stop", cdl.await(5, TimeUnit.SECONDS), is(true));
    }

    @Test
    public void testReplaceEntity() {
        Response response = client
                .target("http://localhost:" + port)
                .path("/")
                .request()
                .post(Entity.text("Hello"));

        assertThat(response.getStatus(), is(200));

        String entity = response.readEntity(String.class);
        assertThat(entity, is("Hello Worldie Suffix"));
    }
}
