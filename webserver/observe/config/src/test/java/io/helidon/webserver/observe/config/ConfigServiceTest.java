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

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigNode;
import io.helidon.http.Status;
import io.helidon.json.JsonObject;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.DirectClient;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ConfigServiceTest {
    private static final String MASKED = "*********";

    private DirectClient client;
    private ServiceRegistryManager registryManager;

    @AfterEach
    void closeClient() {
        if (client != null) {
            client.close();
        }
        if (registryManager != null) {
            registryManager.shutdown();
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

    @Test
    void commonConfigAdapterMasksSingleSecretWithUnsafeValues() {
        Config config = Config.just(ConfigSources.create(Map.of("db.password", "private-value")));
        createClient(wrapCommonOnly(config), ConfigObserver.builder()
                .permitAll(true)
                .unsafeValues(true)
                .buildPrototype());

        assertValue("db.password", MASKED);
    }

    @Test
    void commonConfigAdapterMasksBulkSecretWithUnsafeValues() {
        Config config = Config.just(ConfigSources.create(Map.of("db~1password", "private-value")));
        createClient(wrapCommonOnly(config), ConfigObserver.builder()
                .permitAll(true)
                .unsafeValues(true)
                .buildPrototype());

        JsonObject values = read("/config/values");
        assertThat(values.keysAsStrings(), is(Set.of("db.password")));
        assertThat(values.stringValue("db.password").orElseThrow(), is(MASKED));
    }

    @Test
    void socketListLeavesAreSafeAndAdjacentValuesRemainMasked() {
        var sockets = ConfigNode.ListNode.builder()
                .addValue("admin")
                .addValue("public")
                .build();
        var observe = ConfigNode.ObjectNode.builder()
                .addList("sockets", sockets)
                .addValue("password", "private-password")
                .addValue("custom", "private-value")
                .build();
        var configNode = ConfigNode.ObjectNode.builder()
                .addObject("server.features.observe", observe)
                .build();
        createClient(Config.just(ConfigSources.create(configNode)));

        JsonObject values = read("/config/values");

        assertThat(values.stringValue("server.features.observe.sockets.0").orElseThrow(), is("admin"));
        assertThat(values.stringValue("server.features.observe.sockets.1").orElseThrow(), is("public"));
        assertThat(values.stringValue("server.features.observe.password").orElseThrow(), is(MASKED));
        assertThat(values.stringValue("server.features.observe.custom").orElseThrow(), is(MASKED));
        assertValue("server.features.observe.sockets.0", "admin");
        assertValue("server.features.observe.sockets.1", "public");
    }

    @Test
    void nonIndexedSocketChildrenRemainMasked() {
        createClient(Config.just(ConfigSources.create(Map.of("server.features.observe.sockets.custom", "private-value",
                                                            "server.features.observe.sockets.0.password", "private-password"))));

        JsonObject values = read("/config/values");

        assertThat(values.stringValue("server.features.observe.sockets.custom").orElseThrow(), is(MASKED));
        assertThat(values.stringValue("server.features.observe.sockets.0.password").orElseThrow(), is(MASKED));
    }

    @Test
    void scalarSocketValueRemainsSafe() {
        createClient(Config.just(ConfigSources.create(Map.of("server.features.observe.sockets", "admin"))));

        assertValue("server.features.observe.sockets", "admin");
    }

    @SuppressWarnings("removal")
    private static Config wrapCommonOnly(Config config) {
        var common = (io.helidon.common.config.Config) Proxy.newProxyInstance(
                ConfigServiceTest.class.getClassLoader(),
                new Class<?>[] {io.helidon.common.config.Config.class},
                (proxy, method, args) -> method.invoke(config, args));
        return Config.config(common);
    }

    private void createClient(Config config) {
        createClient(config, ConfigObserver.builder()
                .permitAll(true)
                .buildPrototype());
    }

    private void createClient(Config config, ConfigObserverConfig observerConfig) {
        registryManager = ServiceRegistryManager.create();
        GlobalServiceRegistry.registry(registryManager.registry());
        Services.set(Config.class, config);

        ConfigObserver observer = ConfigObserver.create(observerConfig);
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
