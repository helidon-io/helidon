/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.messaging.health;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.enterprise.inject.spi.CDI;
import javax.json.JsonObject;
import javax.json.JsonValue;

import io.helidon.config.mp.MpConfigSources;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.webclient.WebClient;

import static org.eclipse.microprofile.health.HealthCheckResponse.State.DOWN;
import static org.eclipse.microprofile.health.HealthCheckResponse.State.UP;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

/**
 * Tests for messaging health:
 * <pre>{@code
 * {
 *     "outcome": "UP",
 *     "status": "UP",
 *     "checks": [
 *         {
 *             "name": "messaging",
 *             "state": "UP",
 *             "status": "UP",
 *             "data": {
 *                 "test-channel-1": "UP",
 *                 "test-channel-2": "UP"
 *             }
 *         }
 *     ]
 * }</pre>
 */
public class MessagingHealthTest {

    private SeContainer container;
    private WebClient client;

    @BeforeEach
    void setUp() {
        Config config = ConfigProviderResolver.instance().getBuilder()
                .withSources(MpConfigSources.create(Map.of(
                        "mp.initializer.allow", "true"
                )))
                .build();

        ConfigProviderResolver.instance()
                .registerConfig(config, Thread.currentThread().getContextClassLoader());


        final SeContainerInitializer initializer = SeContainerInitializer.newInstance();
        initializer.addBeanClasses(TestMessagingBean.class);
        container = initializer.initialize();
        ServerCdiExtension server = CDI.current().select(ServerCdiExtension.class).get();
        client = WebClient.builder()
                .baseUri("http://localhost:" + server.port())
                .addReader(JsonpSupport.reader())
                .build();
    }

    @AfterEach
    void tearDown() {
        try {
            container.close();
        } catch (Throwable t) {
            try {
                ServerCdiExtension server = CDI.current()
                        .getBeanManager()
                        .getExtension(ServerCdiExtension.class);

                if (server.started()) {
                    SeContainer container = (SeContainer) CDI.current();
                    container.close();
                }
            } catch (IllegalStateException e) {
                //noop container is not running
            }
        }
    }

    @Test
    void alivenessWithErrorSignal() {
        TestMessagingBean bean = CDI.current().select(TestMessagingBean.class).get();

        assertMessagingHealth(UP, Map.of(
                "test-channel-1", UP,
                "test-channel-2", UP
        ));

        bean.getEmitter1().fail(new RuntimeException("BOOM!"));
        assertMessagingHealth(DOWN, Map.of(
                "test-channel-1", DOWN,
                "test-channel-2", UP
        ));

        bean.getEmitter2().fail(new RuntimeException("BOOM!"));
        assertMessagingHealth(DOWN, Map.of(
                "test-channel-1", DOWN,
                "test-channel-2", DOWN
        ));
    }

    @Test
    void alivenessWithCancelSignal() {
        TestMessagingBean bean = CDI.current().select(TestMessagingBean.class).get();

        assertMessagingHealth(UP, Map.of(
                "test-channel-1", UP,
                "test-channel-2", UP
        ));

        bean.getSubscriber1().cancel();
        assertMessagingHealth(DOWN, Map.of(
                "test-channel-1", DOWN,
                "test-channel-2", UP
        ));

        bean.getSubscriber2().cancel();
        assertMessagingHealth(DOWN, Map.of(
                "test-channel-1", DOWN,
                "test-channel-2", DOWN
        ));
    }

    private void assertMessagingHealth(HealthCheckResponse.State rootState, Map<String, HealthCheckResponse.State> channels) {
        JsonObject messaging = getHealthCheck("messaging");
        assertThat(messaging.getString("state"), equalTo(rootState.name()));
        JsonObject data = messaging.getJsonObject("data");
        channels.forEach((name, state) -> assertThat(data.getString(name), equalTo(state.name())));
    }

    private JsonObject getHealthCheck(String checkName) {
        return client.get()
                .path("/health")
                .submit()
                .await(500, TimeUnit.MILLISECONDS)
                .content()
                .as(JsonObject.class)
                .await(500, TimeUnit.MILLISECONDS)
                .getValue("/checks")
                .asJsonArray().stream()
                .filter(check -> check.asJsonObject().getString("name").equals(checkName))
                .map(JsonValue::asJsonObject)
                .findFirst()
                .orElseThrow(() -> new AssertionFailedError("Health check 'messaging' is missing!"));
    }
}
