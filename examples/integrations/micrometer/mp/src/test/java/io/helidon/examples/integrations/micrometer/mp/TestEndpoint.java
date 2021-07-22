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
 *
 */
package io.helidon.examples.integrations.micrometer.mp;

import javax.inject.Inject;
import javax.json.JsonObject;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import io.helidon.microprofile.tests.junit5.HelidonTest;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.junit.jupiter.api.Test;

import static io.helidon.examples.integrations.micrometer.mp.GreetResource.PERSONALIZED_GETS_COUNTER_NAME;

import static org.junit.jupiter.api.Assertions.assertEquals;


@HelidonTest
public class TestEndpoint {

    @Inject
    private WebTarget webTarget;

    @Inject
    private MeterRegistry registry;

    @Test
    public void pingGreet() {

        JsonObject jsonObject = webTarget
                 .path("/greet/Joe")
                 .request(MediaType.APPLICATION_JSON_TYPE)
                 .get(JsonObject.class);

        String responseString = jsonObject.getString("message");

        assertEquals("Hello Joe!", responseString, "Response string");
        Counter counter = registry.counter(PERSONALIZED_GETS_COUNTER_NAME);
        double before = counter.count();

        jsonObject = webTarget
                .path("/greet/Jose")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get(JsonObject.class);

        responseString = jsonObject.getString("message");

        assertEquals("Hello Jose!", responseString, "Response string");
        double after = counter.count();
        assertEquals(1d, after - before, "Difference in personalized greeting counter between successive calls");

    }
}
