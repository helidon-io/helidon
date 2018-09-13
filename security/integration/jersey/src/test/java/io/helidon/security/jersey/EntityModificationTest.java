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

package io.helidon.security.jersey;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import io.helidon.common.reactive.Flow;
import io.helidon.common.reactive.SubmissionPublisher;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.Principal;
import io.helidon.security.Security;
import io.helidon.security.annot.Authenticated;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.jersey.JerseySupport;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link SecurityFilter} with message modification.
 */
public class EntityModificationTest {
    private static WebServer server;
    private static Client client;
    private static int port;

    @BeforeAll
    public static void initClass() throws Throwable {
        Logger l = Logger.getLogger("AUDIT");

        ConsoleHandler ch = new ConsoleHandler();
        ch.setFormatter(new SimpleFormatter());
        ch.setLevel(Level.FINEST);
        l.addHandler(ch);
        l.setUseParentHandlers(false);
        l.setLevel(Level.FINEST);

        server = Routing.builder()
                .register(JerseySupport.builder()
                                  .register(MyResource.class)
                                  .register(new SecurityFeature(buildSecurity()))
                                  .register(new ExceptionMapper<Exception>() {
                                      @Override
                                      public Response toResponse(Exception exception) {
                                          exception.printStackTrace();
                                          return Response.serverError().build();
                                      }
                                  })
                                  .build())
                .build()
                .createServer();

        client = ClientBuilder.newClient();

        CountDownLatch cdl = new CountDownLatch(1);
        AtomicReference<Throwable> th = new AtomicReference<>();
        server.start().whenComplete((webServer, throwable) -> {
            th.set(throwable);
            cdl.countDown();
        });

        cdl.await();

        if (th.get() != null) {
            throw th.get();
        }

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
                                subscription.request(1);
                                outPublisher.submit(item);
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
                        CompletableFuture<AuthenticationResponse> future = new CompletableFuture<>();

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
                                        future.complete(forUser("user"));
                                    }
                                });
                            };
                        });

                        return future;
                    })
                    .orElse(CompletableFuture.completedFuture(forUser("user")));
        }).build();
    }

    private static AuthenticationResponse forUser(String user) {
        return AuthenticationResponse.success(Principal.create(user));
    }

    @AfterAll
    public static void afterClass() throws InterruptedException {
        client.close();

        CountDownLatch cdl = new CountDownLatch(1);
        server.shutdown().whenComplete((webServer, throwable) -> cdl.countDown());
        cdl.await();
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

    @Path("/")
    public static class MyResource {
        @POST
        @Authenticated
        @Produces(MediaType.TEXT_PLAIN)
        @Consumes(MediaType.TEXT_PLAIN)
        public String getIt(String request) {
            return request;
        }
    }
}
