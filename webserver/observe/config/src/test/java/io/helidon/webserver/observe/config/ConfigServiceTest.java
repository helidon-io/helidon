/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.observe.config;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.Status;
import io.helidon.json.JsonObject;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.DirectClient;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ConfigServiceTest {
    private static final String MASKED = "*********";

    private DirectClient client;

    @AfterEach
    void closeClient() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void escapedKeyIsMaskedAfterSafeKeyWasRead() {
        createClient(Config.just(ConfigSources.create(Map.of("server.host", "localhost",
                                                            "server~1host", "private-value"))));

        assertValue("server.host", "localhost");
        assertValue("server~1host", MASKED);
        assertValue("server.host", "localhost");
        assertValue("server~1host", MASKED);
    }

    @Test
    void safeKeyIsVisibleAfterEscapedKeyWasRead() {
        createClient(Config.just(ConfigSources.create(Map.of("server.host", "localhost",
                                                            "server~1host", "private-value"))));

        assertValue("server~1host", MASKED);
        assertValue("server.host", "localhost");
        assertValue("server~1host", MASKED);
        assertValue("server.host", "localhost");
    }

    @Test
    void bulkValuesMaskEscapedKeys() {
        createClient(Config.just(ConfigSources.create(Map.of("server~1host", "private-value",
                                                            "server.port", "8080"))));

        JsonObject values = read("/config/values");

        assertThat(values.stringValue("server.host").orElseThrow(), is(MASKED));
        assertThat(values.stringValue("server.port").orElseThrow(), is("8080"));
        assertValue("server~1host", MASKED);
        assertValue("server.port", "8080");
    }

    @Test
    void leafValuesMaskEscapedKeys() {
        Config config = Config.just(ConfigSources.create(Map.of("server~1host", "private-value")));
        createClient(config.get("server~1host"));

        JsonObject values = read("/config/values");

        assertThat(values.stringValue("server.host").orElseThrow(), is(MASKED));
    }

    @Test
    void customSafePatternMatchesEscapedKey() {
        Config config = Config.just(ConfigSources.create(Map.of("server.host", "private-value",
                                                               "server~1host", "explicitly-safe")));
        createClient(config, ConfigObserver.builder()
                .permitAll(true)
                .safeKeys(Set.of("server~1host"))
                .buildPrototype());

        assertValue("server~1host", "explicitly-safe");
        assertValue("server.host", MASKED);
    }

    @Test
    void secretPatternsStillMatchUnescapedNamesWithUnsafeValues() {
        Config config = Config.just(ConfigSources.create(Map.of("api~1key", "private-value",
                                                               "server~1host", "visible-value")));
        createClient(config, ConfigObserver.builder()
                .permitAll(true)
                .unsafeValues(true)
                .buildPrototype());

        assertValue("api~1key", MASKED);
        assertValue("server~1host", "visible-value");
        JsonObject values = read("/config/values");
        assertThat(values.stringValue("api.key").orElseThrow(), is(MASKED));
        assertThat(values.stringValue("server.host").orElseThrow(), is("visible-value"));
    }

    private void createClient(Config config) {
        createClient(config, ConfigObserver.builder()
                .permitAll(true)
                .buildPrototype());
    }

    private void createClient(Config config, ConfigObserverConfig observerConfig) {
        ConfigObserver observer = ConfigObserver.create(observerConfig, () -> config);
        HttpRouting.Builder routing = HttpRouting.builder();
        observer.register(null, List.of(routing), endpoint -> "/" + endpoint);
        client = new DirectClient(routing);
    }

    private void assertValue(String name, String expected) {
        JsonObject value = read("/config/values/" + name);
        assertThat(value.stringValue("name").orElseThrow(), is(name));
        assertThat("Value for " + name, value.stringValue("value").orElseThrow(), is(expected));
    }

    private JsonObject read(String path) {
        var response = client.get(path).request(JsonObject.class);
        assertThat("Response status for " + path, response.status(), is(Status.OK_200));
        return response.entity();
    }
}
