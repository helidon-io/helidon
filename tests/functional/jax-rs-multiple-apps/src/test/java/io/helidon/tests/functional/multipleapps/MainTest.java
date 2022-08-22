/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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

package io.helidon.tests.functional.multipleapps;

import io.helidon.microprofile.tests.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@HelidonTest
@Disabled("3.0.0-JAKARTA")
class MainTest {

    @Inject
    private WebTarget target;

    @Test
    void testHelloWorld1() {
        Response response = target.path("app1")
                .path("greet1")
                .request()
                .header("who", "World 1")
                .get();
        assertEquals(response.getStatus(), 200);
        assertTrue(response.getHeaders().containsKey("sharedfilter"));
        assertTrue(response.getHeaders().containsKey("filter1"));
        assertFalse(response.getHeaders().containsKey("filter2"));
        JsonObject jsonObject = response.readEntity(JsonObject.class);
        assertEquals("Hello World 1!", jsonObject.getString("message"),
                "default message");
    }

    @Test
    void testHelloWorld2() {
        Response response = target.path("app2")
                .path("greet2")
                .request()
                .header("who", "World 2")
                .get();
        assertEquals(response.getStatus(), 200);
        assertTrue(response.getHeaders().containsKey("sharedfilter"));
        assertTrue(response.getHeaders().containsKey("filter2"));
        assertTrue(response.getHeaders().containsKey("filter3"));       // MyFeature
        assertFalse(response.getHeaders().containsKey("filter1"));
        JsonObject jsonObject = response.readEntity(JsonObject.class);
        assertEquals("Hello World 2!", jsonObject.getString("message"),
                "default message");
    }

    @Test
    void testContextApps() {
        assertThat(SharedFeature.applications(), hasItem(GreetApplication1.class));
        assertThat(SharedFeature.applications(), hasItem(GreetApplication2.class));
    }
}
