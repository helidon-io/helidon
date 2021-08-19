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
package io.helidon.security.examples.outbound;

import java.util.concurrent.CompletionStage;

import io.helidon.security.Security;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.security.WebClientSecurity;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.security.examples.outbound.OutboundOverrideJwtExample.clientPort;
import static io.helidon.security.examples.outbound.OutboundOverrideJwtExample.startClientService;
import static io.helidon.security.examples.outbound.OutboundOverrideJwtExample.startServingService;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Test of security override example.
 */
public class OutboundOverrideJwtExampleTest {

    private static WebClient webClient;

    @BeforeAll
    public static void setup() {
        CompletionStage<Void> first = startClientService(-1);
        CompletionStage<Void> second = startServingService(-1);

        first.toCompletableFuture().join();
        second.toCompletableFuture().join();

        Security security = Security.builder()
                .addProvider(HttpBasicAuthProvider.builder().build())
                .build();

        webClient = WebClient.builder()
                .baseUri("http://localhost:" + clientPort())
                .addService(WebClientSecurity.create(security))
                .build();
    }

    @Test
    public void testOverrideExample() {
        String value = webClient.get()
                .path("/override")
                .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_USER, "jack")
                .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_PASSWORD, "password")
                .request(String.class)
                .await();

        assertThat(value, is("You are: jack, backend service returned: jill"));
    }

    @Test
    public void testPropagateExample() {
        String value = webClient.get()
                .path("/propagate")
                .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_USER, "jack")
                .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_PASSWORD, "password")
                .request(String.class)
                .await();

        assertThat(value, is("You are: jack, backend service returned: jack"));
    }

}
