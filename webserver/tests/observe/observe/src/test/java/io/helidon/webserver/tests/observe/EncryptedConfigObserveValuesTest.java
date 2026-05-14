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

package io.helidon.webserver.tests.observe;

import io.helidon.config.Config;
import io.helidon.http.Status;
import io.helidon.json.JsonObject;
import io.helidon.service.registry.Services;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.config.ConfigObserver;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class EncryptedConfigObserveValuesTest {
    private static final String DECRYPTED_VALUE = "\u00dcberstring";

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        Config config = Services.get(Config.class);

        server.featuresDiscoverServices(false)
                .addFeature(ObserveFeature.builder()
                                    .addObserver(ConfigObserver.builder()
                                                         .permitAll(true)
                                                         .endpoint("defaultConfig")
                                                         .name("defaultConfig")
                                                         .build())
                                    .addObserver(ConfigObserver.builder()
                                                         .config(config.get("unsafe-config-observer"))
                                                         .name("unsafeConfig")
                                                         .build())
                                    .config(config.get("observe"))
                                    .build());
    }

    @Test
    void encryptedValuesAreNotReturnedAsPlainText(WebClient client) {
        ClientResponseTyped<String> response = client.get("/observe/myConfig/values")
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));

        String entity = response.entity();
        assertThat("Entity: " + entity, entity, containsString("\"db.url\":\"*********\""));
        assertThat("Entity: " + entity, entity, not(containsString(DECRYPTED_VALUE)));
    }

    @Test
    void encryptedValuesAreNotReturnedAsPlainTextByName(WebClient client) {
        ClientResponseTyped<JsonObject> response = client.get("/observe/myConfig/values/db.url")
                .request(JsonObject.class);

        assertThat(response.status(), is(Status.OK_200));

        JsonObject entity = response.entity();
        assertThat("JSON: " + entity, entity.stringValue("value").orElseThrow(), is("*********"));
    }

    @Test
    void defaultSafeKeysAreReturned(WebClient client) {
        ClientResponseTyped<JsonObject> response = client.get("/observe/defaultConfig/values/server.features.observe.endpoint")
                .request(JsonObject.class);

        assertThat(response.status(), is(Status.OK_200));

        JsonObject entity = response.entity();
        assertThat("JSON: " + entity, entity.stringValue("value").orElseThrow(), is("/observe"));
    }

    @Test
    void unsafeValuesReturnNonSecretValues(WebClient client) {
        ClientResponseTyped<JsonObject> response = client.get("/observe/unsafeConfig/values/db.url")
                .request(JsonObject.class);

        assertThat(response.status(), is(Status.OK_200));

        JsonObject entity = response.entity();
        assertThat("JSON: " + entity, entity.stringValue("value").orElseThrow(), is(DECRYPTED_VALUE));
    }

    @Test
    void secretsOverrideSafeKeys(WebClient client) {
        ClientResponseTyped<JsonObject> response = client.get("/observe/myConfig/values/app.safe-secret-text")
                .request(JsonObject.class);

        assertThat(response.status(), is(Status.OK_200));

        JsonObject entity = response.entity();
        assertThat("JSON: " + entity, entity.stringValue("value").orElseThrow(), is("*********"));
    }

    @Test
    void unsafeValuesStillObfuscateDefaultSecretsWhenSecretsConfigured(WebClient client) {
        ClientResponseTyped<JsonObject> response = client.get("/observe/unsafeConfig/values/app.some-password")
                .request(JsonObject.class);

        assertThat(response.status(), is(Status.OK_200));

        JsonObject entity = response.entity();
        assertThat("JSON: " + entity, entity.stringValue("value").orElseThrow(), is("*********"));
    }

    @Test
    void unsafeValuesStillObfuscateDefaultSecretsByCanonicalKey(WebClient client) {
        ClientResponseTyped<JsonObject> response = client.get("/observe/unsafeConfig/values/app.some-password.")
                .request(JsonObject.class);

        assertThat(response.status(), is(Status.OK_200));

        JsonObject entity = response.entity();
        assertThat("JSON: " + entity, entity.stringValue("value").orElseThrow(), is("*********"));
    }

    @Test
    void unsafeValuesStillObfuscateConfiguredSecrets(WebClient client) {
        ClientResponseTyped<JsonObject> response = client.get("/observe/unsafeConfig/values/app.other-sensitive-value")
                .request(JsonObject.class);

        assertThat(response.status(), is(Status.OK_200));

        JsonObject entity = response.entity();
        assertThat("JSON: " + entity, entity.stringValue("value").orElseThrow(), is("*********"));
    }

    @Test
    void unsafeValuesStillObfuscateEscapedConfiguredSecretsByName(WebClient client) {
        ClientResponseTyped<JsonObject> response = client.get("/observe/unsafeConfig/values/app.api~1key")
                .request(JsonObject.class);

        assertThat(response.status(), is(Status.OK_200));

        JsonObject entity = response.entity();
        assertThat("JSON: " + entity, entity.stringValue("value").orElseThrow(), is("*********"));
    }
}
