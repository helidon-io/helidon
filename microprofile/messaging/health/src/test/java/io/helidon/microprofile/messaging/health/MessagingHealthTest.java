/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
import javax.enterprise.inject.spi.CDI;
import javax.json.JsonObject;
import javax.json.JsonValue;

import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.health.HealthCdiExtension;
import io.helidon.microprofile.messaging.MessagingCdiExtension;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddBeans;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.AddExtensions;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import io.helidon.webclient.WebClient;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import static io.helidon.microprofile.messaging.health.TestMessagingBean.CHANNEL_1;
import static io.helidon.microprofile.messaging.health.TestMessagingBean.CHANNEL_2;
import static org.eclipse.microprofile.health.HealthCheckResponse.State.DOWN;
import static org.eclipse.microprofile.health.HealthCheckResponse.State.UP;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

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
@HelidonTest(resetPerTest = true)
@DisableDiscovery
@AddBeans({
        @AddBean(MessagingLivenessCheck.class),
        @AddBean(TestMessagingBean.class),
})
@AddExtensions({
        @AddExtension(ConfigCdiExtension.class),
        @AddExtension(ServerCdiExtension.class),
        @AddExtension(JaxRsCdiExtension.class),
        @AddExtension(HealthCdiExtension.class),
        @AddExtension(MessagingCdiExtension.class),
})
public class MessagingHealthTest {

    private static final String ERROR_MESSAGE = "BOOM!";

    private WebClient client;

    @BeforeEach
    void setUp() {
        ServerCdiExtension server = CDI.current().select(ServerCdiExtension.class).get();
        client = WebClient.builder()
                .baseUri("http://localhost:" + server.port())
                .addReader(JsonpSupport.reader())
                .build();
    }

    @Test
    void alivenessWithErrorSignal(SeContainer container) {
        TestMessagingBean bean = container.select(TestMessagingBean.class).get();

        assertMessagingHealth(UP, Map.of(
                CHANNEL_1, UP,
                CHANNEL_2, UP
        ));

        bean.getEmitter1().fail(new RuntimeException(ERROR_MESSAGE));
        assertMessagingHealth(DOWN, Map.of(
                CHANNEL_1, DOWN,
                CHANNEL_2, UP
        ));
        assertThat(bean.getSubscriber1().error().await(200, TimeUnit.MILLISECONDS).getMessage(),
                equalTo(ERROR_MESSAGE));

        bean.getEmitter2().fail(new RuntimeException(ERROR_MESSAGE));
        assertMessagingHealth(DOWN, Map.of(
                CHANNEL_1, DOWN,
                CHANNEL_2, DOWN
        ));
        assertThat(bean.getSubscriber1().error().await(200, TimeUnit.MILLISECONDS).getMessage(),
                equalTo(ERROR_MESSAGE));
    }

    @Test
    void alivenessWithCancelSignal(SeContainer container) {
        TestMessagingBean bean = container.select(TestMessagingBean.class).get();

        assertMessagingHealth(UP, Map.of(
                CHANNEL_1, UP,
                CHANNEL_2, UP
        ));
        assertThat(bean.getEmitter1().isCancelled(), equalTo(Boolean.FALSE));
        assertThat(bean.getEmitter2().isCancelled(), equalTo(Boolean.FALSE));

        bean.getSubscriber1().cancel();
        assertMessagingHealth(DOWN, Map.of(
                CHANNEL_1, DOWN,
                CHANNEL_2, UP
        ));
        assertThat(bean.getEmitter1().isCancelled(), equalTo(Boolean.TRUE));

        bean.getSubscriber2().cancel();
        assertMessagingHealth(DOWN, Map.of(
                CHANNEL_1, DOWN,
                CHANNEL_2, DOWN
        ));
        assertThat(bean.getEmitter2().isCancelled(), equalTo(Boolean.TRUE));
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
                .await(5, TimeUnit.SECONDS)
                .content()
                .as(JsonObject.class)
                .await(500, TimeUnit.MILLISECONDS)
                .getValue("/checks")
                .asJsonArray().stream()
                .map(JsonValue::asJsonObject)
                .filter(check -> check.getString("name").equals(checkName))
                .findFirst()
                .orElseThrow(() -> new AssertionFailedError("Health check 'messaging' is missing!"));
    }
}
