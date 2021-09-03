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

package io.helidon.tests.functional.multipleapps;

import javax.inject.Inject;
import javax.json.JsonObject;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.Test;

import io.helidon.microprofile.server.Server;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

@HelidonTest
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
        assertFalse(response.getHeaders().containsKey("filter1"));
        JsonObject jsonObject = response.readEntity(JsonObject.class);
        assertEquals("Hello World 2!", jsonObject.getString("message"),
                "default message");
    }
}
