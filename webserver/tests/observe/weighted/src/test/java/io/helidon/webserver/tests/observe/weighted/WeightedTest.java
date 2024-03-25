/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests.observe.weighted;

import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class WeightedTest {

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        routing.get("/greet", (req, res) -> res.send("Hello World!"))
                .get("/observe/*", (req, res) -> res.send("User's observe endpoint"));
    }

    @Test
    void testInfoObserver(WebClient client) {
        JsonObject jsonObject = client.get("/observe/info/name")
                .requestEntity(JsonObject.class);
        assertThat("JSON: " + jsonObject, jsonObject.getString("name"), is("name"));
        assertThat("JSON: " + jsonObject, jsonObject.getString("value"), is("ObserveTest"));

        jsonObject = client.get("/observe/info")
                .requestEntity(JsonObject.class);
        assertThat("JSON: " + jsonObject, jsonObject.getString("name"), is("ObserveTest"));
        assertThat("JSON: " + jsonObject, jsonObject.getString("description"), is("Test for observability features"));
        assertThat("JSON: " + jsonObject, jsonObject.getString("version"), is("1.0.0"));
    }

    @Test
    void testMetricsObserver(WebClient client) {
        ClientResponseTyped<String> response = client.get("/observe/metrics")
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat("Entity: " + response.entity(), response.entity(), startsWith("# HELP"));
    }

    @Test
    void testHealthObserver(WebClient client) {
        ClientResponseTyped<String> response = client.get("/observe/health")
                .request(String.class);

        assertThat(response.status(), is(Status.NO_CONTENT_204));
    }

    @Test
    void testLogObserver(WebClient client) {
        ClientResponseTyped<JsonObject> response = client.get("/observe/log/loggers")
                .request(JsonObject.class);

        assertThat(response.status(), is(Status.OK_200));
        JsonObject entity = response.entity();
        assertThat("Entity: " + entity, entity.getJsonArray("levels").getString(0), is("OFF"));
    }

    @Test
    void testLogObserverLogger(WebClient client) {
        ClientResponseTyped<JsonObject> response = client.get("/observe/log/loggers/io.helidon.webserver.ServerListener")
                .request(JsonObject.class);

        assertThat(response.status(), is(Status.OK_200));
        JsonObject entity = response.entity();
        assertThat("Entity: " + entity, entity.getJsonObject("io.helidon.webserver.ServerListener"), notNullValue());
    }

    @Test
    void testConfigObserver(WebClient client) {
        ClientResponseTyped<String> response = client.get("/observe/config/profile")
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        String entity = response.entity();
        assertThat("Entity: " + entity, entity, not("should not be seen"));
    }

    @Test
    void testConfigObserverValue(WebClient client) {
        ClientResponseTyped<String> response = client.get("/observe/config/values/app.text")
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        String entity = response.entity();
        assertThat("Entity: " + entity, entity, containsString("should be seen"));
    }

    @Test
    void testFallbackToUserRouting(WebClient client) {
        ClientResponseTyped<String> response = client.get("/observe/notthere")
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        String entity = response.entity();
        assertThat("Entity: " + entity, entity, is("User's observe endpoint"));
    }
}
