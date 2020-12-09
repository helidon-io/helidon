/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.nativeimage.se1;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

import io.helidon.common.http.Http;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.security.Security;
import io.helidon.security.providers.common.OutboundTarget;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webclient.security.WebClientSecurity;

import org.glassfish.tyrus.client.ClientManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test for {@link io.helidon.tests.integration.nativeimage.se1.Se1Main}.
 */
abstract class Se1IT {
    private static WebClient webClient;
    private static HelidonProcessRunner runner;
    private static String port;

    protected static void startTheServer(HelidonProcessRunner.ExecType type) {
        runner = HelidonProcessRunner
                .create(type,
                        "helidon.tests.integration.nativeimage.se1",
                        Se1Main.class.getName(),
                        "helidon-tests-native-image-se-1",
                        Se1Main::startServer,
                        Se1Main::stopServer);

        runner.startApplication();
        port = String.valueOf(runner.port());

        webClient = WebClient.builder()
                .baseUri("http://localhost:" + port)
                .addMediaSupport(JsonpSupport.create())
                .addService(WebClientSecurity
                                    .create(Security.builder()
                                                    .addProvider(HttpBasicAuthProvider.builder()
                                                                         .addOutboundTarget(OutboundTarget.builder("all")
                                                                                                    .addHost("*")
                                                                                                    .build())
                                                                         .build()).build()))
                .build();
    }

    @AfterAll
    public static void stopServer() {
        runner.stopApplication();
    }

    @Test
    void testDefaultGreeting() {
        JsonObject response = webClient.get()
                .path("/greet")
                .request(JsonObject.class)
                .await(10, TimeUnit.SECONDS);

        assertThat(response.getString("message"), is("Hello World!"));
    }

    @Test
    void testNamedGreeting() {
        JsonObject response = webClient.get()
                .path("/greet/Joe")
                .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_USER, "jack")
                .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_PASSWORD, "password")
                .request(JsonObject.class)
                .await(10, TimeUnit.SECONDS);

        // greeting based on security, not path
        assertThat(response.getString("message"), is("Hello jack!"));
    }

    @Test
    public void testChangedGreeting() throws Exception {
        JsonBuilderFactory json = Json.createBuilderFactory(Map.of());
        JsonObject request = json.createObjectBuilder()
                .add("greeting", "Hola")
                .build();

        WebClientResponse putResponse = webClient.put()
                .path("/greet/greeting")
                .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_USER, "jack")
                .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_PASSWORD, "password")
                .submit(request)
                .await(10, TimeUnit.SECONDS);

        assertThat(putResponse.status(), is(Http.Status.NO_CONTENT_204));

        JsonObject response = webClient.get()
                .path("/greet/Jose")
                .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_USER, "jack")
                .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_PASSWORD, "password")
                .request(JsonObject.class)
                .await(10, TimeUnit.SECONDS);

        assertThat(response.getString("message"), is("Hola jack!"));

        request = json.createObjectBuilder()
                .add("greeting", "Hello")
                .build();

        putResponse = webClient.put()
                .path("/greet/greeting")
                .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_USER, "jack")
                .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_PASSWORD, "password")
                .submit(request)
                .await(10, TimeUnit.SECONDS);

        assertThat("Set back to Hello", putResponse.status(), is(Http.Status.NO_CONTENT_204));

        response = webClient.get()
                .path("/greet/Jose")
                .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_USER, "jack")
                .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_PASSWORD, "password")
                .request(JsonObject.class)
                .await(10, TimeUnit.SECONDS);

        assertThat("Original greeting again", response.getString("message"), is("Hello jack!"));
    }

    @Test
    void testHealthEndpoint() {
        WebClientResponse healthResponse = webClient.get()
                .path("/health")
                .request()
                .await(10, TimeUnit.SECONDS);

        assertThat(healthResponse.status(), is(Http.Status.OK_200));
    }

    @Test
    void testMetricsEndpoint() {
        WebClientResponse healthResponse = webClient.get()
                .path("/metrics")
                .request()
                .await(10, TimeUnit.SECONDS);

        assertThat(healthResponse.status(), is(Http.Status.OK_200));
    }

    @Test
    void testStaticContentAnotherJar() {
        String content = webClient.get()
                .path("/static/jar/resource.txt")
                .request(String.class)
                .await(10, TimeUnit.SECONDS);
        assertThat(content, is("jar-resource-text"));
    }

    @Test
    void testStaticContentThisJar() {
        String content = webClient.get()
                .path("/static/classpath/resource.txt")
                .request(String.class)
                .await(10, TimeUnit.SECONDS);
        assertThat(content, is("classpath-resource-text"));
    }

    @Test
    void testStaticContentPath() {
        String content = webClient.get()
                .path("/static/path/resource.txt")
                .request(String.class)
                .await(10, TimeUnit.SECONDS);
        assertThat(content, is("file-resource-text"));
    }

    @Test
    void testWebSocketEndpoint()
            throws IOException, DeploymentException, InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<String> openFuture = new CompletableFuture<>();
        CompletableFuture<String> closeFuture = new CompletableFuture<>();
        AtomicReference<List<String>> messagesRef = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        ClientManager.createClient()
                .connectToServer(new Endpoint() {
                    @Override
                    public void onOpen(Session session, EndpointConfig endpointConfig) {
                        List<String> messages = new LinkedList<>();
                        messagesRef.set(messages);
                        openFuture.complete("opened");
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String s) {
                                messages.add(s);
                                if (messages.size() == 2) {
                                    try {
                                        session.close();
                                    } catch (IOException e) {
                                        error.set(e);
                                    }
                                }
                            }
                        });

                        try {
                            session.getBasicRemote().sendText("Message");
                            session.getBasicRemote().sendText("1");
                            session.getBasicRemote().sendText("SEND");
                            session.getBasicRemote().sendText("Message2");
                            session.getBasicRemote().sendText("SEND");
                        } catch (IOException e) {
                            error.set(e);
                        }
                    }

                    @Override
                    public void onError(Session session, Throwable thr) {
                        error.set(thr);
                        closeFuture.completeExceptionally(thr);
                    }

                    @Override
                    public void onClose(Session session, CloseReason closeReason) {
                        closeFuture.complete("closed");
                    }
                }, URI.create("ws://localhost:" + port + "/ws/messages"));

        closeFuture.get(10, TimeUnit.SECONDS);

        Throwable throwable = error.get();
        if (throwable != null) {
            fail("Failed to handle websocket", throwable);
        }
        assertThat(openFuture.isDone(), is(true));
        assertThat(messagesRef.get(), hasItems("Message1", "Message2"));
    }
}