/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
package io.helidon.examples.integrations.micrometer.mp;

import io.helidon.microprofile.tests.junit5.HelidonTest;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;

import static io.helidon.examples.integrations.micrometer.mp.GreetResource.PERSONALIZED_GETS_COUNTER_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
public class TestEndpoint {

    @Inject
    private WebTarget webTarget;

    @Inject
    private MeterRegistry registry;

    @Test
    public void pingGreet() {

        GreetingMessage message = webTarget
                 .path("/greet/Joe")
                 .request(MediaType.APPLICATION_JSON_TYPE)
                 .get(GreetingMessage.class);

        String responseString = message.getMessage();

        assertThat("Response string", responseString, is("Hello Joe!"));
        Counter counter = registry.counter(PERSONALIZED_GETS_COUNTER_NAME);
        double before = counter.count();

        message = webTarget
                .path("/greet/Jose")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .get(GreetingMessage.class);

        responseString = message.getMessage();

        assertThat("Response string", responseString, is("Hello Jose!"));
        double after = counter.count();
        assertThat("Difference in personalized greeting counter between successive calls", after - before, is(1d));

    }
}
