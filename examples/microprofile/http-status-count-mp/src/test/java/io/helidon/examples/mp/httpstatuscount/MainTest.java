/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
package io.helidon.examples.mp.httpstatuscount;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;

import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@HelidonTest
@TestMethodOrder(MethodOrderer.MethodName.class)
public class MainTest {

    @Inject
    private MetricRegistry registry;

    @Inject
    private WebTarget target;


    @Test
    public void testMicroprofileMetrics() {
        String message = target.path("simple-greet/Joe")
                .request()
                .get(String.class);

        assertThat(message, is("Hello Joe"));
        Counter counter = registry.counter("personalizedGets");
        double before = counter.getCount();

        message = target.path("simple-greet/Eric")
                .request()
                .get(String.class);

        assertThat(message, is("Hello Eric"));
        double after = counter.getCount();
        assertThat("Difference in personalized greeting counter between successive calls", after - before, is(1d));
    }

    @Test
    public void testMetrics() throws Exception {
        Response response = target
                .path("metrics")
                .request()
                .get();
        assertThat(response.getStatus(), is(200));
    }

    @Test
    public void testHealth() throws Exception {
        Response response = target
                .path("health")
                .request()
                .get();
        assertThat(response.getStatus(), is(200));
    }

    @Test
    public void testGreet() throws Exception {
        GreetingMessage message = target
                .path("simple-greet")
                .request()
                .get(GreetingMessage.class);
        assertThat(message.getMessage(), is("Hello World!"));
    }
                
    @Test
    public void testGreetings() throws Exception {
        GreetingMessage jsonMessage = target
                .path("greet/Joe")
                .request()
                .get(GreetingMessage.class);
        assertThat(jsonMessage.getMessage(), is("Hello Joe!"));

        try (Response r = target
                .path("greet/greeting")
                .request()
                .put(Entity.entity("{\"message\" : \"Hola\"}", MediaType.APPLICATION_JSON))) {
            assertThat(r.getStatus(), is(204));
        }

        jsonMessage = target
                .path("greet/Jose")
                .request()
                .get(GreetingMessage.class);
        assertThat(jsonMessage.getMessage(), is("Hola Jose!"));
    }
                
}
