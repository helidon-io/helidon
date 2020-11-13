/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;

import io.helidon.common.http.Http;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.security.Security;
import io.helidon.security.providers.common.OutboundTarget;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webclient.security.WebClientSecurity;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test for {@link io.helidon.tests.integration.nativeimage.se1.Se1Main}.
 */
class Se1MainTest {
    private static final Logger LOGGER = Logger.getLogger(Se1MainTest.class.getName());

    private static WebClient webClient;
    private static HelidonTestProcess runner;

    @BeforeAll
    public static void startTheServer() throws Exception {
        WebClient.Builder clientBuilder = WebClient.builder();
        runner = HelidonTestProcess
                .create("helidon.tests.integration.nativeimage.se1",
                        Se1Main.class,
                        "helidon-tests-native-image-se-1",
                        Se1Main::startServer,
                        Se1Main::stopServer);
        LOGGER.info("Runtime type: " + runner.execType());

        runner.startApplication();

        Thread.sleep(2000);
        // read the port
        Properties props = new Properties();
        try {
            props.load(Files.newBufferedReader(Paths.get("runtime.properties")));
        } catch (IOException e) {
            fail("Could not find properties with port", e);
        }
        String port = props.getProperty("port");
        clientBuilder.baseUri("http://localhost:" + port);

        webClient = clientBuilder.addMediaSupport(JsonpSupport.create())
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

}